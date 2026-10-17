package net.vanfleteren.jazygit.keys;

import net.vanfleteren.jazygit.gh.model.PullRequestSummary;
import net.vanfleteren.jazygit.git.model.*;
import net.vanfleteren.jazygit.keys.Keymap.Scope;

/**
 * The panels with keys of their own, and what is selected in each, which its keys act on.
 */
public final class Scopes {

    /**
     * The selected node of the file tree.
     */
    public static final Scope<FileTree> FILES = new Scope<>("files", "panel.files.title");
    /**
     * The name of the selected branch.
     */
    public static final Scope<Branch.Name> BRANCHES = new Scope<>("branches", "panel.branches.title");
    /**
     * The selected remote-tracking branch.
     */
    public static final Scope<RemoteBranch> REMOTE_BRANCHES =
            new Scope<>("remoteBranches", "panel.remoteBranches.title");
    /**
     * The selected worktree.
     */
    public static final Scope<Worktree> WORKTREES = new Scope<>("worktrees", "panel.worktrees.title");
    /**
     * The selected commit of the divergence the branches pane lists in place of the local branches.
     */
    public static final Scope<Commit> DIVERGENCE = new Scope<>("divergence", "panel.divergence.title");
    /**
     * The selected commit.
     */
    public static final Scope<Commit> COMMITS = new Scope<>("commits", "panel.commits.title");
    /**
     * The selected stash.
     */
    public static final Scope<Stash> STASH = new Scope<>("stash", "panel.stash.title");

    /**
     * The selected pull request of the repository, on the pull requests screen.
     */
    public static final Scope<PullRequestSummary> PR_REPOSITORY =
            new Scope<>("prRepository", "panel.prRepository.title");

    private Scopes() {}
}
