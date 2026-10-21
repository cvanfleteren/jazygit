package net.vanfleteren.jazygit.state;

import static net.vanfleteren.jazygit.state.TestModels.errorText;
import static net.vanfleteren.jazygit.state.TestModels.refused;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import net.vanfleteren.jazygit.feature.branch.checkout.CheckoutMsg;
import net.vanfleteren.jazygit.feature.branch.delete.DeleteBranchMsg;
import net.vanfleteren.jazygit.feature.branch.newbranch.NewBranchMsg;
import net.vanfleteren.jazygit.feature.commit.MessageEdited;
import net.vanfleteren.jazygit.feature.commit.amend.AmendMsg;
import net.vanfleteren.jazygit.feature.commit.amend.AmendPopup;
import net.vanfleteren.jazygit.feature.commit.create.CommitMsg;
import net.vanfleteren.jazygit.feature.commit.create.CommitPopup;
import net.vanfleteren.jazygit.feature.commit.reword.RewordMsg;
import net.vanfleteren.jazygit.feature.fetch.FetchMsg;
import net.vanfleteren.jazygit.feature.stage.StageMsg;
import net.vanfleteren.jazygit.gh.StubGitHub;
import net.vanfleteren.jazygit.git.GitError;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.GitFailure;
import net.vanfleteren.jazygit.git.StubGitProvider;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.ChangeType;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.git.model.CommitMessage;
import net.vanfleteren.jazygit.git.model.DeleteScope;
import net.vanfleteren.jazygit.git.model.DiffOptions;
import net.vanfleteren.jazygit.git.model.DiffText;
import net.vanfleteren.jazygit.git.model.Diffs;
import net.vanfleteren.jazygit.git.model.DiscardPlan;
import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.git.model.PushMode;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.git.model.RepoStatus;
import net.vanfleteren.jazygit.git.model.RepoStatus.Head;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.model.SquashMode;
import net.vanfleteren.jazygit.git.model.Stash;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.settings.SettingsStore;
import net.vanfleteren.jazygit.state.Loadable.Failed;
import net.vanfleteren.jazygit.state.Loadable.Loaded;
import org.junit.jupiter.api.Test;

/**
 * The runtime loop: commands are performed against the provider and their results fed back.
 * Both executors are queues drained by the test, so every step is deterministic.
 */
class ProgramTest {

    private static final class FakeProvider implements StubGitProvider {

        RepoStatus status = new RepoStatus(Head.onBranch(new Branch.Name("main"), new Sha("aaaa")), List.of());
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
        int fetches;
        final java.util.List<String> logLoads = new ArrayList<>();

        @Override
        public Either<ProcessError, RepoStatus> status() {
            return statusError != null ? Either.left(new GitError.CommandFailed(statusError, 1)) : Either.right(status);
        }

        @Override
        public Either<GitFailure<GitError>, List<ExecutedCommand>> drop(Stash stash) {
            return StubGitProvider.ran("git stash drop " + stash.ref());
        }

        @Override
        public Either<GitFailure<GitError>, List<ExecutedCommand>> pop(Stash stash) {
            return StubGitProvider.ran("git stash pop " + stash.ref());
        }

        @Override
        public Either<GitFailure<ProcessError>, List<ExecutedCommand>> stash(Option<String> message) {
            return StubGitProvider.ran("git stash");
        }

        @Override
        public Either<ProcessError, List<Branch>> branches() {
            return Either.right(List.of(
                    new Branch(
                            new Branch.Name("main"),
                            status.head().branchName().contains(new Branch.Name("main")),
                            new Sha("main-tip")),
                    new Branch(
                            new Branch.Name("other"),
                            status.head().branchName().contains(new Branch.Name("other")),
                            new Sha("other-tip"),
                            Instant.EPOCH,
                            true)));
        }

        @Override
        public Either<ProcessError, List<Commit>> commits() {
            commitLoads++;
            return Either.right(List.of(commit(status.headCommit().get().value())));
        }

        @Override
        public Either<GitError, List<Commit>> log(BranchRef ref) {
            String branch = ref.name();
            logLoads.add(branch);
            return Either.right(List.of(commit(branch + "-tip")));
        }

        @Override
        public Either<GitFailure<GitError>, List<ExecutedCommand>> checkout(Branch.Name branch) {
            return GitFailure.widen(switchTo(branch, "git checkout"));
        }

        @Override
        public Either<GitFailure<GitError>, List<ExecutedCommand>> checkoutWithStash(Branch.Name branch) {
            return GitFailure.widen(switchTo(branch, "git checkoutWithStash"));
        }

