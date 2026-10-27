package net.vanfleteren.jazygit.feature.branch.push;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.branch.BranchCmd;
import net.vanfleteren.jazygit.git.GitError;
import net.vanfleteren.jazygit.git.GitFailure;
import net.vanfleteren.jazygit.git.GitInfoProvider;
import net.vanfleteren.jazygit.git.model.*;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Operation;

/**
 * Pushing a branch: to the branch it tracks, or to a new upstream it then tracks.
 */
public sealed interface PushCmd extends BranchCmd.KeepsBranchBusy {

    Operation PUSH = Operation.of("log.title.push", "error.push.failed").labelledWhenBusy("branches.busy.push");

    @Override
    default Operation operation() {
        return PUSH;
    }

    @Override
    default Option<Guard> guard() {
        return Option.of(Guard.REMOTE_REFS);
    }

    @Override
    default boolean changesGitHub() {
        return true;
    }

    /**
     * Pushes {@code branch} to {@code upstream}, which it then tracks.
     */
    record PushSettingUpstream(BranchName branch, RemoteBranch upstream) implements PushCmd {

        @Override
        public Either<? extends GitFailure<?>, List<ExecutedCommand>> perform(GitInfoProvider git) {
            return git.pushSettingUpstream(branch, upstream);
        }
    }

    /**
     * Pushes {@code branch}, which tracks {@code upstream}, where {@code push.default} says. When it says not to,
     * the user is asked where to push it instead of being told it failed.
     */
    record Push(BranchName branch, RemoteBranch upstream, PushMode mode) implements PushCmd {

        @Override
        public Either<? extends GitFailure<?>, List<ExecutedCommand>> perform(GitInfoProvider git) {
            return git.push(branch, upstream, mode);
        }

        @Override
        public Msg run(Env env) {
            return env.git()
                    .push(branch, upstream, mode)
                    .fold(
                            failure -> failure.error() instanceof GitError.PushRefused(var pushDefault, var target)
                                    ? new PushMsg.Refused(
                                            this, new SetUpstreamPopup.Refusal(pushDefault, target), failure.commands())
                                    : failedWith(failure),
                            this::succeeded);
        }
    }
}
