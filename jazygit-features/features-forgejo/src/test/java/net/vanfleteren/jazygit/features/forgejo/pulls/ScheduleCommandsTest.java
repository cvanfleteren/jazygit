package net.vanfleteren.jazygit.features.forgejo.pulls;

import static net.vanfleteren.jazygit.features.forgejo.pulls.PullRequestFixtures.HEAD;
import static net.vanfleteren.jazygit.features.forgejo.pulls.PullRequestFixtures.ORIGIN;
import static net.vanfleteren.jazygit.features.forgejo.pulls.PullRequestFixtures.details;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.util.concurrent.atomic.AtomicReference;
import net.vanfleteren.jazygit.core.forgejo.CommitStatus;
import net.vanfleteren.jazygit.core.forgejo.ForgejoBranchRules;
import net.vanfleteren.jazygit.core.forgejo.ForgejoError;
import net.vanfleteren.jazygit.core.forgejo.ForgejoMerge;
import net.vanfleteren.jazygit.core.forgejo.ForgejoMergeStyle;
import net.vanfleteren.jazygit.core.forgejo.ForgejoMerged;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.features.forgejo.pulls.PullRequestFixtures.FakeForgejo;
import net.vanfleteren.jazygit.kernel.state.operation.OperationMsg;
import org.junit.jupiter.api.Test;

/**
 * Scheduling a merge on Forgejo for when the checks succeed, and cancelling it (ADR 181).
 */
class ScheduleCommandsTest {

    private static final BranchName BRANCH = new BranchName("feature");
    private static final ForgejoBranchRules REQUIRED = new ForgejoBranchRules(true, true, List.empty(), 0);
    private static final MergePullRequestCmd WHEN_READY = new MergePullRequestCmd(
            ORIGIN,
            BRANCH,
            12,
            ForgejoMergeStyle.REBASE,
            new FailedMerge.Standing(HEAD),
            false,
            Option.of(REQUIRED),
            0);
    private static final CancelScheduledMergeCmd CANCEL = new CancelScheduledMergeCmd(ORIGIN, BRANCH, 12);

    private static CommitStatus checks(CommitStatus.State state) {
        return new CommitStatus(state, List.of(new CommitStatus.Status("ci", state, "")));
    }

    private static FakeForgejo recording(AtomicReference<ForgejoMerge> asked, CommitStatus.State now) {
        return FakeForgejo.of(details("Merge it"))
                .withStatus(Either.right(checks(now)))
                .merging(merge -> {
                    asked.set(merge);
                    return Either.right(
                            merge.whenChecksSucceed() ? new ForgejoMerged.Scheduled() : new ForgejoMerged.Merged());
                });
    }

    @Test
    void checksStillRunningAreWaitedFor() {
        AtomicReference<ForgejoMerge> asked = new AtomicReference<>();

        assertEquals(
                new PullRequestMsg.Merged(WHEN_READY, new ForgejoMerged.Scheduled()),
                WHEN_READY.run(recording(asked, CommitStatus.State.RUNNING).env()));
        assertEquals(new ForgejoMerge(ForgejoMergeStyle.REBASE, HEAD, false, true), asked.get());
        assertTrue(WHEN_READY.whenChecksSucceed());
    }

    @Test
    void checksThatPassedSinceTheyWereReadMergeItNowAsForgejoWouldWait() {
        AtomicReference<ForgejoMerge> asked = new AtomicReference<>();

        assertEquals(
                new PullRequestMsg.Merged(WHEN_READY, new ForgejoMerged.Merged()),
                WHEN_READY.run(recording(asked, CommitStatus.State.PASSED).env()));
        assertFalse(asked.get().whenChecksSucceed());
    }

    @Test
    void checksThatCannotBeReadAgainAreWaitedForAsRead() {
        AtomicReference<ForgejoMerge> asked = new AtomicReference<>();
        FakeForgejo unread = recording(asked, CommitStatus.State.PASSED)
                .withStatus(Either.left(new ForgejoError.Unreachable("timed out")));

        WHEN_READY.run(unread.env());

        assertTrue(asked.get().whenChecksSucceed());
    }

    @Test
    void aMergeScheduledAlreadyIsNoFailure() {
        FakeForgejo already = FakeForgejo.of(details("Merge it"))
                .withStatus(Either.right(checks(CommitStatus.State.RUNNING)))
                .merging(
                        merge -> Either.left(
                                new ForgejoError.Refused(
                                        409,
                                        "{\"message\":\"pull request is already scheduled to auto merge when checks succeed [pull_id: 81]\"}")));

        assertEquals(new PullRequestMsg.AlreadyScheduled(WHEN_READY), WHEN_READY.run(already.env()));
    }

    @Test
    void cancellingIsDoneOrToldNoLongerScheduledOrFails() {
        FakeForgejo forgejo = FakeForgejo.of(details("Merge it"));
        ForgejoError forbidden = new ForgejoError.Refused(
                403, "{\"message\":\"user has no permission to cancel the scheduled auto merge\"}");

        assertEquals(new PullRequestMsg.MergeCancelled(CANCEL), CANCEL.run(forgejo.env()));
        assertEquals(
                new PullRequestMsg.NoLongerScheduled(CANCEL),
                CANCEL.run(forgejo.scheduling(
                                Either.right(ForgejoBranchRules.NONE),
                                Either.right(Option.none()),
                                Option.of(new ForgejoError.Refused(404, "")))
                        .env()));
        assertEquals(
                new OperationMsg.Failed(CANCEL, new MergeFailure(forbidden, "code.example.net", "cvf/nix")),
                CANCEL.run(forgejo.scheduling(
                                Either.right(ForgejoBranchRules.NONE),
                                Either.right(Option.none()),
                                Option.of(forbidden))
                        .env()));
    }
}
