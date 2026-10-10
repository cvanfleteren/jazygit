package net.vanfleteren.jazygit.state;

import net.vanfleteren.jazygit.feature.commit.CommitPopup;
import net.vanfleteren.jazygit.feature.commit.AmendPopup;

import net.vanfleteren.jazygit.feature.branch.BranchCmd;

import net.vanfleteren.jazygit.feature.selection.SelectionMsg;

import net.vanfleteren.jazygit.feature.commit.RewordMsg;

import net.vanfleteren.jazygit.feature.commit.AmendMsg;

import net.vanfleteren.jazygit.feature.commit.CommitMsg;

import net.vanfleteren.jazygit.feature.stage.StageMsg;

import net.vanfleteren.jazygit.feature.branch.DeleteBranchMsg;

import net.vanfleteren.jazygit.feature.branch.NewBranchMsg;

import net.vanfleteren.jazygit.feature.branch.CheckoutMsg;

import net.vanfleteren.jazygit.state.Loadable.Loaded;

import net.vanfleteren.jazygit.state.Loadable.Failed;

import net.vanfleteren.jazygit.git.model.Diffs;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.ChangeType;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.git.GitInfoProvider;
import net.vanfleteren.jazygit.git.model.RepoStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The runtime loop: commands are performed against the provider and their results fed back.
 * Both executors are queues drained by the test, so every step is deterministic.
 */
class ProgramTest {

    private static final class FakeProvider implements GitInfoProvider {

        RepoStatus status = new RepoStatus("main", "aaaa", List.of());
        RuntimeException statusError;
        RuntimeException checkoutError;
        RuntimeException indexError;
        String commitFailure;
        final List<String> commits = new ArrayList<>();
        final List<String> deleted = new ArrayList<>();
        final List<String> pushed = new ArrayList<>();
        final List<String> indexCalls = new ArrayList<>();
        int commitLoads;
        final List<String> logLoads = new ArrayList<>();

        @Override
        public String repositoryName() {
            return "repo";
        }

        @Override
        public RepoStatus status() {
            if (statusError != null) {
                throw statusError;
            }
            return status;
        }

        @Override
        public List<Branch> branches() {
            return List.of(new Branch("main", status.head().equals("main"), "main-tip"),
                    new Branch("other", status.head().equals("other"), "other-tip", Instant.EPOCH, true));
        }

        @Override
        public List<Commit> commits() {
            commitLoads++;
            return List.of(commit(status.headOid()));
        }

        @Override
        public List<Commit> log(String branch) {
            logLoads.add(branch);
            return List.of(commit(branch + "-tip"));
        }

        private static Commit commit(String sha) {
            return new Commit(sha, sha, "Ada", "ada@example.com", Instant.EPOCH, "msg", "");
        }

        @Override
        public void checkout(String branch) {
            if (checkoutError != null) {
                throw checkoutError;
            }
            status = new RepoStatus(branch, branch + "-head", List.of());
        }

        @Override
        public void push(String branch, boolean forceWithLease) {
            pushed.add(branch + (forceWithLease ? " force" : ""));
        }

        @Override
        public void deleteBranch(String name, boolean local, boolean remote) {
            deleted.add(name + (local ? " local" : "") + (remote ? " remote" : ""));
        }

        @Override
        public void createBranch(String name, String startPoint) {
            if (checkoutError != null) {
                throw checkoutError;
            }
            status = new RepoStatus(name, name + "-head", List.of());
        }

        @Override
        public void stage(List<String> paths) {
            index("stage", paths);
        }

        @Override
        public void unstageNew(List<String> paths) {
            index("unstageNew", paths);
        }

        @Override
        public void reword(String summary, String description) {
            commits.add("reword " + summary + "|" + description);
        }

        @Override
        public void amendLastCommit() {
            index("amend", List.of());
        }

        @Override
        public void commit(String summary, String description) {
            if (commitFailure != null) {
                throw new IllegalStateException(commitFailure);
            }
            commits.add(summary + "|" + description);
        }

        @Override
        public void unstage(List<String> paths) {
            index("unstage", paths);
        }

        @Override
        public String commitChanges(String sha) {
            return "changes of " + sha;
        }

        @Override
        public Diffs diff(List<FileEntry> files) {
            return new Diffs("", "diff of " + files.size());
        }

        private void index(String operation, List<String> paths) {
            if (indexError != null) {
                throw indexError;
            }
            indexCalls.add(operation + paths);
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
        provider.statusError = new IllegalStateException("git status failed");
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
        assertEquals("other-head", ((Loaded<List<Commit>>) model.commits()).value().get(0).shortSha());
        assertEquals(Optional.empty(), model.error());
    }

    @Test
    void confirmedNewBranchIsCreatedAndShownAsCurrent() {
        settle();

        program.dispatch(new NewBranchMsg.Requested("other"));
        program.dispatch(new NewBranchMsg.Confirmed("topic"));
        settle();

        Model model = program.model();
        assertEquals("topic", ((Loaded<RepoStatus>) model.status()).value().head());
        assertEquals(Optional.empty(), model.popup());
        assertEquals(Optional.empty(), model.error());
    }

    @Test
    void failedNewBranchIsReported() {
        settle();
        provider.checkoutError = new IllegalStateException("fatal: exists");

        program.dispatch(new NewBranchMsg.Requested("other"));
        program.dispatch(new NewBranchMsg.Confirmed("topic"));
        settle();

        assertEquals(Optional.of("Creating branch topic failed: fatal: exists"), program.model().error());
    }

