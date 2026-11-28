package com.lexvay.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lexvay.common.AbstractDatabaseTest;
import com.lexvay.common.TenantContext;
import com.lexvay.common.TestTenants;
import com.lexvay.llm.LlmUsageRepository.ModelUsage;
import com.lexvay.observability.LexvayMetrics;
import com.lexvay.tenant.TenantRepository;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * The spend gate against the real database: the month's recorded cost adds up every tenant's stored calls, leaves the
 * scripted model out, and never leaves a tenant able to see another tenant's rows afterwards.
 */
class SpendGateDatabaseTest extends AbstractDatabaseTest {

    @Autowired PlatformTransactionManager transactionManager;
    @Autowired LlmUsageRepository usage;
    @Autowired TestTenants tenants;
    @Autowired JdbcClient jdbc;
    @Autowired TransactionTemplate transaction;
    @Autowired LexvayMetrics metrics;
    @Autowired TenantRepository tenantRows;
    @Autowired SpendGate spendGate;
    @Autowired LlmRefusalAudit refusalAudit;
    @Autowired ObjectMapper json;

    private static final LlmRequest REQUEST = new LlmRequest(LlmPurpose.MATCH_EVIDENCE, "MATCH:X-1", "ssss", "uuuuuuuuuu",
            JsonMapper.builder().build().createObjectNode(), 1000);

    /** A client that sends nothing: the gate reads what the recorder stored for it, and the recorder asks it whether it is metered. */
    private static LlmClient client(String model, boolean metered) {
        return new LlmClient() {
            @Override
            public LlmResponse complete(LlmRequest request) {
                throw new AssertionError("nothing is sent");
            }

            @Override
            public String modelId() {
                return model;
            }

            @Override
            public boolean metered() {
                return metered;
            }
        };
    }

    private void record(UUID tenantId, String model, int inputTokens, int outputTokens, boolean metered) {
        LlmCallRecorder recorder = new LlmCallRecorder(jdbc, json, transactionManager, client(model, metered));
        TenantContext.runAs(tenantId, () -> recorder.record(REQUEST, new LlmResponse("{}", model, inputTokens, outputTokens, 1), "match-v1", null, null, null));
    }

    private void record(UUID tenantId, String model, int inputTokens, int outputTokens) {
        record(tenantId, model, inputTokens, outputTokens, true);
    }

    private ModelUsage usageOf(String model) {
        return TenantContext.runWithoutTenant(() -> usage.meteredThisMonth(FakeLlmClient.MODEL_ID)).stream()
                .filter(u -> u.model().equals(model)).findFirst().orElseThrow();
    }

    @Test
    void theMonthsUsageAddsUpEveryTenantAndLeavesTheScriptedModelOut() {
        String model = "priced-" + UUID.randomUUID();
        var a = tenants.create("spend-a");
        var b = tenants.create("spend-b");
        record(a.tenantId(), model, 400_000, 1_000);
        record(b.tenantId(), model, 500_000, 2_000);
        record(a.tenantId(), FakeLlmClient.MODEL_ID, 9_000_000, 9_000_000);

        assertThat(usageOf(model)).isEqualTo(new ModelUsage(model, 900_000, 3_000));
        assertThat(TenantContext.runWithoutTenant(() -> usage.meteredThisMonth(FakeLlmClient.MODEL_ID)))
                .extracting(ModelUsage::model).doesNotContain(FakeLlmClient.MODEL_ID);
    }

    @Test
    void theFlagThatLetsTheGateAddUpTenantsDoesNotOutliveItsQuery() {
        var a = tenants.create("spend-flag-a");
        var b = tenants.create("spend-flag-b");
        record(a.tenantId(), "priced-flag", 10, 10);
        record(b.tenantId(), "priced-flag", 10, 10);

        long visibleToA = TenantContext.runAs(a.tenantId(), () -> transaction.execute(status -> {
            usage.meteredThisMonth(FakeLlmClient.MODEL_ID);
            return jdbc.sql("SELECT count(*) FROM llm_call").query(Long.class).single();
        }));

        assertThat(visibleToA).as("only tenant A's own call, even in the transaction that asked the gate").isEqualTo(1);
    }

