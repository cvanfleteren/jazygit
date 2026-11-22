package net.vanfleteren.jazygit.core.forgejo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * What the base branch of a pull request on Forgejo requires before it is merged, and the merge scheduled for when it
 * is met, as its timeline tells (ADR 181).
 */
class ForgejoBranchRulesTest {

    private static CommitStatus checks(CommitStatus.State lint, CommitStatus.State test) {
        return new CommitStatus(
                CommitStatus.State.RUNNING,
                List.of(
                        new CommitStatus.Status("ci / lint (push)", lint, ""),
                        new CommitStatus.Status("ci / test (push)", test, "")));
    }

    @Test
    void aBranchWithoutARuleWaitsForNothing() {
        assertFalse(ForgejoBranchRules.NONE.waitFor(CommitStatus.NONE, 0));
        assertFalse(new ForgejoBranchRules(false, true, List.empty(), 2).waitFor(CommitStatus.NONE, 0));
    }

    @Test
    void withoutRequiredContextsEveryCheckMustPassAndNoCheckNeverDoes() {
        ForgejoBranchRules all = new ForgejoBranchRules(true, true, List.empty(), 0);

        assertTrue(all.waitFor(CommitStatus.NONE, 0));
        assertTrue(all.waitFor(checks(CommitStatus.State.PASSED, CommitStatus.State.RUNNING), 0));
        assertFalse(all.waitFor(checks(CommitStatus.State.PASSED, CommitStatus.State.PASSED), 0));
    }

    @Test
    void withRequiredContextsEachMustMatchACheckAndAllItMatchesPass() {
        ForgejoBranchRules lint = new ForgejoBranchRules(true, true, List.of("ci / lint*"), 0);
        ForgejoBranchRules build = new ForgejoBranchRules(true, true, List.of("ci / build*"), 0);
        ForgejoBranchRules any = new ForgejoBranchRules(true, true, List.of("ci / *"), 0);

        assertFalse(lint.waitFor(checks(CommitStatus.State.PASSED, CommitStatus.State.FAILED), 0));
        assertTrue(lint.waitFor(checks(CommitStatus.State.RUNNING, CommitStatus.State.PASSED), 0));
        assertTrue(build.waitFor(checks(CommitStatus.State.PASSED, CommitStatus.State.PASSED), 0));
        assertTrue(any.waitFor(checks(CommitStatus.State.PASSED, CommitStatus.State.FAILED), 0));
    }

    @Test
    void missingApprovalsAreWaitedForTooWithoutChecksRequired() {
        ForgejoBranchRules two = new ForgejoBranchRules(true, false, List.empty(), 2);

        assertTrue(two.waitFor(CommitStatus.NONE, 1));
        assertFalse(two.waitFor(CommitStatus.NONE, 2));
    }

    @Test
    void aRequiredContextIsAGlobAsForgejoCompilesIt() {
        assertTrue(ForgejoBranchRules.glob("ci/*").matcher("ci/lint (push)").matches());
        assertTrue(ForgejoBranchRules.glob("ci/??st").matcher("ci/test").matches());
        assertTrue(ForgejoBranchRules.glob("ci/{lint,test}").matcher("ci/test").matches());
        assertFalse(
                ForgejoBranchRules.glob("ci/{lint,test}").matcher("ci/build").matches());
        assertTrue(ForgejoBranchRules.glob("v[0-9]").matcher("v7").matches());
        assertFalse(ForgejoBranchRules.glob("v[!0-9]").matcher("v7").matches());
        // What a regular expression would read otherwise is as it is.
        assertTrue(
                ForgejoBranchRules.glob("build (1.0)+").matcher("build (1.0)+").matches());
        assertFalse(ForgejoBranchRules.glob("build.x").matcher("buildyx").matches());
    }

    @Test
    void theLastEventOfTheTimelineTellsWhetherAMergeIsScheduled() {
        Instant at = Instant.parse("2026-11-22T10:00:00Z");
        ForgejoScheduledMerge.Event scheduled = new ForgejoScheduledMerge.Event.Scheduled("anna", at);
        ForgejoScheduledMerge.Event cancelled = new ForgejoScheduledMerge.Event.Cancelled();

        assertEquals(Option.none(), ForgejoScheduledMerge.of(List.empty()));
        assertEquals(Option.of(new ForgejoScheduledMerge("anna", at)), ForgejoScheduledMerge.of(List.of(scheduled)));
        assertEquals(Option.none(), ForgejoScheduledMerge.of(List.of(scheduled, cancelled)));
        assertEquals(
                Option.of(new ForgejoScheduledMerge("anna", at)),
                ForgejoScheduledMerge.of(List.of(scheduled, cancelled, scheduled)));
    }

    @Test
    void aReviewersLastApprovalThatCountsIsAnApproval() {
        List<ForgejoReview> reviews = List.of(
                new ForgejoReview("anna", ForgejoReview.Verdict.APPROVED, false, false),
                new ForgejoReview("bob", ForgejoReview.Verdict.APPROVED, false, false),
                new ForgejoReview("bob", ForgejoReview.Verdict.CHANGES_REQUESTED, false, false),
                new ForgejoReview("carl", ForgejoReview.Verdict.APPROVED, false, true),
                new ForgejoReview("dora", ForgejoReview.Verdict.COMMENTED, false, false));

        assertEquals(1, ForgejoReview.approvals(reviews));
    }
}
