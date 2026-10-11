package net.vanfleteren.jazygit.feature.help;

import static dev.tamboui.toolkit.Toolkit.*;

import net.vanfleteren.jazygit.ui.Theme;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.StyledElement;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.KeyEvent;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.keys.Keymap;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Popup listing the keys of a panel and what they do. Escape, Enter or ? closes it. Whether it is
 * open lives in the {@link Model}.
 */
public final class HelpDialog {

    public static final String ID = "help";

    private static final int WIDTH = 50;

    // The key right-aligned in a column of this many characters, then a space and the description.
    private static final int KEY_COLUMN = 12;
    private static final String KEY_FORMAT = "%" + KEY_COLUMN + "s %s";

    private final Consumer<Msg> dispatch;
    private final Keymap keymap;

    /**
     * @param dispatch receives the messages for the user's actions, on the render thread
     */
    public HelpDialog(Consumer<Msg> dispatch) {
        this(dispatch, Keymap.standard());
    }

    HelpDialog(Consumer<Msg> dispatch, Keymap keymap) {
        this.dispatch = dispatch;
        this.keymap = keymap;
    }

    /**
     * The popup, while the model asks for the keybindings of a panel: the keys of that panel, then the
     * global ones.
     */
    public Optional<Element> render(Model model) {
        return model.popup(HelpPopup.class).map(HelpPopup::scopeId).flatMap(scopeId ->
                keymap.titleKey(scopeId).map(titleKey -> {
                    List<StyledElement<?>> rows = new ArrayList<>();
                    keymap.bindings(scopeId).forEach(b -> rows.add(row(b.label(), b.descriptionKey())));
                    rows.add(text(""));
                    rows.add(text(Messages.get("dialog.help.global")).addClass(Theme.Classes.BOLD));
                    keymap.globals().forEach(g -> rows.add(row(g.label(), g.descriptionKey())));
                    rows.add(text(""));
                    rows.add(text(Messages.get("dialog.help.hint")).addClass(Theme.Classes.DIM));
                    return dialog(Messages.get("dialog.help.title", Messages.get(titleKey)),
                            rows.toArray(StyledElement[]::new))
                            .id(ID)
                            .focusable()
                            .rounded()
                            .width(WIDTH)
                            // The rows, plus the border.
                            .length(rows.size() + 2)
                            .padding(1)
                            .onKeyEvent(this::handleKey)
                            .onConfirm(this::close)
                            .onCancel(this::close);
                }));
    }

    private static StyledElement<?> row(String key, String descriptionKey) {
        return text(KEY_FORMAT.formatted(key, Messages.get(descriptionKey)));
    }

    private EventResult handleKey(KeyEvent event) {
        if (event.isChar('?')) {
            close();
            return EventResult.HANDLED;
        }
        return EventResult.UNHANDLED;
    }

    private void close() {
        dispatch.accept(new HelpMsg.Closed());
    }
}
