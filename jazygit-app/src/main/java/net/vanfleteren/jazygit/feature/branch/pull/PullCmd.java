package net.vanfleteren.jazygit.feature.branch.pull;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.branch.BranchCmd;
import net.vanfleteren.jazygit.git.GitFailure;
import net.vanfleteren.jazygit.git.GitInfoProvider;
import net.vanfleteren.jazygit.git.model.*;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.Operation;

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
    record Pull(Branch.Name branch) implements PullCmd {

        @Override
        public Operation operation() {
            return PULL;
        }

        @Override
        public Either<? extends GitFailure<?>, List<ExecutedCommand>> perform(GitInfoProvider git) {
            return git.pull();
        }
    }

    /**
     * Fast-forwards {@code branch}, which is not checked out, to {@code upstream}, the branch it tracks.
     */
    record FastForward(Branch.Name branch, RemoteBranch upstream) implements PullCmd {

        @Override
        public Operation operation() {
            return FAST_FORWARD;
        }

        @Override
        public Either<? extends GitFailure<?>, List<ExecutedCommand>> perform(GitInfoProvider git) {
            return git.fastForward(branch, upstream);
        }
    }
}
