package net.vanfleteren.jazygit.git;

import io.vavr.control.Either;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.model.RepoStatus;

/**
 * What {@code git status} says about the working tree and the index.
 */
public interface GitStatus {

    Either<ProcessError, RepoStatus> status();
}
