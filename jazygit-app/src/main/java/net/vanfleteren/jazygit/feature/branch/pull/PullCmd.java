package net.vanfleteren.jazygit.feature.branch.pull;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.branch.BranchCmd;
import net.vanfleteren.jazygit.git.Git;
import net.vanfleteren.jazygit.git.model.*;
import net.vanfleteren.jazygit.git.process.GitFailure;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.operation.Operation;

/**
 * Bringing a branch up to date with the branch it tracks: pulling the checked out one, or fast-forwarding another.
 */
public sealed interface PullCmd extends BranchCmd.KeepsBranchBusy {

    Operation PULL = Operation.of("log.title.pull", "error.pull.failed")
            .waitingForUnfinished()
            .labelledWhenBusy("branches.busy.pull");

    Operation FAST_FORWARD =
            Operation.of("log.title.fastForward", "error.fastForward.failed").labelledWhenBusy("branches.busy.pull");

    @Override
    default Option<Guard> guard() {
        return Option.of(Guard.REMOTE_REFS);
    }

    /**
     * Pulls {@code branch}, the checked out branch, from its upstream.
     */
    record Pull(BranchName branch) implements PullCmd {

        @Override
        public Operation operation() {
            return PULL;
        }

        @Override
        public Either<GitFailure, List<ExecutedCommand>> perform(Git git) {
            return git.remotes().pull();
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