    @Test
    void deletingABranchCallsTheProviderWithTheChosenScope() {
        settle();

        program.dispatch(new DeleteBranchMsg.Requested("other"));
        program.dispatch(new DeleteBranchMsg.Chosen(BranchCmd.DeleteBranch.DeleteScope.BOTH));
        settle();

        assertEquals(List.of("other local remote"), provider.deleted);
        assertEquals(Optional.empty(), program.model().popup());
        assertEquals(Optional.empty(), program.model().error());
    }

    @Test
    void toggleStageRunsTheGitCommandsAndReloadsStatus() {
        settle();

        program.dispatch(new StageMsg.Requested(List.of(new FileEntry("a.txt", ChangeType.UNTRACKED))));
        settle();
        program.dispatch(new StageMsg.Requested(List.of(
                new FileEntry("b.txt", ChangeType.ADDED), new FileEntry("c.txt", ChangeType.MODIFIED, true, false))));
        settle();

        assertEquals(List.of("stage[a.txt]", "unstageNew[b.txt]", "unstage[c.txt]"), provider.indexCalls);
        assertEquals(Optional.empty(), program.model().error());
    }

    @Test
    void stagingAllAndCommitting() {
        provider.status = new RepoStatus("main", "aaaa", List.of(new FileEntry("a.txt", ChangeType.UNTRACKED)));
        settle();

        program.dispatch(new CommitMsg.Requested());
        program.dispatch(new CommitMsg.StageAllConfirmed());
        settle();

        assertEquals(List.of("stage[a.txt]"), provider.indexCalls);
        assertEquals(true, program.model().popup(CommitPopup.class).isPresent());

        program.dispatch(new CommitMsg.Confirmed("summary", "more"));
        settle();

        assertEquals(List.of("summary|more"), provider.commits);
        assertEquals(false, program.model().popup(CommitPopup.class).isPresent());
        assertEquals(Optional.empty(), program.model().error());
    }

    @Test
    void amendingStagesAllFilesWhenNoneIsStaged() {
        provider.status = new RepoStatus("main", "aaaa", List.of(new FileEntry("a.txt", ChangeType.MODIFIED)));
        settle();

        program.dispatch(new AmendMsg.Requested());
        program.dispatch(new AmendMsg.Confirmed());
        settle();

        assertEquals(List.of("stage[a.txt]", "amend[]"), provider.indexCalls);
        assertEquals(false, program.model().popup(AmendPopup.class).isPresent());
        assertEquals(Optional.empty(), program.model().error());
    }

    @Test
    void rewordingTheLastCommit() {
        settle();

        program.dispatch(new RewordMsg.Requested(0));
        program.dispatch(new RewordMsg.Confirmed("new summary", "new body"));
        settle();

        assertEquals(List.of("reword new summary|new body"), provider.commits);
        assertEquals(Optional.empty(), program.model().popup());
        assertEquals(Optional.empty(), program.model().error());
    }

    @Test
    void failedCommitIsReported() {
        provider.status = new RepoStatus("main", "aaaa", List.of(new FileEntry("a.txt", ChangeType.ADDED)));
        provider.commitFailure = "boom";
        settle();

        program.dispatch(new CommitMsg.Requested());
        program.dispatch(new CommitMsg.Confirmed("summary", ""));
        settle();

        assertEquals(Optional.of("Commit failed: boom"), program.model().error());
    }

    @Test
    void selectingFilesLoadsTheirDiff() {
        settle();
        List<FileEntry> files = List.of(new FileEntry("a.txt", ChangeType.UNTRACKED));

        program.dispatch(new SelectionMsg.FilesSelected(files));
        settle();

        assertEquals(new Loaded<>(new Diffs("", "diff of 1")), program.model().fileDiff().orElseThrow().diff());
    }

    @Test
    void failedToggleStageIsReported() {
        settle();

        provider.indexError = new IllegalStateException("fatal: pathspec 'a.txt' did not match");
        program.dispatch(new StageMsg.Requested(List.of(new FileEntry("a.txt", ChangeType.UNTRACKED))));
        settle();

        assertEquals(Optional.of("Staging failed: fatal: pathspec 'a.txt' did not match"), program.model().error());
    }

    @Test
    void failedCheckoutIsReportedUntilTheNextOne() {
        settle();

        provider.checkoutError = new IllegalStateException("error: Your local changes would be overwritten");
        program.dispatch(new CheckoutMsg.Requested("other"));
        settle();
        assertEquals(Optional.of("Checkout of other failed: error: Your local changes would be overwritten"),
                program.model().error());
        assertEquals("main", ((Loaded<RepoStatus>) program.model().status()).value().head());

        provider.checkoutError = null;
        program.dispatch(new CheckoutMsg.Requested("other"));
        settle();
        assertEquals(Optional.empty(), program.model().error());
        assertEquals("other", ((Loaded<RepoStatus>) program.model().status()).value().head());
    }

    @Test
    void selectingABranchLoadsItsLog() {
        settle();

        program.dispatch(new SelectionMsg.BranchSelected("other"));
        settle();

        assertEquals(Optional.of(new BranchLog("other", new Loaded<>(List.of(FakeProvider.commit("other-tip"))))),
                program.model().branchLog());
        assertEquals(List.of("other"), provider.logLoads);
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
