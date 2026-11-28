package com.lexvay.pathway;

import static org.assertj.core.api.Assertions.assertThat;

import com.lexvay.program.CreditUnit;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The truth table of the pathway evaluator: three values, never an enumeration of paths (AC-1). */
class PathwayCoverageTest {

    static PathwayGroup.Member course(String code, String credits) {
        return course(code, credits, CreditUnit.SEMESTER);
    }

    static PathwayGroup.Member course(String code, String credits, CreditUnit unit) {
        return new PathwayGroup.Member(UUID.nameUUIDFromBytes(code.getBytes()), code, "Course " + code,
                credits == null ? null : new BigDecimal(credits), unit);
    }

    static PathwayCoverage.Course ref(PathwayGroup.Member m) {
        return new PathwayCoverage.Course(m.courseId(), m.code());
    }

    static PathwayGroup allOf(String label, PathwayGroup.Member... members) {
        return new PathwayGroup(UUID.randomUUID(), label, GroupKind.ALL_OF, null, null, List.of(members), null);
    }

    static PathwayGroup chooseCount(String label, Integer min, PathwayGroup.Member... members) {
        return new PathwayGroup(UUID.randomUUID(), label, GroupKind.CHOOSE_COUNT, min, null, List.of(members), null);
    }

    static PathwayGroup chooseCredits(String label, String min, PathwayGroup.Member... members) {
        return new PathwayGroup(UUID.randomUUID(), label, GroupKind.CHOOSE_CREDITS, null, min == null ? null : new BigDecimal(min),
                List.of(members), null);
    }

    static PathwayCoverage of(PathwayGroup... groups) {
        return new PathwayCoverage(List.of(groups));
    }

    static final PathwayGroup.Member A = course("COUN 605", "3");
    static final PathwayGroup.Member B = course("COUN 650", "3");
    static final PathwayGroup.Member C = course("COUN 655", "3");
    static final PathwayGroup.Member D = course("COUN 645", "3");

    @Test
    void aCourseOfAnAllOfGroupIsOnEveryPath() {
        var verdict = of(allOf("Required courses", A), chooseCount("Electives", 1, B, C)).onEveryPath(List.of(ref(A)));
        assertThat(verdict.value()).isEqualTo(Tri.TRUE);
        assertThat(verdict.unknowns()).isEmpty();
    }

    @Test
    void anElectiveOfChooseTwoOfThreeIsNotOnEveryPath() {
        var pathway = of(allOf("Required courses", A), chooseCount("Electives: choose 2 of 3", 2, B, C, D));
        assertThat(pathway.onEveryPath(List.of(ref(B))).value()).isEqualTo(Tri.FALSE);
        assertThat(pathway.onEveryPath(List.of(ref(D))).value()).isEqualTo(Tri.FALSE);
    }

    @Test
    void everyMemberOfChooseThreeOfThreeIsOnEveryPath() {
        var pathway = of(chooseCount("Electives: choose 3 of 3", 3, B, C, D));
        assertThat(pathway.onEveryPath(List.of(ref(B))).value()).isEqualTo(Tri.TRUE);
        assertThat(pathway.onEveryPath(List.of(ref(C))).value()).isEqualTo(Tri.TRUE);
    }

    @Test
    void aSetOfElectivesIsOnEveryPathWhenNoPathCanAvoidAllOfThem() {
        var oneOfTwo = of(chooseCount("Electives", 1, B, C));
        assertThat(oneOfTwo.onEveryPath(List.of(ref(B), ref(C))).value()).isEqualTo(Tri.TRUE);
        assertThat(oneOfTwo.onEveryPath(List.of(ref(B))).value()).isEqualTo(Tri.FALSE);
        var twoOfThree = of(chooseCount("Electives", 2, B, C, D));
        assertThat(twoOfThree.onEveryPath(List.of(ref(B), ref(C))).value()).isEqualTo(Tri.TRUE);
    }

    @Test
    void anUnknownMinimumIsUnknownAndNamesTheGroup() {
        var verdict = of(allOf("Required courses", A), chooseCount("Electives", null, B, C)).onEveryPath(List.of(ref(B)));
        assertThat(verdict.value()).isEqualTo(Tri.UNKNOWN);
        assertThat(verdict.unknowns()).containsExactly("Electives: minimum not recorded");
    }

