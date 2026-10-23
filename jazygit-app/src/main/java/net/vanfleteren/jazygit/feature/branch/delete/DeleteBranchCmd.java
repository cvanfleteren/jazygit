package net.vanfleteren.jazygit.feature.branch.delete;

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
 * Deleting a branch: a local one, where the user chose, or one on its remote.
 */
public sealed interface DeleteBranchCmd extends BranchCmd {

    Operation DELETE_BRANCH = Operation.of("log.title.deleteBranch", "error.deleteBranch.failed");

    @Override
    default Operation operation() {
        return DELETE_BRANCH;
    }

    /**
     * Deletes {@code branch} where {@code scope} says; on its remote, that is {@code remote}.
     */
    record DeleteBranch(BranchName branch, RemoteBranch remote, DeleteScope scope)
            implements DeleteBranchCmd, BranchCmd.KeepsBranchBusy {

        @Override
        public Option<Guard> guard() {
            return Option.when(scope.remote(), Guard.REMOTE_REFS);
        }

        @Override
        public Either<? extends GitFailure<?>, List<ExecutedCommand>> perform(GitInfoProvider git) {
            return git.deleteBranch(branch, remote, scope);
        }
    }

    /**
     * Deletes {@code remote} on its remote; a local branch that follows it is left alone.
     */
    record DeleteRemoteBranch(RemoteBranch remote) implements DeleteBranchCmd {

        @Override
        public BranchName branch() {
            return remote.branch();
        }

        @Override
        public Option<String> subject() {
            return Option.of(remote.shortName());
        }

        @Override
        public Option<BranchRef> busyBranch() {
            return Option.of(new BranchRef.Remote(remote));
        }

        @Override
        public Option<Guard> guard() {
            return Option.of(Guard.REMOTE_REFS);
        }

        @Override
        public Either<? extends GitFailure<?>, List<ExecutedCommand>> perform(GitInfoProvider git) {
            return git.deleteRemoteBranch(remote);
        }
    }
}