    @Test
    void theGateCountsEveryTenantAgainstTheOneBudget() {
        String model = "priced-" + UUID.randomUUID();
        record(tenants.create("gate-a").tenantId(), model, 400_000, 0);
        record(tenants.create("gate-b").tenantId(), model, 500_000, 0);
        // Other tests of this run stored calls too, so every model recorded this month gets the price 1.00 and 1.00.
        var recordedUsage = TenantContext.runWithoutTenant(() -> usage.meteredThisMonth(FakeLlmClient.MODEL_ID));
        LlmRates rates = LlmRates.parse(recordedUsage.stream().map(u -> u.model() + "=1.00:1.00").collect(Collectors.joining(",")));
        TokenPrice price = rates.priceOf(model).orElseThrow();
        BigDecimal recorded = recordedUsage.stream().map(u -> price.cost(u.inputTokens(), u.outputTokens())).reduce(BigDecimal.ZERO, BigDecimal::add);
        // The request is 16 bytes (the prompts and the empty schema) and 1,000 output tokens at 1.00 per million.
        BigDecimal projected = recorded.add(price.cost(16, 1000));
        SpendGate within = new SpendGate("anthropic", true, projected, rates, usage);
        SpendGate over = new SpendGate("anthropic", true, projected.subtract(new BigDecimal("0.000001")), rates, usage);

        assertThat(recorded).as("both tenants' calls are in the month's cost").isGreaterThanOrEqualTo(new BigDecimal("0.90"));
        assertThatCode(() -> within.check(REQUEST, model)).doesNotThrowAnyException();
        assertThatThrownBy(() -> over.check(REQUEST, model)).isInstanceOfSatisfying(LlmCallBlockedException.class,
                e -> assertThat(e.reason()).isEqualTo(LlmCallBlockedException.Reason.OVER_BUDGET));
    }

    @Test
    void aLocalModelsCallsNeverMakeABilledCallCostUnknown() {
        String paid = "paid-" + UUID.randomUUID();
        String local = "local-" + UUID.randomUUID();
        var a = tenants.create("gate-local-a");
        record(a.tenantId(), paid, 100_000, 0);
        record(a.tenantId(), local, 50_000_000, 50_000_000, false);
        var recordedUsage = TenantContext.runWithoutTenant(() -> usage.meteredThisMonth(FakeLlmClient.MODEL_ID));
        // Other tests of this run stored billed calls too, so every model recorded as billed this month gets the price 1.00 and 1.00.
        LlmRates rates = LlmRates.parse(recordedUsage.stream().map(u -> u.model() + "=1.00:1.00").collect(Collectors.joining(",")));
        TokenPrice price = rates.priceOf(paid).orElseThrow();
        BigDecimal recorded = recordedUsage.stream().map(u -> price.cost(u.inputTokens(), u.outputTokens())).reduce(BigDecimal.ZERO, BigDecimal::add);
        SpendGate gate = new SpendGate("anthropic", true, recorded.add(price.cost(16, 1000)), rates, usage);

        assertThat(recordedUsage).extracting(ModelUsage::model).contains(paid).doesNotContain(local);
        assertThatCode(() -> gate.check(REQUEST, paid)).as("the local model has no price and costs nothing").doesNotThrowAnyException();
    }

    @Test
    void aStoredCallWithoutTheMeteredKeyStillCountsBecauseItPredatesTheKey() {
        String old = "old-" + UUID.randomUUID();
        var a = tenants.create("gate-old-a");
        TenantContext.runAs(a.tenantId(), () -> jdbc.sql("""
                INSERT INTO llm_call (tenant_id, purpose, model, prompt_version, request, response, input_tokens, output_tokens)
                VALUES (:tenantId, 'MATCH_EVIDENCE', :model, 'match-v1', '{"user": "u"}'::jsonb, '{}', 123, 45)
                """).param("tenantId", a.tenantId()).param("model", old).update());

        assertThat(usageOf(old)).isEqualTo(new ModelUsage(old, 123, 45));
    }

    @Test
    void aRefusedMeteredCallIsAuditedWithItsReasonEvenWhenTheJobsTransactionRollsBack() {
        var t = tenants.create("spend-audit");
        LlmClient metered = client("metered-model", true);
        // The context's own gate has the defaults: paid calls are not approved.
        LlmGateway gateway = new LlmGateway(metered, metrics, tenantRows, spendGate, refusalAudit);

        assertThatThrownBy(() -> TenantContext.runAs(t.tenantId(), () -> transaction.execute(status -> gateway.complete(REQUEST))))
                .isInstanceOfSatisfying(LlmCallBlockedException.class,
                        e -> assertThat(e.reason()).isEqualTo(LlmCallBlockedException.Reason.PAID_CALLS_NOT_APPROVED));

        var entries = TenantContext.runAs(t.tenantId(), () -> jdbc.sql(
                "SELECT entity_type, detail::text AS detail FROM audit_log WHERE action = 'LLM_CALL_REFUSED'").query().listOfRows());
        assertThat(entries).singleElement().satisfies(row -> {
            assertThat(row.get("entity_type")).isEqualTo("llm_call");
            var detail = json.readTree((String) row.get("detail"));
            assertThat(detail.get("reason").asString()).isEqualTo("PAID_CALLS_NOT_APPROVED");
            assertThat(detail.get("purpose").asString()).isEqualTo("MATCH_EVIDENCE");
            assertThat(detail.get("model").asString()).isEqualTo("metered-model");
            assertThat(row.get("detail").toString()).as("nothing of the request")
                    .doesNotContain("ssss").doesNotContain("uuuuuuuuuu").doesNotContain("MATCH:X-1");
        });
    }
}
