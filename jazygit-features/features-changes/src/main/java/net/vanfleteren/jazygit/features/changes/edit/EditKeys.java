package net.vanfleteren.jazygit.features.changes.edit;

import net.vanfleteren.jazygit.core.git.model.FileTree;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.diff.DiffCursorAt;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap.Binding;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;

/**
 * The keys for opening a file in an editor (ADR 145): the file selected in the files, at its start, also in the files
 * of a commit, a stash, a branch or a pull request (ADR 192), and the file the cursor of a rich diff is in, at its
 * line.
 */
public final class EditKeys {

    private EditKeys() {}

    public static Keymap register(Keymap keys) {
        // A directory is not opened: an editor set to open files may open none.
        return keys.add(
                        Scopes.FILES,
                        new Binding<>(
                                "e",
                                "changes.help.files.edit",
                                selection -> selection
                                        .filter(FileTree.File.class::isInstance)
                                        .map(FileTree.File.class::cast)
                                        .<Msg>map(file -> new EditMsg.Requested(
                                                file.entry().path()))))
                // The file on disk, listed with the files of a commit, a stash, a branch or a pull request: those of a
                // branch or a pull request only while it is checked out (ADR 192).
                .add(
                        Scopes.COMMIT_FILES,
                        new Binding<>(
                                "e",
                                "changes.help.commitFiles.edit",
                                selection -> selection
                                        .filter(FileTree.File.class::isInstance)
                                        .map(FileTree.File.class::cast)
                                        .<Msg>map(file -> new EditMsg.ListedRequested(
                                                file.entry().path()))))
                .add(
                        Scopes.DIFF,
                        new Binding<DiffCursorAt>(
                                "e",
                                "changes.help.diff.edit",
                                at -> at.filter(cursor ->
                                                EditUpdate.file(cursor.cursor()).isDefined())
                                        .map(EditMsg.AtCursor::new)));
    }
}
