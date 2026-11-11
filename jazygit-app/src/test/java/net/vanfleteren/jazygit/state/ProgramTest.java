package net.vanfleteren.jazygit.state;

import static net.vanfleteren.jazygit.state.TestModels.errorText;
import static net.vanfleteren.jazygit.state.TestModels.program;
import static net.vanfleteren.jazygit.state.TestModels.refused;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Queue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import net.vanfleteren.jazygit.feature.branch.checkout.CheckoutMsg;
import net.vanfleteren.jazygit.feature.branch.delete.DeleteBranchMsg;
import net.vanfleteren.jazygit.feature.branch.newbranch.NewBranchMsg;
import net.vanfleteren.jazygit.feature.commit.MessageEdited;
import net.vanfleteren.jazygit.feature.commit.amend.AmendMsg;
import net.vanfleteren.jazygit.feature.commit.amend.AmendPopup;
import net.vanfleteren.jazygit.feature.commit.create.CommitMsg;
import net.vanfleteren.jazygit.feature.commit.create.CommitPopup;
import net.vanfleteren.jazygit.feature.commit.reword.RewordMsg;
import net.vanfleteren.jazygit.feature.commit.reword.RewordPopup;
import net.vanfleteren.jazygit.feature.fetch.FetchCmd;
import net.vanfleteren.jazygit.feature.fetch.FetchMsg;
import net.vanfleteren.jazygit.feature.stage.StageMsg;
import net.vanfleteren.jazygit.gh.StubGitHub;
import net.vanfleteren.jazygit.git.Branches;
import net.vanfleteren.jazygit.git.Checkout;
import net.vanfleteren.jazygit.git.Commits;
import net.vanfleteren.jazygit.git.Git;
import net.vanfleteren.jazygit.git.GitError;
import net.vanfleteren.jazygit.git.GitFailure;
import net.vanfleteren.jazygit.git.History;
import net.vanfleteren.jazygit.git.Index;
import net.vanfleteren.jazygit.git.Integrate;
import net.vanfleteren.jazygit.git.Remotes;
import net.vanfleteren.jazygit.git.Stashes;
import net.vanfleteren.jazygit.git.Status;
import net.vanfleteren.jazygit.git.StubBranches;
import net.vanfleteren.jazygit.git.StubCheckout;
import net.vanfleteren.jazygit.git.StubCommits;
import net.vanfleteren.jazygit.git.StubDiffs;
import net.vanfleteren.jazygit.git.StubGit;
import net.vanfleteren.jazygit.git.StubHistory;
import net.vanfleteren.jazygit.git.StubIndex;
import net.vanfleteren.jazygit.git.StubIntegrate;
import net.vanfleteren.jazygit.git.StubRemotes;
import net.vanfleteren.jazygit.git.StubStashes;
import net.vanfleteren.jazygit.git.StubStatus;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.BranchName;
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
import net.vanfleteren.jazygit.git.model.Fixtures;
import net.vanfleteren.jazygit.git.model.Head;
import net.vanfleteren.jazygit.git.model.PushMode;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.git.model.RemoteName;
import net.vanfleteren.jazygit.git.model.RepoStatus;
import net.vanfleteren.jazygit.git.model.Revision;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.model.SquashMode;
import net.vanfleteren.jazygit.git.model.Stash;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.settings.SettingsStore;
import net.vanfleteren.jazygit.state.Loadable.Failed;
import net.vanfleteren.jazygit.state.Loadable.Loaded;
import net.vanfleteren.jazygit.state.github.LoadBranchChecks;
import net.vanfleteren.jazygit.state.github.PullRequestsMsg;
import net.vanfleteren.jazygit.state.github.prs.LoadRepositoryPullRequests;
import net.vanfleteren.jazygit.state.load.Load;
import net.vanfleteren.jazygit.state.load.LoadBranchLog;
import net.vanfleteren.jazygit.state.load.SelectionMsg;
import net.vanfleteren.jazygit.state.popup.PopupMsg;
import net.vanfleteren.jazygit.state.preferences.RepositoryTheme;
import net.vanfleteren.jazygit.state.view.Preview;
import net.vanfleteren.jazygit.state.view.ViewMsg;
import org.junit.jupiter.api.Test;

/**
 * The runtime loop: commands are performed against a fake repository and their results fed back.
 * Both executors are queues drained by the test, so every step is deterministic.
 */
