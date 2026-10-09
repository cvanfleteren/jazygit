package net.vanfleteren.jazygit.model;

import java.util.List;

/**
 * Source of the git information shown by the TUI, and the operations it performs. Every read
 * reflects the current state of the repository; deciding when to call is up to the caller.
 */
public interface GitInfoProvider {

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
     * Checks out the local branch {@code branch}.
     *
     * @throws IllegalStateException if the checkout fails, with git's explanation as message
     */
    void checkout(String branch);

    /**
     * Creates the local branch {@code name} at {@code startPoint} and checks it out.
     *
     * @throws IllegalStateException if git fails, with git's explanation as message
     */
    void createBranch(String name, String startPoint);

    /**
     * Stages the changes of {@code paths}.
     *
     * @throws IllegalStateException if git fails, with git's explanation as message
     */
    void stage(List<String> paths);

    /**
     * Removes the newly added {@code paths} from the index; they stay in the working tree, untracked.
     *
     * @throws IllegalStateException if git fails, with git's explanation as message
     */
    void unstageNew(List<String> paths);

    /**
     * Resets the index entries of {@code paths} to HEAD.
     *
     * @throws IllegalStateException if git fails, with git's explanation as message
     */
    void unstage(List<String> paths);

    /**
     * The staged and the unstaged diff of {@code files}.
     *
     * @throws IllegalStateException if git fails, with git's explanation as message
     */
    Diffs diff(List<FileEntry> files);
}
