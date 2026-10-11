package net.vanfleteren.jazygit.ui;

import dev.tamboui.toolkit.elements.Panel;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.KeyEvent;
import net.vanfleteren.jazygit.git.model.Stash;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Loadable;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.keys.Keymap;
import net.vanfleteren.jazygit.keys.Scopes;
import net.vanfleteren.jazygit.state.Msg;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.IntStream;

/**
 * Bottom-left panel listing the stashes, newest first. The highlighted stash is unstashed with {@code g} and deleted with {@code d}.
 */
public final class StashPanel {

    public static final String ID = Scopes.STASH.id();

    private final LoadableList<List<Stash>> list = new LoadableList<>(Messages.get("panel.stash.title"), ID,
            stashes -> IntStream.range(0, stashes.size())
                    .mapToObj(i -> LoadableList.Row.of(row(i, stashes.get(i))))
                    .toList());
    // The list's key handler also sees keys typed in other panes, so it must know whether it has focus.
    private boolean focused;
    private final Keymap keymap = Keymap.standard();

    /**
     * @param dispatch receives the messages for the user's actions, on the render thread
     */
    public StashPanel(Consumer<Msg> dispatch) {
        list.onKeyEvent(event -> handleKey(event, dispatch));
    }

    /**
     * @param focusedId the id of the currently focused left pane, as reported by
     *                  {@code runner().focusManager().focusedId()}
     */
    public Panel render(Model model, String focusedId) {
        focused = ID.equals(focusedId);
        return list.render(model.stashes(), focused);
    }

    /**
     * The highlighted stash, once the stashes are shown.
     */
    public Optional<Stash> selectedStash() {
        return list.shown() instanceof Loadable.Loaded<List<Stash>>(List<Stash> stashes) && !stashes.isEmpty()
                ? Optional.of(stashes.get(Math.min(list.selectedIndex(), stashes.size() - 1)))
                : Optional.empty();
    }

    /**
     * The text of a stash row: its position in the stash list, then what it was stashed as.
     */
    static String row(int index, Stash stash) {
        return index + " " + stash.message();
    }

    private EventResult handleKey(KeyEvent event, Consumer<Msg> dispatch) {
        return Optional.of(event)
                .filter(e -> focused)
                .flatMap(this::request)
                .map(msg -> {
                    dispatch.accept(msg);
                    return EventResult.HANDLED;
                })
                .orElse(EventResult.UNHANDLED);
    }

    private Optional<Msg> request(KeyEvent event) {
        return keymap.handle(Scopes.STASH, event, selectedStash());
    }
}
