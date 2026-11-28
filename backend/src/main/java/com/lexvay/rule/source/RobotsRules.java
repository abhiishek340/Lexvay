package com.lexvay.rule.source;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * What a robots.txt says about the monitor (RG-2, D-571). Only {@code User-agent}, {@code Disallow} and {@code Allow}
 * are read; everything else, such as {@code Crawl-delay} and {@code Sitemap}, is ignored. As in RFC 9309, the groups
 * that name {@link MonitorSettings#PRODUCT} apply, and only when there is none the groups for {@code *}. Among the
 * rules that match a path the longest pattern wins and, at equal length, Allow wins; no match means allowed. In a
 * pattern {@code *} matches any run of characters and a final {@code $} anchors the end of the path.
 */
public final class RobotsRules {

    /** A robots.txt that forbids nothing: the file is absent, or has no group for the monitor. */
    public static final RobotsRules ALLOW_ALL = new RobotsRules(List.of());

    /** Only this many characters are read, the 500 KiB a crawler must at least process. */
    static final int MAX_CHARS = 512 * 1024;

    private static final String ANY_AGENT = "*";

    /** The verdict for one path; {@code rule} is the line that decided it, such as {@code Disallow: /rules/}, or null. */
    public record Verdict(boolean allowed, String rule) {
    }

    private record Rule(boolean allow, String pattern, Pattern matcher) {

        static Rule of(boolean allow, String pattern) {
            boolean anchored = pattern.endsWith("$");
            String body = anchored ? pattern.substring(0, pattern.length() - 1) : pattern;
            StringBuilder regex = new StringBuilder("^");
            String[] pieces = body.split("\\*", -1);
            for (int i = 0; i < pieces.length; i++) {
                regex.append(i == 0 ? "" : ".*").append(pieces[i].isEmpty() ? "" : Pattern.quote(pieces[i]));
            }
            return new Rule(allow, pattern, Pattern.compile(regex + (anchored ? "$" : ""), Pattern.DOTALL));
        }

        boolean matches(String path) {
            return matcher.matcher(path).find();
        }

        String line() {
            return (allow ? "Allow: " : "Disallow: ") + pattern;
        }
    }

    /** The user agents a run of {@code User-agent} lines names and the rules that follow them. */
    private static final class Group {
        final List<String> agents = new ArrayList<>();
        final List<Rule> rules = new ArrayList<>();
    }

    private final List<Rule> rules;

    private RobotsRules(List<Rule> rules) {
        this.rules = rules;
    }

    /** Reads the rules that apply to the monitor out of a robots.txt body. */
    public static RobotsRules parse(String body) {
        String text = body.length() > MAX_CHARS ? body.substring(0, MAX_CHARS) : body;
        List<Group> groups = new ArrayList<>();
        Group current = null;
        boolean collectingAgents = false;
        for (String raw : text.replace("﻿", "").split("\\R")) {
            int comment = raw.indexOf('#');
            String line = comment < 0 ? raw : raw.substring(0, comment);
            int colon = line.indexOf(':');
            if (colon < 0) {
                continue;
            }
            String field = line.substring(0, colon).strip().toLowerCase(Locale.ROOT);
            String value = line.substring(colon + 1).strip();
            if (field.equals("user-agent")) {
                if (!collectingAgents) {
                    current = new Group();
                    groups.add(current);
                }
                collectingAgents = true;
                current.agents.add(value.toLowerCase(Locale.ROOT));
            } else if (field.equals("allow") || field.equals("disallow")) {
                collectingAgents = false;
                if (current != null && !value.isEmpty()) {
                    current.rules.add(Rule.of(field.equals("allow"), value));
                }
            }
        }
        List<Rule> applicable = rulesOf(groups, MonitorSettings.PRODUCT.toLowerCase(Locale.ROOT));
        if (applicable == null) {
            applicable = rulesOf(groups, ANY_AGENT);
        }
        return applicable == null ? ALLOW_ALL : new RobotsRules(applicable);
    }

    /** The rules of every group that names {@code agent}, or null when no group does (a group without rules still counts). */
    private static List<Rule> rulesOf(List<Group> groups, String agent) {
        List<Rule> found = null;
        for (Group group : groups) {
            if (group.agents.contains(agent)) {
                found = found == null ? new ArrayList<>() : found;
                found.addAll(group.rules);
            }
        }
        return found;
    }

    /** Whether the monitor may fetch {@code pathAndQuery}, the path as sent to the server with its query string, if any. */
    public Verdict check(String pathAndQuery) {
        Rule decisive = null;
        for (Rule rule : rules) {
            if (rule.matches(pathAndQuery) && (decisive == null || rule.pattern().length() > decisive.pattern().length()
                    || (rule.pattern().length() == decisive.pattern().length() && rule.allow()))) {
                decisive = rule;
            }
        }
        return decisive == null ? new Verdict(true, null) : new Verdict(decisive.allow(), decisive.line());
    }
}
