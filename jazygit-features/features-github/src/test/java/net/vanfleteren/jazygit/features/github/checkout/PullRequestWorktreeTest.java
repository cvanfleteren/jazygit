package net.vanfleteren.jazygit.features.github.checkout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.LinkedHashMap;
import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.nio.file.Path;
import java.time.Instant;
import net.vanfleteren.jazygit.core.gh.GitHub;
import net.vanfleteren.jazygit.core.gh.StubGitHub;
import net.vanfleteren.jazygit.core.gh.model.PullRequest;
import net.vanfleteren.jazygit.core.gh.model.PullRequestSummary;
import net.vanfleteren.jazygit.core.gh.process.GhError;
import net.vanfleteren.jazygit.core.gh.process.GhFailure;
import net.vanfleteren.jazygit.core.gh.pullrequests.StubPullRequests;
import net.vanfleteren.jazygit.core.git.Git;
import net.vanfleteren.jazygit.core.git.StubGit;
import net.vanfleteren.jazygit.core.git.branches.StubWorktrees;
import net.vanfleteren.jazygit.core.git.process.GitError;
import net.vanfleteren.jazygit.core.git.process.GitFailure;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.core.repo.ForgeBranch;
import net.vanfleteren.jazygit.core.repo.PrNumber;
import net.vanfleteren.jazygit.core.repo.Revision;
import net.vanfleteren.jazygit.features.branches.worktree.add.AddWorktreeCmd;
import net.vanfleteren.jazygit.features.branches.worktree.add.AddWorktreeMsg;
import net.vanfleteren.jazygit.features.branches.worktree.add.ReadWorktreeTemplate;
import net.vanfleteren.jazygit.features.github.GitHubFailure;
import net.vanfleteren.jazygit.features.github.GitHubServices;
import net.vanfleteren.jazygit.kernel.state.Cmd;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.operation.OperationMsg;
import org.junit.jupiter.api.Test;

/**
 * A new worktree of a pull request, which gh checks out in it (ADR 68, 134, 169).
 */
class PullRequestWorktreeTest {

    @Test
    void itIsNamedAfterItsBranchAndHasATemplateAndVariablesOfItsOwn() {
        PullRequestWorktree source = new PullRequestWorktree(pullRequest());

        assertEquals("#9", source.label());
        assertEquals(new BranchName("pr-9"), source.directory());
        assertEquals(Option.none(), source.busyBranch());
        assertEquals(Option.of(Cmd.Guard.REMOTE_REFS), source.guard());
        assertEquals(List.of("jazygit.pullRequestWorktreeTemplate", ReadWorktreeTemplate.KEY), source.templateKeys());
        assertEquals(Option.of("owner/repo"), source.repository());
        assertEquals(LinkedHashMap.of("number", "9"), source.values());
    }

    @Test
    void oneOfTheRepositoryIsOnItsOwnBranchWhichIsBusyMeanwhile() {
        PullRequestWorktree own = new PullRequestWorktree(new PullRequestSummary(
                "owner/repo",
                new PrNumber(9),
                "Title",
                "bob",
                new ForgeBranch("patch-1"),
                false,
                false,
                PullRequest.Checks.NONE,
                PullRequest.Review.NONE,
                Instant.EPOCH,
                "https://github.com/owner/repo/pull/9"));

        assertEquals(new BranchName("patch-1"), own.directory());
        assertEquals(Option.of(new BranchRef.Local(new BranchName("patch-1"))), own.busyBranch());
    }

    private static PullRequestSummary pullRequest() {
        return new PullRequestSummary(
                "owner/repo",
                new PrNumber(9),
                "Title",
                "bob",
                new ForgeBranch("patch-1"),
                false,
                true,
                PullRequest.Checks.NONE,
                PullRequest.Review.NONE,
                Instant.EPOCH,
                "https://github.com/owner/repo/pull/9");
    }

    @Test
    void gitAddsTheWorktreeAndGhChecksThePullRequestOutInIt() {
        java.util.List<String> ran = new java.util.ArrayList<>();
        Git git = StubGit.of().withWorktrees(new StubWorktrees() {
            @Override
            public Either<GitFailure, List<ExecutedCommand>> addDetached(Path location, Revision startPoint) {
                ran.add("add " + location + " " + startPoint.toArg());
                return Either.right(StubGit.executed("git worktree add"));
            }
        });
        GitHub gitHub = StubGitHub.of().withPullRequests(new StubPullRequests() {
            @Override
            public Either<GhFailure, List<ExecutedCommand>> checkout(PrNumber number, Path in) {
                ran.add("gh " + number.value() + " " + in);
                return Either.right(StubGit.executed("gh pr checkout 9"));
            }
        });

        Msg added = new AddWorktreeCmd(new PullRequestWorktree(pullRequest()), "/repos/app-pr-9")
                .run(new Env(git).with(GitHubServices.GH, gitHub));

        assertEquals(java.util.List.of("add /repos/app-pr-9 HEAD", "gh 9 /repos/app-pr-9"), ran);
        assertEquals(
                List.of("git worktree add", "gh pr checkout 9"),
                ((AddWorktreeMsg.Added) added).commands().map(ExecutedCommand::line));
    }

