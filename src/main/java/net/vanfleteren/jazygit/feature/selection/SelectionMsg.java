package net.vanfleteren.jazygit.feature.selection;

import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Model;

import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.git.model.FileEntry;
import java.util.List;

/**
 * The user moved the highlight in one of the panes.
 */
public sealed interface SelectionMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return SelectionUpdate.update(model, this);
    }

    /**
     * The user highlighted a branch in the branches pane.
     */
    record BranchSelected(String branch) implements SelectionMsg {
    }

    /**
     * The user highlighted a node of the files tree; {@code files} are the changed files under it.
     */
    record FilesSelected(List<FileEntry> files) implements SelectionMsg {

        public FilesSelected {
            files = List.copyOf(files);
        }
    }
}
