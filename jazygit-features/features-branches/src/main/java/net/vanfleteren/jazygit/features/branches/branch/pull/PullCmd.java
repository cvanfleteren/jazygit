package net.vanfleteren.jazygit.features.branches.branch.pull;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.Git;
import net.vanfleteren.jazygit.core.git.model.*;
import net.vanfleteren.jazygit.core.git.process.GitFailure;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.core.repo.*;
import net.vanfleteren.jazygit.features.branches.branch.BranchCmd;
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
