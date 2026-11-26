package com.lexvay.seed;

import com.lexvay.assessment.AssessmentService;
import com.lexvay.auth.LexvayUser;
import com.lexvay.auth.Role;
import com.lexvay.common.TenantContext;
import com.lexvay.document.DocumentKind;
import com.lexvay.document.DocumentService;
import com.lexvay.job.JobRunner;
import com.lexvay.llm.FakeLlmClient;
import com.lexvay.llm.LlmClient;
import com.lexvay.program.CourseService;
import com.lexvay.program.CreateCourseRequest;
import com.lexvay.program.CreateProgramFactRequest;
import com.lexvay.program.CreateProgramRequest;
import com.lexvay.program.ProgramFactService;
import com.lexvay.program.ProgramService;
import com.lexvay.rule.CreateRuleVersionRequest;
import com.lexvay.rule.NumericField;
import com.lexvay.rule.RuleVersionService;
import com.lexvay.tenant.AppUserRepository;
import com.lexvay.tenant.TenantBootstrapService;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

/**
 * Loads the fictional demo tenants through the real services (D-060): the same validation, audit and
 * jobs as the API. Needs {@link FakeLlmClient}, which it scripts from seed/fake-llm.yml, resolving
 * document file names to ids (D-061). Running twice is safe: a tenant whose first user exists is skipped.
 */
@Service
public class SeedLoader {

    private static final Logger log = LoggerFactory.getLogger(SeedLoader.class);

    public record Result(String tenant, boolean skipped, UUID programId, UUID assessmentId, int documents, int requirements) {
    }

    private final TenantBootstrapService tenants;
    private final AppUserRepository users;
    private final ProgramService programs;
    private final CourseService courses;
    private final DocumentService documents;
    private final ProgramFactService facts;
    private final RuleVersionService ruleVersions;
    private final AssessmentService assessments;
    private final JobRunner jobs;
    private final LlmClient llm;
    private final ObjectMapper json;
    private final YAMLMapper yaml = YAMLMapper.builder().build();

    public SeedLoader(TenantBootstrapService tenants, AppUserRepository users, ProgramService programs, CourseService courses,
                      DocumentService documents, ProgramFactService facts, RuleVersionService ruleVersions,
                      AssessmentService assessments, JobRunner jobs, LlmClient llm, ObjectMapper json) {
        this.tenants = tenants;
        this.users = users;
        this.programs = programs;
        this.courses = courses;
        this.documents = documents;
        this.facts = facts;
        this.ruleVersions = ruleVersions;
        this.assessments = assessments;
        this.jobs = jobs;
        this.llm = llm;
        this.json = json;
    }

    public List<Result> load(Path seedDir, String demoPassword) {
        if (!(llm instanceof FakeLlmClient fake)) {
            throw new IllegalStateException("The seed scripts the model; run it with LEXVAY_LLM_PROVIDER=fake");
        }
        SeedManifest manifest = read(seedDir.resolve("manifest.yml"), SeedManifest.class);
        if (!manifest.fictional()) {
            throw new IllegalStateException("seed/manifest.yml must declare fictional: true");
        }
        FakeLlmScripts scripts = read(seedDir.resolve("fake-llm.yml"), FakeLlmScripts.class);
        List<Result> results = new java.util.ArrayList<>();
        for (SeedManifest.Tenant tenant : manifest.tenants()) {
            results.add(loadTenant(tenant, seedDir, demoPassword, scripts, fake));
        }
        return results;
    }

