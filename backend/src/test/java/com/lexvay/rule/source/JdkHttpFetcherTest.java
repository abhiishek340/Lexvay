package com.lexvay.rule.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The real fetcher against a server on the loopback interface of this machine (nothing leaves it), with the address
 * policy replaced so that loopback is reachable. The policy itself is tested in {@link SsrfGuardTest}; here it is
 * only shown to be consulted on every hop.
 */
class JdkHttpFetcherTest {

    private static final MonitorSettings SETTINGS = new MonitorSettings("https://monitor.example.test/about", 0, "9.9.9");

    private HttpServer server;
    private String base;
    private final List<URI> checked = new ArrayList<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        base = "http://127.0.0.1:" + server.getAddress().getPort();
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private JdkHttpFetcher fetcher() {
        return new JdkHttpFetcher(checked::add, SETTINGS);
    }

    private HttpFetcher.FetchRequest get(String path) {
        return new HttpFetcher.FetchRequest(URI.create(base + path), new FetchScope(List.of("127.0.0.1"), List.of()), Validators.NONE);
    }

    private HttpFetcher.FetchRequest get(String path, FetchScope scope, Validators validators) {
        return new HttpFetcher.FetchRequest(URI.create(base + path), scope, validators);
    }

    private void serve(String path, int status, String contentType, byte[] body) {
        server.createContext(path, exchange -> reply(exchange, status, contentType, body));
    }

    private void redirect(String path, String location) {
        server.createContext(path, exchange -> {
            exchange.getResponseHeaders().add("Location", location);
            reply(exchange, 302, "text/plain", new byte[0]);
        });
    }

