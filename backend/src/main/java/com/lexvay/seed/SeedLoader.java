package com.lexvay.seed;

import com.lexvay.assessment.AssessmentService;
import com.lexvay.auth.LexvayUser;
import com.lexvay.auth.Role;
import com.lexvay.common.TenantContext;
import com.lexvay.common.YamlFile;
import com.lexvay.document.DocumentKind;
import com.lexvay.document.DocumentService;
import com.lexvay.job.JobRunner;
import com.lexvay.program.AddCourseVersionRequest;
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
import java.time.LocalDate;
import java.util.ArrayList;
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

/**
 * Loads the fictional demo tenants through the real services (D-060): the same validation, audit and
 * jobs as the API. How the language model is prepared is a {@link ModelStrategy}: the demo seed scripts the
 * fake model, the evaluation harness may use a real one. Running twice is safe: a tenant whose first user
 * exists is skipped.
 */
@Service
public class SeedLoader {

    private static final Logger log = LoggerFactory.getLogger(SeedLoader.class);

    public record Result(String tenant, boolean skipped, UUID tenantId, UUID programId, UUID assessmentId, int documents, int requirements) {
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

    public SeedLoader(TenantBootstrapService tenants, AppUserRepository users, ProgramService programs, CourseService courses,
                      DocumentService documents, ProgramFactService facts, RuleVersionService ruleVersions,
                      AssessmentService assessments, JobRunner jobs) {
        this.tenants = tenants;
        this.users = users;
        this.programs = programs;
        this.courses = courses;
        this.documents = documents;
        this.facts = facts;
        this.ruleVersions = ruleVersions;
        this.assessments = assessments;
        this.jobs = jobs;
    }

    /** Reads seed/manifest.yml and refuses data that is not declared fictional. */
    public SeedManifest manifest(Path seedDir) {
        SeedManifest manifest = YamlFile.read(seedDir.resolve("manifest.yml"), SeedManifest.class);
        if (!manifest.fictional()) {
            throw new IllegalStateException("seed/manifest.yml must declare fictional: true");
        }
        return manifest;
    }

    public List<Result> load(Path seedDir, String demoPassword, ModelStrategy model) {
        List<Result> results = new ArrayList<>();
        for (SeedManifest.Tenant tenant : manifest(seedDir).tenants()) {
            results.add(loadTenant(tenant, seedDir, demoPassword, model));
        }
        return results;
    }

