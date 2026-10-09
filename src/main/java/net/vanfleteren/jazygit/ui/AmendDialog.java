package net.vanfleteren.jazygit.ui;

import static dev.tamboui.toolkit.Toolkit.*;

import dev.tamboui.style.Overflow;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.widgets.paragraph.Paragraph;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Popup asking to confirm amending the last commit. Enter confirms, Escape cancels. Whether it is
 * open lives in the {@link Model}.
 */
public final class AmendDialog {

    public static final String ID = "amend-commit";

    private static final int WIDTH = 60;

    private static final String MESSAGE = "Are you sure you want to amend the last commit? "
            + "Afterwards, you can change the commit message from the commits panel.";
    // The message wrapped at the popup's inner width.
    private static final int MESSAGE_HEIGHT = 3;
    // The message, a blank line and the hint, plus the border.
    private static final int HEIGHT = MESSAGE_HEIGHT + 2 + 2;

    private final Consumer<Msg> dispatch;

    /**
     * @param dispatch receives the messages for the user's actions, on the render thread
     */
    public AmendDialog(Consumer<Msg> dispatch) {
        this.dispatch = dispatch;
    }

    /**
     * The popup, while the model asks to confirm the amend.
     */
    public Optional<Element> render(Model model) {
        return Optional.of(model).filter(Model::amendPrompt)
                .map(m -> dialog("Amend last commit",
                        widget(Paragraph.builder().text(MESSAGE).overflow(Overflow.WRAP_WORD).build())
                                .length(MESSAGE_HEIGHT),
                        text(""),
                        text("(enter) confirm   (esc) cancel").dim())
                        .id(ID)
                        .focusable()
                        .rounded()
                        .width(WIDTH)
                        .length(HEIGHT)
                        .padding(1)
                        .onConfirm(() -> dispatch.accept(new Msg.AmendConfirmed()))
                        .onCancel(() -> dispatch.accept(new Msg.AmendCancelled())));
    }
}
