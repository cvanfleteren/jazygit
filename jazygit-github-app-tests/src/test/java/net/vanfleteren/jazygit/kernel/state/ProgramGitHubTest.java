package net.vanfleteren.jazygit.kernel.state;

import static net.vanfleteren.jazygit.kernel.state.TestModels.program;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import net.vanfleteren.jazygit.core.gh.StubGitHub;
import net.vanfleteren.jazygit.core.git.Git;
import net.vanfleteren.jazygit.core.git.StubGit;
import net.vanfleteren.jazygit.core.git.branches.Branches;
import net.vanfleteren.jazygit.core.git.branches.Checkout;
import net.vanfleteren.jazygit.core.git.branches.Remotes;
import net.vanfleteren.jazygit.core.git.branches.StubBranches;
import net.vanfleteren.jazygit.core.git.branches.StubCheckout;
import net.vanfleteren.jazygit.core.git.branches.StubRemotes;
import net.vanfleteren.jazygit.core.git.commits.Commits;
import net.vanfleteren.jazygit.core.git.commits.StubCommits;
import net.vanfleteren.jazygit.core.git.files.Index;
import net.vanfleteren.jazygit.core.git.files.Stashes;
import net.vanfleteren.jazygit.core.git.files.Status;
import net.vanfleteren.jazygit.core.git.files.StubDiffs;
import net.vanfleteren.jazygit.core.git.files.StubIndex;
import net.vanfleteren.jazygit.core.git.files.StubStashes;
import net.vanfleteren.jazygit.core.git.files.StubStatus;
import net.vanfleteren.jazygit.core.git.history.History;
import net.vanfleteren.jazygit.core.git.history.Integrate;
import net.vanfleteren.jazygit.core.git.history.StubHistory;
import net.vanfleteren.jazygit.core.git.history.StubIntegrate;
import net.vanfleteren.jazygit.core.git.model.DeleteScope;
import net.vanfleteren.jazygit.core.git.model.DiffOptions;
import net.vanfleteren.jazygit.core.git.model.DiffText;
import net.vanfleteren.jazygit.core.git.model.Diffs;
import net.vanfleteren.jazygit.core.git.model.DiscardPlan;
import net.vanfleteren.jazygit.core.git.model.FileEntry;
import net.vanfleteren.jazygit.core.git.model.Fixtures;
import net.vanfleteren.jazygit.core.git.model.PushMode;
import net.vanfleteren.jazygit.core.git.model.RepoStatus;
import net.vanfleteren.jazygit.core.git.model.SquashMode;
import net.vanfleteren.jazygit.core.git.process.GitError;
import net.vanfleteren.jazygit.core.git.process.GitFailure;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.core.repo.Branch;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.core.repo.Commit;
import net.vanfleteren.jazygit.core.repo.CommitMessage;
import net.vanfleteren.jazygit.core.repo.Head;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.core.repo.RemoteName;
import net.vanfleteren.jazygit.core.repo.Revision;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.core.repo.Stash;
import net.vanfleteren.jazygit.features.github.GitHubServices;
import net.vanfleteren.jazygit.features.github.checks.branches.LoadBranchChecks;
import net.vanfleteren.jazygit.features.github.prs.LoadRepositoryPullRequests;
import net.vanfleteren.jazygit.features.github.prs.PullRequestReads;
import net.vanfleteren.jazygit.features.github.state.GitHubState;
import net.vanfleteren.jazygit.kernel.state.Loadable.Loaded;
import net.vanfleteren.jazygit.kernel.state.load.Load;
import org.junit.jupiter.api.Test;

/**
 * The tests of ProgramTest with GitHub in the app (ADR 171).
 */
class ProgramGitHubTest {

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

        public net.vanfleteren.jazygit.core.git.files.Diffs diffs() {
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

    private final Program program =
            program(new Env(repo.git()).with(GitHubServices.GH, StubGitHub.of()), io::add, background::add, ui::add);

    /**
     * Asks for {@code cmds}, changing nothing else.
     */
    private record Asked(List<Cmd> cmds) implements Msg {

        @Override
        public Update.Next apply(Model model) {
            return Update.Next.of(model).and(cmds);
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

        // As a fetch makes GitHub's feature read them, once it found it applies (ADR 171).
        program.dispatch(
                model -> PullRequestReads.load(Update.Next.of(net.vanfleteren.jazygit.app.GitHubKeys.on(model))));
        settleWithout(background);
        assertEquals(true, program.model().jobs().runs(new LoadRepositoryPullRequests()));
        assertEquals(true, program.model().jobs().runs(new LoadBranchChecks()));

        settleWithout(io);
        assertEquals(false, program.model().jobs().runs(new LoadRepositoryPullRequests()));
        assertEquals(false, program.model().jobs().runs(new LoadBranchChecks()));
        assertEquals(
                Option.of(HashMap.empty()),
                program.model().part(GitHubState.PART).pullRequests());
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
            run.forEach(ProgramGitHubTest::drain);
        }
    }

    private static void drain(Queue<Runnable> queue) {
        Runnable task;
        while ((task = queue.poll()) != null) {
            task.run();
        }
    }
}
