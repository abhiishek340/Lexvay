package com.lexvay.rule.source;

import java.time.Duration;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * What each host has been asked and told (RG-2, D-569, D-570): when it was last requested, how long it asked us to
 * wait, and its robots.txt. The table is shared by every tenant and every instance and opens only under the
 * transaction-local worker flag (D-013). Each method runs in a transaction of its own, which commits at once: the row
 * lock that serialises the requests to a host is held for a few statements and never across a fetch.
 */
@Repository
public class SourceHostRepository {

    /** A host's cached robots.txt: the status it answered with and its text (null unless the answer was 2xx). */
    public record CachedRobots(int status, String body) {
    }

    private final JdbcClient jdbc;

    public SourceHostRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Takes the next request slot of {@code host} when it is free, which is {@code minInterval} after the last request
     * and after any hold the host asked for; returns zero. Otherwise takes nothing and returns how long to wait.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Duration reserve(String host, Duration minInterval) {
        enableWorker();
        jdbc.sql("INSERT INTO source_host_fetch (host) VALUES (:host) ON CONFLICT (host) DO NOTHING").param("host", host).update();
        double waitSeconds = jdbc.sql("""
                SELECT coalesce(extract(epoch FROM greatest(last_fetch_at + :interval * interval '1 second', hold_until) - now()), 0)::float8
                FROM source_host_fetch WHERE host = :host FOR UPDATE""")
                .param("interval", minInterval.toMillis() / 1000.0).param("host", host).query(Double.class).single();
        if (waitSeconds > 0) {
            return Duration.ofMillis((long) Math.ceil(waitSeconds * 1000));
        }
        jdbc.sql("UPDATE source_host_fetch SET last_fetch_at = now() WHERE host = :host").param("host", host).update();
        return Duration.ZERO;
    }

    /** Makes every request to {@code host} wait for {@code wait} from now, because the server asked for it (Retry-After). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void hold(String host, Duration wait) {
        enableWorker();
        jdbc.sql("""
                INSERT INTO source_host_fetch (host, hold_until) VALUES (:host, now() + :wait * interval '1 second')
                ON CONFLICT (host) DO UPDATE SET hold_until = greatest(source_host_fetch.hold_until, EXCLUDED.hold_until)""")
                .param("host", host).param("wait", wait.toMillis() / 1000.0).update();
    }

    /** The robots.txt of {@code host} when it was fetched within {@code maxAge}. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<CachedRobots> freshRobots(String host, Duration maxAge) {
        enableWorker();
        return jdbc.sql("""
                SELECT robots_status, robots_body FROM source_host_fetch
                WHERE host = :host AND robots_fetched_at > now() - :age * interval '1 second'""")
                .param("host", host).param("age", maxAge.toSeconds())
                .query((rs, i) -> new CachedRobots(rs.getInt("robots_status"), rs.getString("robots_body"))).optional();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveRobots(String host, int status, String body) {
        enableWorker();
        jdbc.sql("""
                INSERT INTO source_host_fetch (host, robots_status, robots_body, robots_fetched_at)
                VALUES (:host, :status, :body, now())
                ON CONFLICT (host) DO UPDATE SET robots_status = EXCLUDED.robots_status, robots_body = EXCLUDED.robots_body,
                                                 robots_fetched_at = EXCLUDED.robots_fetched_at""")
                .param("host", host).param("status", status).param("body", body).update();
    }

    private void enableWorker() {
        jdbc.sql("SELECT set_config('lexvay.worker', 'on', true)").query().singleRow();
    }
}
