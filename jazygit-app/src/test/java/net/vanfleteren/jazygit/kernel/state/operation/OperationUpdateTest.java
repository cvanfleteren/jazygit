package net.vanfleteren.jazygit.kernel.state.operation;

import static net.vanfleteren.jazygit.core.git.StubGit.executed;
import static net.vanfleteren.jazygit.kernel.state.TestModels.*;
import static net.vanfleteren.jazygit.kernel.ui.text.ErrorTexts.errorText;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.core.gh.StubGitHub;
import net.vanfleteren.jazygit.core.git.Git;
import net.vanfleteren.jazygit.core.git.StubGit;
import net.vanfleteren.jazygit.core.git.branches.StubRemotes;
import net.vanfleteren.jazygit.core.git.history.StubIntegrate;
import net.vanfleteren.jazygit.core.git.model.MergeMode;
import net.vanfleteren.jazygit.core.git.model.PushMode;
import net.vanfleteren.jazygit.core.git.process.GitError;
import net.vanfleteren.jazygit.core.git.process.GitFailure;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.InProgress;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.features.branches.branch.checkout.CheckoutModels;
import net.vanfleteren.jazygit.features.branches.branch.merge.MergeCmd;
import net.vanfleteren.jazygit.features.branches.branch.pull.PullCmd;
import net.vanfleteren.jazygit.features.branches.branch.push.PushCmd;
import net.vanfleteren.jazygit.features.branches.branch.rebase.RebaseCmd;
import net.vanfleteren.jazygit.kernel.state.AppError;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.Program;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.state.Update;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.github.LoadBranchChecks;
import net.vanfleteren.jazygit.kernel.state.github.LoadProtectedBranches;
import net.vanfleteren.jazygit.kernel.state.github.prs.LoadRepositoryPullRequests;
import net.vanfleteren.jazygit.kernel.state.load.LoadBranches;
import net.vanfleteren.jazygit.kernel.state.load.LoadStashes;
import net.vanfleteren.jazygit.kernel.state.load.LoadStatus;
import net.vanfleteren.jazygit.kernel.state.load.LoadWorktrees;
import org.junit.jupiter.api.Test;

class OperationUpdateTest {

    private static final MergeCmd MERGE = new MergeCmd(new BranchName("feature"), MergeMode.MERGE_COMMIT);
    private static final InProgress MERGING =
            new InProgress.Merging(new Sha("0123456789012345678901234567890123456789"));
    private static final PushCmd.Push PUSH = new PushCmd.Push(
            new BranchName("feature"), new RemoteBranch("origin", new BranchName("feature")), PushMode.FAST_FORWARD);

    @Test
    void aSuccessLogsItsCommandsReleasesItsBranchClearsTheErrorAndReadsTheRepositoryAgain() {
        Model pushing = TestModels.running(CheckoutModels.withCheckoutError(loaded(), "foo", "boom"), PUSH);

        Next next = TestModels.done(pushing, PUSH, succeeded(PUSH, "git push origin feature"));

        assertEquals(
                List.of(new LogEntry(PushCmd.PUSH, executed("git push origin feature"), Instant.EPOCH)),
                next.model().outcome().commandLog());
        assertEquals(HashSet.empty(), next.model().jobs().busy().keySet());
        assertEquals(Option.none(), next.model().outcome().error());
        assertEquals(
                List.of(
                        new LoadStatus(),
                        LoadBranches.LISTED,
                        new LoadStashes(),
                        new LoadWorktrees(),
                        new LoadProtectedBranches(),
                        new LoadRepositoryPullRequests(),
                        new LoadBranchChecks()),
                next.cmds());
    }

    @Test
    void onlyAnOperationThatChangesGitHubReadsItAgain() {
        PullCmd.Pull pull = new PullCmd.Pull(new BranchName("feature"));

        Next next = Update.update(loaded(), succeeded(pull, "git pull"));

        assertEquals(
                List.of(new LoadStatus(), LoadBranches.LISTED, new LoadStashes(), new LoadWorktrees()), next.cmds());
    }

    @Test
    void aFailureLogsTheCommandsThatRanAndShowsWhy() {
        Failure failure = new Failure.Git(
                new GitError.CommandFailed("rejected", 1), executed("git push origin feature"), Option.none());

        Next next = TestModels.done(TestModels.running(loaded(), PUSH), PUSH, new OperationMsg.Failed(PUSH, failure));

        assertEquals(
                List.of(new LogEntry(PushCmd.PUSH, executed("git push origin feature"), Instant.EPOCH)),
                next.model().outcome().commandLog());
        assertEquals(HashSet.empty(), next.model().jobs().busy().keySet());
        assertEquals(
                Option.of(new AppError.OperationFailed(PushCmd.PUSH, Option.of("feature"), failure)),
                next.model().outcome().error());
        assertEquals(Option.of("Pushing branch feature failed: rejected"), errorText(next.model()));
    }

