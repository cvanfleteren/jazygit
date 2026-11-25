package net.vanfleteren.jazygit.features.midway.resolve;

import net.vanfleteren.jazygit.core.git.model.FileEntry;
import net.vanfleteren.jazygit.kernel.state.popup.Popup;

/**
 * Asking how to resolve {@code file}, which is in conflict between {@code sides}.
 */
public record ResolvePopup(FileEntry file, Sides sides) implements Popup {}
