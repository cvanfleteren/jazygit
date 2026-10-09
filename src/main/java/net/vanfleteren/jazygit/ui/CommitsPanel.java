package net.vanfleteren.jazygit.ui;

import dev.tamboui.toolkit.elements.Panel;
import net.vanfleteren.jazygit.model.Commit;
import net.vanfleteren.jazygit.state.Model;

import java.util.List;

/**
 * Left-side panel listing the commit log.
 */
public class CommitsPanel {

    public static final String ID = "commits";

    private final LoadableList<List<Commit>> list = new LoadableList<>("Commits", ID,
            commits -> commits.stream()
                    .map(c -> c.shortSha() + " " + c.message())
                    .toList());

    /**
     * @param focusedId the id of the currently focused left pane, as reported by
     *                  {@code runner().focusManager().focusedId()}
     */
    public Panel render(Model model, String focusedId) {
        return list.render(model.commits(), ID.equals(focusedId));
    }

    /**
     * The index of the currently highlighted commit, never negative.
     */
    public int selectedIndex() {
        return list.selectedIndex();
    }
}
