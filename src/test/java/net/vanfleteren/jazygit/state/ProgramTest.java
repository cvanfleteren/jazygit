package net.vanfleteren.jazygit.state;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.branch.BranchCmd;
import net.vanfleteren.jazygit.feature.branch.CheckoutMsg;
import net.vanfleteren.jazygit.feature.branch.DeleteBranchMsg;
import net.vanfleteren.jazygit.feature.branch.NewBranchMsg;
import net.vanfleteren.jazygit.feature.commit.AmendMsg;
import net.vanfleteren.jazygit.feature.commit.AmendPopup;
import net.vanfleteren.jazygit.feature.commit.CommitMsg;
import net.vanfleteren.jazygit.feature.commit.CommitPopup;
import net.vanfleteren.jazygit.feature.commit.RewordMsg;
import net.vanfleteren.jazygit.feature.selection.SelectionMsg;
import net.vanfleteren.jazygit.feature.stage.StageMsg;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.git.GitError;
import net.vanfleteren.jazygit.git.GitError.CheckoutError;
import net.vanfleteren.jazygit.git.GitError.DiffError;
import net.vanfleteren.jazygit.git.GitError.DiscardError;
import net.vanfleteren.jazygit.git.GitError.LogError;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.GitError.RepositoryUnreadable;
import net.vanfleteren.jazygit.git.GitError.RewordError;
import net.vanfleteren.jazygit.git.GitError.StashCheckoutError;
import net.vanfleteren.jazygit.git.GitFailure;
import net.vanfleteren.jazygit.git.StubGitProvider;
import net.vanfleteren.jazygit.git.model.CommitMessage;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.ChangeType;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.git.model.Diffs;
import net.vanfleteren.jazygit.git.model.DiscardPlan;
import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.git.model.RepoStatus;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.state.Loadable.Failed;
import net.vanfleteren.jazygit.state.Loadable.Loaded;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The runtime loop: commands are performed against the provider and their results fed back.
 * Both executors are queues drained by the test, so every step is deterministic.
 */
class ProgramTest {

    private static final class FakeProvider implements StubGitProvider {

        RepoStatus status = new RepoStatus("main", "aaaa", List.of());
        String statusError;
        String checkoutError;
        String indexError;
        String commitFailure;
        final java.util.List<DiscardPlan> discarded = new ArrayList<>();
        final java.util.List<String> commits = new ArrayList<>();
        final java.util.List<String> deleted = new ArrayList<>();
        final java.util.List<String> pushed = new ArrayList<>();
        final java.util.List<String> indexCalls = new ArrayList<>();
        int commitLoads;
        final java.util.List<String> logLoads = new ArrayList<>();

        @Override
        public Either<ProcessError, RepoStatus> status() {
            return statusError != null ? Either.left(new GitError.CommandFailed(statusError, 1)) : Either.right(status);
        }

        @Override
        public Either<GitFailure<ProcessError>, List<String>> drop(String ref) {
            return StubGitProvider.ran("git stash drop " + ref);
        }

        @Override
        public Either<GitFailure<ProcessError>, List<String>> pop(String ref) {
            return StubGitProvider.ran("git stash pop " + ref);
        }

        @Override
        public Either<GitFailure<ProcessError>, List<String>> stash(String message) {
            return StubGitProvider.ran("git stash");
        }

        @Override
        public Either<RepositoryUnreadable, List<Branch>> branches() {
            return Either.right(List.of(new Branch("main", status.head().equals("main"), "main-tip"),
                    new Branch("other", status.head().equals("other"), "other-tip", Instant.EPOCH, true)));
        }

        @Override
        public Either<RepositoryUnreadable, List<Commit>> commits() {
            commitLoads++;
            return Either.right(List.of(commit(status.headOid())));
        }

        @Override
        public Either<LogError, List<Commit>> log(String branch) {
            logLoads.add(branch);
            return Either.right(List.of(commit(branch + "-tip")));
        }

        private static Commit commit(String sha) {
            return new Commit(new Sha(sha), "Ada", "ada@example.com", Instant.EPOCH, "msg", "");
        }

