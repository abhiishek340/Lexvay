package com.lexvay.assessment.match;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** The excerpt rules of D-174, on pages built line by line so every boundary is known. */
class PassageExcerpterTest {

    static final String D = PassageExcerpter.FRAGMENT_DELIMITER;
    static final String GAP = "\n" + PassageExcerpter.OMISSION + "\n";

    /** Header of 3 lines (about 150 characters), then numbered filler lines, so a page of {@code lines} lines. */
    static String page(int lines) {
        return IntStream.rangeClosed(1, lines).mapToObj(PassageExcerpterTest::line).collect(Collectors.joining("\n"));
    }

    static String line(int n) {
        return "Line " + n + " of the syllabus keeps going with ordinary words about nothing in particular here.";
    }

    @Test
    void aPageOfAtMostTwoThousandFiveHundredCharactersIsSentWhole() {
        String exactly = "x".repeat(PassageExcerpter.WHOLE_PAGE_CHARS);

        assertThat(PassageExcerpter.excerpt(exactly, null)).isSameAs(exactly);
        assertThat(PassageExcerpter.excerpt("short", "short")).isEqualTo("short");
    }

    @Test
    void aLongPageIsTheHeaderThenTheFragmentWidenedToWholeLinesSeparatedByAnOmissionLine() {
        String page = page(60);
        assertThat(page.length()).isGreaterThan(PassageExcerpter.WHOLE_PAGE_CHARS);
        // The database returns a piece of lines 40 and 41, starting and ending inside a line.
        String fragment = line(40).substring(10) + "\n" + line(41).substring(0, 30);

        String excerpt = PassageExcerpter.excerpt(page, fragment);

        String header = line(1) + "\n" + line(2) + "\n" + line(3);
        assertThat(excerpt).isEqualTo(header + GAP + line(40) + "\n" + line(41));
        assertThat(PassageExcerpter.HEADER_CHARS).isGreaterThanOrEqualTo(header.length());
        assertThat(excerpt).doesNotContain(line(4));
    }

    @Test
    void theHeaderIsTheLastWholeLineThatFitsInThreeHundredCharacters() {
        String page = page(60);
        String excerpt = PassageExcerpter.excerpt(page, line(30));

        String header = excerpt.substring(0, excerpt.indexOf(GAP));
        assertThat(header.length()).isLessThanOrEqualTo(PassageExcerpter.HEADER_CHARS);
        assertThat(page).startsWith(header + "\n");
        assertThat(header).endsWith("keeps going with ordinary words about nothing in particular here.");
    }

    @Test
    void aFirstLineLongerThanTheHeaderBudgetIsCutAtTheBudget() {
        String page = "A".repeat(400) + "\n" + page(60);
        String excerpt = PassageExcerpter.excerpt(page, line(30));

        assertThat(excerpt).startsWith("A".repeat(PassageExcerpter.HEADER_CHARS) + GAP);
    }

    @Test
    void fragmentsInPageOrderAreEachLocatedAndSeparatedEvenWhenTheDatabaseReturnsThemOutOfOrder() {
        String page = page(60);
        String headline = line(50) + D + line(20);

        String excerpt = PassageExcerpter.excerpt(page, headline);

        assertThat(excerpt).endsWith(GAP + line(20) + GAP + line(50));
        assertThat(excerpt.indexOf(line(20))).isLessThan(excerpt.indexOf(line(50)));
    }

    @Test
    void fragmentsThatTouchOrOverlapAreMergedIntoOnePart() {
        String page = page(60);
        // Line 30 and line 31 are adjacent; the third fragment overlaps the second.
        String headline = line(30) + D + line(31) + D + line(31).substring(5) + "\n" + line(32);

        String excerpt = PassageExcerpter.excerpt(page, headline);

        assertThat(excerpt).endsWith(GAP + line(30) + "\n" + line(31) + "\n" + line(32));
        assertThat(excerpt.split("\\[\\.\\.\\.]", -1)).hasSize(2);
    }

    @Test
    void aFragmentTouchingTheHeaderIsMergedWithIt() {
        String page = page(60);
        String excerpt = PassageExcerpter.excerpt(page, line(3) + "\n" + line(4));

        assertThat(excerpt).isEqualTo(line(1) + "\n" + line(2) + "\n" + line(3) + "\n" + line(4));
        assertThat(excerpt).doesNotContain(PassageExcerpter.OMISSION);
    }

    @Test
    void aFragmentThatIsNotInThePageSendsTheFirstTwoThousandFiveHundredCharactersInstead() {
        String page = page(60);

        String excerpt = PassageExcerpter.excerpt(page, line(30) + D + "words the database invented that are not in the page");

        assertThat(excerpt).isEqualTo(page.substring(0, PassageExcerpter.WHOLE_PAGE_CHARS));
    }

    @Test
    void aBlankHeadlineAlsoFallsBackToThePrefix() {
        String page = page(60);

        assertThat(PassageExcerpter.excerpt(page, null)).isEqualTo(page.substring(0, PassageExcerpter.WHOLE_PAGE_CHARS));
        assertThat(PassageExcerpter.excerpt(page, "  \n")).isEqualTo(page.substring(0, PassageExcerpter.WHOLE_PAGE_CHARS));
        assertThat(PassageExcerpter.excerpt(page, D)).isEqualTo(page.substring(0, PassageExcerpter.WHOLE_PAGE_CHARS));
    }

    @Test
    void anExcerptThatWouldNotBeShorterThanThePageIsThePage() {
        String page = "X".repeat(3000);

        assertThat(PassageExcerpter.excerpt(page, "X".repeat(50))).isEqualTo(page);
    }

    @Test
    void whitespaceInTheHeadlineMayDifferFromThePageBecauseLocatingIsWhitespaceInsensitive() {
        String page = page(60);
        String headline = line(30).replace(" ", "  ") + "\n\n" + line(31);

        String excerpt = PassageExcerpter.excerpt(page, headline);

        assertThat(excerpt).endsWith(GAP + line(30) + "\n" + line(31));
    }

    @Test
    void everyPartOfAnExcerptIsAVerbatimSliceOfThePage() {
        String page = page(80);
        String excerpt = PassageExcerpter.excerpt(page, line(10) + D + line(44) + D + line(70));

        for (String part : excerpt.split(GAP.replace("[", "\\[").replace("]", "\\]"))) {
            assertThat(page).contains(part);
        }
    }
}
