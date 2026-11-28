package com.lexvay.rule.source;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.stereotype.Component;

/**
 * {@link HttpFetcher} on the JDK's HTTP client (D-181). Connecting gives up after 10 seconds and the whole response,
 * body included, must have arrived after 30. Redirects are followed by hand, at most three, and the {@link UrlPolicy}
 * and the source's {@link FetchScope} check every hop, so a redirect to an internal address, to another host or to a
 * path the source may not use is refused like a direct request. A body of more than 20 MB is refused while it is
 * read, not after it is in memory. Every request names the monitor in its User-Agent and, when the source has
 * validators, asks conditionally (RG-2, D-566).
 */
@Component
public class JdkHttpFetcher implements HttpFetcher {

    static final int MAX_BODY_BYTES = 20 * 1024 * 1024;
    static final int MAX_REDIRECTS = 3;
    private static final Set<Integer> REDIRECTS = Set.of(301, 302, 303, 307, 308);
    private static final Duration RESPONSE_TIMEOUT = Duration.ofSeconds(30);

    private final UrlPolicy policy;
    private final MonitorSettings settings;
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public JdkHttpFetcher(UrlPolicy policy, MonitorSettings settings) {
        this.policy = policy;
        this.settings = settings;
    }

    @Override
    public Fetched fetch(FetchRequest request) {
        URI current = request.url();
        for (int redirects = 0; ; redirects++) {
            policy.requireAllowed(current);
            request.scope().requireWithin(current);
            HttpResponse<byte[]> response = send(current, request.validators());
            var location = response.headers().firstValue("Location");
            if (!REDIRECTS.contains(response.statusCode()) || location.isEmpty()) {
                return fetched(response);
            }
            if (redirects == MAX_REDIRECTS) {
                throw new FetchRejected("The address redirects more than " + MAX_REDIRECTS + " times");
            }
            current = resolve(current, location.get());
        }
    }

    private static Fetched fetched(HttpResponse<byte[]> response) {
        var headers = response.headers();
        return new Fetched(response.statusCode(), headers.firstValue("Content-Type").orElse(null), response.body(),
                new Validators(headers.firstValue("ETag").orElse(null), headers.firstValue("Last-Modified").orElse(null)),
                headers.firstValue("Retry-After").flatMap(value -> parseRetryAfter(value, Instant.now())).orElse(null));
    }

    /** The wait a {@code Retry-After} header asks for, which is a number of seconds or a date; empty when it is neither. */
    static Optional<Duration> parseRetryAfter(String value, Instant now) {
        String trimmed = value.strip();
        if (trimmed.matches("[0-9]{1,9}")) {
            return Optional.of(Duration.ofSeconds(Long.parseLong(trimmed)));
        }
        try {
            Instant at = ZonedDateTime.parse(trimmed, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
            return Optional.of(at.isAfter(now) ? Duration.between(now, at) : Duration.ZERO);
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    private HttpResponse<byte[]> send(URI url, Validators validators) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(url)
                .timeout(RESPONSE_TIMEOUT)
                .header("User-Agent", settings.userAgent())
                .GET();
        if (validators.etag() != null) {
            builder.header("If-None-Match", validators.etag());
        }
        if (validators.lastModified() != null) {
            builder.header("If-Modified-Since", validators.lastModified());
        }
        HttpRequest request = builder.build();
        CompletableFuture<HttpResponse<byte[]>> pending = client.sendAsync(request, info -> new CappedBody());
        try {
            return pending.get(RESPONSE_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            pending.cancel(true);
            throw new FetchRejected("The server did not finish answering within " + RESPONSE_TIMEOUT.toSeconds() + " seconds", e);
        } catch (ExecutionException e) {
            throw new FetchRejected(describe(e.getCause()), e.getCause());
        } catch (InterruptedException e) {
            pending.cancel(true);
            Thread.currentThread().interrupt();
            throw new FetchRejected("The fetch was interrupted", e);
        }
    }

    private static URI resolve(URI from, String location) {
        try {
            return from.resolve(location);
        } catch (IllegalArgumentException e) {
            throw new FetchRejected("The address redirects to a location that is not a valid address", e);
        }
    }

    private static String describe(Throwable e) {
        String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        return "Could not fetch the address: " + message;
    }

    /** Collects the body and gives up, cancelling the download, at the first byte beyond {@link #MAX_BODY_BYTES}. */
    private static final class CappedBody implements HttpResponse.BodySubscriber<byte[]> {

        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private final ByteArrayOutputStream body = new ByteArrayOutputStream();
        private Flow.Subscription subscription;

        @Override
        public CompletionStage<byte[]> getBody() {
            return result;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) {
                if ((long) body.size() + buffer.remaining() > MAX_BODY_BYTES) {
                    subscription.cancel();
                    result.completeExceptionally(
                            new IOException("the response is larger than " + MAX_BODY_BYTES / (1024 * 1024) + " MB"));
                    return;
                }
                byte[] chunk = new byte[buffer.remaining()];
                buffer.get(chunk);
                body.write(chunk, 0, chunk.length);
            }
        }

        @Override
        public void onError(Throwable error) {
            result.completeExceptionally(error);
        }

        @Override
        public void onComplete() {
            result.complete(body.toByteArray());
        }
    }
}
