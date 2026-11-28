package com.lexvay.pathway;

import com.lexvay.assessment.ProgramCitationService;
import com.lexvay.audit.AuditService;
import com.lexvay.auth.CurrentUser;
import com.lexvay.common.ConflictException;
import com.lexvay.common.NotFoundException;
import com.lexvay.common.TenantContext;
import com.lexvay.program.Program;
import com.lexvay.program.ProgramService;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A program's graduation paths (AC-1). Groups are added and retired, never edited (product rule 5); every change is
 * audited (rule 9). The numbers it shows come from {@link PathwayCoverage}, not from a model (rule 3).
 */
@Service
public class PathwayService {

    /** The groups a course joins when it is created, as V026's backfill names them (D-588). */
    public static final String REQUIRED_LABEL = "Required courses";
    public static final String ELECTIVES_LABEL = "Electives";

    /** The group a new course joined; {@code created} when the course had to make the group first. */
    public record Placement(UUID groupId, String label, boolean created) {
    }

    private final PathwayRepository pathways;
    private final ProgramService programs;
    private final ProgramCitationService citations;
    private final AuditService audit;

    public PathwayService(PathwayRepository pathways, ProgramService programs, ProgramCitationService citations, AuditService audit) {
        this.pathways = pathways;
        this.programs = programs;
        this.citations = citations;
        this.audit = audit;
    }

    /** One statement. {@code catalogYear} null reads the program's own. */
    @Transactional(readOnly = true)
    public PathwayView read(UUID programId, String catalogYear) {
        PathwayRepository.Read read = pathways.read(programId, catalogYear).orElseThrow(() -> new NotFoundException("Program not found"));
        return view(programId, read);
    }

    @Transactional
    public PathwayView create(UUID programId, CreatePathwayGroupRequest request) {
        Program program = programs.get(programId);
        String catalogYear = request.catalogYear().trim();
        List<UUID> courseIds = validatedCourses(program, request);
        UUID tenantId = TenantContext.require();
        UUID actor = CurrentUser.require().id();
        UUID spanId = null;
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("programId", programId.toString());
        detail.put("catalogYear", catalogYear);
        detail.put("label", request.label().trim());
        detail.put("kind", request.kind().name());
        if (request.minCount() != null) {
            detail.put("minCount", request.minCount());
        }
        if (request.minCredits() != null) {
            detail.put("minCredits", request.minCredits().toPlainString());
        }
        detail.put("courseIds", courseIds.stream().map(UUID::toString).toList());
        if (request.citation() != null) {
            ProgramCitationService.Citation citation = citations.cite(programId, request.citation().documentId(),
                    request.citation().pageNumber(), request.citation().quote(), actor);
            spanId = citation.spanId();
            detail.put("documentId", citation.document().id().toString());
            detail.put("pageNumber", citation.page().pageNumber());
            detail.put("quote", citation.match().exactText());
        }
        UUID id = UUID.randomUUID();
        pathways.insertGroup(id, tenantId, programId, catalogYear, request.label().trim(), request.kind(), request.minCount(),
                request.minCredits(), spanId, actor);
        pathways.insertMembers(id, tenantId, courseIds);
        audit.record("PATHWAY_GROUP_CREATED", "pathway_group", id, detail);
        return read(programId, catalogYear);
    }