    @Test
    void whenGhFailsTheWorktreeOfThePullRequestIsRemovedAgain() {
        java.util.List<String> ran = new java.util.ArrayList<>();
        Git git = StubGit.of().withWorktrees(new StubWorktrees() {
            @Override
            public Either<GitFailure, List<ExecutedCommand>> remove(Path location, boolean force) {
                ran.add("remove " + location + " " + force);
                return Either.right(StubGit.executed("git worktree remove --force"));
            }
        });
        GitHub fails = StubGitHub.of().withPullRequests(new StubPullRequests() {
            @Override
            public Either<GhFailure, List<ExecutedCommand>> checkout(PrNumber number, Path in) {
                return Either.left(new GhFailure(
                        new GhError.CommandFailed(Option.of("no such pull request"), 1),
                        StubGit.executed("gh pr checkout 9")));
            }
        });

        Msg failed = new AddWorktreeCmd(new PullRequestWorktree(pullRequest()), "/repos/app-pr-9")
                .run(new Env(git).with(GitHubServices.GH, fails));

        assertEquals(java.util.List.of("remove /repos/app-pr-9 true"), ran);
        assertTrue(failed instanceof OperationMsg.Failed, failed.toString());
    }

    @Test
    void whenRemovingTheWorktreeOfThePullRequestFailsTooThatIsSaid() {
        GitError.CommandFailed locked = new GitError.CommandFailed(Option.of("fatal: cannot remove"), 128, "");
        Git git = StubGit.of().withWorktrees(new StubWorktrees() {
            @Override
            public Either<GitFailure, List<ExecutedCommand>> addDetached(Path location, Revision startPoint) {
                return Either.right(StubGit.executed("git worktree add"));
            }

            @Override
            public Either<GitFailure, List<ExecutedCommand>> remove(Path location, boolean force) {
                return Either.left(new GitFailure(locked, StubGit.executed("git worktree remove --force")));
            }
        });
        GitHub fails = StubGitHub.of().withPullRequests(new StubPullRequests() {
            @Override
            public Either<GhFailure, List<ExecutedCommand>> checkout(PrNumber number, Path in) {
                return Either.left(new GhFailure(
                        new GhError.CommandFailed(Option.of("no such pull request"), 1),
                        StubGit.executed("gh pr checkout 9")));
            }
        });

        Msg failed = new AddWorktreeCmd(new PullRequestWorktree(pullRequest()), "/repos/app-pr-9")
                .run(new Env(git).with(GitHubServices.GH, fails));

        Failure failure = ((OperationMsg.Failed) failed).failure();
        assertEquals(
                List.of("git worktree add", "gh pr checkout 9", "git worktree remove --force"),
                failure.commands().map(ExecutedCommand::line));
        assertEquals(
                new GitHubFailure(
                        new GhError.CommandFailed(Option.of("no such pull request"), 1),
                        failure.commands(),
                        Option.of(locked)),
                failure);
    }

    @Test
    void whenRemovingTheWorktreeOfThePullRequestFailsForAReasonOfItsOwnThatIsSaidToo() {
        GitError.WorktreeHasChanges changed = new GitError.WorktreeHasChanges("changed");
        Git git = StubGit.of().withWorktrees(new StubWorktrees() {
            @Override
            public Either<GitFailure, List<ExecutedCommand>> addDetached(Path location, Revision startPoint) {
                return Either.right(StubGit.executed("git worktree add"));
            }

            @Override
            public Either<GitFailure, List<ExecutedCommand>> remove(Path location, boolean force) {
                return Either.left(new GitFailure(changed, StubGit.executed("git worktree remove --force")));
            }
        });
        GitHub fails = StubGitHub.of().withPullRequests(new StubPullRequests() {
            @Override
            public Either<GhFailure, List<ExecutedCommand>> checkout(PrNumber number, Path in) {
                return Either.left(new GhFailure(
                        new GhError.CommandFailed(Option.of("no such pull request"), 1),
                        StubGit.executed("gh pr checkout 9")));
            }
        });

        Msg failed = new AddWorktreeCmd(new PullRequestWorktree(pullRequest()), "/repos/app-pr-9")
                .run(new Env(git).with(GitHubServices.GH, fails));

        assertEquals(Option.of(changed), ((GitHubFailure) ((OperationMsg.Failed) failed).failure()).cleanupFailed());
    }
}
