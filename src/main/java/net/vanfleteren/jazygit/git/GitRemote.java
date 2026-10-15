package net.vanfleteren.jazygit.git;

import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.git.GitError.ProcessError;

/**
 * The remotes, as {@code git remote} lists them.
 */
public interface GitRemote {

    /**
     * The remotes that fetching all fetches from: all of them but those with {@code skipFetchAll} set.
     */
    Either<ProcessError, List<String>> remotes();
}
