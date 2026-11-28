package com.lexvay.rule.source;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Decides whether the monitor may make a request to a host now (RG-2, D-569 to D-571): the host's robots.txt, fetched
 * at most daily, must allow the path, and the host must not have been asked within the minimum interval, by any
 * instance, for any tenant. A request that has to wait is not slept on; the caller puts its job back in the queue.
 */
@Component
public class HostAdmission {

    /** How long a robots.txt is trusted before it is fetched again. */
    static final Duration ROBOTS_MAX_AGE = Duration.ofHours(24);

    /** The longest wait a server can ask for with Retry-After that is honoured; for a longer one we ask again after this. */
    static final Duration MAX_HOLD = Duration.ofDays(7);

    /** The answer: go ahead (the host's request slot is taken), wait, or do not ask at all. */
    public sealed interface Decision {
    }

    public record Admitted() implements Decision {
    }

    /** The host was asked too recently, or asked us to wait. Nothing was taken. */
    public record Wait(Duration duration, String reason) implements Decision {
    }

    /** The host's robots.txt forbids the path for the monitor. */
    public record Disallowed(String reason) implements Decision {
    }

    /** robots.txt could not be read, so nothing may be assumed; try again later. */
    public record Unavailable(String reason) implements Decision {
    }

    private final SourceHostRepository hosts;
    private final HttpFetcher fetcher;
    private final MonitorSettings settings;

    public HostAdmission(SourceHostRepository hosts, HttpFetcher fetcher, MonitorSettings settings) {
        this.hosts = hosts;
        this.fetcher = fetcher;
        this.settings = settings;
    }

    /** Asks for permission to fetch {@code url}. Only {@link Admitted} uses up the host's request slot. */
    public Decision admit(URI url) {
        String host = FetchScope.hostOf(url);
        Optional<SourceHostRepository.CachedRobots> cached = hosts.freshRobots(host, ROBOTS_MAX_AGE);
        if (cached.isPresent()) {
            Optional<Decision> refusal = refusal(host, url, cached.get());
            if (refusal.isPresent()) {
                return refusal.get();
            }
        }
        Duration wait = hosts.reserve(host, settings.minHostInterval());
        if (!wait.isZero()) {
            return new Wait(wait, host + " was asked too recently; the next request to it is allowed in " + wait.toSeconds() + " seconds");
        }
        return cached.isPresent() ? new Admitted() : afterReadingRobots(host, url);
    }

    /** Remembers that {@code host} asked us to wait, so that no source on it is fetched sooner. */
    public void hold(URI url, Duration requested) {
        hosts.hold(FetchScope.hostOf(url), requested.compareTo(MAX_HOLD) > 0 ? MAX_HOLD : requested);
    }

    /** Fetches the host's robots.txt, remembers what it said, and decides on it. */
    private Decision afterReadingRobots(String host, URI url) {
        URI robotsUrl = url.resolve("/robots.txt");
        HttpFetcher.Fetched robots;
        try {
            robots = fetcher.fetch(new HttpFetcher.FetchRequest(robotsUrl, FetchScope.hostOnly(host), Validators.NONE));
        } catch (FetchRejected e) {
            return new Unavailable("robots.txt of " + host + " could not be fetched: " + e.getMessage());
        }
        int status = robots.status();
        SourceHostRepository.CachedRobots read;
        if (status >= 200 && status < 300) {
            read = new SourceHostRepository.CachedRobots(status, new String(robots.body(), StandardCharsets.UTF_8));
        } else if (status >= 400 && status < 500 && status != 429) {
            read = new SourceHostRepository.CachedRobots(status, null);
        } else {
            if (robots.retryAfter() != null) {
                hold(robotsUrl, robots.retryAfter());
            }
            return new Unavailable("robots.txt of " + host + " answered HTTP " + status + "; nothing is fetched from it until it can be read");
        }
        hosts.saveRobots(host, read.status(), read.body());
        return refusal(host, url, read).orElseGet(Admitted::new);
    }

    private static Optional<Decision> refusal(String host, URI url, SourceHostRepository.CachedRobots robots) {
        if (robots.body() == null) {
            return Optional.empty();
        }
        RobotsRules.Verdict verdict = RobotsRules.parse(robots.body()).check(pathAndQuery(url));
        return verdict.allowed() ? Optional.empty() : Optional.of(new Disallowed(
                "robots.txt of " + host + " forbids " + pathAndQuery(url) + " for " + MonitorSettings.PRODUCT + " (" + verdict.rule() + ")"));
    }

    /** The request target robots.txt rules are matched against. */
    static String pathAndQuery(URI url) {
        String path = url.getRawPath() == null || url.getRawPath().isEmpty() ? "/" : url.getRawPath();
        return url.getRawQuery() == null ? path : path + "?" + url.getRawQuery();
    }
}
