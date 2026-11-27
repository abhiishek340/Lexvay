package com.lexvay.auth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.TimeMeter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * The single rate-limiting filter (D-092): login attempts per client address, authenticated API calls and
 * uploads per user. A rejected request gets a 429 problem detail with {@code Retry-After}. Buckets live in a
 * bounded Caffeine cache that evicts idle keys, so the number of distinct addresses or users cannot grow
 * memory without limit.
 */
public final class RateLimitFilter extends OncePerRequestFilter {

    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final PathPatternRequestMatcher LOGIN_POST =
            PathPatternRequestMatcher.pathPattern(HttpMethod.POST, SecurityConfig.LOGIN_URL);
    private static final PathPatternRequestMatcher OIDC_CALLBACK =
            PathPatternRequestMatcher.pathPattern(HttpMethod.GET, SecurityConfig.OIDC_CALLBACK_PATTERN);
    private static final PathPatternRequestMatcher API = PathPatternRequestMatcher.pathPattern("/api/**");
    private static final PathPatternRequestMatcher UPLOAD =
            PathPatternRequestMatcher.pathPattern(HttpMethod.POST, "/api/v1/programs/*/documents");

    private enum Budget { LOGIN, API, UPLOAD }

    private final RateLimitProperties limits;
    private final ProblemWriter problems;
    private final TimeMeter clock;
    private final Cache<String, Bucket> buckets;

    /** @param nanoClock monotonic nanoseconds; {@code System::nanoTime} in production, a fake in tests */
    public RateLimitFilter(RateLimitProperties limits, ProblemWriter problems, LongSupplier nanoClock) {
        this.limits = limits;
        this.problems = problems;
        this.clock = new TimeMeter() {
            @Override
            public long currentTimeNanos() {
                return nanoClock.getAsLong();
            }

            @Override
            public boolean isWallClockBased() {
                return false;
            }
        };
        this.buckets = Caffeine.newBuilder()
                .maximumSize(limits.maxKeys())
                .expireAfterAccess(limits.idleExpiry())
                .ticker(nanoClock::getAsLong)
                .build();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        ConsumptionProbe rejected = firstRejection(request);
        if (rejected == null) {
            chain.doFilter(request, response);
            return;
        }
        long retryAfterSeconds = Math.max(1, TimeUnit.NANOSECONDS.toSeconds(rejected.getNanosToWaitForRefill() + 999_999_999L));
        response.setHeader("Retry-After", Long.toString(retryAfterSeconds));
        problems.write(response, HttpStatus.TOO_MANY_REQUESTS, "Too many requests. Try again in " + retryAfterSeconds + " seconds");
    }

    /** Consumes one token from every budget that applies to the request; returns the first refusal, if any. */
    private ConsumptionProbe firstRejection(HttpServletRequest request) {
        if (LOGIN_POST.matches(request) || OIDC_CALLBACK.matches(request)) {
            return refusal(consume(Budget.LOGIN, request.getRemoteAddr(), limits.loginPerMinute()));
        }
        var user = CurrentUser.get();
        if (user.isEmpty() || !API.matches(request)) {
            return null;
        }
        String userKey = user.get().id().toString();
        ConsumptionProbe api = consume(Budget.API, userKey, limits.apiPerMinute());
        if (!api.isConsumed() || !UPLOAD.matches(request)) {
            return refusal(api);
        }
        return refusal(consume(Budget.UPLOAD, userKey, limits.uploadPerMinute()));
    }

    private static ConsumptionProbe refusal(ConsumptionProbe probe) {
        return probe.isConsumed() ? null : probe;
    }

    private ConsumptionProbe consume(Budget budget, String key, int perMinute) {
        Bucket bucket = buckets.get(budget + ":" + key, k -> newBucket(perMinute));
        return bucket.tryConsumeAndReturnRemaining(1);
    }

    private Bucket newBucket(int perMinute) {
        return Bucket.builder()
                .withCustomTimePrecision(clock)
                .addLimit(limit -> limit.capacity(perMinute).refillGreedy(perMinute, WINDOW))
                .build();
    }

    /** For tests: runs pending evictions and returns the number of live buckets. */
    long bucketCount() {
        buckets.cleanUp();
        return buckets.estimatedSize();
    }
}
