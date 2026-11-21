package net.vanfleteren.jazygit.kernel.ui.keys;

import net.vanfleteren.jazygit.core.git.model.*;
import net.vanfleteren.jazygit.core.repo.*;
import net.vanfleteren.jazygit.kernel.state.build.FailedBuild;
import net.vanfleteren.jazygit.kernel.state.diff.DiffCursorAt;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap.Scope;

/**
 * The panels with keys of their own, and what is selected in each, which its keys act on.
 */
public final class Scopes {

    /**
     * The selected node of the file tree.
     */
    public static final Scope<FileTree<FileEntry>> FILES = new Scope<>("files", "panel.files.title");
    /**
     * The selected node of the tree of the files a commit or a stash changed, which the files pane lists in place of
     * the files of the working tree.
     */
    public static final Scope<FileTree<CommitFile>> COMMIT_FILES =
            new Scope<>("commitFiles", "panel.commitFiles.title");
    /**
     * The cursor of a diff a part states remarks for, as a forge does for the files of a pull request, while the content panel has the focus on it (ADR 60, 171).
     */
    public static final Scope<DiffCursorAt> REMARKED_DIFF = new Scope<>("remarkedDiff", "panel.remarkedDiff.title");
    /**
     * The cursor of a rich diff, while the content panel has the focus on it: its keys expand the lines a marker
     * leaves out (ADR 77).
     */
    public static final Scope<DiffCursorAt> DIFF = new Scope<>("diff", "panel.diff.title");
    /**
     * The name of the selected branch.
     */
    public static final Scope<BranchName> BRANCHES = new Scope<>("branches", "panel.branches.title");
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
     * The selected commit of the branch the branches pane lists the commits of in place of the branches.
     */
    public static final Scope<Commit> BRANCH_COMMITS = new Scope<>("branchCommits", "panel.branchCommits.title");
    /**
     * The selected commit.
     */
    public static final Scope<Commit> COMMITS = new Scope<>("commits", "panel.commits.title");
    /**
     * The selected stash.
     */
    public static final Scope<Stash> STASH = new Scope<>("stash", "panel.stash.title");

    /**
     * The keys of a failed build, which features add to the views of a forge that show one, as they host this scope
     * (ADR 161); a view gives it the failed build it shows.
     */
    public static final Scope<FailedBuild> FAILED_BUILD = new Scope<>("failedBuild", "panel.failedBuild.title");

    private Scopes() {}
}