    @Test
    void anUnknownMinimumOfAGroupTheCoursesAreNotInDoesNotMatter() {
        var pathway = of(allOf("Required courses", A), chooseCount("Electives", null, B, C));
        assertThat(pathway.onEveryPath(List.of(ref(A))).value()).isEqualTo(Tri.TRUE);
        assertThat(of(chooseCount("Electives", null, B, C), allOf("Required courses", A)).onEveryPath(List.of(ref(A))).value()).isEqualTo(Tri.TRUE);
    }

    @Test
    void aCourseNoGroupPlacesIsUnknown() {
        var verdict = of(allOf("Required courses", A), chooseCount("Electives", 2, B, C, D)).onEveryPath(List.of(ref(course("COUN 700", "3"))));
        assertThat(verdict.value()).isEqualTo(Tri.UNKNOWN);
        assertThat(verdict.unknowns()).containsExactly("COUN 700 is not placed in a graduation group");
    }

    @Test
    void withoutGroupsNothingIsKnown() {
        var verdict = of().onEveryPath(List.of(ref(A)));
        assertThat(verdict.value()).isEqualTo(Tri.UNKNOWN);
        assertThat(verdict.unknowns()).containsExactly(PathwayCoverage.NO_GROUPS);
    }

    @Test
    void noCoursesAreOnEveryPath() {
        assertThat(of(allOf("Required courses", A)).onEveryPath(List.of()).value()).isEqualTo(Tri.FALSE);
    }

    @Test
    void aGroupThatForcesOneCourseSettlesTheAnswerEvenWhenAnotherCourseIsUnplaced() {
        var verdict = of(allOf("Required courses", A)).onEveryPath(List.of(ref(A), ref(B)));
        assertThat(verdict.value()).isEqualTo(Tri.TRUE);
        assertThat(verdict.unknowns()).isEmpty();
    }

    @Test
    void emptyGroupsRequireNothing() {
        var pathway = of(allOf("Required courses"), chooseCount("Electives", 1), chooseCredits("Credit electives", "6"), allOf("Core", A));
        assertThat(pathway.onEveryPath(List.of(ref(A))).value()).isEqualTo(Tri.TRUE);
        assertThat(pathway.onEveryPath(List.of(ref(B))).value()).isEqualTo(Tri.UNKNOWN);
        assertThat(of(allOf("Required courses"), chooseCount("Electives", null)).onEveryPath(List.of(ref(A))).unknowns())
                .containsExactly("COUN 605 is not placed in a graduation group");
    }

    @Test
    void chooseCreditsIsOnEveryPathWhenTheOthersCannotReachTheMinimum() {
        var four = course("COUN 660", "4");
        var six = chooseCredits("Credit electives", "6", B, C, four);
        assertThat(of(six).onEveryPath(List.of(ref(four))).value()).isEqualTo(Tri.FALSE);
        var seven = chooseCredits("Credit electives", "7", B, C, four);
        assertThat(of(seven).onEveryPath(List.of(ref(four))).value()).isEqualTo(Tri.TRUE);
        assertThat(of(seven).onEveryPath(List.of(ref(B))).value()).isEqualTo(Tri.FALSE);
    }

    @Test
    void chooseCreditsWithAnUnknownMinimumOrUnknownCreditsIsUnknown() {
        var four = course("COUN 660", "4");
        assertThat(of(chooseCredits("Credit electives", null, B, C, four)).onEveryPath(List.of(ref(four))).unknowns())
                .containsExactly("Credit electives: minimum not recorded");
        var unrecorded = course("COUN 670", null);
        var verdict = of(chooseCredits("Credit electives", "6", B, unrecorded, four)).onEveryPath(List.of(ref(four)));
        assertThat(verdict.value()).isEqualTo(Tri.UNKNOWN);
        assertThat(verdict.unknowns()).containsExactly("Credit electives: credits not recorded for COUN 670");
    }

    @Test
    void creditsThatAlreadyReachTheMinimumSettleItWhateverIsUnrecorded() {
        var unrecorded = course("COUN 670", null);
        var four = course("COUN 660", "4");
        assertThat(of(chooseCredits("Credit electives", "6", B, C, unrecorded, four)).onEveryPath(List.of(ref(four))).value()).isEqualTo(Tri.FALSE);
    }