        @Override
        public Either<GitFailure<ProcessError>, List<ExecutedCommand>> checkoutNewBranch(
                Branch.Name name, String startPoint, Option<Branch.Name> parent) {
            return switchTo(name, "git checkoutNewBranch");
        }

        private Either<GitFailure<ProcessError>, List<ExecutedCommand>> switchTo(Branch.Name branch, String command) {
            if (checkoutError != null) {
                return StubGitProvider.refused(command, checkoutError);
            }
            status = new RepoStatus(Head.onBranch(branch, new Sha(branch.value() + "-head")), List.of());
            return StubGitProvider.ran(command);
        }

        @Override
        public Either<GitFailure<ProcessError>, List<ExecutedCommand>> push(
                Branch.Name branch, RemoteBranch upstream, PushMode mode) {
            pushed.add(branch.value() + (mode == PushMode.FORCE_WITH_LEASE ? " force" : ""));
            return StubGitProvider.ran("git push");
        }

        @Override
        public Either<ProcessError, List<String>> remotes() {
            return Either.right(List.of("origin", "upstream"));
        }

        @Override
        public Either<GitFailure<ProcessError>, List<ExecutedCommand>> fetch(String remote) {
            fetches++;
            return StubGitProvider.ran("git fetch --quiet --prune " + remote);
        }

        @Override
        public Either<GitFailure<GitError>, List<ExecutedCommand>> rebase(Branch.Name onto) {
            return StubGitProvider.ran("git rebase");
        }

        @Override
        public Either<GitFailure<ProcessError>, List<ExecutedCommand>> deleteBranch(
                Branch.Name name, RemoteBranch remote, DeleteScope scope) {
            deleted.add(name.value() + (scope.local() ? " local" : "") + (scope.remote() ? " remote" : ""));
            return StubGitProvider.ran("git deleteBranch");
        }

        @Override
        public Either<GitFailure<ProcessError>, List<ExecutedCommand>> stage(List<String> paths) {
            return index("stage", paths, "git stage");
        }

        @Override
        public Either<GitFailure<ProcessError>, List<ExecutedCommand>> unstageNew(List<String> paths) {
            return index("unstageNew", paths, "git unstageNew");
        }

        @Override
        public Either<GitFailure<GitError>, List<ExecutedCommand>> squash(Sha sha, SquashMode mode) {
            return StubGitProvider.ran("git squash");
        }

        @Override
        public Either<GitFailure<GitError>, List<ExecutedCommand>> reword(Sha sha, CommitMessage message) {
            commits.add("reword " + message.summary() + "|" + message.description());
            return StubGitProvider.ran("git reword");
        }

        @Override
        public Either<GitFailure<ProcessError>, List<ExecutedCommand>> amendLastCommit() {
            return index("amend", List.of(), "git amendLastCommit");
        }

        @Override
        public Either<GitFailure<ProcessError>, List<ExecutedCommand>> commit(CommitMessage message) {
            if (commitFailure != null) {
                return StubGitProvider.refused("git commit", commitFailure);
            }
            commits.add(message.summary() + "|" + message.description());
            return StubGitProvider.ran("git commit");
        }

        private static Commit commit(String sha) {
            return new Commit(new Sha(sha), "Ada", "ada@example.com", Instant.EPOCH, "msg", "");
        }

        @Override
        public Either<GitFailure<GitError>, List<ExecutedCommand>> discard(DiscardPlan plan) {
            discarded.add(plan);
            return StubGitProvider.ran("git discard");
        }

        @Override
        public Either<GitFailure<ProcessError>, List<ExecutedCommand>> unstage(List<String> paths) {
            return index("unstage", paths, "git unstage");
        }

        @Override
        public Either<ProcessError, DiffText> commitChanges(Sha sha, DiffOptions options) {
            return Either.right(DiffText.of("changes of " + sha.value()));
        }

        @Override
        public Either<GitError, Diffs> diff(List<FileEntry> files, DiffOptions options) {
            return Either.right(new Diffs("", "diff of " + files.size()));
        }

