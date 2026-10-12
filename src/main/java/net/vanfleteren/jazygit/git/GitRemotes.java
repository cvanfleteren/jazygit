package net.vanfleteren.jazygit.git;

import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.git.GitError.ProcessError;

/**
 * Operations on the remotes.
 */
public interface GitRemotes {

    /**
     * Fetches from all remotes. It never asks for credentials or passphrases: what would need them fails.
     */
    Either<GitFailure<ProcessError>, List<String>> fetchAll();
}
