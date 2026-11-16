package net.vanfleteren.jazygit.state.view;

import io.vavr.control.Option;
import lombok.With;

/**
 * What the files pane shows, apart from the diff read for the node highlighted in it.
 *
 * @param shown       whether the pane lists all files of the working tree, not only those with changes (ADR 108); a
 *                    reload then lists them too
 * @param commitFiles the files a commit or a stash changed, which the pane lists in place of the changed files of the
 *                    working tree while it is open
 * @param filter      the filter of the files of the working tree: the pane shows only those that match it (ADR 105)
 * @param unchanged   the node highlighted in the pane while it lists all files, when nothing under it has changed: the
 *                    content panel shows it in place of a diff (ADR 108)
 * @param unlisted    why the row highlighted in the pane with the focus has no files to list, which the pane says in
 *                    place of the files of the working tree while no files of a commit are listed (ADR 141)
 */
@With
public record FilesView(
        FilesShown shown,
        Option<CommitFilesView> commitFiles,
        Option<FileFilter> filter,
        Option<UnchangedNode> unchanged,
        Option<Unlisted> unlisted) {

    public static final FilesView INITIAL =
            new FilesView(FilesShown.CHANGED, Option.none(), Option.none(), Option.none(), Option.none());

    /**
     * What the pane lists for the pane with the focus that follows its highlight: the files of a commit, nothing, or
     * the files of the working tree (ADR 141).
     */
    public Followed followed() {
        return commitFiles
                .<Followed>map(view -> new Followed.Files(view.of()))
                .orElse(() -> unlisted.map(Followed.Nothing::new))
                .getOrElse(Followed.WorkingTree::new);
    }
}