    @Test
    void creditsInTwoUnitsAreUnknownBecauseThereIsNoConversion() {
        var quarter = course("COUN 660", "4", CreditUnit.QUARTER);
        var verdict = of(chooseCredits("Credit electives", "6", B, C, quarter)).onEveryPath(List.of(ref(B)));
        assertThat(verdict.value()).isEqualTo(Tri.UNKNOWN);
        assertThat(verdict.unknowns()).containsExactly("Credit electives: credits are recorded in more than one unit");
    }

    @Test
    void aCourseInTwoGroupsIsOnEveryPathIfEitherGroupForcesIt() {
        var pathway = of(chooseCount("Electives", 3, B, C, D), chooseCount("Skills", 1, B, A));
        assertThat(pathway.onEveryPath(List.of(ref(B))).value()).isEqualTo(Tri.TRUE);
        assertThat(pathway.onEveryPath(List.of(ref(A))).value()).isEqualTo(Tri.FALSE);
    }

    @Test
    void aCourseIsOnSomePathWhenAGroupCanChooseItAndEveryGroupCanBeSatisfied() {
        var pathway = of(allOf("Required courses", A), chooseCount("Electives", 2, B, C, D));
        assertThat(pathway.onSomePath(List.of(ref(A))).value()).isEqualTo(Tri.TRUE);
        assertThat(pathway.onSomePath(List.of(ref(B))).value()).isEqualTo(Tri.TRUE);
    }

    @Test
    void noPathExistsWhenAGroupCannotBeSatisfied() {
        var pathway = of(allOf("Required courses", A), chooseCount("Electives", 3, B, C));
        assertThat(pathway.onSomePath(List.of(ref(A))).value()).isEqualTo(Tri.FALSE);
        assertThat(of(allOf("Required courses", A), chooseCredits("Credit electives", "7", B, C)).onSomePath(List.of(ref(A))).value())
                .isEqualTo(Tri.FALSE);
    }

    @Test
    void somePathIsUnknownWhileAMinimumOrACreditIsNotRecorded() {
        var unknownMinimum = of(allOf("Required courses", A), chooseCount("Electives", null, B, C)).onSomePath(List.of(ref(A)));
        assertThat(unknownMinimum.value()).isEqualTo(Tri.UNKNOWN);
        assertThat(unknownMinimum.unknowns()).containsExactly("Electives: minimum not recorded");
        var unrecorded = course("COUN 670", null);
        assertThat(of(chooseCredits("Credit electives", "9", B, C, unrecorded)).onSomePath(List.of(ref(B))).value()).isEqualTo(Tri.UNKNOWN);
        assertThat(of(chooseCredits("Credit electives", "6", B, C, unrecorded)).onSomePath(List.of(ref(B))).value()).isEqualTo(Tri.TRUE);
    }

    @Test
    void somePathForACourseNoGroupPlacesIsUnknownAndForNoCoursesIsFalse() {
        var pathway = of(allOf("Required courses", A));
        assertThat(pathway.onSomePath(List.of(ref(B))).value()).isEqualTo(Tri.UNKNOWN);
        assertThat(pathway.onSomePath(List.of()).value()).isEqualTo(Tri.FALSE);
        assertThat(of().onSomePath(List.of(ref(A))).unknowns()).containsExactly(PathwayCoverage.NO_GROUPS);
    }

    @Test
    void theMinimumAddsEveryAllOfCourseAndTheCheapestChoice() {
        var clinical = course("COUN 660", "6");
        var pathway = of(allOf("Required courses", A, course("COUN 610", "3"), clinical),
                chooseCount("Electives: choose 2 of 3", 2, course("COUN 650", "3"), course("COUN 655", "4.5"), course("COUN 645", "3")));
        var minimum = pathway.minimumCredits();
        assertThat(minimum.known()).isTrue();
        assertThat(minimum.credits()).isEqualByComparingTo("18");
        assertThat(minimum.unit()).isEqualTo(CreditUnit.SEMESTER);
        assertThat(minimum.unknowns()).isEmpty();
        assertThat(minimum.basis()).isEqualTo("the credits of the courses in Required courses; Electives: choose 2 of 3");
    }

    @Test
    void theCheapestChoiceTakesTheSmallestCredits() {
        var minimum = of(chooseCount("Electives", 2, course("COUN 650", "3"), course("COUN 655", "6"), course("COUN 645", "4"))).minimumCredits();
        assertThat(minimum.credits()).isEqualByComparingTo("7");
    }

