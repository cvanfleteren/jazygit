package net.vanfleteren.jazygit.changes.edit;

import net.vanfleteren.jazygit.git.model.FileTree;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.diff.DiffCursorAt;
import net.vanfleteren.jazygit.ui.keys.Keymap;
import net.vanfleteren.jazygit.ui.keys.Keymap.Binding;
import net.vanfleteren.jazygit.ui.keys.Scopes;

/**
 * The keys for opening a file in an editor (ADR 145): the file selected in the files, at its start, and the file the
 * cursor of a rich diff is in, at its line.
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
