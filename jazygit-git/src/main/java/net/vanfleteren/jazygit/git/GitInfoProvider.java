package net.vanfleteren.jazygit.git;

import java.nio.file.Path;

/**
 * Everything the TUI does with git: the information it shows and the operations it performs, grouped by the
 * git subcommand they run. Each operation that changes the repository returns the command lines it ran, or why
 * it failed together with the command lines run up to then.
 */
public interface GitInfoProvider {

    /**
     * The name of the repository, as {@link RepositoryIdentity#label()} has it: followed by the worktree in
     * parentheses when this is a linked one.
     */
    String repositoryName();

    /**
     * The root of the working tree of the repository.
     */
    Path worktree();

    /**
     * What git status says about the working tree and the index, and the stamps that tell whether to read more.
     */
    Status status();

    /**
     * Listing, renaming and deleting branches, as git branch does, and the branch each was made from.
     */
    Branches branches();

    /**
     * Checking out branches, as git checkout does, and finding the commit a revision the user typed names.
     */
    Checkout checkout();

    /**
     * Bringing commits from elsewhere onto the checked out branch, and going on with what that stopped in the middle
     * of.
     */
    Integrate integrate();

    /**
     * The remotes, and everything that talks to them, with the credentials it was given.
     */
    Remotes remotes();

    /**
     * The configuration of the repository itself, as git config --local reads and writes it.
     */
    Config config();

    /**
     * The history, as git log shows it.
     */
    History history();

    /**
     * Reading what changed: a commit, a pull request, a branch against its base, and the working tree and the index.
     */
    Diffs diffs();

    /**
     * Listing and changing stashes, as git stash does.
     */
    Stashes stashes();

    /**
     * Creating and rewriting commits, moving them to another branch, and moving the checked out branch.
     */
    Commits commits();

    /**
     * Operations on the index.
     */
    Index index();

    /**
     * Listing, adding and removing the worktrees of the repository, as git worktree does.
     */
    Worktrees worktrees();

    /**
     * Editing files, as git edits commit messages.
     */
    Editor editor();
}
