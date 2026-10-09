package net.vanfleteren.jazygit.ui;

import dev.tamboui.toolkit.elements.Panel;
import net.vanfleteren.jazygit.model.Branch;
import net.vanfleteren.jazygit.state.Model;

import java.util.List;

/**
 * Left-side panel listing the local branches, with the current branch marked.
 */
public class BranchesPanel {

    public static final String ID = "branches";

    private final LoadableList<List<Branch>> list = new LoadableList<>("Branches", ID,
            branches -> branches.stream()
                    .map(b -> (b.current() ? "* " : "  ") + b.name())
                    .toList());

    /**
     * @param focusedId the id of the currently focused left pane, as reported by
     *                  {@code runner().focusManager().focusedId()}
     */
    public Panel render(Model model, String focusedId) {
        return list.render(model.branches(), ID.equals(focusedId));
    }

    /**
     * The index of the currently highlighted branch, never negative.
     */
    public int selectedIndex() {
        return list.selectedIndex();
    }
}