        private Either<GitFailure<ProcessError>, List<ExecutedCommand>> index(
                String operation, List<String> paths, String command) {
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
    private final Queue<Runnable> background = new ArrayDeque<>();
    private final Program program =
            Program.start(new Env(provider, StubGitHub.empty()), io::add, background::add, ui::add);

    @Test
    void initialLoadFillsTheModel() {
        settle();

        Model model = program.model();
        assertEquals(new Loaded<>(provider.status), model.repo().status());
        assertEquals(
                new Loaded<>(List.of(
                        new Branch(new Branch.Name("main"), true, new Sha("main-tip")),
                        new Branch(new Branch.Name("other"), false, new Sha("other-tip"), Instant.EPOCH, true))),
                model.repo().branches());
        assertEquals(1, provider.commitLoads);
    }

    @Test
    void aMessageThatChangesTheModelSaysSoAndOneThatDoesNotSaysNothing() {
        AtomicInteger changes = new AtomicInteger();
        Program told = Program.start(
                new Env(provider, StubGitHub.empty()),
                SettingsStore.Loaded.DEFAULT,
                Option.none(),
                Landing.START,
                Runnable::run,
                Runnable::run,
                Runnable::run,
                bug -> {},
                changes::incrementAndGet);
        told.dispatch(new Msg.Focused("commits", false));
        int focused = changes.get();

        told.dispatch(new Msg.Focused("commits", false));
        assertEquals(focused, changes.get());

        told.dispatch(new Msg.Focused("files", false));
        assertEquals(focused + 1, changes.get());
    }

    @Test
    void loadsThatBringNothingNewDoNotSayTheModelChanged() {
        AtomicInteger changes = new AtomicInteger();
        Queue<Runnable> queued = new ArrayDeque<>();
        Program told = Program.start(
                new Env(provider, StubGitHub.empty()),
                SettingsStore.Loaded.DEFAULT,
                Option.none(),
                Landing.START,
                queued::add,
                queued::add,
                queued::add,
                bug -> {},
                changes::incrementAndGet);
        told.dispatch(new Msg.Tick(Instant.EPOCH));
        drain(queued);
        int loaded = changes.get();

        // Starting the periodic loads and their finishing only changes which loads run.
        told.dispatch(new Msg.Tick(Instant.EPOCH));
        drain(queued);

        assertEquals(loaded, changes.get());
    }

    @Test
    void ticksPickUpWorkingTreeChangesWithoutReloadingCommits() {
        settle();

        provider.status = new RepoStatus(
                Head.onBranch(new Branch.Name("main"), new Sha("aaaa")),
                List.of(new FileEntry("a.txt", ChangeType.UNTRACKED)));
        program.dispatch(new Msg.Tick(Instant.EPOCH));
        settle();

        assertEquals(new Loaded<>(provider.status), program.model().repo().status());
        assertEquals(1, provider.commitLoads);
    }

    @Test
    void ticksReloadCommitsWhenHeadMoves() {
        settle();

        provider.status = new RepoStatus(Head.onBranch(new Branch.Name("main"), new Sha("bbbb")), List.of());
        program.dispatch(new Msg.Tick(Instant.EPOCH));
        settle();

        assertEquals(2, provider.commitLoads);
        assertEquals(
                "bbbb",
                ((Loaded<List<Commit>>) program.model().repo().commits())
                        .value()
                        .get(0)
                        .shortSha());
    }

    @Test
    void providerErrorsBecomeFailedState() {
        provider.statusError = "git status failed";
        settle();

        assertEquals(
                new Failed<>(refused("git status failed")),
                program.model().repo().status());

        provider.statusError = null;
        program.dispatch(new Msg.Tick(Instant.EPOCH));
        settle();
        assertEquals(new Loaded<>(provider.status), program.model().repo().status());
    }

    @Test
    void checkoutReloadsStatusBranchesAndCommits() {
        settle();

        program.dispatch(new CheckoutMsg.Requested(new Branch.Name("other")));
        settle();

        Model model = program.model();
        assertEquals(new Loaded<>(provider.status), model.repo().status());
        assertEquals(
                new Loaded<>(List.of(
                        new Branch(new Branch.Name("main"), false, new Sha("main-tip")),
                        new Branch(new Branch.Name("other"), true, new Sha("other-tip"), Instant.EPOCH, true))),
                model.repo().branches());
        assertEquals(
                "other-head",
                ((Loaded<List<Commit>>) model.repo().commits())
                        .value()
                        .get(0)
                        .sha()
                        .value());
        assertEquals(Option.none(), model.outcome().error());
    }

    @Test
    void confirmedNewBranchIsCreatedAndShownAsCurrent() {
        settle();

        program.dispatch(new NewBranchMsg.Requested(new Branch.Name("other")));
        program.dispatch(new Msg.TextEdited("topic"));
        program.dispatch(new NewBranchMsg.Confirmed());
        settle();

        Model model = program.model();
        assertEquals(
                new Branch.Name("topic"),
                ((Loaded<RepoStatus>) model.repo().status())
                        .value()
                        .head()
                        .branchName()
                        .get());
        assertEquals(Option.none(), model.popup());
        assertEquals(Option.none(), model.outcome().error());
    }

    @Test
    void failedNewBranchIsReported() {
        settle();
        provider.checkoutError = "fatal: exists";

        program.dispatch(new NewBranchMsg.Requested(new Branch.Name("other")));
        program.dispatch(new Msg.TextEdited("topic"));
        program.dispatch(new NewBranchMsg.Confirmed());
        settle();

        assertEquals(Option.of("Creating branch topic failed: fatal: exists"), errorText(program.model()));
    }

    @Test
    void deletingABranchCallsTheProviderWithTheChosenScope() {
        settle();

        program.dispatch(new DeleteBranchMsg.Requested(new Branch.Name("other")));
        program.dispatch(new DeleteBranchMsg.Chosen(DeleteScope.BOTH));
        settle();

        assertEquals(java.util.List.of("other local remote"), provider.deleted);
        assertEquals(Option.none(), program.model().popup());
        assertEquals(Option.none(), program.model().outcome().error());
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
        assertEquals(Option.none(), program.model().outcome().error());
    }

    @Test
    void stagingAllAndCommitting() {
        provider.status = new RepoStatus(
                Head.onBranch(new Branch.Name("main"), new Sha("aaaa")),
                List.of(new FileEntry("a.txt", ChangeType.UNTRACKED)));
        settle();

        program.dispatch(new CommitMsg.Requested());
        program.dispatch(new CommitMsg.StageAllConfirmed());
        settle();

        assertEquals(java.util.List.of("stage[a.txt]"), provider.indexCalls);
        assertEquals(true, program.model().popup(CommitPopup.class).isDefined());

        program.dispatch(new MessageEdited(new CommitMessage("summary", "more")));
        program.dispatch(new CommitMsg.Confirmed());
        settle();

        assertEquals(java.util.List.of("summary|more"), provider.commits);
        assertEquals(false, program.model().popup(CommitPopup.class).isDefined());
        assertEquals(Option.none(), program.model().outcome().error());
    }

    @Test
    void amendingStagesAllFilesWhenNoneIsStagedOnceAgreed() {
        provider.status = new RepoStatus(
                Head.onBranch(new Branch.Name("main"), new Sha("aaaa")),
                List.of(new FileEntry("a.txt", ChangeType.MODIFIED)));
        settle();

        program.dispatch(new AmendMsg.Requested());
        program.dispatch(new AmendMsg.Confirmed());
        program.dispatch(new AmendMsg.StageAllConfirmed());
        settle();

        assertEquals(java.util.List.of("stage[a.txt]", "amend[]"), provider.indexCalls);
        assertEquals(false, program.model().popup(AmendPopup.class).isDefined());
        assertEquals(Option.none(), program.model().outcome().error());
    }

    @Test
    void rewordingTheLastCommit() {
        settle();

        program.dispatch(new RewordMsg.Requested(
                program.model().repo().commits().toOption().get().head().sha()));
        program.dispatch(new MessageEdited(new CommitMessage("new summary", "new body")));
        program.dispatch(new RewordMsg.Confirmed());
        settle();

        assertEquals(java.util.List.of("reword new summary|new body"), provider.commits);
        assertEquals(Option.none(), program.model().popup());
        assertEquals(Option.none(), program.model().outcome().error());
    }

    @Test
    void failedCommitIsReported() {
        provider.status = new RepoStatus(
                Head.onBranch(new Branch.Name("main"), new Sha("aaaa")),
                List.of(new FileEntry("a.txt", ChangeType.ADDED)));
        provider.commitFailure = "boom";
        settle();

        program.dispatch(new CommitMsg.Requested());
        program.dispatch(new MessageEdited(new CommitMessage("summary", "")));
        program.dispatch(new CommitMsg.Confirmed());
        settle();

        assertEquals(Option.of("Commit failed: boom"), errorText(program.model()));
    }

    @Test
    void selectingFilesLoadsTheirDiff() {
        settle();
        List<FileEntry> files = List.of(new FileEntry("a.txt", ChangeType.UNTRACKED));

        program.dispatch(new SelectionMsg.FilesSelected(files));
        settle();

        assertEquals(
                new Loaded<>(new Diffs("", "diff of 1")),
                program.model().repoView().fileDiff().get().value());
    }

    @Test
    void failedToggleStageIsReported() {
        settle();

        provider.indexError = "fatal: pathspec 'a.txt' did not match";
        program.dispatch(new StageMsg.Requested(List.of(new FileEntry("a.txt", ChangeType.UNTRACKED))));
        settle();

        assertEquals(Option.of("Staging failed: fatal: pathspec 'a.txt' did not match"), errorText(program.model()));
    }

    @Test
    void failedCheckoutIsReportedUntilTheNextOne() {
        settle();

        provider.checkoutError = "error: Your local changes would be overwritten";
        program.dispatch(new CheckoutMsg.Requested(new Branch.Name("other")));
        settle();
        assertEquals(
                Option.of("Checkout of other failed: error: Your local changes would be overwritten"),
                errorText(program.model()));
        assertEquals(
                new Branch.Name("main"),
                ((Loaded<RepoStatus>) program.model().repo().status())
                        .value()
                        .head()
                        .branchName()
                        .get());

        provider.checkoutError = null;
        program.dispatch(new CheckoutMsg.Requested(new Branch.Name("other")));
        settle();
        assertEquals(Option.none(), program.model().outcome().error());
        assertEquals(
                new Branch.Name("other"),
                ((Loaded<RepoStatus>) program.model().repo().status())
                        .value()
                        .head()
                        .branchName()
                        .get());
    }

    @Test
    void selectingABranchLoadsItsLog() {
        settle();

        program.dispatch(new SelectionMsg.BranchSelected(new BranchRef.Local(new Branch.Name("other"))));
        settle();

        assertEquals(
                Option.of(new Preview<>(
                        new BranchRef.Local(new Branch.Name("other")),
                        new Loaded<>(List.of(FakeProvider.commit("other-tip"))))),
                program.model().repoView().branchLog());
        assertEquals(java.util.List.of("other"), provider.logLoads);
    }

    /**
     * Runs queued IO and UI work until both queues are empty.
     */
    @Test
    void aFetchRunsInTheBackgroundAndThenReloadsTheBranches() {
        settle();

        program.dispatch(new FetchMsg.Tick());
        program.dispatch(new FetchMsg.Tick());

        // Only one fetch, queued next to the other git commands instead of after them.
        assertEquals(1, background.size());
        assertEquals(true, io.isEmpty());
        settle();
        assertEquals(2, provider.fetches);
        assertEquals(Option.none(), program.model().jobs().fetching());
        // Not logged: it runs every few seconds.
        assertEquals(List.of(), program.model().outcome().commandLog());
    }

    /**
     * Asks for {@code cmds}, changing nothing else.
     */
    private record Asked(List<Cmd> cmds) implements Msg {

        @Override
        public Update.Next apply(Model model) {
            return new Update.Next(model, cmds);
        }
    }

    /**
     * Notes in {@code heard} that the command {@code name} reported.
     */
    private record Heard(String name, java.util.List<String> heard) implements Msg {

        @Override
        public Update.Next apply(Model model) {
            heard.add(name);
            return Update.Next.of(model);
        }
    }

    /**
     * Notes in {@code ran} that it ran, and reports that it did to {@code heard}; it takes the preview's slot.
     */
    private record Latest(String name, java.util.List<String> ran, java.util.List<String> heard) implements Cmd {

        @Override
        public boolean background() {
            return true;
        }

        @Override
        public Option<Slot> slot() {
            return Option.of(Slot.PULL_REQUEST_PREVIEW);
        }

        @Override
        public Msg run(Env env) {
            ran.add(name);
            return new Heard(name, heard);
        }

        @Override
        public Msg failed(Failure failure) {
            return new Heard(name, heard);
        }
    }

    @Test
    void aCommandOfASlotThatHasNotStartedIsSkippedWhenANewerOneComes() {
        settle();
        java.util.List<String> ran = new ArrayList<>();
        java.util.List<String> heard = new ArrayList<>();

        program.dispatch(new Asked(List.of(new Latest("first", ran, heard))));
        program.dispatch(new Asked(List.of(new Latest("second", ran, heard))));
        program.dispatch(new Asked(List.of(new Latest("third", ran, heard))));
        // The third one runs before a newer one comes, so it is not skipped.
        drain(background);
        program.dispatch(new Asked(List.of(new Latest("fourth", ran, heard))));
        settle();

        assertEquals(java.util.List.of("third", "fourth"), ran);
        assertEquals(java.util.List.of("third", "fourth"), heard);
    }

    private void settle() {
        while (!io.isEmpty() || !background.isEmpty() || !ui.isEmpty()) {
            drain(io);
            drain(background);
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
