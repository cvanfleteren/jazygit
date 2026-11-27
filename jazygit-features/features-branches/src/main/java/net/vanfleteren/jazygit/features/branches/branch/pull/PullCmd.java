package net.vanfleteren.jazygit.features.branches.branch.pull;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.Git;
import net.vanfleteren.jazygit.core.git.model.*;
import net.vanfleteren.jazygit.core.git.process.GitError;
import net.vanfleteren.jazygit.core.git.process.GitFailure;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.core.repo.*;
import net.vanfleteren.jazygit.features.branches.branch.BranchCmd;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.operation.Operation;

/**
 * Bringing a branch up to date with the branch it tracks: pulling the checked out one, or fast-forwarding another.
 */
public sealed interface PullCmd extends BranchCmd.KeepsBranchBusy {

    Operation PULL = Operation.of("branches.log.title.pull", "branches.error.pull.failed")
            .waitingForUnfinished()
            .labelledWhenBusy("branches.branches.busy.pull");

    Operation FAST_FORWARD = Operation.of("branches.log.title.fastForward", "branches.error.fastForward.failed")
            .labelledWhenBusy("branches.branches.busy.pull");

    @Override
    default Option<Guard> guard() {
        return Option.of(Guard.REMOTE_REFS);
    }

    /**
     * Pulls {@code branch}, the checked out branch, from its upstream, reconciling the two as {@code mode} says.
     */
    record Pull(BranchName branch, PullMode mode) implements PullCmd {

        /**
         * What git says, in the C locale the commands run in, when it refuses to pull a diverged branch.
         */
        private static final String DIVERGED = "divergent branches";

        /**
         * Pulls as git's own settings say.
         */
        public Pull(BranchName branch) {
            this(branch, PullMode.CONFIGURED);
        }

        @Override
        public Operation operation() {
            return PULL;
        }

        @Override
        public Either<GitFailure, List<ExecutedCommand>> perform(Git git) {
            return git.remotes().pull(mode);
        }

        /**
         * A pull as the settings say, which git refuses as the branch has diverged and nothing says how to pull it, is
         * answered by asking how, as the branch can look only ahead until the pull fetched. Any other failure fails
         * it.
         */
        @Override
        public Msg run(Env env) {
            Either<GitFailure, List<ExecutedCommand>> result = perform(env.git());
            return mode == PullMode.CONFIGURED
                            && result.isLeft()
                            && result.getLeft().error() instanceof GitError.CommandFailed refused
                            && refused.says(DIVERGED)
                    ? new PullMsg.NeedsStrategy(this, result.getLeft().commands())
                    : reported(result);
        }
    }

    /**
     * Fast-forwards {@code branch}, which is not checked out, to {@code upstream}, the branch it tracks.
     */
    record FastForward(BranchName branch, BranchRef upstream) implements PullCmd {

        @Override
        public Operation operation() {
            return FAST_FORWARD;
        }

        @Override
        public Either<GitFailure, List<ExecutedCommand>> perform(Git git) {
            return git.remotes().fastForward(branch, upstream);
        }
    }
}