class ProgramTest {

    private static final class FakeRepo {

        RepoStatus status = new RepoStatus(Head.onBranch(new BranchName("main"), new Sha("aaaa")), List.of());
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

        public Status status() {
            return new StubStatus() {
                @Override
                public Either<GitError, RepoStatus> read() {
                    return statusError != null
                            ? Either.left(new GitError.CommandFailed(statusError, 1))
                            : Either.right(status);
                }
            };
        }

        public Stashes stashes() {
            return new StubStashes() {
                @Override
                public Either<GitFailure, List<ExecutedCommand>> drop(Stash stash) {
                    return StubGit.ran("git stash drop " + stash.ref());
                }

                @Override
                public Either<GitFailure, List<ExecutedCommand>> pop(Stash stash) {
                    return StubGit.ran("git stash pop --index " + stash.ref());
                }

                @Override
                public Either<GitFailure, List<ExecutedCommand>> push(Option<String> message) {
                    return StubGit.ran("git stash");
                }
            };
        }

        public Branches branches() {
            return new StubBranches() {
                @Override
                public Either<GitError, List<Branch>> local() {
                    return Either.right(List.of(
                            Fixtures.branch(
                                    new BranchName("main"),
                                    status.head().branchName().contains(new BranchName("main")),
                                    new Sha("main-tip")),
                            Fixtures.branch(
                                    new BranchName("other"),
                                    status.head().branchName().contains(new BranchName("other")),
                                    new Sha("other-tip"),
                                    Instant.EPOCH,
                                    true)));
                }
            };
        }

        public History history() {
            return new StubHistory() {
                @Override
                public Either<GitError, List<Commit>> commits(Sha head) {
                    commitLoads++;
                    return Either.right(List.of(commit(status.headCommit().get().value())));
                }

                @Override
                public Either<GitError, List<Commit>> log(BranchRef ref) {
                    String branch = ref.name();
                    logLoads.add(branch);
                    return Either.right(List.of(commit(branch + "-tip")));
                }
            };
        }

        public Checkout checkout() {
            return new StubCheckout() {
                @Override
                public Either<GitFailure, List<ExecutedCommand>> branch(BranchName branch) {
                    return switchTo(branch, "git checkout");
                }

                @Override
                public Either<GitFailure, List<ExecutedCommand>> branchWithStash(BranchName branch) {
                    return switchTo(branch, "git branchWithStash");
                }

                @Override
                public Either<GitFailure, List<ExecutedCommand>> newBranch(
                        BranchName name, Revision startPoint, Option<BranchName> parent) {
                    return switchTo(name, "git newBranch");
                }
            };
        }

        private Either<GitFailure, List<ExecutedCommand>> switchTo(BranchName branch, String command) {
            if (checkoutError != null) {
                return StubGit.refused(command, checkoutError);
            }
            status = new RepoStatus(Head.onBranch(branch, new Sha(branch.value() + "-head")), List.of());
            return StubGit.ran(command);
        }

        public Index index() {
            return new StubIndex() {
                @Override
                public Either<GitFailure, List<ExecutedCommand>> stage(List<String> paths) {
                    return onIndex("stage", paths, "git stage");
                }

                @Override
                public Either<GitFailure, List<ExecutedCommand>> unstageNew(List<String> paths) {
                    return onIndex("unstageNew", paths, "git unstageNew");
                }

                @Override
                public Either<GitFailure, List<ExecutedCommand>> discard(DiscardPlan plan) {
                    discarded.add(plan);
                    return StubGit.ran("git discard");
                }

                @Override
                public Either<GitFailure, List<ExecutedCommand>> unstage(List<String> paths) {
                    return onIndex("unstage", paths, "git unstage");
                }
            };
        }

        private static Commit commit(String sha) {
            return Fixtures.commit(new Sha(sha), "Ada", "ada@example.com", Instant.EPOCH, "msg", "");
        }

        public net.vanfleteren.jazygit.git.Diffs diffs() {
            return new StubDiffs() {
                @Override
                public Either<GitError, DiffText> commitChanges(Sha sha, DiffOptions options) {
                    return Either.right(DiffText.of("changes of " + sha.value()));
                }

                @Override
                public Either<GitError, Diffs> uncommitted(List<FileEntry> files, DiffOptions options) {
                    return Either.right(new Diffs("", "diff of " + files.size()));
                }
            };
        }

