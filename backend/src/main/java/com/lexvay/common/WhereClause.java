package com.lexvay.common;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Optional filters of one SQL statement (D-116): only the predicates that apply are in the SQL text, so every
 * combination gets its own plan instead of one generic plan with {@code :x IS NULL OR} branches. Predicates are
 * fixed SQL written by the caller; values are always bind parameters.
 */
public final class WhereClause {

    private final List<String> predicates = new ArrayList<>();
    private final Map<String, Object> params = new LinkedHashMap<>();

    /** Adds {@code predicate}, which refers to {@code :name}, when {@code value} is not null. */
    public WhereClause andIfPresent(String predicate, String name, Object value) {
        if (value != null) {
            predicates.add(predicate);
            params.put(name, value);
        }
        return this;
    }

    /** Adds a predicate without parameters when {@code condition} holds. */
    public WhereClause andIf(boolean condition, String predicate) {
        if (condition) {
            predicates.add(predicate);
        }
        return this;
    }

    /** {@code " WHERE a AND b"}, or the empty string when no predicate applies. */
    public String sql() {
        return predicates.isEmpty() ? "" : " WHERE " + String.join(" AND ", predicates);
    }

    public JdbcClient.StatementSpec bind(JdbcClient.StatementSpec spec) {
        params.forEach(spec::param);
        return spec;
    }
}
