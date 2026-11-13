package net.vanfleteren.jazygit.state;

/**
 * The id of a scrollable area of the content panel, under which what the area shows is kept: where it is scrolled
 * to, the lines expanded in it (ADR 77) and the version of a file it shows (ADR 102). It is also the id of the element
 * that draws the area, which can have the focus.
 *
 * @param value the id
 */
public record AreaId(String value) {

    /**
     * The area of the staged changes of the node highlighted in the files pane.
     */
    public static final AreaId STAGED = new AreaId("diff-staged");

    /**
     * The area of the unstaged changes of the node highlighted in the files pane.
     */
    public static final AreaId UNSTAGED = new AreaId("diff-unstaged");

    /**
     * The area of what a commit or a stash changed in the files highlighted in the tree of them the files pane lists.
     */
    public static final AreaId COMMIT_FILE = new AreaId("commit-file-changes");

    /**
     * The area of a file without changes highlighted among all files of the working tree (ADR 108).
     */
    public static final AreaId FILE_CONTENT = new AreaId("file-content");

    /**
     * The area of what the files pane shows in place of a diff or a file's content, such as that a file is binary, or
     * still read: it holds the focus while the files are walked from the content panel (ADR 117).
     */
    public static final AreaId FILE_NOTE = new AreaId("file-note");

    /**
     * The area of the commit highlighted in the commits pane.
     */
    public static final AreaId COMMIT = new AreaId("commit-changes");

    /**
     * The area of what the stash highlighted in the stash pane stashed.
     */
    public static final AreaId STASH = new AreaId("stash-changes");

    /**
     * The area of the commit highlighted in the divergence the branches pane lists.
     */
    public static final AreaId DIVERGENCE = new AreaId("divergence-commit");

    /**
     * The area of the commit highlighted in the commits of a branch the branches pane lists.
     */
    public static final AreaId BRANCH_COMMIT = new AreaId("branch-commit");

    /**
     * The area of the pull request highlighted in whichever list of the pull requests screen is in use.
     */
    public static final AreaId PULL_REQUEST = new AreaId("pull-request-preview");

    /**
     * The area of the log of the branch highlighted in the branches pane.
     */
    public static final AreaId LOG = new AreaId("branch-log");
}
