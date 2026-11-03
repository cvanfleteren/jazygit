package net.vanfleteren.jazygit.git;

import java.nio.file.Path;
import lombok.With;

/**
 * Everything the TUI does with git, in a repository, split into roles (ADR 69): the information it shows and the
 * operations it performs, grouped by the git subcommands they run. Each operation that changes the repository
 * returns the command lines it ran, or why it failed together with the command lines run up to then.
 *
 * <p>Only the roles can be replaced, so a {@code Git} cannot be given another worktree than the one its roles run git
 * in. {@link CliGit} gives one for a real repository.
 *
 * @param worktree       the root of the working tree of the repository
 * @param repositoryName the name of the repository, as {@link RepositoryIdentity#label()} has it: followed by the
 *                       worktree in parentheses when this is a linked one; read when the repository was opened
 * @param status         what git status says about the working tree and the index, and the stamps that tell whether
 *                       to read more
 * @param branches       listing, renaming and deleting branches, as git branch does, and the branch each was made
 *                       from
 * @param checkout       checking out branches, as git checkout does, and finding the commit a revision the user typed
 *                       names
 * @param history        the history, as git log shows it
 * @param diffs          reading what changed: a commit, a pull request, a branch against its base, and the working
 *                       tree and the index
 * @param stashes        listing and changing stashes, as git stash does
 * @param commits        creating and rewriting commits, moving them to another branch, and moving the checked out
 *                       branch
 * @param integrate      bringing commits from elsewhere onto the checked out branch, and going on with what that
 *                       stopped in the middle of
 * @param remotes        the remotes, and everything that talks to them, with the credentials it was given
 * @param index          operations on the index
 * @param worktrees      listing, adding and removing the worktrees of the repository, as git worktree does
 * @param config         the configuration of the repository itself, as git config --local reads and writes it
 * @param editor         editing files, as git edits commit messages
 */
public record Git(
        Path worktree,
        String repositoryName,
        @With Status status,
        @With Branches branches,
        @With Checkout checkout,
        @With History history,
        @With Diffs diffs,
        @With Stashes stashes,
        @With Commits commits,
        @With Integrate integrate,
        @With Remotes remotes,
        @With Index index,
        @With Worktrees worktrees,
        @With Config config,
        @With Editor editor) {}
