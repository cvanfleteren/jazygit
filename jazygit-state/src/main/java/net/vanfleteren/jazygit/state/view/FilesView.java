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
 */
@With
public record FilesView(
        FilesShown shown,
        Option<CommitFilesView> commitFiles,
        Option<FileFilter> filter,
        Option<UnchangedNode> unchanged) {

    public static final FilesView INITIAL =
            new FilesView(FilesShown.CHANGED, Option.none(), Option.none(), Option.none());
}
