package com.lexvay.assessment.match;

/**
 * Where the lexemes that found a page came from (F7c): the requirement's title, its text, or its search terms. A page is
 * labelled with the source of the highest-weighted lexeme that matched it, and when two sources weigh the same the
 * requirement's own words win, so {@link #TERMS} means that no word of the requirement itself occurs on the page.
 */
public enum MatchSource {
    TITLE, TEXT, TERMS
}
