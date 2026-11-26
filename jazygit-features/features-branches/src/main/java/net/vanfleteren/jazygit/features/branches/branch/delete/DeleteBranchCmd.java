package net.vanfleteren.jazygit.features.branches.branch.delete;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.Git;
import net.vanfleteren.jazygit.core.git.model.*;
import net.vanfleteren.jazygit.core.git.process.GitFailure;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.core.repo.*;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.features.branches.branch.BranchCmd;
import net.vanfleteren.jazygit.features.branches.forcedelete.ForceDeleteCmd;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.operation.Operation;

/**
 * Deleting a branch: a local one, where the user chose, or one on its remote.
 */
public sealed interface DeleteBranchCmd extends BranchCmd {

    Operation DELETE_BRANCH = Operation.of("branches.log.title.deleteBranch", "branches.error.deleteBranch.failed");

    @Override
    default Operation operation() {
        return DELETE_BRANCH;
    }

    /**
     * Deletes {@code branch} where {@code scope} says; on its remote, that is {@code remote}. Locally, only when it
     * is merged: when it is not, it is deleted anyway if no commit would be lost, and else whether to lose them is
     * asked instead of being told it failed.
     */
    record DeleteBranch(BranchName branch, RemoteBranch remote, DeleteScope scope)
            implements DeleteBranchCmd, BranchCmd.KeepsBranchBusy {

        @Override
        public Option<Guard> guard() {
            return Option.when(scope.remote(), Guard.REMOTE_REFS);
        }

        @Override
        public Either<GitFailure, List<ExecutedCommand>> perform(Git git) {
            return git.remotes().deleteBranch(branch, remote, scope);
        }

        @Override
        public Msg run(Env env) {
            return env.git()
                    .remotes()
                    .deleteBranch(branch, remote, scope)
                    .fold(failure -> ForceDeleteCmd.refused(this, branch, failure, env.git()), this::succeeded);
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
        public Either<GitFailure, List<ExecutedCommand>> perform(Git git) {
            return git.remotes().deleteRemoteBranch(remote);
        }
    }
}
