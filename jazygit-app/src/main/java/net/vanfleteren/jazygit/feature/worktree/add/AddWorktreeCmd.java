package net.vanfleteren.jazygit.feature.worktree.add;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import io.vavr.control.Try;
import java.nio.file.Path;
import net.vanfleteren.jazygit.feature.worktree.WorktreeCmd;
import net.vanfleteren.jazygit.feature.worktree.add.WorktreeSource.*;
import net.vanfleteren.jazygit.gh.GitHub;
import net.vanfleteren.jazygit.git.GitError;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.GitFailure;
import net.vanfleteren.jazygit.git.GitInfoProvider;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Operation;
import net.vanfleteren.jazygit.state.OperationCmd;

/**
 * Adds a worktree at {@code location} that checks out what {@code source} says. A pull request is checked out by
 * gh in the new worktree, which starts out on no branch; when gh fails, the worktree is removed again. Once added,
 * it tells where the worktree is, as git lists it.
 */
public record AddWorktreeCmd(WorktreeSource source, String location) implements OperationCmd {

    public static final Operation ADD_WORKTREE = Operation.of("log.title.addWorktree", "error.addWorktree.failed");

    @Override
    public Operation operation() {
        return ADD_WORKTREE;
    }

    @Override
    public Option<String> subject() {
        return Option.of(source.label());
    }

    @Override
    public Option<BranchRef> busyBranch() {
        return source.busyBranch();
    }

    @Override
    public Msg run(Env env) {
        GitInfoProvider git = env.git();
        Path path = WorktreeCmd.path(location);
        return switch (source) {
            case Existing(var branch) -> reported(env, git.addWorktree(path, branch));
            case Created(var name, var from) ->
                reported(env, git.addWorktreeWithNewBranch(path, name, from.start(), from.parent(), from.track()));
            case Detached(var start) -> reported(env, git.addDetachedWorktree(path, start));
            case PullRequest(var pullRequest) ->
                git.addDetachedWorktree(path, "HEAD")
                        .fold(
                                this::failedWith,
                                added -> checkout(git, env.gitHub(), path, pullRequest.number(), added)
                                        .fold(this::failed, all -> added(env, all)));
        };
    }

    private Msg reported(Env env, Either<GitFailure<ProcessError>, List<ExecutedCommand>> outcome) {
        return outcome.fold(this::failedWith, commands -> added(env, commands));
    }

    /**
     * Has gh check pull request {@code number} out in the worktree at {@code path}, which was just added running
     * {@code added}; removes it again when gh fails. A failure carries every command that ran, and why removing the
     * worktree failed too, if it did, as the worktree is then left behind.
     */
    private static Either<Failure, List<ExecutedCommand>> checkout(
            GitInfoProvider git, GitHub gitHub, Path path, int number, List<ExecutedCommand> added) {
        return gitHub.checkoutPullRequest(number, path).map(added::appendAll).mapLeft(failure -> {
            Either<GitFailure<GitError>, List<ExecutedCommand>> removed = git.removeWorktree(path, true);
            return new Failure.GitHub(
                    failure.error(),
                    added.appendAll(failure.commands()).appendAll(removed.fold(GitFailure::commands, ran -> ran)),
                    removed.swap()
                            .toOption()
                            .map(GitFailure::error)
                            .flatMap(error ->
                                    error instanceof ProcessError process ? Option.of(process) : Option.none()));
        });
    }

    /**
     * Where the worktree was added: {@code location} taken relative to the working tree, with its links
     * resolved, as git lists it.
     */
    private Msg added(Env env, List<ExecutedCommand> commands) {
        Path added = env.git().worktree().resolve(WorktreeCmd.path(location)).normalize();
        return new AddWorktreeMsg.Added(
                this, Try.of(added::toRealPath).getOrElse(added).toString(), commands);
    }
}
