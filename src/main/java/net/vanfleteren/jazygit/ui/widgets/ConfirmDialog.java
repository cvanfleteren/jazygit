package net.vanfleteren.jazygit.ui.widgets;

import static dev.tamboui.toolkit.Toolkit.*;

import dev.tamboui.style.Overflow;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.widgets.paragraph.Paragraph;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Popup;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The standard popup asking to confirm an action: a title, a message, and always the same hint that Enter
 * confirms and Escape cancels. Whether it is open lives in the {@link Model}, as a popup of type {@code P}.
 *
 * @param <P> the popup that makes this dialog show
 */
public final class ConfirmDialog<P extends Popup> {

    private static final int WIDTH = 60;
    // The popup's border and padding on both sides.
    private static final int INNER_WIDTH = WIDTH - 2 - 2;
    // The blank line and the hint below the message, plus the top and bottom border.
    private static final int EXTRA_HEIGHT = 2 + 2;

    private final String id;
    private final Class<P> popupType;
    private final Function<P, String> title;
    private final Function<P, String> message;
    private final Supplier<Msg> onConfirm;
    private final Supplier<Msg> onCancel;
    private final Consumer<Msg> dispatch;

    /**
     * @param id        the id of the dialog, which takes the focus while it is open
     * @param popupType the popup that makes the dialog show
     * @param title     the title, from the popup
     * @param message   the question, from the popup; it is wrapped at the dialog's width
     * @param onConfirm the message for Enter
     * @param onCancel  the message for Escape
     * @param dispatch  receives the messages for the user's actions, on the render thread
     */
    public ConfirmDialog(String id, Class<P> popupType, Function<P, String> title, Function<P, String> message,
                         Supplier<Msg> onConfirm, Supplier<Msg> onCancel, Consumer<Msg> dispatch) {
        this.id = id;
        this.popupType = popupType;
        this.title = title;
        this.message = message;
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;
        this.dispatch = dispatch;
    }

    public String id() {
        return id;
    }

    /**
     * The popup, while the model has {@code P} open.
     */
    public Optional<Element> render(Model model) {
        return model.popup(popupType).map(popup -> {
            String text = message.apply(popup);
            int messageHeight = lines(text, INNER_WIDTH);
            return dialog(title.apply(popup),
                    widget(Paragraph.builder().text(text).overflow(Overflow.WRAP_WORD).build()).length(messageHeight),
                    text(""),
                    text(Messages.get("dialog.hint.confirmCancel")).dim())
                    .id(id)
                    .focusable()
                    .rounded()
                    .width(WIDTH)
                    .length(messageHeight + EXTRA_HEIGHT)
                    .padding(1)
                    .onConfirm(() -> dispatch.accept(onConfirm.get()))
                    .onCancel(() -> dispatch.accept(onCancel.get()));
        });
    }

    /**
     * How many lines {@code text} takes when wrapped at {@code width} on spaces.
     */
    static int lines(String text, int width) {
        int lines = 1;
        int used = 0;
        for (String word : text.split(" ")) {
            if (used > 0 && used + 1 + word.length() > width) {
                lines++;
                used = word.length();
            } else {
                used += (used > 0 ? 1 : 0) + word.length();
            }
        }
        return lines;
    }
}
