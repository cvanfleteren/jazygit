package net.vanfleteren.jazygit.features.changes.edit;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.kernel.settings.EditorProgram;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.Update;
import net.vanfleteren.jazygit.kernel.state.diff.DiffCursorAt;

/**
 * Opening a file in an editor: one the user set to open files, or else git's editor (ADR 145).
 */
public sealed interface EditMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return EditUpdate.update(model, this);
    }

    /**
     * The user asked to open the file at {@code path}, relative to the working tree, in an editor, at its first line.
     */
    record Requested(String path) implements EditMsg {}

    /**
     * The user asked to open the file at {@code path}, listed with the files of a commit, a stash, a branch or a pull
     * request, in an editor, at its first line: the file of the working tree, which for a branch or a pull request
     * must be checked out (ADR 192).
     */
    record ListedRequested(String path) implements EditMsg {}

    /**
     * The user asked to open the file the cursor {@code at} of a rich diff is in, at the line it is on, in an editor.
     */
    record AtCursor(DiffCursorAt at) implements EditMsg {}

    /**
     * The file in conflict at {@code path}, asked to be opened, was read: its first conflict starts at {@code line},
     * if it has markers left (ADR 201).
     */
    record FirstConflictRead(String path, Option<Integer> line) implements EditMsg {}

    /**
     * The user chose {@code editor} in the menu of the editors that open files.
     */
    record Chosen(EditorProgram editor) implements EditMsg {}

    /**
     * The user closed the menu of the editors that open files without choosing one: the popup under it comes back.
     */
    record Cancelled() implements EditMsg {}
}
