package net.vanfleteren.jazygit.feature.discard;

import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.state.Popup;

import java.util.List;

/**
 * Asking what to discard of {@code files}: every changed file under the node the user picked.
 */
public record DiscardPopup(List<FileEntry> files) implements Popup {

    public DiscardPopup {
        files = List.copyOf(files);
    }

    /**
     * Whether any of the files has unstaged changes, or is untracked.
     */
    public boolean hasUnstaged() {
        return files.stream().anyMatch(FileEntry::unstaged);
    }
}
