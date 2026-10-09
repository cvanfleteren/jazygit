package net.vanfleteren.jazygit.ui;

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
 * Popup asking for the name of a new branch. Enter creates it, Escape closes the popup. Whether it
 * is open lives in the {@link Model}; the text being typed lives in the input widget.
 */
public final class NewBranchDialog {

    public static final String ID = "new-branch-name";

    private static final int WIDTH = 50;

    private final TextInputState input = new TextInputState();
    private final Consumer<Msg> dispatch;

    /**
     * @param dispatch receives the messages for the user's actions, on the render thread
     */
    public NewBranchDialog(Consumer<Msg> dispatch) {
        this.dispatch = dispatch;
    }

    /**
     * The popup, while the model asks for a branch name.
     */
    public Optional<Element> render(Model model) {
        return model.newBranchBase().map(base -> dialog("New branch from " + base,
                        textInput(input)
                                .id(ID)
                                .placeholder("branch name")
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
        // A blank name keeps the popup open, see Update.
        if (!name.isBlank()) {
            input.clear();
        }
        dispatch.accept(new Msg.NewBranchConfirmed(name));
    }

    private void cancel() {
        input.clear();
        dispatch.accept(new Msg.NewBranchCancelled());
    }
}
