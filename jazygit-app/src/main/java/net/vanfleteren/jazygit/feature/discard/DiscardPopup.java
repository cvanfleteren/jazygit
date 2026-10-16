package net.vanfleteren.jazygit.feature.discard;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.state.Popup;

/**
 * Asking what to discard of {@code files}: every changed file under the node the user picked.
 */
public record DiscardPopup(List<FileEntry> files) implements Popup {

    /**
     * Whether any of the files has unstaged changes, or is untracked.
     */
    public boolean hasUnstaged() {
        return files.exists(FileEntry::unstaged);
    }
}
