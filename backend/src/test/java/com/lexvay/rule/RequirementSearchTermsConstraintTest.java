package com.lexvay.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lexvay.common.AbstractDatabaseTest;
import com.lexvay.common.SchemaFixtures;
import com.lexvay.common.TenantContext;
import com.lexvay.common.TestTenants;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/** V014: the database holds the limits of the search terms (at most 12, each 1 to 40 characters after trimming) whoever writes them. */
class RequirementSearchTermsConstraintTest extends AbstractDatabaseTest {

    @Autowired TestTenants tenants;
    @Autowired SchemaFixtures fixtures;
    @Autowired JdbcClient jdbc;

    SchemaFixtures.Chain chain;

    @BeforeEach
    void setUp() {
        var t = tenants.create("terms");
        chain = fixtures.insertChain(t.tenantId(), t.admin().id());
    }

    private int setTerms(String... terms) {
        return TenantContext.runAs(chain.tenantId(), () -> jdbc.sql("UPDATE requirement SET search_terms = :terms WHERE id = :id")
                .param("terms", terms).param("id", chain.requirementId()).update());
    }

    private boolean valid(String... terms) {
        return TenantContext.runAs(chain.tenantId(), () -> jdbc.sql("SELECT search_terms_valid(:terms)").param("terms", terms).query(Boolean.class).single());
    }

    @Test
    void aRequirementStartsWithNoTerms() {
        String[] terms = TenantContext.runAs(chain.tenantId(), () -> jdbc.sql("SELECT search_terms FROM requirement WHERE id = :id")
                .param("id", chain.requirementId()).query((rs, i) -> (String[]) rs.getArray(1).getArray()).single());

        assertThat(terms).isEmpty();
    }

    @Test
    void twelveTermsAndTermsOfOneToFortyCharactersAreAccepted() {
        assertThat(setTerms(IntStream.rangeClosed(1, 12).mapToObj(i -> "term " + i).toArray(String[]::new))).isEqualTo(1);
        assertThat(setTerms("x", "y".repeat(40), "  padded  ")).isEqualTo(1);
        assertThat(setTerms()).isEqualTo(1);
    }

    @Test
    void thirteenTermsAreRejected() {
        assertThatThrownBy(() -> setTerms(IntStream.rangeClosed(1, 13).mapToObj(i -> "term " + i).toArray(String[]::new)))
                .hasMessageContaining("requirement_search_terms_valid");
    }

    @Test
    void aTermOfMoreThanFortyCharactersAnEmptyOneOrABlankOneIsRejected() {
        assertThatThrownBy(() -> setTerms("fine", "z".repeat(41))).hasMessageContaining("requirement_search_terms_valid");
        assertThatThrownBy(() -> setTerms("fine", "")).hasMessageContaining("requirement_search_terms_valid");
        assertThatThrownBy(() -> setTerms("fine", "   ")).hasMessageContaining("requirement_search_terms_valid");
        assertThatThrownBy(() -> setTerms("fine", null)).hasMessageContaining("requirement_search_terms_valid");
    }

    @Test
    void theFunctionAnswersTheSameQuestionDirectly() {
        assertThat(valid("a", "b")).isTrue();
        assertThat(valid()).isTrue();
        assertThat(valid("x".repeat(41))).isFalse();
        assertThat(valid(IntStream.rangeClosed(1, 13).mapToObj(Integer::toString).toArray(String[]::new))).isFalse();
    }
}
