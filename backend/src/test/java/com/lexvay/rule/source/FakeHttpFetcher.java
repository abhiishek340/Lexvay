package com.lexvay.rule.source;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import org.springframework.context.annotation.Primary;

/**
 * The {@link HttpFetcher} of every database test: scripted by URL, so no test touches the network. A URL nobody
 * scripted is refused like an unreachable server, except {@code /robots.txt}, which answers 404 (no robots file, so
 * everything is allowed) until a test scripts it. Several tests share one instance, so each test uses URLs of its own.
 */
@Primary
public class FakeHttpFetcher implements HttpFetcher {

    private static final byte[] NOTHING = new byte[0];

    private final Map<URI, Object> scripted = new ConcurrentHashMap<>();
    private final List<FetchRequest> requests = new CopyOnWriteArrayList<>();

    public FakeHttpFetcher respond(String url, int status, String contentType, byte[] body) {
        return respond(url, new Fetched(status, contentType, body, Validators.NONE, null));
    }

    public FakeHttpFetcher respond(String url, Fetched fetched) {
        scripted.put(URI.create(url), fetched);
        return this;
    }

    public FakeHttpFetcher respond(String url, String contentType, String body) {
        return respond(url, 200, contentType, body.getBytes(StandardCharsets.UTF_8));
    }

    public FakeHttpFetcher html(String url, String html) {
        return respond(url, "text/html; charset=utf-8", html);
    }

    /** Answers 200 with the page and its validators; a request that sends the same ETag gets 304 and no body, as a server does. */
    public FakeHttpFetcher conditionalHtml(String url, String etag, String lastModified, String html) {
        scripted.put(URI.create(url), (Function<FetchRequest, Fetched>) request -> etag.equals(request.validators().etag())
                ? new Fetched(304, null, NOTHING, new Validators(etag, lastModified), null)
                : new Fetched(200, "text/html; charset=utf-8", html.getBytes(StandardCharsets.UTF_8),
                        new Validators(etag, lastModified), null));
        return this;
    }

    /** Answers with the given status and a Retry-After. */
    public FakeHttpFetcher retryAfter(String url, int status, Duration wait) {
        return respond(url, new Fetched(status, "text/plain", NOTHING, Validators.NONE, wait));
    }

    /** Scripts the robots.txt of the host of {@code pageUrl}. */
    public FakeHttpFetcher robots(String pageUrl, int status, String body) {
        return respond(URI.create(pageUrl).resolve("/robots.txt").toString(), status, "text/plain", body.getBytes(StandardCharsets.UTF_8));
    }

    /** The fetch fails before any status exists, for example because the body is over the cap. */
    public FakeHttpFetcher reject(String url, String message) {
        scripted.put(URI.create(url), new FetchRejected(message));
        return this;
    }

    public List<URI> requested() {
        return requests.stream().map(FetchRequest::url).toList();
    }

    /** Forgets every script and every request, so that a test starts from a fetcher nobody has touched. */
    public void reset() {
        scripted.clear();
        requests.clear();
    }

    /** What was asked, in order, with the validators and scope that came with each request. */
    public List<FetchRequest> requests() {
        return List.copyOf(requests);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Fetched fetch(FetchRequest request) {
        requests.add(request);
        Object response = scripted.get(request.url());
        if (response instanceof Fetched fetched) {
            return fetched;
        }
        if (response instanceof Function<?, ?> script) {
            return ((Function<FetchRequest, Fetched>) script).apply(request);
        }
        if (response instanceof FetchRejected rejected) {
            throw rejected;
        }
        if (request.url().getPath().equals("/robots.txt")) {
            return new Fetched(404, "text/plain", NOTHING, Validators.NONE, null);
        }
        throw new FetchRejected("Nothing is scripted for " + request.url());
    }
}
