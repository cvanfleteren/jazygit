package net.vanfleteren.jazygit.git;

import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.git.GitError.ProcessError;

/**
 * Operations on the remotes.
 */
public interface GitRemotes {

    /**
     * The remotes that fetching all fetches from: all of them but those with {@code skipFetchAll} set.
     */
    Either<ProcessError, List<String>> remotes();

    /**
     * Fetches from {@code remote}. It never asks for credentials or passphrases: what would need them fails.
     */
    Either<GitFailure<ProcessError>, List<String>> fetch(String remote);
}
