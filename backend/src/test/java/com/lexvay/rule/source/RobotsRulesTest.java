package com.lexvay.rule.source;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** What a robots.txt means for the monitor (RG-2, D-571): groups, longest match, wildcards, and what is ignored. */
class RobotsRulesTest {

    private static boolean allowed(String robots, String path) {
        return RobotsRules.parse(robots).check(path).allowed();
    }

    @Test
    void anEmptyFileAndAFileWithoutRulesForUsForbidNothing() {
        assertThat(allowed("", "/rules")).isTrue();
        assertThat(allowed("User-agent: Googlebot\nDisallow: /\n", "/rules")).isTrue();
        assertThat(RobotsRules.ALLOW_ALL.check("/anything").rule()).isNull();
    }

    @Test
    void theStarGroupAppliesToUs() {
        String robots = "User-agent: *\nDisallow: /private/\n";

        assertThat(allowed(robots, "/private/rules.pdf")).isFalse();
        assertThat(allowed(robots, "/public/rules.pdf")).isTrue();
        assertThat(RobotsRules.parse(robots).check("/private/x").rule()).isEqualTo("Disallow: /private/");
    }

    @Test
    void ourOwnGroupReplacesTheStarGroupRatherThanAddingToIt() {
        String robots = """
                User-agent: *
                Disallow: /
                User-agent: lexvaymonitor
                Disallow: /admin/
                """;

        assertThat(allowed(robots, "/rules")).as("we are named, so the star group does not apply").isTrue();
        assertThat(allowed(robots, "/admin/users")).isFalse();
        assertThat(allowed("User-agent: LexvayMonitor\nDisallow:\n\nUser-agent: *\nDisallow: /\n", "/rules"))
                .as("a group with no rules for us still counts").isTrue();
    }

    @Test
    void severalAgentLinesShareOneGroup() {
        String robots = "User-agent: Googlebot\nUser-agent: LexvayMonitor\nDisallow: /nope\n";

        assertThat(allowed(robots, "/nope")).isFalse();
        assertThat(allowed(robots, "/fine")).isTrue();
    }

    @Test
    void theLongestMatchingPatternWinsAndAllowWinsATie() {
        String robots = """
                User-agent: *
                Disallow: /rules/
                Allow: /rules/public/
                Disallow: /rules/public/draft
                Allow: /tie
                Disallow: /tie
                """;

        assertThat(allowed(robots, "/rules/board.pdf")).isFalse();
        assertThat(allowed(robots, "/rules/public/board.pdf")).as("Allow is longer").isTrue();
        assertThat(allowed(robots, "/rules/public/draft-2.pdf")).as("the Disallow below it is longer again").isFalse();
        assertThat(allowed(robots, "/tie")).as("equal length: allowed").isTrue();
        assertThat(RobotsRules.parse(robots).check("/rules/public/x").rule()).isEqualTo("Allow: /rules/public/");
    }

    @Test
    void wildcardsAndTheEndAnchorAreHonoured() {
        String robots = "User-agent: *\nDisallow: /*.pdf$\nDisallow: /search?*q=\n";

        assertThat(allowed(robots, "/a/b/rules.pdf")).isFalse();
        assertThat(allowed(robots, "/a/b/rules.pdf?download=1")).as("$ anchors the end").isTrue();
        assertThat(allowed(robots, "/rules.html")).isTrue();
        assertThat(allowed(robots, "/search?lang=en&q=x")).isFalse();
        assertThat(allowed(robots, "/search?lang=en")).isTrue();
    }

    @Test
    void anEmptyDisallowAllowsEverythingAndDisallowSlashForbidsEverything() {
        assertThat(allowed("User-agent: *\nDisallow:\n", "/rules")).isTrue();
        assertThat(allowed("User-agent: *\nDisallow: /\n", "/rules")).isFalse();
        assertThat(allowed("User-agent: *\nDisallow: /\n", "/")).isFalse();
    }

    @Test
    void commentsCaseAndDirectivesWeDoNotReadAreIgnored() {
        String robots = """
                # robots for the board
                USER-AGENT: *   # everybody
                Crawl-delay: 120
                Sitemap: https://rules.example.test/sitemap.xml
                disallow: /Private   # case matters in the path
                Host: rules.example.test
                """;

        assertThat(allowed(robots, "/Private/x")).isFalse();
        assertThat(allowed(robots, "/private/x")).isTrue();
        assertThat(allowed(robots, "/rules")).isTrue();
    }

    @Test
    void aByteOrderMarkAndWindowsLineEndingsDoNotHideTheGroup() {
        assertThat(allowed("﻿User-agent: *\r\nDisallow: /private/\r\n", "/private/x")).isFalse();
    }

    @Test
    void aRuleBeforeAnyUserAgentLineBelongsToNoGroup() {
        assertThat(allowed("Disallow: /\nUser-agent: Googlebot\nDisallow: /x\n", "/rules")).isTrue();
    }
}
