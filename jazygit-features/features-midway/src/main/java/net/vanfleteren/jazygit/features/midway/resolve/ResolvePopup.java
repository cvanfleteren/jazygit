package net.vanfleteren.jazygit.features.midway.resolve;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.model.ConflictFile;
import net.vanfleteren.jazygit.core.git.model.ConflictTools;
import net.vanfleteren.jazygit.core.git.model.FileEntry;
import net.vanfleteren.jazygit.features.changes.edit.FileEditor;
import net.vanfleteren.jazygit.kernel.state.popup.Popup;

/**
 * Asking how to resolve {@code file}, which is in conflict between {@code sides}.
 *
 * @param editor        what editing it opens it with, as {@code e} does, for the dialog to say (ADR 200)
 * @param tools         what it can be handed to, read once the dialog opened (ADR 201); none until then
 * @param firstConflict the line its first conflict starts at, read with {@code tools}, if it has markers left
 * @param written       how it stands against the conflict git wrote of it, read with {@code tools}, which says
 *                      whether mergiraf rewrites it, and whether that loses edits
 */
public record ResolvePopup(
        FileEntry file,
        Sides sides,
        FileEditor editor,
        ConflictTools tools,
        Option<Integer> firstConflict,
        ConflictFile written)
        implements Popup {

    /**
     * This popup, once {@code tools}, {@code firstConflict} and how the file stands were read.
     */
    public ResolvePopup read(ConflictTools tools, Option<Integer> firstConflict, ConflictFile written) {
        return new ResolvePopup(file, sides, editor, tools, firstConflict, written);
    }

    /**
     * Whether mergiraf writes the conflict again before it solves it: not when the file has its bases already.
     */
    public boolean rewrites() {
        return written != ConflictFile.WITH_BASE;
    }
}
