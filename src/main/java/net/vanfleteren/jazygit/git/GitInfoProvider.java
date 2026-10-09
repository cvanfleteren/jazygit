package net.vanfleteren.jazygit.git;

/**
 * Everything the TUI does with git: the information it shows and the operations it performs. It
 * is only the composition of the capability interfaces; new operations belong in one of those,
 * or in a new one added here.
 */
public interface GitInfoProvider extends GitRead, GitBranches, GitIndex, GitCommits {
}