        private Either<GitFailure, List<ExecutedCommand>> onIndex(
                String operation, List<String> paths, String command) {
            if (indexError != null) {
                return StubGit.refused(command, indexError);
            }
            indexCalls.add(operation + paths.asJava());
            return StubGit.ran(command);
        }

        public Commits commits() {
            return new StubCommits() {
                @Override
                public Either<GitFailure, List<ExecutedCommand>> squash(Sha sha, SquashMode mode) {
                    return StubGit.ran("git squash");
                }

                @Override
                public Either<GitFailure, List<ExecutedCommand>> reword(Sha sha, CommitMessage message) {
                    commits.add("reword " + message.summary() + "|" + message.description());
                    return StubGit.ran("git reword");
                }

                @Override
                public Either<GitFailure, List<ExecutedCommand>> amendLast() {
                    return onIndex("amend", List.of(), "git amendLastCommit");
                }

                @Override
                public Either<GitFailure, List<ExecutedCommand>> commit(CommitMessage message) {
                    if (commitFailure != null) {
                        return StubGit.refused("git commit", commitFailure);
                    }
                    commits.add(message.summary() + "|" + message.description());
                    return StubGit.ran("git commit");
                }
            };
        }

        public Integrate integrate() {
            return new StubIntegrate() {
                @Override
                public Either<GitFailure, List<ExecutedCommand>> rebase(BranchName onto) {
                    return StubGit.ran("git rebase");
                }
            };
        }

        public Remotes remotes() {
            return new StubRemotes() {
                @Override
                public Either<GitFailure, List<ExecutedCommand>> push(
                        BranchName branch, RemoteBranch upstream, PushMode mode) {
                    pushed.add(branch.value() + (mode == PushMode.FORCE_WITH_LEASE ? " force" : ""));
                    return StubGit.ran("git push");
                }

                @Override
                public Either<GitError, List<RemoteName>> list() {
                    return Either.right(List.of(new RemoteName("origin"), new RemoteName("upstream")));
                }

                @Override
                public Either<GitFailure, Boolean> fetch(RemoteName remote) {
                    fetches++;
                    return Either.right(true);
                }

                @Override
                public Either<GitFailure, List<ExecutedCommand>> deleteBranch(
                        BranchName name, RemoteBranch remote, DeleteScope scope) {
                    deleted.add(name.value() + (scope.local() ? " local" : "") + (scope.remote() ? " remote" : ""));
                    return StubGit.ran("git deleteBranch");
                }
            };
        }

        Git git() {
            return StubGit.of()
                    .withStatus(status())
                    .withStashes(stashes())
                    .withBranches(branches())
                    .withHistory(history())
                    .withCheckout(checkout())
                    .withIndex(index())
                    .withDiffs(diffs())
                    .withCommits(commits())
                    .withIntegrate(integrate())
                    .withRemotes(remotes());
        }
    }

    private final FakeRepo repo = new FakeRepo();
    private final Queue<Runnable> io = new ArrayDeque<>();
    private final Queue<Runnable> ui = new ArrayDeque<>();
    private final Queue<Runnable> background = new ArrayDeque<>();
    private final Program program = program(new Env(repo.git(), StubGitHub.empty()), io::add, background::add, ui::add);

    @Test
    void initialLoadFillsTheModel() {
        settle();

        Model model = program.model();
        assertEquals(new Loaded<>(repo.status), model.repo().status());
        assertEquals(
                new Loaded<>(List.of(
                        Fixtures.branch(new BranchName("main"), true, new Sha("main-tip")),
                        Fixtures.branch(new BranchName("other"), false, new Sha("other-tip"), Instant.EPOCH, true))),
                model.repo().branches());
        assertEquals(1, repo.commitLoads);
    }

    @Test
    void aMessageThatChangesTheModelSaysSoAndOneThatDoesNotSaysNothing() {
        AtomicInteger changes = new AtomicInteger();
        Program told = program(
                new Env(repo.git(), StubGitHub.empty()),
                Runnable::run,
                Runnable::run,
                Runnable::run,
                changes::incrementAndGet);
        told.dispatch(new ViewMsg.Focused("commits", false));
        int focused = changes.get();

        told.dispatch(new ViewMsg.Focused("commits", false));
        assertEquals(focused, changes.get());

        told.dispatch(new ViewMsg.Focused("files", false));
        assertEquals(focused + 1, changes.get());
    }

