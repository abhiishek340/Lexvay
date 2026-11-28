package com.lexvay.llm;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Deterministic scripted model. Responses are queued per script key; the last response for a key is
 * repeated once the queue has one entry left, so re-runs are stable. A call with no script fails loudly.
 */
public class FakeLlmClient implements LlmClient {

    public static final String MODEL_ID = "fake-llm";

    private final Map<String, Deque<String>> scripts = new ConcurrentHashMap<>();
    private final List<LlmRequest> calls = new CopyOnWriteArrayList<>();

    public FakeLlmClient script(String key, String... responses) {
        scripts.computeIfAbsent(key, k -> new ArrayDeque<>()).addAll(List.of(responses));
        return this;
    }

    /** Replaces whatever was scripted for the key, so loading the same data again is not answered with the earlier load's ids. */
    public FakeLlmClient replaceScript(String key, String response) {
        scripts.put(key, new ArrayDeque<>(List.of(response)));
        return this;
    }

    public void reset() {
        scripts.clear();
        calls.clear();
    }

    public List<LlmRequest> calls() {
        return List.copyOf(calls);
    }

    public long callCount(LlmPurpose purpose) {
        return calls.stream().filter(c -> c.purpose() == purpose).count();
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        calls.add(request);
        Deque<String> queue = scripts.get(request.scriptKey());
        if (queue == null || queue.isEmpty()) {
            throw new IllegalStateException("FakeLlmClient has no scripted response for key '" + request.scriptKey() + "'");
        }
        String text = queue.size() > 1 ? queue.poll() : queue.peek();
        return new LlmResponse(text, MODEL_ID, request.userPrompt().length() / 4, text.length() / 4, 0);
    }

    @Override
    public String modelId() {
        return MODEL_ID;
    }

    @Override
    public boolean scripted() {
        return true;
    }
}