    @Test
    void chooseCreditsTakesTheSmallestTotalThatReachesTheMinimum() {
        var members = new PathwayGroup.Member[] {course("COUN 650", "3"), course("COUN 655", "3"), course("COUN 645", "4"), course("COUN 660", "6")};
        assertThat(of(chooseCredits("Credit electives", "7", members)).minimumCredits().credits()).isEqualByComparingTo("7");
        assertThat(of(chooseCredits("Credit electives", "8", members)).minimumCredits().credits()).isEqualByComparingTo("9");
        assertThat(of(chooseCredits("Credit electives", "16", members)).minimumCredits().credits()).isEqualByComparingTo("16");
        assertThat(of(chooseCredits("Credit electives", "16.01", members)).minimumCredits().known()).isFalse();
    }

    @Test
    void theMinimumIsUnknownWhenAMinimumOrACreditIsNotRecorded() {
        var unknownMinimum = of(allOf("Required courses", A), chooseCount("Electives", null, B, C)).minimumCredits();
        assertThat(unknownMinimum.known()).isFalse();
        assertThat(unknownMinimum.credits()).isNull();
        assertThat(unknownMinimum.unknowns()).containsExactly("Electives: minimum not recorded");
        var unrecorded = of(allOf("Required courses", A, course("COUN 670", null)), chooseCount("Electives", 1, B, course("COUN 680", null))).minimumCredits();
        assertThat(unrecorded.unknowns()).containsExactly("Required courses: credits not recorded for COUN 670",
                "Electives: credits not recorded for COUN 680");
    }

    @Test
    void theMinimumIsUnknownForMixedUnitsAndSaysNothingIsConverted() {
        var minimum = of(allOf("Required courses", A, course("COUN 610", "4", CreditUnit.QUARTER))).minimumCredits();
        assertThat(minimum.known()).isFalse();
        assertThat(minimum.unknowns()).containsExactly("credits are recorded in more than one unit (QUARTER, SEMESTER)");
    }

    @Test
    void theMinimumInQuarterCreditsIsKnownWithItsUnit() {
        var minimum = of(allOf("Required courses", course("COUN 605", "4", CreditUnit.QUARTER), course("COUN 610", "4", CreditUnit.QUARTER))).minimumCredits();
        assertThat(minimum.credits()).isEqualByComparingTo("8");
        assertThat(minimum.unit()).isEqualTo(CreditUnit.QUARTER);
    }

    @Test
    void theMinimumIsUnknownWhenACourseIsCountedByTwoGroups() {
        var minimum = of(allOf("Required courses", A, B), chooseCount("Electives", 1, B, C)).minimumCredits();
        assertThat(minimum.known()).isFalse();
        assertThat(minimum.unknowns()).containsExactly("COUN 650 is in more than one group, so its credits cannot be counted once");
    }

    @Test
    void theMinimumIsUnknownWithoutGroupsOrCoursesAndForAGroupThatCannotBeSatisfied() {
        assertThat(of().minimumCredits().unknowns()).containsExactly(PathwayCoverage.NO_GROUPS);
        assertThat(of(allOf("Required courses")).minimumCredits().unknowns()).containsExactly("no courses are placed in the graduation groups");
        assertThat(of(chooseCount("Electives", 3, B, C)).minimumCredits().unknowns()).containsExactly("Electives: it asks for 3 of 2 courses");
        assertThat(of(chooseCredits("Credit electives", "7", B, C)).minimumCredits().unknowns())
                .containsExactly("Credit electives: its courses add up to fewer credits than it asks for");
    }

    @Test
    void anEmptyGroupAddsNothingToTheMinimumAndAnEmptyChoiceCannotBeMet() {
        var minimum = of(allOf("Required courses", A), allOf("Nothing"), chooseCount("Electives", 1)).minimumCredits();
        assertThat(minimum.unknowns()).containsExactly("Electives: it asks for 1 of 0 courses");
        assertThat(of(allOf("Required courses", A), allOf("Nothing")).minimumCredits().credits()).isEqualByComparingTo("3");
    }

    @Test
    void triAndFollowsKleeneLogic() {
        assertThat(Tri.UNKNOWN.and(Tri.FALSE)).isEqualTo(Tri.FALSE);
        assertThat(Tri.FALSE.and(Tri.UNKNOWN)).isEqualTo(Tri.FALSE);
        assertThat(Tri.UNKNOWN.and(Tri.TRUE)).isEqualTo(Tri.UNKNOWN);
        assertThat(Tri.TRUE.and(Tri.TRUE)).isEqualTo(Tri.TRUE);
    }
}
