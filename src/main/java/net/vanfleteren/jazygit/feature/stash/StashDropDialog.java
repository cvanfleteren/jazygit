package net.vanfleteren.jazygit.feature.stash;

import static dev.tamboui.toolkit.Toolkit.*;

import dev.tamboui.style.Overflow;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.widgets.paragraph.Paragraph;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Popup asking to confirm the deletion of a stash.
 * Enter confirms, Escape cancels. Whether it is open lives in the {@link Model}.
 */
public final class StashDropDialog {

    public static final String ID = "stash-drop";

    private static final int WIDTH = 60;

    // The message wrapped at the popup's inner width.
    private static final int MESSAGE_HEIGHT = 2;
    private static final int HEIGHT = MESSAGE_HEIGHT + 2 + 2;

    private final Consumer<Msg> dispatch;

    /**
     * @param dispatch receives the messages for the user's actions, on the render thread
     */
    public StashDropDialog(Consumer<Msg> dispatch) {
        this.dispatch = dispatch;
    }

    /**
     * The popup, while the model asks to confirm deleting a stash.
     */
    public Optional<Element> render(Model model) {
        return model.popup(StashDropPopup.class).map(popup -> dialog(Messages.get("dialog.stashDrop.title"),
                        widget(Paragraph.builder().text(Messages.get("dialog.stashDrop.message")).overflow(Overflow.WRAP_WORD).build())
                                .length(MESSAGE_HEIGHT))
                        .id(ID)
                        .focusable()
                        .rounded()
                        .width(WIDTH)
                        .length(HEIGHT)
                        .padding(1)
                        .onConfirm(() -> dispatch.accept(new StashMsg.DropConfirmed()))
                        .onCancel(() -> dispatch.accept(new StashMsg.DropCancelled())));
    }
}