    @Test
    void loadsThatBringNothingNewDoNotSayTheModelChanged() {
        AtomicInteger changes = new AtomicInteger();
        Queue<Runnable> queued = new ArrayDeque<>();
        Program told = program(
                new Env(repo.git(), StubGitHub.empty()),
                queued::add,
                queued::add,
                queued::add,
                changes::incrementAndGet);
        told.dispatch(new TimerMsg.Tick());
        drain(queued);
        int loaded = changes.get();

        // Starting the periodic loads and their finishing only changes which loads run.
        told.dispatch(new TimerMsg.Tick());
        drain(queued);

        assertEquals(loaded, changes.get());
    }

    @Test
    void ticksPickUpWorkingTreeChangesWithoutReloadingCommits() {
        settle();

        repo.status = new RepoStatus(
                Head.onBranch(new BranchName("main"), new Sha("aaaa")),
                List.of(Fixtures.fileEntry("a.txt", ChangeType.UNTRACKED)));
        program.dispatch(new TimerMsg.Tick());
        settle();

        assertEquals(new Loaded<>(repo.status), program.model().repo().status());
        assertEquals(1, repo.commitLoads);
    }

    @Test
    void ticksReloadCommitsWhenHeadMoves() {
        settle();

        repo.status = new RepoStatus(Head.onBranch(new BranchName("main"), new Sha("bbbb")), List.of());
        program.dispatch(new TimerMsg.Tick());
        settle();

        assertEquals(2, repo.commitLoads);
        assertEquals(
                "bbbb",
                ((Loaded<List<Commit>>) program.model().repo().commits())
                        .value()
                        .get(0)
                        .shortSha());
    }

    @Test
    void gitErrorsBecomeFailedState() {
        repo.statusError = "git status failed";
        settle();

        assertEquals(
                new Failed<>(refused("git status failed")),
                program.model().repo().status());

        repo.statusError = null;
        program.dispatch(new TimerMsg.Tick());
        settle();
        assertEquals(new Loaded<>(repo.status), program.model().repo().status());
    }

