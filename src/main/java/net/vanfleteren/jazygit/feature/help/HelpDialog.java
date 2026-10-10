package net.vanfleteren.jazygit.feature.help;

import static dev.tamboui.toolkit.Toolkit.*;

import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.StyledElement;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.KeyEvent;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

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

    /**
     * @param dispatch receives the messages for the user's actions, on the render thread
     */
    public HelpDialog(Consumer<Msg> dispatch) {
        this.dispatch = dispatch;
    }

    /**
     * The popup, while the model asks for the keybindings of a panel.
     */
    public Optional<Element> render(Model model) {
        return model.popup(HelpPopup.class).map(HelpPopup::topic).map(topic -> {
            StyledElement<?>[] rows = Stream.concat(
                    topic.bindings().stream()
                            .map(b -> text(KEY_FORMAT.formatted(b.key(), Messages.get(b.descriptionKey())))),
                    Stream.of(text(""), text(Messages.get("dialog.help.hint")).dim()))
                    .toArray(StyledElement[]::new);
            return dialog(Messages.get("dialog.help.title", Messages.get(topic.titleKey())), rows)
                    .id(ID)
                    .focusable()
                    .rounded()
                    .width(WIDTH)
                    // The rows, the blank line and the hint, plus the border.
                    .length(topic.bindings().size() + 2 + 2)
                    .padding(1)
                    .onKeyEvent(this::handleKey)
                    .onConfirm(this::close)
                    .onCancel(this::close);
        });
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