        @Override
        public Either<GitFailure<CheckoutError>, List<String>> checkout(String branch) {
            return GitFailure.widen(switchTo(branch, "git checkout"));
        }

        @Override
        public Either<GitFailure<StashCheckoutError>, List<String>> checkoutWithStash(String branch) {
            return GitFailure.widen(switchTo(branch, "git checkoutWithStash"));
        }

        @Override
        public Either<GitFailure<ProcessError>, List<String>> createBranch(String name, String startPoint) {
            return switchTo(name, "git createBranch");
        }

        private Either<GitFailure<ProcessError>, List<String>> switchTo(String branch, String command) {
            if (checkoutError != null) {
                return StubGitProvider.refused(command, checkoutError);
            }
            status = new RepoStatus(branch, branch + "-head", List.of());
            return StubGitProvider.ran(command);
        }

        @Override
        public Either<GitFailure<ProcessError>, List<String>> push(String branch, boolean forceWithLease) {
            pushed.add(branch + (forceWithLease ? " force" : ""));
            return StubGitProvider.ran("git push");
        }

        @Override
        public Either<GitFailure<ProcessError>, List<String>> rebase(String onto) {
            return StubGitProvider.ran("git rebase");
        }

        @Override
        public Either<GitFailure<ProcessError>, List<String>> deleteBranch(String name, boolean local, boolean remote) {
            deleted.add(name + (local ? " local" : "") + (remote ? " remote" : ""));
            return StubGitProvider.ran("git deleteBranch");
        }

        @Override
        public Either<GitFailure<ProcessError>, List<String>> stage(List<String> paths) {
            return index("stage", paths, "git stage");
        }

        @Override
        public Either<GitFailure<ProcessError>, List<String>> unstageNew(List<String> paths) {
            return index("unstageNew", paths, "git unstageNew");
        }

        @Override
        public Either<GitFailure<ProcessError>, List<String>> squash(Sha sha) {
            return StubGitProvider.ran("git squash");
        }

        @Override
        public Either<GitFailure<RewordError>, List<String>> reword(Sha sha, CommitMessage message) {
            commits.add("reword " + message.summary() + "|" + message.description());
            return StubGitProvider.ran("git reword");
        }

        @Override
        public Either<GitFailure<ProcessError>, List<String>> amendLastCommit() {
            return index("amend", List.of(), "git amendLastCommit");
        }

        @Override
        public Either<GitFailure<ProcessError>, List<String>> commit(CommitMessage message) {
            if (commitFailure != null) {
                return StubGitProvider.refused("git commit", commitFailure);
            }
            commits.add(message.summary() + "|" + message.description());
            return StubGitProvider.ran("git commit");
        }

        @Override
        public Either<GitFailure<DiscardError>, List<String>> discard(DiscardPlan plan) {
            discarded.add(plan);
            return StubGitProvider.ran("git discard");
        }

        @Override
        public Either<GitFailure<ProcessError>, List<String>> unstage(List<String> paths) {
            return index("unstage", paths, "git unstage");
        }

        @Override
        public Either<ProcessError, String> commitChanges(Sha sha) {
            return Either.right("changes of " + sha);
        }

        @Override
        public Either<DiffError, Diffs> diff(List<FileEntry> files) {
            return Either.right(new Diffs("", "diff of " + files.size()));
        }

        private Either<GitFailure<ProcessError>, List<String>> index(String operation, List<String> paths,
                                                                   String command) {
            if (indexError != null) {
                return StubGitProvider.refused(command, indexError);
            }
            indexCalls.add(operation + paths.asJava());
            return StubGitProvider.ran(command);
        }
    }

    private final FakeProvider provider = new FakeProvider();
    private final Queue<Runnable> io = new ArrayDeque<>();
    private final Queue<Runnable> ui = new ArrayDeque<>();
    private final Program program = Program.start(provider, io::add, ui::add);

    @Test
    void initialLoadFillsTheModel() {
        settle();

        Model model = program.model();
        assertEquals(new Loaded<>(provider.status), model.status());
        assertEquals(new Loaded<>(List.of(new Branch("main", true, "main-tip"), new Branch("other", false, "other-tip", Instant.EPOCH, true))), model.branches());
        assertEquals(1, provider.commitLoads);
    }

