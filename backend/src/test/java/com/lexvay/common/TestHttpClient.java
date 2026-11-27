package com.lexvay.common;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

/**
 * Real HTTP against the running application, the way the Angular client talks to it: a session cookie,
 * the XSRF-TOKEN cookie echoed in the X-XSRF-TOKEN header on writes, and gzip accepted. The wire size is
 * reported next to the decoded body so the compression setting is visible in the benchmark.
 */
public final class TestHttpClient {

    public record Reply(int status, String body, int wireBytes, String etag, String contentEncoding) {
    }

    private final String base;
    private final CookieManager cookies = new CookieManager();
    private final HttpClient http = HttpClient.newBuilder().cookieHandler(cookies).build();

    public TestHttpClient(int port) {
        this.base = "http://localhost:" + port;
    }

    public void login(String email, String password) {
        get("/api/v1/auth/session");
        String form = "username=" + URLEncoder.encode(email, StandardCharsets.UTF_8)
                + "&password=" + URLEncoder.encode(password, StandardCharsets.UTF_8);
        Reply reply = send(request("/api/v1/auth/login").header("Content-Type", "application/x-www-form-urlencoded")
                .header("X-XSRF-TOKEN", xsrfToken()).POST(HttpRequest.BodyPublishers.ofString(form)));
        if (reply.status() != 200) {
            throw new IllegalStateException("Login failed with " + reply.status() + ": " + reply.body());
        }
    }

    public Reply get(String path) {
        return send(request(path).GET());
    }

    /** A response read as it arrives, for bodies too large to hold; the caller closes the stream. */
    public HttpResponse<InputStream> stream(String path) {
        try {
            return http.send(request(path).GET().build(), HttpResponse.BodyHandlers.ofInputStream());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    public Reply postJson(String path, String json) {
        return send(request(path).header("Content-Type", "application/json").header("X-XSRF-TOKEN", xsrfToken())
                .POST(HttpRequest.BodyPublishers.ofString(json)));
    }

    public Reply putJson(String path, String json) {
        return send(request(path).header("Content-Type", "application/json").header("X-XSRF-TOKEN", xsrfToken())
                .PUT(HttpRequest.BodyPublishers.ofString(json)));
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create(base + path)).header("Accept", "application/json")
                .header("Accept-Encoding", "gzip");
    }

    private String xsrfToken() {
        return cookies.getCookieStore().getCookies().stream().filter(c -> c.getName().equals("XSRF-TOKEN"))
                .map(c -> c.getValue()).findFirst().orElseThrow(() -> new IllegalStateException("No XSRF-TOKEN cookie"));
    }

    private Reply send(HttpRequest.Builder request) {
        try {
            HttpResponse<byte[]> response = http.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
            byte[] wire = response.body();
            byte[] decoded = wire;
            if (response.headers().firstValue("Content-Encoding").orElse("").equalsIgnoreCase("gzip") && wire.length > 0) {
                try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(wire))) {
                    decoded = in.readAllBytes();
                }
            }
            return new Reply(response.statusCode(), new String(decoded, StandardCharsets.UTF_8), wire.length,
                    response.headers().firstValue("ETag").orElse(null),
                    response.headers().firstValue("Content-Encoding").orElse(null));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
