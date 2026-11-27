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
        return new JdkHttpFetcher(checked::add);
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

        var fetched = fetcher().fetch(URI.create(base + "/rules"));

        assertThat(fetched.status()).isEqualTo(200);
        assertThat(fetched.contentType()).isEqualTo("text/html; charset=utf-8");
        assertThat(new String(fetched.body(), StandardCharsets.UTF_8)).isEqualTo("<p>Rule</p>");
    }

    @Test
    void anErrorStatusIsReturnedNotThrown() {
        serve("/gone", 500, "text/plain", "oops".getBytes(StandardCharsets.UTF_8));

        assertThat(fetcher().fetch(URI.create(base + "/gone")).status()).isEqualTo(500);
    }

    @Test
    void redirectsAreFollowedAndEveryHopIsChecked() {
        redirect("/a", base + "/b");
        redirect("/b", "/c");
        serve("/c", 200, "text/plain", "final".getBytes(StandardCharsets.UTF_8));

        var fetched = fetcher().fetch(URI.create(base + "/a"));

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

        assertThatThrownBy(() -> fetcher().fetch(URI.create(base + "/r0")))
                .isInstanceOf(FetchRejected.class).hasMessageContaining("redirects more than 3 times");
        assertThat(fetcher().fetch(URI.create(base + "/r1")).status()).as("three redirects are allowed").isEqualTo(200);
    }

    @Test
    void aRedirectToAForbiddenAddressIsRefusedBeforeItIsFollowed() {
        redirect("/start", "/forbidden");
        serve("/forbidden", 200, "text/plain", "secret".getBytes(StandardCharsets.UTF_8));
        var refusing = new JdkHttpFetcher(url -> {
            if (url.getPath().equals("/forbidden")) {
                throw new FetchRejected("not allowed");
            }
        });

        assertThatThrownBy(() -> refusing.fetch(URI.create(base + "/start")))
                .isInstanceOf(FetchRejected.class).hasMessage("not allowed");
    }

    @Test
    void theRealPolicyRefusesTheLoopbackServer() {
        serve("/rules", 200, "text/plain", "x".getBytes(StandardCharsets.UTF_8));
        var guarded = new JdkHttpFetcher(new SsrfGuard(new SystemHostResolver()));

        assertThatThrownBy(() -> guarded.fetch(URI.create(base + "/rules")))
                .isInstanceOf(FetchRejected.class).hasMessageContaining("internal address");
    }

    @Test
    void aBodyOverTheCapIsRefusedAndOneAtTheCapIsRead() {
        byte[] atCap = new byte[JdkHttpFetcher.MAX_BODY_BYTES];
        byte[] overCap = new byte[JdkHttpFetcher.MAX_BODY_BYTES + 1];
        serve("/at", 200, "application/pdf", atCap);
        serve("/over", 200, "application/pdf", overCap);

        assertThat(fetcher().fetch(URI.create(base + "/at")).body()).hasSize(JdkHttpFetcher.MAX_BODY_BYTES);
        assertThatThrownBy(() -> fetcher().fetch(URI.create(base + "/over")))
                .isInstanceOf(FetchRejected.class).hasMessageContaining("larger than 20 MB");
    }

    @Test
    void aServerThatCannotBeReachedIsRefusedWithAMessage() {
        server.stop(0);

        assertThatThrownBy(() -> fetcher().fetch(URI.create(base + "/rules")))
                .isInstanceOf(FetchRejected.class).hasMessageStartingWith("Could not fetch the address");
    }
}