    @Test
    void checkoutReloadsStatusBranchesAndCommits() {
        settle();

        program.dispatch(new CheckoutMsg.Requested(new BranchName("other")));
        settle();

        Model model = program.model();
        assertEquals(new Loaded<>(repo.status), model.repo().status());
        assertEquals(
                new Loaded<>(List.of(
                        Fixtures.branch(new BranchName("main"), false, new Sha("main-tip")),
                        Fixtures.branch(new BranchName("other"), true, new Sha("other-tip"), Instant.EPOCH, true))),
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

        program.dispatch(new NewBranchMsg.Requested(new BranchName("other")));
        program.dispatch(new PopupMsg.TextEdited("topic"));
        program.dispatch(new NewBranchMsg.Confirmed());
        settle();

        Model model = program.model();
        assertEquals(
                new BranchName("topic"),
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
        repo.checkoutError = "fatal: exists";

        program.dispatch(new NewBranchMsg.Requested(new BranchName("other")));
        program.dispatch(new PopupMsg.TextEdited("topic"));
        program.dispatch(new NewBranchMsg.Confirmed());
        settle();

        assertEquals(Option.of("Creating branch topic failed: fatal: exists"), errorText(program.model()));
    }

    @Test
    void deletingABranchCallsGitWithTheChosenScope() {
        settle();

        program.dispatch(new DeleteBranchMsg.Requested(new BranchName("other")));
        program.dispatch(new DeleteBranchMsg.Chosen(DeleteScope.BOTH));
        settle();

        assertEquals(java.util.List.of("other local remote"), repo.deleted);
        assertEquals(Option.none(), program.model().popup());
        assertEquals(Option.none(), program.model().outcome().error());
    }

    @Test
    void toggleStageRunsTheGitCommandsAndReloadsStatus() {
        settle();

        program.dispatch(new StageMsg.Requested(List.of(Fixtures.fileEntry("a.txt", ChangeType.UNTRACKED))));
        settle();
        program.dispatch(new StageMsg.Requested(List.of(
                Fixtures.fileEntry("b.txt", ChangeType.ADDED),
                new FileEntry("c.txt", ChangeType.MODIFIED, true, false))));
        settle();

        assertEquals(java.util.List.of("stage[a.txt]", "unstageNew[b.txt]", "unstage[c.txt]"), repo.indexCalls);
        assertEquals(Option.none(), program.model().outcome().error());
    }

    @Test
    void stagingAllAndCommitting() {
        repo.status = new RepoStatus(
                Head.onBranch(new BranchName("main"), new Sha("aaaa")),
                List.of(Fixtures.fileEntry("a.txt", ChangeType.UNTRACKED)));
        settle();

        program.dispatch(new CommitMsg.Requested());
        program.dispatch(new CommitMsg.StageAllConfirmed());
        settle();

        assertEquals(java.util.List.of("stage[a.txt]"), repo.indexCalls);
        assertEquals(true, program.model().popup(CommitPopup.class).isDefined());

        program.dispatch(new MessageEdited(new CommitMessage("summary", "more")));
        program.dispatch(new PopupMsg.Confirmed(CommitPopup.class));
        settle();

        assertEquals(java.util.List.of("summary|more"), repo.commits);
        assertEquals(false, program.model().popup(CommitPopup.class).isDefined());
        assertEquals(Option.none(), program.model().outcome().error());
    }

    @Test
    void amendingStagesAllFilesWhenNoneIsStagedOnceAgreed() {
        repo.status = new RepoStatus(
                Head.onBranch(new BranchName("main"), new Sha("aaaa")),
                List.of(Fixtures.fileEntry("a.txt", ChangeType.MODIFIED)));
        settle();

        program.dispatch(new AmendMsg.Requested());
        program.dispatch(new AmendMsg.Confirmed());
        program.dispatch(new AmendMsg.StageAllConfirmed());
        settle();

        assertEquals(java.util.List.of("stage[a.txt]", "amend[]"), repo.indexCalls);
        assertEquals(false, program.model().popup(AmendPopup.class).isDefined());
        assertEquals(Option.none(), program.model().outcome().error());
    }

    @Test
    void rewordingTheLastCommit() {
        settle();

        program.dispatch(new RewordMsg.Requested(
                program.model().repo().commits().toOption().get().head().sha()));
        program.dispatch(new MessageEdited(new CommitMessage("new summary", "new body")));
        program.dispatch(new PopupMsg.Confirmed(RewordPopup.class));
        settle();

        assertEquals(java.util.List.of("reword new summary|new body"), repo.commits);
        assertEquals(Option.none(), program.model().popup());
        assertEquals(Option.none(), program.model().outcome().error());
    }

    @Test
    void failedCommitIsReported() {
        repo.status = new RepoStatus(
                Head.onBranch(new BranchName("main"), new Sha("aaaa")),
                List.of(Fixtures.fileEntry("a.txt", ChangeType.ADDED)));
        repo.commitFailure = "boom";
        settle();

        program.dispatch(new CommitMsg.Requested());
        program.dispatch(new MessageEdited(new CommitMessage("summary", "")));
        program.dispatch(new PopupMsg.Confirmed(CommitPopup.class));
        settle();

        assertEquals(Option.of("Commit failed: boom"), errorText(program.model()));
    }

    @Test
    void selectingFilesLoadsTheirDiff() {
        settle();
        List<FileEntry> files = List.of(Fixtures.fileEntry("a.txt", ChangeType.UNTRACKED));

        program.dispatch(new SelectionMsg.FilesSelected(files));
        settle();

        assertEquals(
                new Loaded<>(new Diffs("", "diff of 1")),
                program.model().repoView().fileDiff().get().value());
    }

    @Test
    void failedToggleStageIsReported() {
        settle();

        repo.indexError = "fatal: pathspec 'a.txt' did not match";
        program.dispatch(new StageMsg.Requested(List.of(Fixtures.fileEntry("a.txt", ChangeType.UNTRACKED))));
        settle();

        assertEquals(Option.of("Staging failed: fatal: pathspec 'a.txt' did not match"), errorText(program.model()));
    }

    @Test
    void failedCheckoutIsReportedUntilTheNextOne() {
        settle();

        repo.checkoutError = "error: Your local changes would be overwritten";
        program.dispatch(new CheckoutMsg.Requested(new BranchName("other")));
        settle();
        assertEquals(
                Option.of("Checkout of other failed: error: Your local changes would be overwritten"),
                errorText(program.model()));
        assertEquals(
                new BranchName("main"),
                ((Loaded<RepoStatus>) program.model().repo().status())
                        .value()
                        .head()
                        .branchName()
                        .get());

        repo.checkoutError = null;
        program.dispatch(new CheckoutMsg.Requested(new BranchName("other")));
        settle();
        assertEquals(Option.none(), program.model().outcome().error());
        assertEquals(
                new BranchName("other"),
                ((Loaded<RepoStatus>) program.model().repo().status())
                        .value()
                        .head()
                        .branchName()
                        .get());
    }

    @Test
    void selectingABranchLoadsItsLog() {
        settle();

        program.dispatch(new SelectionMsg.BranchSelected(new BranchRef.Local(new BranchName("other"))));
        settle();

        assertEquals(
                Option.of(new Preview<>(
                        new BranchRef.Local(new BranchName("other")),
                        new Loaded<>(List.of(FakeRepo.commit("other-tip"))))),
                program.model().repoView().branchLog());
        assertEquals(java.util.List.of("other"), repo.logLoads);
    }

    @Test
    void highlightingBranchesFasterThanTheirLogsAreReadOnlyReadsTheLogOfTheLastOne() {
        settle();

        program.dispatch(new SelectionMsg.BranchSelected(new BranchRef.Local(new BranchName("main"))));
        program.dispatch(new SelectionMsg.BranchSelected(new BranchRef.Local(new BranchName("feature"))));
        program.dispatch(new SelectionMsg.BranchSelected(new BranchRef.Local(new BranchName("other"))));
        settle();

        assertEquals(java.util.List.of("other"), repo.logLoads);
        assertEquals(
                Option.of(new Loaded<>(List.of(FakeRepo.commit("other-tip")))),
                program.model().repoView().branchLog().map(Preview::value));
    }

    @Test
    void aReloadThatIsSkippedForANewerHighlightIsNoLongerRunning() {
        settle();
        LoadBranchLog reload = TestModels.loadBranchLog(new BranchRef.Local(new BranchName("main")));

        program.dispatch(new Refreshing(reload));
        program.dispatch(new SelectionMsg.BranchSelected(new BranchRef.Local(new BranchName("other"))));
        settle();

        assertEquals(java.util.List.of("other"), repo.logLoads);
        assertEquals(false, program.model().jobs().runs(reload));
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
        assertEquals(2, repo.fetches);
        assertEquals(false, program.model().jobs().runs(FetchCmd.class::isInstance));
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
     * Asks for {@code load} as a periodic reload does: it is running until it reports.
     */
    private record Refreshing(Load load) implements Msg {

        @Override
        public Update.Next apply(Model model) {
            return Update.Next.of(model, load);
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

    @Test
    void everyCommandRunsUntilItIsDoneWhateverCameOfIt() {
        settle();
        java.util.List<String> ran = new ArrayList<>();
        java.util.List<String> heard = new ArrayList<>();

        program.dispatch(
                new Asked(List.of(new Latest("first", ran, heard), new Throws(), new Latest("second", ran, heard))));
        assertEquals(3, program.model().jobs().running().size());
        settle();

        // The first was skipped, as the second took its slot; the one that threw failed.
        assertEquals(java.util.List.of("second"), ran);
        assertEquals(HashMap.empty(), program.model().jobs().running());
    }

    @Test
    void aCommandThatThrowsAlsoWhenItFailsIsDone() {
        settle();

        program.dispatch(new Asked(List.of(new ThrowsTwice())));
        settle();

        assertEquals(HashMap.empty(), program.model().jobs().running());
    }

    /**
     * A command that has a bug, also in how it fails, as when memory ran out.
     */
    private record ThrowsTwice() implements Cmd {

        @Override
        public Msg run(Env env) {
            throw new IllegalStateException("bug");
        }

        @Override
        public Msg failed(Failure failure) {
            throw new OutOfMemoryError("another bug");
        }
    }

    /**
     * A command that has a bug.
     */
    private record Throws() implements Cmd {

        @Override
        public Msg run(Env env) {
            throw new IllegalStateException("bug");
        }

        @Override
        public Msg failed(Failure failure) {
            return new Asked(List.empty());
        }
    }

    /**
     * Notes in {@code ran} that it ran, then waits for {@code release}; it runs where {@code background} says, and
     * holds {@code guard}, if any.
     */
    private record Guarded(
            String name,
            boolean background,
            Option<Cmd.Guard> guard,
            CountDownLatch release,
            java.util.List<String> ran)
            implements Cmd {

        @Override
        public Msg run(Env env) {
            ran.add(name);
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return new Heard(name, new ArrayList<>());
        }

        @Override
        public Msg failed(Failure failure) {
            return new Heard(name, new ArrayList<>());
        }
    }

    @Test
    void aCommandWaitsForTheOneThatHoldsItsGuardAndOthersDoNot() throws InterruptedException {
        ExecutorService io = Executors.newSingleThreadExecutor();
        ExecutorService background = Executors.newFixedThreadPool(2);
        try {
            // The messages the commands answer with are not handled: only which commands ran counts.
            Program guarding = program(new Env(repo.git(), StubGitHub.empty()), io, background, task -> {});
            java.util.List<String> ran = new CopyOnWriteArrayList<>();
            CountDownLatch fetched = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(0);
            Option<Cmd.Guard> remoteRefs = Option.of(Cmd.Guard.REMOTE_REFS);

            guarding.dispatch(new Asked(List.of(new Guarded("fetch", true, remoteRefs, fetched, ran))));
            await(() -> ran.contains("fetch"));
            guarding.dispatch(new Asked(List.of(
                    new Guarded("pull", false, remoteRefs, done, ran),
                    new Guarded("preview", true, Option.none(), done, ran))));
            await(() -> ran.contains("preview"));
            Thread.sleep(50);
            assertEquals(java.util.List.of("fetch", "preview"), ran);

            fetched.countDown();
            await(() -> ran.contains("pull"));
            assertEquals(java.util.List.of("fetch", "preview", "pull"), ran);
        } finally {
            io.shutdownNow();
            background.shutdownNow();
        }
    }

    @Test
    void aCommandThatWaitsForItsGuardIsSkippedWhenTheProgramStopsMeanwhile() throws Exception {
        ExecutorService io = Executors.newSingleThreadExecutor();
        ExecutorService background = Executors.newFixedThreadPool(2);
        try {
            Program guarding = program(new Env(repo.git(), StubGitHub.empty()), io, background, task -> {});
            java.util.List<String> ran = new CopyOnWriteArrayList<>();
            CountDownLatch fetched = new CountDownLatch(1);
            Option<Cmd.Guard> remoteRefs = Option.of(Cmd.Guard.REMOTE_REFS);

            guarding.dispatch(new Asked(List.of(new Guarded("fetch", true, remoteRefs, fetched, ran))));
            await(() -> ran.contains("fetch"));
            guarding.dispatch(new Asked(List.of(new Guarded("pull", false, remoteRefs, new CountDownLatch(0), ran))));
            guarding.stop();
            fetched.countDown();
            // Whatever io runs next runs once the pull had its turn.
            io.submit(() -> {}).get(5, TimeUnit.SECONDS);

            assertEquals(java.util.List.of("fetch"), ran);
        } finally {
            io.shutdownNow();
            background.shutdownNow();
        }
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("timed out");
            }
            Thread.sleep(5);
        }
    }

    @Test
    void gitIsReadOnTheIoExecutorAndGitHubInTheBackground() {
        settleWithout(background);
        assertEquals(new Loaded<>(repo.status), program.model().repo().status());

        program.dispatch(new PullRequestsMsg.Refresh());
        settleWithout(background);
        assertEquals(true, program.model().jobs().runs(new LoadRepositoryPullRequests()));
        assertEquals(true, program.model().jobs().runs(new LoadBranchChecks()));

        settleWithout(io);
        assertEquals(false, program.model().jobs().runs(new LoadRepositoryPullRequests()));
        assertEquals(false, program.model().jobs().runs(new LoadBranchChecks()));
        assertEquals(Option.of(HashMap.empty()), program.model().gitHub().pullRequests());
    }

    @Test
    void theModelIsToldTheTimeOfEachMessageAndDrawsAgainOnlyOnceASecondWentBy() {
        Instant start = Instant.parse("2026-10-26T10:00:00Z");
        MovingClock clock = new MovingClock(start);
        AtomicInteger changes = new AtomicInteger();
        Queue<Runnable> queued = new ArrayDeque<>();
        Program timed = Program.start(
                new Env(repo.git(), StubGitHub.empty()),
                SettingsStore.Loaded.DEFAULT,
                Option.none(),
                RepositoryTheme.NONE,
                Landing.START,
                queued::add,
                queued::add,
                queued::add,
                bug -> {},
                changes::incrementAndGet,
                () -> {},
                clock);
        // The first loads run from the start.
        assertEquals(start, timed.model().now());
        assertTrue(timed.model().jobs().running().nonEmpty());
        assertTrue(timed.model()
                .jobs()
                .running()
                .values()
                .forAll(running -> running.started().equals(start)));
        drain(queued);
        int loaded = changes.get();

        clock.at(start.plusMillis(200));
        timed.dispatch(new TimerMsg.Tick());
        assertTrue(timed.model().jobs().running().nonEmpty());
        assertTrue(timed.model()
                .jobs()
                .running()
                .values()
                .forAll(running -> running.started().equals(start.plusMillis(200))));
        // Done before they ran long enough to show.
        clock.at(start.plusMillis(400));
        drain(queued);

        // The loads brought nothing new, and the time moved on within the same second: nothing to draw.
        assertEquals(loaded, changes.get());
        assertEquals(start.plusMillis(400), timed.model().now());
        assertEquals(HashMap.empty(), timed.model().jobs().running());

        clock.at(start.plusMillis(1100));
        timed.dispatch(new TimerMsg.Clock());

        assertEquals(loaded + 1, changes.get());
    }

    @Test
    void aCommandThatIsDoneIsLoggedWithHowLongItRanAndWhatItReported() {
        Instant start = Instant.parse("2026-10-26T10:00:00Z");
        MovingClock clock = new MovingClock(start);
        Queue<Runnable> queued = new ArrayDeque<>();
        Program timed = Program.start(
                new Env(repo.git(), StubGitHub.empty()),
                SettingsStore.Loaded.DEFAULT,
                Option.none(),
                RepositoryTheme.NONE,
                Landing.START,
                queued::add,
                queued::add,
                queued::add,
                bug -> {},
                () -> {},
                () -> {},
                clock);
        drain(queued);
        java.util.List<String> heard = new ArrayList<>();
        Latest latest = new Latest("first", new ArrayList<>(), heard);

        clock.at(start.plusMillis(200));
        timed.dispatch(new Asked(List.of(latest)));
        long id = timed.model()
                .jobs()
                .running()
                .find(running -> running._2().cmd() == latest)
                .get()
                ._1();

        assertEquals(
                "Done ProgramTest.Latest 412ms → ProgramTest.Heard",
                timed.described(new Msg.Done(id, Option.of(new Heard("first", heard))), start.plusMillis(612)));
        assertEquals(
                "Done ProgramTest.Latest 412ms",
                timed.described(new Msg.Done(id, Option.none()), start.plusMillis(612)));
        assertEquals("ProgramTest.Asked", timed.described(new Asked(List.empty()), start.plusMillis(612)));
    }

    /**
     * A clock that tells the time it was set to.
     */
    private static final class MovingClock extends java.time.Clock {

        private Instant now;

        MovingClock(Instant now) {
            this.now = now;
        }

        void at(Instant time) {
            now = time;
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public java.time.ZoneId getZone() {
            return java.time.ZoneOffset.UTC;
        }

        @Override
        public java.time.Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }

    private void settle() {
        while (!io.isEmpty() || !background.isEmpty() || !ui.isEmpty()) {
            drain(io);
            drain(background);
            drain(ui);
        }
    }

    // Runs everything except what is queued on {@code held}, which stays queued.
    private void settleWithout(Queue<Runnable> held) {
        java.util.List<Queue<Runnable>> run = java.util.List.of(io, background, ui).stream()
                .filter(queue -> queue != held)
                .toList();
        while (run.stream().anyMatch(queue -> !queue.isEmpty())) {
            run.forEach(ProgramTest::drain);
        }
    }

    private static void drain(Queue<Runnable> queue) {
        Runnable task;
        while ((task = queue.poll()) != null) {
            task.run();
        }
    }
}
