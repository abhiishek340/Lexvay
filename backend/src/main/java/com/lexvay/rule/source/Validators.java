package com.lexvay.rule.source;

/** The {@code ETag} and {@code Last-Modified} a server gave for a page, sent back as {@code If-None-Match} and {@code If-Modified-Since}. */
public record Validators(String etag, String lastModified) {

    public static final Validators NONE = new Validators(null, null);
}
