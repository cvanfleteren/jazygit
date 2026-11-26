package net.vanfleteren.jazygit.features.github.checkout;

import io.vavr.collection.LinkedHashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.nio.file.Path;
import net.vanfleteren.jazygit.core.gh.model.PullRequestSummary;
import net.vanfleteren.jazygit.core.git.Git;
import net.vanfleteren.jazygit.core.git.process.GitFailure;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.core.repo.RepoSlug;
import net.vanfleteren.jazygit.core.repo.Revision;
import net.vanfleteren.jazygit.features.branches.worktree.add.ReadWorktreeTemplate;
import net.vanfleteren.jazygit.features.branches.worktree.add.WorktreeSource;
import net.vanfleteren.jazygit.features.github.GitHubFailure;
import net.vanfleteren.jazygit.features.github.GitHubServices;
import net.vanfleteren.jazygit.features.github.state.FromGitHub;
import net.vanfleteren.jazygit.kernel.state.Cmd.Guard;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Failure;

/**
 * A new worktree of {@code pullRequest}, of the repository, on the branch gh makes for it (ADR 68, 169): gh checks it
 * out in the new worktree, which starts out on no branch; when gh fails, the worktree is removed again.
 */
public record PullRequestWorktree(PullRequestSummary pullRequest) implements WorktreeSource {

    /**
     * The key of the git configuration that holds where a new worktree for a pull request starts out (ADR 134).
     */
    public static final String TEMPLATE_KEY = "jazygit.pullRequestWorktreeTemplate";

    @Override
    public String label() {
        return "#" + pullRequest.number().value();
    }

    /**
     * Its branch; for one from a fork, whose branch gh may name otherwise, its number.
     */
    @Override
    public BranchName directory() {
        return pullRequest.fromFork()
                ? new BranchName("pr-" + pullRequest.number().value())
                : FromGitHub.branch(pullRequest.headBranch());
    }

    /**
     * Its branch, which gh checks out, unless it is from a fork, whose branch gh may name otherwise.
     */
    @Override
    public Option<BranchRef> busyBranch() {
        return Option.when(
                !pullRequest.fromFork(), () -> new BranchRef.Local(FromGitHub.branch(pullRequest.headBranch())));
    }

    // gh fetches the pull request, into the remote-tracking refs the worktrees share.
    @Override
    public Option<Guard> guard() {
        return Option.of(Guard.REMOTE_REFS);
    }

    /**
     * Its own template, else the one for every new worktree.
     */
    @Override
    public List<String> templateKeys() {
        return List.of(TEMPLATE_KEY, ReadWorktreeTemplate.KEY);
    }

    @Override
    public Option<RepoSlug> repository() {
        return Option.of(pullRequest.repository());
    }

    @Override
    public Map<String, String> values() {
        return LinkedHashMap.of("number", String.valueOf(pullRequest.number().value()));
    }

    /**
     * Adds a worktree on no branch at {@code path}, and has gh check the pull request out in it; removes it again when
     * gh fails. A failure carries every command that ran, and why removing the worktree failed too, if it did, as the
     * worktree is then left behind.
     */
    @Override
    public Either<Failure, List<ExecutedCommand>> add(Env env, Path path) {
        Git git = env.git();
        return git.worktrees()
                .addDetached(path, new Revision.Head())
                .mapLeft(Failure::of)
                .flatMap(added -> GitHubServices.of(env)
                        .pullRequests()
                        .checkout(pullRequest.number(), path)
                        .map(added::appendAll)
                        .mapLeft(failure -> {
                            Either<GitFailure, List<ExecutedCommand>> removed =
                                    git.worktrees().remove(path, true);
                            return new GitHubFailure(
                                    failure.error(),
                                    added.appendAll(failure.commands())
                                            .appendAll(removed.fold(GitFailure::commands, ran -> ran)),
                                    removed.swap().toOption().map(GitFailure::error));
                        }));
    }
}
