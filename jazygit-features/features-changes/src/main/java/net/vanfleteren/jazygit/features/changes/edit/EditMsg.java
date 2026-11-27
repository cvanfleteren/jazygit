package net.vanfleteren.jazygit.features.changes.edit;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.repo.RepoPath;
import net.vanfleteren.jazygit.kernel.settings.EditorProgram;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.Phrase;
import net.vanfleteren.jazygit.kernel.state.Update;
import net.vanfleteren.jazygit.kernel.state.diff.DiffCursorAt;
import net.vanfleteren.jazygit.kernel.state.edit.EditedFile;

/**
 * Opening a file in an editor: one the user set to open files, or else git's editor (ADR 145).
 */
public sealed interface EditMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return EditUpdate.update(model, this);
    }

    /**
     * The user asked to open {@code file} in an editor, at its first line, or at its first conflict when it is in
     * conflict; the menu of the editors, if it asks, titled {@code title} when
     * another feature asked for the file so (ADR 206), else by the file.
     */
    record Requested(EditedFile file, Option<Phrase> title) implements EditMsg {

        /**
         * The user asked to open the file at {@code path} in the working tree, with the menu, if it asks, titled by
         * the file.
         */
        public Requested(RepoPath path) {
            this(EditedFile.of(path), Option.none());
        }
    }

    /**
     * The user asked to open the file at {@code path}, listed with the files of a commit, a stash, a branch or a pull
     * request, in an editor, at its first line: the file of the working tree, which for a branch or a pull request
     * must be checked out (ADR 192).
     */
    record ListedRequested(RepoPath path) implements EditMsg {}

    /**
     * The user asked to open the file the cursor {@code at} of a rich diff is in, at the line it is on, in an editor.
     */
    record AtCursor(DiffCursorAt at) implements EditMsg {}

    /**
     * The file in conflict at {@code path}, asked to be opened, was read: its first conflict starts at {@code line},
     * if it has markers left (ADR 201); the menu of the editors, if it asks, titled {@code title} (ADR 206).
     */
    record FirstConflictRead(RepoPath path, Option<Integer> line, Option<Phrase> title) implements EditMsg {

        /**
         * The first conflict of the file at {@code path} was read, the menu, if it asks, titled by the file.
         */
        public FirstConflictRead(RepoPath path, Option<Integer> line) {
            this(path, line, Option.none());
        }
    }

    /**
     * The user chose {@code editor} in the menu of the editors that open files.
     */
    record Chosen(EditorProgram editor) implements EditMsg {}

    /**
     * The user closed the menu of the editors that open files without choosing one: the popup under it comes back.
     */
    record Cancelled() implements EditMsg {}
}
