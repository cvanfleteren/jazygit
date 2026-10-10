package net.vanfleteren.jazygit.git;

import net.vanfleteren.jazygit.git.model.*;

import java.util.List;

/**
 * Reading the repository. Every read reflects the current state of the repository; deciding when
 * to call is up to the caller.
 */
public interface GitRead {

    /**
     * The name of the repository, i.e. the name of its working tree directory.
     */
    String repositoryName();

    RepoStatus status();

    List<Branch> branches();

    /**
     * The log of HEAD, newest first.
     */
    List<Commit> commits();

    /**
     * The log of the local branch {@code branch}, newest first.
     *
     * @throws IllegalStateException if there is no such branch
     */
    List<Commit> log(String branch);

    /**
     * What the commit {@code sha} changed, like {@code git show}: the changed files, a summary of the
     * changes, a blank line and the diff. Long output is cut off.
     *
     * @throws IllegalStateException if git fails, with git's explanation as message
     */
    String commitChanges(String sha);

    /**
     * The staged and the unstaged diff of {@code files}.
     *
     * @throws IllegalStateException if git fails, with git's explanation as message
     */
    Diffs diff(List<FileEntry> files);
}
