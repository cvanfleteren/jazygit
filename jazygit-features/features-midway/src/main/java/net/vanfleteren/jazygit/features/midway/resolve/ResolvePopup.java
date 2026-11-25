package net.vanfleteren.jazygit.features.midway.resolve;

import net.vanfleteren.jazygit.core.git.model.FileEntry;
import net.vanfleteren.jazygit.kernel.state.popup.Popup;

/**
 * Asking how to resolve {@code file}, which is in conflict between {@code sides}.
 *
 * @param editing what editing it opens it with, for the dialog to say (ADR 200)
 */
public record ResolvePopup(FileEntry file, Sides sides, EditWith editing) implements Popup {

    /**
     * Asking how to resolve {@code file}, which editing opens in git's editor.
     */
    public ResolvePopup(FileEntry file, Sides sides) {
        this(file, sides, new EditWith.GitEditor());
    }
}