    private static void reply(HttpExchange exchange, int status, String contentType, byte[] body) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", contentType);
        exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
        if (body.length > 0) {
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        }
        exchange.close();
    }

    @Test
    void returnsStatusContentTypeAndBody() {
        serve("/rules", 200, "text/html; charset=utf-8", "<p>Rule</p>".getBytes(StandardCharsets.UTF_8));

        var fetched = fetcher().fetch(get("/rules"));

        assertThat(fetched.status()).isEqualTo(200);
        assertThat(fetched.contentType()).isEqualTo("text/html; charset=utf-8");
        assertThat(new String(fetched.body(), StandardCharsets.UTF_8)).isEqualTo("<p>Rule</p>");
    }

    @Test
    void anErrorStatusIsReturnedNotThrown() {
        serve("/gone", 500, "text/plain", "oops".getBytes(StandardCharsets.UTF_8));

        assertThat(fetcher().fetch(get("/gone")).status()).isEqualTo(500);
    }

    @Test
    void redirectsAreFollowedAndEveryHopIsChecked() {
        redirect("/a", base + "/b");
        redirect("/b", "/c");
        serve("/c", 200, "text/plain", "final".getBytes(StandardCharsets.UTF_8));

        var fetched = fetcher().fetch(get("/a"));

        assertThat(new String(fetched.body(), StandardCharsets.UTF_8)).isEqualTo("final");
        assertThat(checked).extracting(URI::getPath).containsExactly("/a", "/b", "/c");
    }

    @Test
    void aFourthRedirectIsRefused() {
        redirect("/r0", "/r1");
        redirect("/r1", "/r2");
        redirect("/r2", "/r3");
        redirect("/r3", "/end");
        serve("/end", 200, "text/plain", "x".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> fetcher().fetch(get("/r0")))
                .isInstanceOf(FetchRejected.class).hasMessageContaining("redirects more than 3 times");
        assertThat(fetcher().fetch(get("/r1")).status()).as("three redirects are allowed").isEqualTo(200);
    }

    @Test
    void aRedirectToAForbiddenAddressIsRefusedBeforeItIsFollowed() {
        redirect("/start", "/forbidden");
        serve("/forbidden", 200, "text/plain", "secret".getBytes(StandardCharsets.UTF_8));
        var refusing = new JdkHttpFetcher(url -> {
            if (url.getPath().equals("/forbidden")) {
                throw new FetchRejected("not allowed");
            }
        }, SETTINGS);

        assertThatThrownBy(() -> refusing.fetch(get("/start")))
                .isInstanceOf(FetchRejected.class).hasMessage("not allowed");
    }

    @Test
    void theRealPolicyRefusesTheLoopbackServer() {
        serve("/rules", 200, "text/plain", "x".getBytes(StandardCharsets.UTF_8));
        var guarded = new JdkHttpFetcher(new SsrfGuard(new SystemHostResolver()), SETTINGS);

        assertThatThrownBy(() -> guarded.fetch(get("/rules")))
                .isInstanceOf(FetchRejected.class).hasMessageContaining("internal address");
    }

    @Test
    void aBodyOverTheCapIsRefusedAndOneAtTheCapIsRead() {
        byte[] atCap = new byte[JdkHttpFetcher.MAX_BODY_BYTES];
        byte[] overCap = new byte[JdkHttpFetcher.MAX_BODY_BYTES + 1];
        serve("/at", 200, "application/pdf", atCap);
        serve("/over", 200, "application/pdf", overCap);

        assertThat(fetcher().fetch(get("/at")).body()).hasSize(JdkHttpFetcher.MAX_BODY_BYTES);
        assertThatThrownBy(() -> fetcher().fetch(get("/over")))
                .isInstanceOf(FetchRejected.class).hasMessageContaining("larger than 20 MB");
    }

    @Test
    void aServerThatCannotBeReachedIsRefusedWithAMessage() {
        server.stop(0);

        assertThatThrownBy(() -> fetcher().fetch(get("/rules")))
                .isInstanceOf(FetchRejected.class).hasMessageStartingWith("Could not fetch the address");
    }

    @Test
    void everyRequestNamesTheMonitorAndItsContact() {
        List<String> agents = new ArrayList<>();
        server.createContext("/rules", exchange -> {
            agents.add(exchange.getRequestHeaders().getFirst("User-Agent"));
            reply(exchange, 200, "text/plain", "x".getBytes(StandardCharsets.UTF_8));
        });
        redirect("/old", "/rules");

        fetcher().fetch(get("/old"));

        assertThat(agents).containsExactly("LexvayMonitor/9.9.9 (+https://monitor.example.test/about)");
        assertThat(new MonitorSettings("", 30, "1.2.3").userAgent()).as("no contact configured").isEqualTo("LexvayMonitor/1.2.3");
    }

    @Test
    void validatorsAreSentAsAConditionalRequestAndA304IsReturnedNotThrown() {
        List<String> conditions = new ArrayList<>();
        server.createContext("/page", exchange -> {
            conditions.add(exchange.getRequestHeaders().getFirst("If-None-Match") + "|"
                    + exchange.getRequestHeaders().getFirst("If-Modified-Since"));
            exchange.getResponseHeaders().add("ETag", "\"v2\"");
            reply(exchange, 304, "text/plain", new byte[0]);
        });
        var scope = new FetchScope(List.of("127.0.0.1"), List.of());

        var fetched = fetcher().fetch(get("/page", scope, new Validators("\"v1\"", "Tue, 01 Dec 2026 10:00:00 GMT")));
        fetcher().fetch(get("/page", scope, Validators.NONE));

        assertThat(fetched.status()).isEqualTo(304);
        assertThat(fetched.validators().etag()).isEqualTo("\"v2\"");
        assertThat(conditions).containsExactly("\"v1\"|Tue, 01 Dec 2026 10:00:00 GMT", "null|null");
    }

    @Test
    void theServersValidatorsAndRetryAfterAreReturned() {
        server.createContext("/busy", exchange -> {
            exchange.getResponseHeaders().add("Retry-After", "120");
            exchange.getResponseHeaders().add("Last-Modified", "Mon, 30 Nov 2026 08:00:00 GMT");
            reply(exchange, 503, "text/plain", "busy".getBytes(StandardCharsets.UTF_8));
        });

        var fetched = fetcher().fetch(get("/busy"));

        assertThat(fetched.status()).isEqualTo(503);
        assertThat(fetched.retryAfter()).isEqualTo(Duration.ofSeconds(120));
        assertThat(fetched.validators()).isEqualTo(new Validators(null, "Mon, 30 Nov 2026 08:00:00 GMT"));
    }

    @Test
    void retryAfterIsASecondsCountOrADateAndNothingElse() {
        Instant now = Instant.parse("2026-12-01T10:00:00Z");

        assertThat(JdkHttpFetcher.parseRetryAfter("90", now)).contains(Duration.ofSeconds(90));
        assertThat(JdkHttpFetcher.parseRetryAfter(" 0 ", now)).contains(Duration.ZERO);
        assertThat(JdkHttpFetcher.parseRetryAfter("Tue, 01 Dec 2026 10:05:00 GMT", now)).contains(Duration.ofMinutes(5));
        assertThat(JdkHttpFetcher.parseRetryAfter("Tue, 01 Dec 2026 09:00:00 GMT", now)).as("a date in the past").contains(Duration.ZERO);
        assertThat(JdkHttpFetcher.parseRetryAfter("soon", now)).isEmpty();
        assertThat(JdkHttpFetcher.parseRetryAfter("-5", now)).isEmpty();
        assertThat(JdkHttpFetcher.parseRetryAfter("99999999999", now)).isEmpty();
    }

    @Test
    void aRedirectToAnotherHostIsRefusedBeforeItIsFollowed() {
        redirect("/start", "http://localhost:" + server.getAddress().getPort() + "/elsewhere");
        serve("/elsewhere", 200, "text/plain", "x".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> fetcher().fetch(get("/start")))
                .isInstanceOf(FetchRejected.class).hasMessageContaining("localhost").hasMessageContaining("allowed hosts");
    }

    @Test
    void everyHopAndTheFinalAddressMustMatchAnAllowedPath() {
        var scope = new FetchScope(List.of("127.0.0.1"), List.of("/rules/"));
        redirect("/rules/a", "/rules/b");
        serve("/rules/b", 200, "text/plain", "inside".getBytes(StandardCharsets.UTF_8));
        redirect("/rules/out", "/private/x");
        serve("/private/x", 200, "text/plain", "outside".getBytes(StandardCharsets.UTF_8));
        redirect("/rules/up", "/rules/../private/x");
        serve("/start", 200, "text/plain", "x".getBytes(StandardCharsets.UTF_8));

        assertThat(fetcher().fetch(get("/rules/a", scope, Validators.NONE)).status()).isEqualTo(200);
        assertThatThrownBy(() -> fetcher().fetch(get("/rules/out", scope, Validators.NONE)))
                .isInstanceOf(FetchRejected.class).hasMessageContaining("/private/x").hasMessageContaining("allowed paths");
        assertThatThrownBy(() -> fetcher().fetch(get("/rules/up", scope, Validators.NONE)))
                .as("a path walked out of the prefix with ..").isInstanceOf(FetchRejected.class).hasMessageContaining("allowed paths");
        assertThatThrownBy(() -> fetcher().fetch(get("/start", scope, Validators.NONE)))
                .as("the first address is a hop too").isInstanceOf(FetchRejected.class).hasMessageContaining("/start");
    }
}
