package net.vanfleteren.jazygit.feature.branch.stack;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.Git;
import net.vanfleteren.jazygit.git.GitError;
import net.vanfleteren.jazygit.git.GitFailure;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.git.model.StackLayer;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.GitOperationCmd;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Operation;

/**
 * Rebasing a stack in one rebase, and pushing it whole (ADR 74).
 */
public sealed interface StackRebaseCmd extends Cmd {

    // It does not start while the repository is in the middle of something, as any rebase.
    Operation REBASE_STACK =
            Operation.of("log.title.rebaseStack", "error.rebaseStack.failed").waitingForUnfinished();

    Operation PUSH_STACK = Operation.of("log.title.pushStack", "error.pushStack.failed");

    /**
     * Reads the own commits of each branch of {@code rebase}, and the merges a rebase of it drops, to ask about.
     */
    record ReadStack(StackRebase rebase) implements StackRebaseCmd {

        @Override
        public Msg run(Env env) {
            return env.git()
                    .history()
                    .stackCommits(rebase.layers(), rebase.base())
                    .fold(this::failedWith, commits -> new StackMsg.StackRead(rebase, commits));
        }

        @Override
        public Msg failed(Failure failure) {
            return new StackMsg.StackReadFailed(rebase.stack(), failure);
        }
    }

    /**
     * Rebases the stack of {@code rebase}, picking {@code commits}, then checks out again the branch that was, when
     * it was not the top and the rebase did not stop.
     */
    record RebaseStack(StackRebase rebase, List<StackLayer.Commits> commits)
            implements StackRebaseCmd, GitOperationCmd {

        @Override
        public Operation operation() {
            return REBASE_STACK;
        }

        @Override
        public Option<String> subject() {
            return Option.of("#" + rebase.stack());
        }

        @Override
        public Either<GitFailure<GitError>, List<ExecutedCommand>> perform(Git git) {
            return GitFailure.<GitError>sequence(
                    () -> git.integrate().rebaseStack(rebase.base(), commits),
                    () -> rebase.back()
                            .map(branch -> git.checkout().branch(branch))
                            .getOrElse(GitFailure::nothing));
        }

        @Override
        public Msg succeeded(List<ExecutedCommand> commands) {
            return new StackMsg.Rebased(this, commands);
        }
    }

    /**
     * Pushes {@code branches} of stack {@code stack} to the default remote, all of them or none, each only while it is
     * there as last fetched.
     */
    record PushStack(int stack, List<BranchName> branches) implements StackRebaseCmd, GitOperationCmd {

        @Override
        public Operation operation() {
            return PUSH_STACK;
        }

        @Override
        public Option<String> subject() {
            return Option.of("#" + stack);
        }

        @Override
        public boolean changesGitHub() {
            return true;
        }

        @Override
        public Either<GitFailure<GitError>, List<ExecutedCommand>> perform(Git git) {
            return GitFailure.widen(git.remotes().pushAll(branches, RemoteBranch.DEFAULT_REMOTE));
        }
    }
}
