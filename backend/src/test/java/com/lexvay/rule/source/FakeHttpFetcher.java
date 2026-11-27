package com.lexvay.rule.source;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.context.annotation.Primary;

/**
 * The {@link HttpFetcher} of every database test: scripted by URL, so no test touches the network. A URL nobody
 * scripted is refused like an unreachable server. Several tests share one instance, so each test uses URLs of its
 * own.
 */
@Primary
public class FakeHttpFetcher implements HttpFetcher {

    private final Map<URI, Object> scripted = new ConcurrentHashMap<>();
    private final List<URI> requested = new CopyOnWriteArrayList<>();

    public FakeHttpFetcher respond(String url, int status, String contentType, byte[] body) {
        scripted.put(URI.create(url), new Fetched(status, contentType, body));
        return this;
    }

    public FakeHttpFetcher respond(String url, String contentType, String body) {
        return respond(url, 200, contentType, body.getBytes(StandardCharsets.UTF_8));
    }

    public FakeHttpFetcher html(String url, String html) {
        return respond(url, "text/html; charset=utf-8", html);
    }

    /** The fetch fails before any status exists, for example because the body is over the cap. */
    public FakeHttpFetcher reject(String url, String message) {
        scripted.put(URI.create(url), new FetchRejected(message));
        return this;
    }

    public List<URI> requested() {
        return List.copyOf(requested);
    }

    @Override
    public Fetched fetch(URI url) {
        requested.add(url);
        Object response = scripted.get(url);
        if (response instanceof Fetched fetched) {
            return fetched;
        }
        if (response instanceof FetchRejected rejected) {
            throw rejected;
        }
        throw new FetchRejected("Nothing is scripted for " + url);
    }
}
