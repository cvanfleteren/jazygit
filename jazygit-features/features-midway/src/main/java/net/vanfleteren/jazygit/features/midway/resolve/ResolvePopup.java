package net.vanfleteren.jazygit.features.midway.resolve;

import net.vanfleteren.jazygit.core.git.model.FileEntry;
import net.vanfleteren.jazygit.features.changes.edit.FileEditor;
import net.vanfleteren.jazygit.kernel.state.popup.Popup;

/**
 * Asking how to resolve {@code file}, which is in conflict between {@code sides}.
 *
 * @param editor what editing it opens it with, as {@code e} does, for the dialog to say (ADR 200)
 */
public record ResolvePopup(FileEntry file, Sides sides, FileEditor editor) implements Popup {}
