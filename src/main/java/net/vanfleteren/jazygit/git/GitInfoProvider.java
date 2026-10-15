package net.vanfleteren.jazygit.git;

import java.nio.file.Path;

/**
 * Everything the TUI does with git: the information it shows and the operations it performs. Apart from
 * the repository itself, it is only the composition of the capability interfaces, one per git subcommand;
 * new operations belong in the one of the subcommand they run, or in a new one added here.
 */
public interface GitInfoProvider extends GitStatus, GitBranch, GitCheckout, GitMerge, GitRebase, GitPush, GitPull,
        GitFetch, GitRemote, GitConfig, GitLog, GitShow, GitDiff, GitStash, GitCommit, GitReset, GitIndex {

    /**
     * The name of the repository, i.e. the name of its working tree directory.
     */
    String repositoryName();

    /**
     * The root of the working tree of the repository.
     */
    Path workTree();
}
