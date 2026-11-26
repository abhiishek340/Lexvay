package com.lexvay.seed;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import tools.jackson.databind.annotation.JsonDeserialize;

/** Shape of seed/manifest.yml. Unknown fields are rejected so typos surface at once. */
public record SeedManifest(boolean fictional, List<Tenant> tenants) {

    public record Tenant(String name, boolean demo, List<User> users, Program program, List<Course> courses,
                         List<Document> documents, List<Fact> facts, List<RuleVersion> ruleVersions, Changes changes) {
    }

    public record User(String email, String displayName, String title, String role) {
    }

    public record Program(String name, String licenseType, String catalogYear, List<String> jurisdictions) {
    }

    public record Course(String code, String title, boolean required, String syllabus) {
    }

    public record Document(String file, String kind) {
    }

    public record Fact(String key, BigDecimal value, String document, int page, String quote) {
    }

    public record RuleVersion(String jurisdiction, String label, String sourceUrl, LocalDate effectiveFrom, String text) {
    }

    public record Changes(ChangeRuleVersion ruleVersion, ChangeCourseVersion courseVersion) {
    }

    public record ChangeRuleVersion(String jurisdiction, String label, String supersedes, String sourceUrl, LocalDate effectiveFrom, String text) {
    }

    public record ChangeCourseVersion(String course, String catalogYear, boolean required, String syllabus) {
    }
}