    private Result loadTenant(SeedManifest.Tenant t, Path seedDir, String password, FakeLlmScripts scripts, FakeLlmClient fake) {
        SeedManifest.User adminSpec = t.users().stream().filter(u -> u.role().equals("ADMIN")).findFirst()
                .orElseThrow(() -> new IllegalStateException("Tenant " + t.name() + " needs one ADMIN user"));
        if (users.findForLogin(adminSpec.email()).isPresent()) {
            log.info("Seed: tenant '{}' already exists, skipping", t.name());
            return new Result(t.name(), true, null, null, 0, 0);
        }
        var created = tenants.createTenant(t.name(), adminSpec.email(), adminSpec.displayName(), password);
        UUID tenantId = created.tenantId();
        Map<Role, LexvayUser> byRole = new HashMap<>();
        byRole.put(Role.ADMIN, created.admin());
        for (SeedManifest.User u : t.users()) {
            if (!u.role().equals("ADMIN")) {
                byRole.put(Role.valueOf(u.role()), tenants.createUser(tenantId, created.admin().id(), u.email(), u.displayName(), Role.valueOf(u.role()), password));
            }
        }
        LexvayUser contributor = byRole.getOrDefault(Role.CONTRIBUTOR, created.admin());
        LexvayUser reviewer = byRole.getOrDefault(Role.REVIEWER, created.admin());

        return TenantContext.runAs(tenantId, () -> {
            UUID programId = as(contributor, () -> programs.create(new CreateProgramRequest(t.program().name(), t.program().licenseType(),
                    t.program().catalogYear(), t.program().jurisdictions())).id());
            if (t.courses() == null) {
                return new Result(t.name(), false, programId, null, 0, 0);
            }
            Map<String, UUID> courseVersionByCode = new LinkedHashMap<>();
            for (SeedManifest.Course c : t.courses()) {
                var course = as(contributor, () -> courses.create(programId, new CreateCourseRequest(c.code(), c.title(), c.required())));
                courseVersionByCode.put(c.code(), course.versions().get(0).id());
            }
            Map<String, UUID> documentByFile = new LinkedHashMap<>();
            for (SeedManifest.Course c : t.courses()) {
                if (c.syllabus() != null) {
                    documentByFile.put(c.syllabus(), upload(contributor, programId, seedDir, c.syllabus(), DocumentKind.SYLLABUS, courseVersionByCode.get(c.code())));
                }
            }
            for (SeedManifest.Document d : t.documents()) {
                documentByFile.put(d.file(), upload(contributor, programId, seedDir, d.file(), DocumentKind.valueOf(d.kind()), null));
            }
            jobs.runPending();
            for (SeedManifest.Fact f : t.facts()) {
                as(contributor, () -> facts.create(programId, new CreateProgramFactRequest(NumericField.valueOf(f.key()), f.value(),
                        documentByFile.get(f.document()), f.page(), f.quote())));
            }
            int requirementCount = 0;
            for (SeedManifest.RuleVersion rv : t.ruleVersions()) {
                var extraction = scripts.extraction().get(rv.label());
                if (extraction == null) {
                    throw new IllegalStateException("fake-llm.yml has no extraction script for '" + rv.label() + "'");
                }
                fake.script("EXTRACT:" + rv.label(), json.writeValueAsString(extraction));
                String text = readText(seedDir.resolve("rules").resolve(rv.text()));
                var result = as(contributor, () -> ruleVersions.create(new CreateRuleVersionRequest(rv.jurisdiction(), t.program().licenseType(),
                        rv.label(), rv.sourceUrl(), rv.effectiveFrom(), null, text)));
                jobs.runPending();
                as(reviewer, () -> ruleVersions.approveRequirements(result.ruleVersionId()));
                requirementCount += (int) ruleVersions.requirements(result.ruleVersionId()).requirements().size();
            }
            for (var entry : scripts.match().entrySet()) {
                FakeLlmScripts.MatchScript m = entry.getValue();
                Map<String, Object> out = new HashMap<>();
                out.put("finding", m.finding());
                out.put("document_id", m.document() == null ? null : requireDocument(documentByFile, m.document()).toString());
                out.put("page_number", m.page());
                out.put("quote", m.quote());
                out.put("rationale", m.rationale());
                fake.script("MATCH:" + entry.getKey(), json.writeValueAsString(out));
            }
            UUID assessmentId = as(reviewer, () -> assessments.start(programId).assessmentId());
            jobs.runPending();
            log.info("Seed: tenant '{}' loaded: program {}, {} documents, {} requirements, assessment {}", t.name(), programId,
                    documentByFile.size(), requirementCount, assessmentId);
            return new Result(t.name(), false, programId, assessmentId, documentByFile.size(), requirementCount);
        });
    }

    private UUID upload(LexvayUser as, UUID programId, Path seedDir, String file, DocumentKind kind, UUID courseVersionId) {
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(seedDir.resolve("pdf").resolve(file));
        } catch (IOException e) {
            throw new UncheckedIOException("Missing seed PDF " + file + " (run make seed-pdfs)", e);
        }
        return as(as, () -> documents.upload(programId, new BytesMultipartFile(file, "application/pdf", bytes), kind, courseVersionId).documentId());
    }

    private static UUID requireDocument(Map<String, UUID> byFile, String file) {
        UUID id = byFile.get(file);
        if (id == null) {
            throw new IllegalStateException("fake-llm.yml refers to unknown document " + file);
        }
        return id;
    }

    /** Runs an action with the given seeded user authenticated, so audit rows name a real person. */
    private static <T> T as(LexvayUser user, Supplier<T> action) {
        var previous = SecurityContextHolder.getContext().getAuthentication();
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities()));
        try {
            return action.get();
        } finally {
            SecurityContextHolder.getContext().setAuthentication(previous);
        }
    }

    private <T> T read(Path path, Class<T> type) {
        try {
            return yaml.readValue(Files.readString(path, StandardCharsets.UTF_8), type);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + path, e);
        }
    }

    private static String readText(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + path, e);
        }
    }
}
