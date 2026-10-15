package net.vanfleteren.jazygit.git;

import net.vanfleteren.jazygit.process.ExecutedCommand;

import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.PushMode;
import net.vanfleteren.jazygit.git.model.RemoteBranch;

/**
 * Pushing branches, as {@code git push} does. Each returns the command lines it ran, or why it failed together
 * with the command lines run up to then.
 */
public interface GitPush {

    /**
     * Pushes the local branch {@code branch} to {@code upstream}, the branch it tracks.
     */
    Either<GitFailure<ProcessError>, List<ExecutedCommand>> push(Branch.Name branch, RemoteBranch upstream, PushMode mode);

    /**
     * Pushes the local branch {@code branch} to {@code upstream}, which becomes its upstream once the push
     * succeeded.
     */
    Either<GitFailure<ProcessError>, List<ExecutedCommand>> pushSettingUpstream(Branch.Name branch, RemoteBranch upstream);
}
