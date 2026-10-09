package net.vanfleteren.jazygit.ui;

import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.feature.commit.RewordMsg;
import dev.tamboui.toolkit.elements.Panel;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.KeyEvent;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;

import java.util.List;
import java.util.function.Consumer;

/**
 * Left-side panel listing the commit log.
 */
public class CommitsPanel {

    public static final String ID = "commits";

    private final LoadableList<List<Commit>> list = new LoadableList<>(Messages.get("panel.commits.title"), ID,
            commits -> commits.stream()
                    .map(c -> c.shortSha() + " " + c.message())
                    .toList());
    // The list's key handler also sees keys typed in other panes, so it must know whether it has focus.
    private boolean focused;

    public CommitsPanel() {
        this(msg -> {
        });
    }

    /**
     * @param dispatch receives the messages for the user's actions, on the render thread
     */
    public CommitsPanel(Consumer<Msg> dispatch) {
        list.onKeyEvent(event -> handleKey(event, dispatch));
    }

    /**
     * @param focusedId the id of the currently focused left pane, as reported by
     *                  {@code runner().focusManager().focusedId()}
     */
    public Panel render(Model model, String focusedId) {
        focused = ID.equals(focusedId);
        return list.render(model.commits(), focused);
    }

    /**
     * The index of the currently highlighted commit, never negative.
     */
    public int selectedIndex() {
        return list.selectedIndex();
    }

    private EventResult handleKey(KeyEvent event, Consumer<Msg> dispatch) {
        if (focused && event.isChar('r')) {
            dispatch.accept(new RewordMsg.Requested(selectedIndex()));
            return EventResult.HANDLED;
        }
        return EventResult.UNHANDLED;
    }
}
