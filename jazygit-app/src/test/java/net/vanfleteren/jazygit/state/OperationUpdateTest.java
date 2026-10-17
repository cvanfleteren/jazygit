package net.vanfleteren.jazygit.state;

import static net.vanfleteren.jazygit.git.StubGitProvider.executed;
import static net.vanfleteren.jazygit.state.TestModels.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.branch.BranchCmd;
import net.vanfleteren.jazygit.gh.StubGitHub;
import net.vanfleteren.jazygit.git.GitError;
import net.vanfleteren.jazygit.git.GitFailure;
import net.vanfleteren.jazygit.git.GitInfoProvider;
import net.vanfleteren.jazygit.git.StubGitProvider;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.InProgress;
import net.vanfleteren.jazygit.git.model.MergeMode;
import net.vanfleteren.jazygit.git.model.PushMode;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.Cmd.LoadBranches;
import net.vanfleteren.jazygit.state.Cmd.LoadStashes;
import net.vanfleteren.jazygit.state.Cmd.LoadStatus;
import net.vanfleteren.jazygit.state.Update.Next;
import org.junit.jupiter.api.Test;

class OperationUpdateTest {

    private static final BranchCmd.Merge MERGE =
            new BranchCmd.Merge(new Branch.Name("feature"), MergeMode.MERGE_COMMIT);
    private static final InProgress MERGING =
            new InProgress.Merging(new Sha("0123456789012345678901234567890123456789"));
    private static final BranchCmd.Push PUSH = new BranchCmd.Push(
            new Branch.Name("feature"), new RemoteBranch("origin", new Branch.Name("feature")), PushMode.FAST_FORWARD);

    @Test
    void aSuccessLogsItsCommandsReleasesItsBranchClearsTheErrorAndReadsTheRepositoryAgain() {
        Model pushing = TestModels.withCheckoutError(loaded(), "foo", "boom")
                .withBusyBranch(new BranchRef.Local(new Branch.Name("feature")), Operation.PUSH);

        Next next = Update.update(pushing, succeeded(PUSH, "git push origin feature"));

        assertEquals(
                List.of(new LogEntry(Operation.PUSH, executed("git push origin feature"))),
                next.model().outcome().commandLog());
        assertEquals(HashSet.empty(), next.model().jobs().busy().keySet());
        assertEquals(Option.none(), next.model().outcome().error());
        assertEquals(
                List.of(
                        new LoadStatus(),
                        new LoadBranches(),
                        new net.vanfleteren.jazygit.state.Cmd.LoadRemoteBranches(),
                        new LoadStashes(),
                        new LoadPullRequests(),
                        new LoadBranchChecks()),
                next.cmds());
    }

    @Test
    void onlyAnOperationThatChangesGitHubReadsItAgain() {
        BranchCmd.Pull pull = new BranchCmd.Pull(new Branch.Name("feature"));

        Next next = Update.update(loaded(), succeeded(pull, "git pull"));

        assertEquals(
                List.of(
                        new LoadStatus(),
                        new LoadBranches(),
                        new net.vanfleteren.jazygit.state.Cmd.LoadRemoteBranches(),
                        new LoadStashes()),
                next.cmds());
    }

    @Test
    void aFailureLogsTheCommandsThatRanAndShowsWhy() {
        Failure failure = new Failure.Git(
                new GitError.CommandFailed("rejected", 1), executed("git push origin feature"), Option.none());

        Next next = Update.update(
                loaded().withBusyBranch(new BranchRef.Local(new Branch.Name("feature")), Operation.PUSH),
                new OperationMsg.Failed(PUSH, failure));

        assertEquals(
                List.of(new LogEntry(Operation.PUSH, executed("git push origin feature"))),
                next.model().outcome().commandLog());
        assertEquals(HashSet.empty(), next.model().jobs().busy().keySet());
        assertEquals(
                Option.of(new AppError.OperationFailed(Operation.PUSH, Option.of("feature"), failure)),
                next.model().outcome().error());
        assertEquals(Option.of("Pushing branch feature failed: rejected"), errorText(next.model()));
    }