    @Test
    void aFailedCleanupIsShownNextToTheFailure() {
        Failure failure = new Failure.Git(
                new GitError.CommandFailed("CONFLICT", 1),
                List.of(),
                Option.of(new GitError.CommandFailed("fatal: no rebase in progress", 128)));

        Next next = Update.update(loaded(), new OperationMsg.Failed(new RebaseCmd(new BranchName("main")), failure));

        assertEquals(
                Option.of("Rebasing onto main failed: CONFLICT; cleaning up afterwards failed too: "
                        + "fatal: no rebase in progress"),
                errorText(next.model()));
    }

    @Test
    void aCommandThatThrowsIsReportedAsABugOfThatCommand() {
        Git throwing = StubGit.of().withRemotes(new StubRemotes() {
            @Override
            public Either<GitFailure, List<ExecutedCommand>> push(
                    BranchName branch, RemoteBranch upstream, PushMode mode) {
                throw new IllegalStateException("unexpected");
            }
        });

        Msg msg = Program.perform(new Env(throwing, StubGitHub.of()), PUSH);

        OperationMsg.Failed failed = assertInstanceOf(OperationMsg.Failed.class, msg);
        assertInstanceOf(Failure.Bug.class, failed.failure());
        assertEquals(
                Option.of("Pushing branch feature failed: Unexpected error: unexpected"),
                errorText(Update.update(loaded(), failed).model()));
    }

    @Test
    void aCommandThatThrowsAnErrorStillAnswersSoItsBranchIsReleased() {
        Git throwing = StubGit.of().withRemotes(new StubRemotes() {
            @Override
            public Either<GitFailure, List<ExecutedCommand>> push(
                    BranchName branch, RemoteBranch upstream, PushMode mode) {
                throw new StackOverflowError();
            }
        });
        java.util.List<Throwable> bugs = new java.util.ArrayList<>();

        Msg msg = Program.perform(new Env(throwing, StubGitHub.of()), PUSH, bugs::add);

        assertInstanceOf(
                Failure.Bug.class,
                assertInstanceOf(OperationMsg.Failed.class, msg).failure());
        assertInstanceOf(StackOverflowError.class, bugs.getFirst());
    }

    @Test
    void anUnexpectedRefusalReportsWhatWasRefused() {
        assertEquals(
                Option.of("Pushing branch feature failed: boom"),
                errorText(Update.update(loaded(), failed(PUSH, "boom")).model()));
    }

    @Test
    void anOperationThatStopsOnConflictsIsReportedAsStoppedNotAsFailed() {
        Git stopping = StubGit.of().withIntegrate(new StubIntegrate() {
            @Override
            public Either<GitFailure, List<ExecutedCommand>> merge(BranchName branch, MergeMode mode) {
                return Either.left(new GitFailure(
                        new GitError.StoppedOnConflicts(Option.of(MERGING), 2), executed("git merge feature")));
            }
        });

        Msg msg = Program.perform(new Env(stopping, StubGitHub.of()), MERGE);

        assertEquals(
                new OperationMsg.Stopped(
                        MERGE, executed("git merge feature"), new Notice.StoppedOnConflicts(Option.of(MERGING), 2)),
                msg);
    }

    @Test
    void aStopOnConflictsLogsItsCommandsShowsANoticeInsteadOfTheErrorAndReadsTheRepositoryAgain() {
        Notice.StoppedOnConflicts notice = new Notice.StoppedOnConflicts(Option.of(MERGING), 2);

        Next next = Update.update(
                CheckoutModels.withCheckoutError(loaded(), "foo", "boom"),
                new OperationMsg.Stopped(MERGE, executed("git merge feature"), notice));

        assertEquals(
                List.of(new LogEntry(MergeCmd.MERGE, executed("git merge feature"), Instant.EPOCH)),
                next.model().outcome().commandLog());
        assertEquals(Option.none(), next.model().outcome().error());
        assertEquals(Option.of(notice), next.model().outcome().notice());
        assertEquals(
                List.of(new LoadStatus(), LoadBranches.LISTED, new LoadStashes(), new LoadWorktrees()), next.cmds());
    }

    @Test
    void theNextOperationClearsTheNotice() {
        Model stopped = Update.update(
                        loaded(),
                        new OperationMsg.Stopped(
                                MERGE, executed("git merge feature"), new Notice.StoppedOnConflicts(Option.none(), 1)))
                .model();

        assertEquals(
                Option.none(),
                Update.update(stopped, succeeded(PUSH, "git push"))
                        .model()
                        .outcome()
                        .notice());
        assertEquals(
                Option.none(),
                Update.update(stopped, failed(PUSH, "boom")).model().outcome().notice());
    }
}