    @Test
    void ticksPickUpWorkingTreeChangesWithoutReloadingCommits() {
        settle();

        provider.status = new RepoStatus("main", "aaaa", List.of(new FileEntry("a.txt", ChangeType.UNTRACKED)));
        program.dispatch(new Msg.Tick());
        settle();

        assertEquals(new Loaded<>(provider.status), program.model().status());
        assertEquals(1, provider.commitLoads);
    }

    @Test
    void ticksReloadCommitsWhenHeadMoves() {
        settle();

        provider.status = new RepoStatus("main", "bbbb", List.of());
        program.dispatch(new Msg.Tick());
        settle();

        assertEquals(2, provider.commitLoads);
        assertEquals("bbbb", ((Loaded<List<Commit>>) program.model().commits()).value().get(0).shortSha());
    }

    @Test
    void providerErrorsBecomeFailedState() {
        provider.statusError = "git status failed";
        settle();

        assertEquals(new Failed<>("git status failed"), program.model().status());

        provider.statusError = null;
        program.dispatch(new Msg.Tick());
        settle();
        assertEquals(new Loaded<>(provider.status), program.model().status());
    }

    @Test
    void checkoutReloadsStatusBranchesAndCommits() {
        settle();

        program.dispatch(new CheckoutMsg.Requested("other"));
        settle();

        Model model = program.model();
        assertEquals(new Loaded<>(provider.status), model.status());
        assertEquals(new Loaded<>(List.of(new Branch("main", false, "main-tip"), new Branch("other", true, "other-tip", Instant.EPOCH, true))), model.branches());
        assertEquals("other-head", ((Loaded<List<Commit>>) model.commits()).value().get(0).sha().value());
        assertEquals(Option.none(), model.error());
    }

    @Test
    void confirmedNewBranchIsCreatedAndShownAsCurrent() {
        settle();

        program.dispatch(new NewBranchMsg.Requested("other"));
        program.dispatch(new NewBranchMsg.Confirmed("topic"));
        settle();

        Model model = program.model();
        assertEquals("topic", ((Loaded<RepoStatus>) model.status()).value().head());
        assertEquals(Option.none(), model.popup());
        assertEquals(Option.none(), model.error());
    }

    @Test
    void failedNewBranchIsReported() {
        settle();
        provider.checkoutError = "fatal: exists";

        program.dispatch(new NewBranchMsg.Requested("other"));
        program.dispatch(new NewBranchMsg.Confirmed("topic"));
        settle();

        assertEquals(Option.of("Creating branch topic failed: fatal: exists"), program.model().error());
    }

    @Test
    void deletingABranchCallsTheProviderWithTheChosenScope() {
        settle();

        program.dispatch(new DeleteBranchMsg.Requested("other"));
        program.dispatch(new DeleteBranchMsg.Chosen(BranchCmd.DeleteBranch.DeleteScope.BOTH));
        settle();

        assertEquals(java.util.List.of("other local remote"), provider.deleted);
        assertEquals(Option.none(), program.model().popup());
        assertEquals(Option.none(), program.model().error());
    }

    @Test
    void toggleStageRunsTheGitCommandsAndReloadsStatus() {
        settle();

        program.dispatch(new StageMsg.Requested(List.of(new FileEntry("a.txt", ChangeType.UNTRACKED))));
        settle();
        program.dispatch(new StageMsg.Requested(List.of(
                new FileEntry("b.txt", ChangeType.ADDED), new FileEntry("c.txt", ChangeType.MODIFIED, true, false))));
        settle();

        assertEquals(java.util.List.of("stage[a.txt]", "unstageNew[b.txt]", "unstage[c.txt]"), provider.indexCalls);
        assertEquals(Option.none(), program.model().error());
    }

