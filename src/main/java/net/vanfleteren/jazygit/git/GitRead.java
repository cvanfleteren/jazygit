package net.vanfleteren.jazygit.git;

import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.git.GitError.DiffError;
import net.vanfleteren.jazygit.git.GitError.LogError;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.GitError.RepositoryUnreadable;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.git.model.Diffs;
import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.git.model.RepoStatus;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.model.Stash;

/**
 * Reading the repository. Every read reflects the current state of the repository; deciding when
 * to call is up to the caller.
 */
public interface GitRead {

    /**
     * The name of the repository, i.e. the name of its working tree directory.
     */
    String repositoryName();

    Either<ProcessError, RepoStatus> status();

    Either<RepositoryUnreadable, List<Branch>> branches();

    /**
     * The stashes, newest first.
     */
    Either<ProcessError, List<Stash>> stashes();

    /**
     * The log of HEAD, newest first.
     */
    Either<RepositoryUnreadable, List<Commit>> commits();

    /**
     * The log of the local branch {@code branch}, newest first.
     */
    Either<LogError, List<Commit>> log(String branch);

    /**
     * What the commit {@code sha} changed, like {@code git show}: the changed files, a summary of the
     * changes, a blank line and the diff. Long output is cut off.
     */
    Either<ProcessError, String> commitChanges(Sha sha);

    /**
     * The staged and the unstaged diff of {@code files}.
     */
    Either<DiffError, Diffs> diff(List<FileEntry> files);
}
