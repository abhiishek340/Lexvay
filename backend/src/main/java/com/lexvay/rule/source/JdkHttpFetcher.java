package com.lexvay.rule.source;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import org.springframework.stereotype.Component;

/**
 * {@link HttpFetcher} on the JDK's HTTP client (D-181). Connecting gives up after 10 seconds and the whole response,
 * body included, must arrive within 30. Redirects are followed by hand, at most three, and the {@link UrlPolicy}
 * checks every hop, so a redirect to an internal address is refused like a direct one. A body of more than 20 MB
 * is refused while it is read, not after it is in memory.
 */
@Component
public class JdkHttpFetcher implements HttpFetcher {

    static final int MAX_BODY_BYTES = 20 * 1024 * 1024;
    static final int MAX_REDIRECTS = 3;
    private static final Set<Integer> REDIRECTS = Set.of(301, 302, 303, 307, 308);
    private static final Duration RESPONSE_TIMEOUT = Duration.ofSeconds(30);

    private final UrlPolicy policy;
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public JdkHttpFetcher(UrlPolicy policy) {
        this.policy = policy;
    }

    @Override
    public Fetched fetch(URI url) {
        URI current = url;
        for (int redirects = 0; ; redirects++) {
            policy.requireAllowed(current);
            HttpResponse<byte[]> response = send(current);
            var location = response.headers().firstValue("Location");
            if (!REDIRECTS.contains(response.statusCode()) || location.isEmpty()) {
                return new Fetched(response.statusCode(), response.headers().firstValue("Content-Type").orElse(null),
                        response.body());
            }
            if (redirects == MAX_REDIRECTS) {
                throw new FetchRejected("The address redirects more than " + MAX_REDIRECTS + " times");
            }
            current = resolve(current, location.get());
        }
    }

    private HttpResponse<byte[]> send(URI url) {
        HttpRequest request = HttpRequest.newBuilder(url)
                .timeout(RESPONSE_TIMEOUT)
                .header("User-Agent", "Lexvay rule source monitor")
                .GET()
                .build();
        try {
            return client.send(request, info -> new CappedBody());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FetchRejected("The fetch was interrupted", e);
        } catch (IOException e) {
            throw new FetchRejected(describe(e), e);
        }
    }

    private static URI resolve(URI from, String location) {
        try {
            return from.resolve(location);
        } catch (IllegalArgumentException e) {
            throw new FetchRejected("The address redirects to a location that is not a valid address", e);
        }
    }

    private static String describe(IOException e) {
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