    @Test
    void stagingAllAndCommitting() {
        provider.status = new RepoStatus("main", "aaaa", List.of(new FileEntry("a.txt", ChangeType.UNTRACKED)));
        settle();

        program.dispatch(new CommitMsg.Requested());
        program.dispatch(new CommitMsg.StageAllConfirmed());
        settle();

        assertEquals(java.util.List.of("stage[a.txt]"), provider.indexCalls);
        assertEquals(true, program.model().popup(CommitPopup.class).isDefined());

        program.dispatch(new CommitMsg.Confirmed("summary", "more"));
        settle();

        assertEquals(java.util.List.of("summary|more"), provider.commits);
        assertEquals(false, program.model().popup(CommitPopup.class).isDefined());
        assertEquals(Option.none(), program.model().error());
    }

    @Test
    void amendingStagesAllFilesWhenNoneIsStaged() {
        provider.status = new RepoStatus("main", "aaaa", List.of(new FileEntry("a.txt", ChangeType.MODIFIED)));
        settle();

        program.dispatch(new AmendMsg.Requested());
        program.dispatch(new AmendMsg.Confirmed());
        settle();

        assertEquals(java.util.List.of("stage[a.txt]", "amend[]"), provider.indexCalls);
        assertEquals(false, program.model().popup(AmendPopup.class).isDefined());
        assertEquals(Option.none(), program.model().error());
    }

    @Test
    void rewordingTheLastCommit() {
        settle();

        program.dispatch(new RewordMsg.Requested(0));
        program.dispatch(new RewordMsg.Confirmed("new summary", "new body"));
        settle();

        assertEquals(java.util.List.of("reword new summary|new body"), provider.commits);
        assertEquals(Option.none(), program.model().popup());
        assertEquals(Option.none(), program.model().error());
    }

    @Test
    void failedCommitIsReported() {
        provider.status = new RepoStatus("main", "aaaa", List.of(new FileEntry("a.txt", ChangeType.ADDED)));
        provider.commitFailure = "boom";
        settle();

        program.dispatch(new CommitMsg.Requested());
        program.dispatch(new CommitMsg.Confirmed("summary", ""));
        settle();

        assertEquals(Option.of("Commit failed: boom"), program.model().error());
    }

    @Test
    void selectingFilesLoadsTheirDiff() {
        settle();
        List<FileEntry> files = List.of(new FileEntry("a.txt", ChangeType.UNTRACKED));

        program.dispatch(new SelectionMsg.FilesSelected(files));
        settle();

        assertEquals(new Loaded<>(new Diffs("", "diff of 1")), program.model().fileDiff().get().diff());
    }

    @Test
    void failedToggleStageIsReported() {
        settle();

        provider.indexError = "fatal: pathspec 'a.txt' did not match";
        program.dispatch(new StageMsg.Requested(List.of(new FileEntry("a.txt", ChangeType.UNTRACKED))));
        settle();

        assertEquals(Option.of("Staging failed: fatal: pathspec 'a.txt' did not match"), program.model().error());
    }

    @Test
    void failedCheckoutIsReportedUntilTheNextOne() {
        settle();

        provider.checkoutError = "error: Your local changes would be overwritten";
        program.dispatch(new CheckoutMsg.Requested("other"));
        settle();
        assertEquals(Option.of("Checkout of other failed: error: Your local changes would be overwritten"),
                program.model().error());
        assertEquals("main", ((Loaded<RepoStatus>) program.model().status()).value().head());

        provider.checkoutError = null;
        program.dispatch(new CheckoutMsg.Requested("other"));
        settle();
        assertEquals(Option.none(), program.model().error());
        assertEquals("other", ((Loaded<RepoStatus>) program.model().status()).value().head());
    }

    @Test
    void selectingABranchLoadsItsLog() {
        settle();

        program.dispatch(new SelectionMsg.BranchSelected("other"));
        settle();

        assertEquals(Option.of(new BranchLog("other", new Loaded<>(List.of(FakeProvider.commit("other-tip"))))),
                program.model().branchLog());
        assertEquals(java.util.List.of("other"), provider.logLoads);
    }

    /**
     * Runs queued IO and UI work until both queues are empty.
     */
    private void settle() {
        while (!io.isEmpty() || !ui.isEmpty()) {
            drain(io);
            drain(ui);
        }
    }

    private static void drain(Queue<Runnable> queue) {
        Runnable task;
        while ((task = queue.poll()) != null) {
            task.run();
        }
    }
}
