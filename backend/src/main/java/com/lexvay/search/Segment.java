package com.lexvay.search;

/** A run of page text, either matched by the query or not. The client renders it as text, never as markup. */
public record Segment(String text, boolean match) {
}
