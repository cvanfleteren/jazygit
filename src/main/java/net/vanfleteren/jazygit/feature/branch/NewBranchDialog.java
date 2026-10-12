package net.vanfleteren.jazygit.feature.branch;

import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.widgets.input.TextInputState;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import static dev.tamboui.toolkit.Toolkit.*;

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
    public Option<Element> render(Model model) {
        return model.popup(NewBranchPopup.class).map(NewBranchPopup::label).map(label -> dialog(Messages.get("dialog.newBranch.title", label),
                        textInput(input)
                                .id(ID)
                                .placeholder(Messages.get("dialog.newBranch.placeholder"))
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
        dispatch.accept(new NewBranchMsg.Confirmed(name));
    }

    private void cancel() {
        input.clear();
        dispatch.accept(new NewBranchMsg.Cancelled());
    }
}
