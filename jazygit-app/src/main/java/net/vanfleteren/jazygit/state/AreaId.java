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
}