    /**
     * Puts a course that a person has just created, or given a new catalog year, in the group its flag names, as V026's
     * backfill would: a required course in the ALL_OF "Required courses" group, any other in the CHOOSE_COUNT "Electives"
     * group, whose minimum stays unknown until a person records it. A group that does not exist, or that was retired, is
     * made first. Membership rows are only ever added (D-586), so a new member is an append. A course that an active group
     * of that catalog year already holds stays where a person put it. Call it inside the transaction that creates the course.
     */
    public Optional<Placement> place(UUID programId, String catalogYear, UUID courseId, boolean required) {
        if (pathways.isPlaced(programId, catalogYear, courseId)) {
            return Optional.empty();
        }
        String label = required ? REQUIRED_LABEL : ELECTIVES_LABEL;
        GroupKind kind = required ? GroupKind.ALL_OF : GroupKind.CHOOSE_COUNT;
        UUID tenantId = TenantContext.require();
        Optional<UUID> existing = pathways.findActiveGroup(programId, catalogYear, label, kind);
        UUID groupId = existing.orElseGet(UUID::randomUUID);
        if (existing.isEmpty()) {
            pathways.insertGroup(groupId, tenantId, programId, catalogYear, label, kind, null, null, null, CurrentUser.require().id());
            audit.record("PATHWAY_GROUP_CREATED", "pathway_group", groupId, Map.of("programId", programId.toString(),
                    "catalogYear", catalogYear, "label", label, "kind", kind.name(), "courseIds", List.of(courseId.toString())));
        }
        pathways.insertMembers(groupId, tenantId, List.of(courseId));
        return Optional.of(new Placement(groupId, label, existing.isEmpty()));
    }

    @Transactional
    public PathwayView retire(UUID groupId, RetirePathwayGroupRequest request) {
        PathwayRepository.Stored group = pathways.findGroup(groupId).orElseThrow(() -> new NotFoundException("Pathway group not found"));
        if (group.retired()) {
            throw new ConflictException("This pathway group is already retired");
        }
        pathways.retire(groupId, TenantContext.require(), CurrentUser.require().id(), request.reason().trim());
        audit.record("PATHWAY_GROUP_RETIRED", "pathway_group", groupId, Map.of(
                "programId", group.programId().toString(), "catalogYear", group.catalogYear(), "label", group.label(),
                "reason", request.reason().trim()));
        return read(group.programId(), group.catalogYear());
    }

    /** The members as a list without repeats, once the request is known to be consistent with its kind and the program. */
    private List<UUID> validatedCourses(Program program, CreatePathwayGroupRequest request) {
        boolean count = request.kind() == GroupKind.CHOOSE_COUNT;
        boolean credits = request.kind() == GroupKind.CHOOSE_CREDITS;
        if (request.minCount() != null && !count) {
            throw new IllegalArgumentException("minCount belongs to a CHOOSE_COUNT group");
        }
        if (request.minCredits() != null && !credits) {
            throw new IllegalArgumentException("minCredits belongs to a CHOOSE_CREDITS group");
        }
        List<UUID> courseIds = List.copyOf(new LinkedHashSet<>(request.courseIds()));
        if (courseIds.size() != request.courseIds().size()) {
            throw new IllegalArgumentException("A course can be listed once in a group");
        }
        if (pathways.coursesOfProgram(program.id(), courseIds).size() != courseIds.size()) {
            throw new IllegalArgumentException("Every course of a group must belong to this program");
        }
        if (count && request.minCount() != null && request.minCount() > courseIds.size()) {
            throw new IllegalArgumentException("A group cannot ask for " + request.minCount() + " of " + courseIds.size() + " courses");
        }
        return courseIds;
    }

    private PathwayView view(UUID programId, PathwayRepository.Read read) {
        PathwayCoverage coverage = new PathwayCoverage(read.groups());
        PathwayCoverage.Minimum minimum = coverage.minimumCredits();
        List<PathwayView.Group> groups = read.groups().stream().map(g -> new PathwayView.Group(g.id(), g.label(), g.kind(), g.minCount(),
                g.minCredits(), g.citation(), g.members().stream().map(m -> member(coverage, m)).toList())).toList();
        return new PathwayView(programId, read.catalogYear(), groups, minimum.credits(), minimum.unit(), minimum.unknowns());
    }

    private static PathwayView.Member member(PathwayCoverage coverage, PathwayGroup.Member m) {
        List<PathwayCoverage.Course> course = List.of(new PathwayCoverage.Course(m.courseId(), m.code()));
        return new PathwayView.Member(m.courseId(), m.code(), m.title(), m.credits(), m.creditUnit(),
                coverage.onEveryPath(course).value(), coverage.onSomePath(course).value());
    }
}
