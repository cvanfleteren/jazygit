package net.vanfleteren.jazygit.features.branches.branch.upstream;

import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.core.git.Git;
import net.vanfleteren.jazygit.core.git.process.GitFailure;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.features.branches.branch.BranchCmd;
import net.vanfleteren.jazygit.kernel.state.operation.Operation;

/**
 * Changing which branch a local branch follows: git only writes its configuration, nothing is fetched or pushed.
 */
public sealed interface UpstreamCmd extends BranchCmd {

    Operation SET_UPSTREAM = Operation.of("branches.log.title.setUpstream", "branches.error.setUpstream.failed");

    Operation UNSET_UPSTREAM = Operation.of("branches.log.title.unsetUpstream", "branches.error.unsetUpstream.failed");

    /**
     * Makes {@code branch} follow {@code upstream}, a remote-tracking branch that is there.
     */
    record Set(BranchName branch, RemoteBranch upstream) implements UpstreamCmd {

        @Override
        public Operation operation() {
            return SET_UPSTREAM;
        }

        @Override
        public Either<GitFailure, List<ExecutedCommand>> perform(Git git) {
            return git.branches().setUpstream(branch, upstream);
        }
    }

    /**
     * Makes {@code branch} follow nothing.
     */
    record Unset(BranchName branch) implements UpstreamCmd {

        @Override
        public Operation operation() {
            return UNSET_UPSTREAM;
        }

        @Override
        public Either<GitFailure, List<ExecutedCommand>> perform(Git git) {
            return git.branches().unsetUpstream(branch);
        }
    }
}