    @Test
    void aFailedCleanupIsShownNextToTheFailure() {
        Failure failure = new Failure.Git(
                new GitError.CommandFailed("CONFLICT", 1),
                List.of(),
                Option.of(new GitError.CommandFailed("fatal: no rebase in progress", 128)));

        Next next = Update.update(
                loaded(), new OperationMsg.Failed(new BranchCmd.Rebase(new Branch.Name("main")), failure));

        assertEquals(
                Option.of("Rebasing onto main failed: CONFLICT; cleaning up afterwards failed too: "
                        + "fatal: no rebase in progress"),
                errorText(next.model()));
    }

    @Test
    void aCommandThatThrowsIsReportedAsABugOfThatCommand() {
        GitInfoProvider throwing = new StubGitProvider() {
            @Override
            public Either<GitFailure<GitError.ProcessError>, List<ExecutedCommand>> push(
                    Branch.Name branch, RemoteBranch upstream, PushMode mode) {
                throw new IllegalStateException("unexpected");
            }
        };

        Msg msg = Program.perform(new Env(throwing, StubGitHub.empty()), PUSH);

        OperationMsg.Failed failed = assertInstanceOf(OperationMsg.Failed.class, msg);
        assertInstanceOf(Failure.Bug.class, failed.failure());
        assertEquals(
                Option.of("Pushing branch feature failed: Unexpected error: unexpected"),
                errorText(Update.update(loaded(), failed).model()));
    }

    @Test
    void aCommandThatThrowsAnErrorStillAnswersSoItsBranchIsReleased() {
        GitInfoProvider throwing = new StubGitProvider() {
            @Override
            public Either<GitFailure<GitError.ProcessError>, List<ExecutedCommand>> push(
                    Branch.Name branch, RemoteBranch upstream, PushMode mode) {
                throw new StackOverflowError();
            }
        };
        java.util.List<Throwable> bugs = new java.util.ArrayList<>();

        Msg msg = Program.perform(new Env(throwing, StubGitHub.empty()), PUSH, bugs::add);

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
        GitInfoProvider stopping = new StubGitProvider() {
            @Override
            public Either<GitFailure<GitError>, List<ExecutedCommand>> merge(Branch.Name branch, MergeMode mode) {
                return Either.left(new GitFailure<>(
                        new GitError.StoppedOnConflicts(Option.of(MERGING), 2), executed("git merge feature")));
            }
        };

        Msg msg = Program.perform(new Env(stopping, StubGitHub.empty()), MERGE);

        assertEquals(
                new OperationMsg.Stopped(
                        MERGE, executed("git merge feature"), new Notice.StoppedOnConflicts(Option.of(MERGING), 2)),
                msg);
    }

    @Test
    void aStopOnConflictsLogsItsCommandsShowsANoticeInsteadOfTheErrorAndReadsTheRepositoryAgain() {
        Notice.StoppedOnConflicts notice = new Notice.StoppedOnConflicts(Option.of(MERGING), 2);

        Next next = Update.update(
                TestModels.withCheckoutError(loaded(), "foo", "boom"),
                new OperationMsg.Stopped(MERGE, executed("git merge feature"), notice));

        assertEquals(
                List.of(new LogEntry(Operation.MERGE, executed("git merge feature"))),
                next.model().outcome().commandLog());
        assertEquals(Option.none(), next.model().outcome().error());
        assertEquals(Option.of(notice), next.model().outcome().notice());
        assertEquals(
                List.of(
                        new LoadStatus(),
                        new LoadBranches(),
                        new net.vanfleteren.jazygit.state.Cmd.LoadRemoteBranches(),
                        new LoadStashes()),
                next.cmds());
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
