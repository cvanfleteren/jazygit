package net.vanfleteren.jazygit.feature.stash;

import net.vanfleteren.jazygit.i18n.Messages;
import static dev.tamboui.toolkit.Toolkit.*;

import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.widgets.input.TextInputState;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Popup asking for the name of a stash. Enter stashes the changes, Escape closes the popup. A blank name
 * stashes under a generated one. Whether it
 * is open lives in the {@link Model}; the text being typed lives in the input widget.
 */
public final class StashDialog {

    public static final String ID = "stash-name";

    private static final int WIDTH = 50;

    private final TextInputState input = new TextInputState();
    private final Consumer<Msg> dispatch;

    /**
     * @param dispatch receives the messages for the user's actions, on the render thread
     */
    public StashDialog(Consumer<Msg> dispatch) {
        this.dispatch = dispatch;
    }

    /**
     * The popup, while the model asks for a branch name.
     */
    public Optional<Element> render(Model model) {
        return model.popup(StashPopup.class).map(popup -> dialog(Messages.get("dialog.stash.title"),
                        textInput(input)
                                .id(ID)
                                .placeholder(Messages.get("dialog.stash.placeholder"))
                                .onSubmit(this::confirm)
                                .onKeyEvent(event -> {
                                    if (event.code() == KeyCode.ESCAPE) {
                                        cancel();
                                        return EventResult.HANDLED;
                                    }
                                    return EventResult.UNHANDLED;
                                }))
                .rounded()
                .width(WIDTH)
                .padding(1)
                .onConfirm(this::confirm)
                .onCancel(this::cancel));
    }

    private void confirm() {
        String name = input.text();
        input.clear();
        dispatch.accept(new StashMsg.Confirmed(name));
    }

    private void cancel() {
        input.clear();
        dispatch.accept(new StashMsg.Cancelled());
    }
}