    public Result loadTenant(SeedManifest.Tenant t, Path seedDir, String password, ModelStrategy model) {
        SeedManifest.User adminSpec = t.users().stream().filter(u -> u.role().equals("ADMIN")).findFirst()
                .orElseThrow(() -> new IllegalStateException("Tenant " + t.name() + " needs one ADMIN user"));
        if (users.findForLogin(adminSpec.email()).isPresent()) {
            log.info("Seed: tenant '{}' already exists, skipping", t.name());
            return new Result(t.name(), true, null, null, null, 0, 0);
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
                return new Result(t.name(), false, tenantId, programId, null, 0, 0);
            }
            Map<String, UUID> courseVersionByCode = new LinkedHashMap<>();
            for (SeedManifest.Course c : t.courses()) {
                var course = as(contributor, () -> courses.create(programId, new CreateCourseRequest(c.code(), c.title(), c.required())));
                courseVersionByCode.put(c.code(), course.versions().get(0).id());
            }
            Map<String, UUID> documentByFile = new LinkedHashMap<>();
            for (SeedManifest.Course c : t.courses()) {
                if (c.syllabus() != null) {
                    documentByFile.put(c.syllabus(), upload(contributor, programId, pdf(seedDir, c.syllabus()), DocumentKind.SYLLABUS, courseVersionByCode.get(c.code())));
                }
            }
            for (SeedManifest.Document d : t.documents()) {
                documentByFile.put(d.file(), upload(contributor, programId, pdf(seedDir, d.file()), DocumentKind.valueOf(d.kind()), null));
            }
            jobs.runPending();
            for (SeedManifest.Fact f : t.facts()) {
                as(contributor, () -> facts.create(programId, new CreateProgramFactRequest(NumericField.valueOf(f.key()), f.value(),
                        documentByFile.get(f.document()), f.page(), f.quote())));
            }
            int requirementCount = 0;
            for (SeedManifest.RuleVersion rv : t.ruleVersions()) {
                UUID ruleVersionId = createApprovedRuleVersion(t.program().licenseType(), rv.jurisdiction(), rv.label(), rv.sourceUrl(),
                        rv.effectiveFrom(), null, readText(seedDir.resolve("rules").resolve(rv.text())), contributor, reviewer, model);
                requirementCount += ruleVersions.requirements(ruleVersionId).requirements().size();
            }
            model.beforeMatching(documentByFile);
            UUID assessmentId = as(reviewer, () -> assessments.start(programId).assessmentId());
            jobs.runPending();
            if (t.changes() != null) {
                applyChanges(t, seedDir, model, contributor, reviewer, programId, courseVersionByCode, documentByFile);
            }
            log.info("Seed: tenant '{}' loaded: program {}, {} documents, {} requirements, assessment {}", t.name(), programId,
                    documentByFile.size(), requirementCount, assessmentId);
            return new Result(t.name(), false, tenantId, programId, assessmentId, documentByFile.size(), requirementCount);
        });
    }

    /** A tenant holding one program and only the given documents: no courses, facts, rules or assessment. */
    public record Corpus(UUID tenantId, UUID programId, int documents) {
    }

    /** The documents of the retrieval evaluation: a fresh tenant, its program, and every PDF uploaded and ingested through the services. */
    public Corpus loadCorpus(String tenantName, String adminEmail, String password, SeedManifest.Program program, Map<Path, DocumentKind> pdfs) {
        var created = tenants.createTenant(tenantName, adminEmail, "Retrieval Evaluator", password);
        return TenantContext.runAs(created.tenantId(), () -> {
            UUID programId = as(created.admin(), () -> programs.create(new CreateProgramRequest(program.name(), program.licenseType(),
                    program.catalogYear(), program.jurisdictions())).id());
            pdfs.forEach((pdf, kind) -> upload(created.admin(), programId, pdf, kind, null));
            jobs.runPending();
            return new Corpus(created.tenantId(), programId, pdfs.size());
        });
    }

    /** Milestone 7 demo: a superseding rule version and a new course version, left as OPEN change events. */
    private void applyChanges(SeedManifest.Tenant t, Path seedDir, ModelStrategy model, LexvayUser contributor,
                              LexvayUser reviewer, UUID programId, Map<String, UUID> courseVersionByCode, Map<String, UUID> documentByFile) {
        SeedManifest.ChangeRuleVersion rv = t.changes().ruleVersion();
        if (rv != null) {
            UUID previous = ruleVersions.list().stream().filter(v -> v.label().equals(rv.supersedes())).map(v -> v.id()).findFirst()
                    .orElseThrow(() -> new IllegalStateException("changes.ruleVersion.supersedes not found: " + rv.supersedes()));
            createApprovedRuleVersion(t.program().licenseType(), rv.jurisdiction(), rv.label(), rv.sourceUrl(), rv.effectiveFrom(),
                    previous, readText(seedDir.resolve("rules").resolve(rv.text())), contributor, reviewer, model);
        }
        SeedManifest.ChangeCourseVersion cv = t.changes().courseVersion();
        if (cv != null) {
            UUID courseVersionId = courseVersionByCode.get(cv.course());
            UUID courseId = courses.list(programId).stream().filter(c -> c.code().equals(cv.course())).map(c -> c.id()).findFirst()
                    .orElseThrow(() -> new IllegalStateException("changes.courseVersion.course not found: " + cv.course()));
            var course = as(contributor, () -> courses.addVersion(courseId, new AddCourseVersionRequest(cv.catalogYear(), cv.required(), courseVersionId)));
            UUID newVersionId = course.versions().get(course.versions().size() - 1).id();
            documentByFile.put(cv.syllabus(), upload(contributor, programId, pdf(seedDir, cv.syllabus()), DocumentKind.SYLLABUS, newVersionId));
            jobs.runPending();
        }
    }

    /** Creates a rule version, lets the model extract its requirements, and has the reviewer approve the list. */
    private UUID createApprovedRuleVersion(String licenseType, String jurisdiction, String label, String sourceUrl, LocalDate effectiveFrom,
                                           UUID supersedesId, String text, LexvayUser contributor, LexvayUser reviewer, ModelStrategy model) {
        model.beforeExtraction(label);
        var created = as(contributor, () -> ruleVersions.create(new CreateRuleVersionRequest(jurisdiction, licenseType, label, sourceUrl,
                effectiveFrom, supersedesId, text)));
        jobs.runPending();
        as(reviewer, () -> {
            model.afterExtraction(jurisdiction, created.ruleVersionId());
            return ruleVersions.approveRequirements(created.ruleVersionId());
        });
        return created.ruleVersionId();
    }

    private static Path pdf(Path seedDir, String file) {
        return seedDir.resolve("pdf").resolve(file);
    }

    private UUID upload(LexvayUser as, UUID programId, Path pdf, DocumentKind kind, UUID courseVersionId) {
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(pdf);
        } catch (IOException e) {
            throw new UncheckedIOException("Missing seed PDF " + pdf + " (run make seed-pdfs)", e);
        }
        return as(as, () -> documents.upload(programId, new BytesMultipartFile(pdf.getFileName().toString(), "application/pdf", bytes), kind,
                courseVersionId).documentId());
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

    private static String readText(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + path, e);
        }
    }
}
