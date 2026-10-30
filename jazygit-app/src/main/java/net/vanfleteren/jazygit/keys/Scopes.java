package net.vanfleteren.jazygit.keys;

import net.vanfleteren.jazygit.gh.model.PullRequestSummary;
import net.vanfleteren.jazygit.git.model.*;
import net.vanfleteren.jazygit.keys.Keymap.Scope;
import net.vanfleteren.jazygit.state.DiffCursorAt;

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
     * The cursor of the diff of a pull request's files, while the content panel has the focus on it (ADR 60).
     */
    public static final Scope<DiffCursorAt> PULL_REQUEST_DIFF =
            new Scope<>("pullRequestDiff", "panel.pullRequestDiff.title");
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
     * The selected pull request that asks for the user's review, on the pull requests screen.
     */
    public static final Scope<PullRequestSummary> PR_REVIEW_REQUESTED =
            new Scope<>("prReviewRequested", "panel.prReviewRequested.title");
    /**
     * The selected pull request the user opened, on the pull requests screen.
     */
    public static final Scope<PullRequestSummary> PR_AUTHORED = new Scope<>("prAuthored", "panel.prAuthored.title");
    /**
     * The selected pull request of the repository, on the pull requests screen.
     */
    public static final Scope<PullRequestSummary> PR_REPOSITORY =
            new Scope<>("prRepository", "panel.prRepository.title");
    /**
     * The panes of the pull requests screen, top to bottom.
     */
    public static final io.vavr.collection.List<Scope<PullRequestSummary>> PULL_REQUESTS =
            io.vavr.collection.List.of(PR_REPOSITORY, PR_REVIEW_REQUESTED, PR_AUTHORED);

    private Scopes() {}
}
