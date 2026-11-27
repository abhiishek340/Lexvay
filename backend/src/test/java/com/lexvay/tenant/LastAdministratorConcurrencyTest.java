package com.lexvay.tenant;

import static com.lexvay.common.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import com.lexvay.auth.LexvayUser;
import com.lexvay.auth.Role;
import com.lexvay.common.AbstractDatabaseTest;
import com.lexvay.common.TenantContext;
import com.lexvay.common.TestTenants;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * Two administrators remove each other at the same moment: each sees "the other one" still in place, so without the
 * lock on the tenant's active administrators both changes would pass and the tenant would have none (D-376).
 */
class LastAdministratorConcurrencyTest extends AbstractDatabaseTest {

    private static final int ROUNDS = 30;

    @Autowired MockMvcTester mvc;
    @Autowired TestTenants tenants;
    @Autowired TenantBootstrapService bootstrap;
    @Autowired JdbcClient jdbc;

    private int remove(LexvayUser actor, UUID target, boolean byRole) {
        var request = byRole
                ? mvc.put().uri("/api/v1/users/" + target + "/role").content("{\"role\":\"REVIEWER\"}")
                : mvc.put().uri("/api/v1/users/" + target + "/active").content("{\"active\":false}");
        return request.with(user(actor)).with(csrf()).contentType(MediaType.APPLICATION_JSON).exchange().getResponse().getStatus();
    }

    @Test
    void twoAdministratorsRemovingEachOtherLeaveOneActiveAdministrator() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                var t = tenants.create("lastadmin");
                LexvayUser first = t.admin();
                LexvayUser second = bootstrap.createUser(t.tenantId(), first.id(), "second-" + UUID.randomUUID() + "@lastadmin.test",
                        "Second Admin", Role.ADMIN, TestTenants.PASSWORD);
                boolean byRole = round % 2 == 0;
                var barrier = new CyclicBarrier(2);
                List<Future<Integer>> outcomes = new ArrayList<>();
                outcomes.add(pool.submit(attempt(barrier, first, second.id(), byRole)));
                outcomes.add(pool.submit(attempt(barrier, second, first.id(), byRole)));
                List<Integer> statuses = List.of(outcomes.get(0).get(), outcomes.get(1).get());

                long activeAdministrators = TenantContext.runAs(t.tenantId(), () ->
                        jdbc.sql("SELECT count(*) FROM app_user WHERE role = 'ADMIN' AND active").query(Long.class).single());
                assertThat(activeAdministrators).as("round %d, statuses %s", round, statuses).isEqualTo(1);
                assertThat(statuses).as("round %d", round).containsOnlyOnce(200).allMatch(s -> s == 200 || s == 401 || s == 409);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private Callable<Integer> attempt(CyclicBarrier barrier, LexvayUser actor, UUID target, boolean byRole) {
        return () -> {
            barrier.await();
            return remove(actor, target, byRole);
        };
    }
}
