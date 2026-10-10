package net.vanfleteren.jazygit.feature.branch;

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
 * Popup warning that a branch has diverged from its remote branch, and asking to force push it.
 * Enter confirms, Escape cancels. Whether it is open lives in the {@link Model}.
 */
public final class ForcePushDialog {

    public static final String ID = "force-push";

    private static final int WIDTH = 60;

    // The message wrapped at the popup's inner width.
    private static final int MESSAGE_HEIGHT = 3;
    private static final int HEIGHT = MESSAGE_HEIGHT + 2 + 2;

    private final Consumer<Msg> dispatch;

    /**
     * @param dispatch receives the messages for the user's actions, on the render thread
     */
    public ForcePushDialog(Consumer<Msg> dispatch) {
        this.dispatch = dispatch;
    }

    /**
     * The popup, while the model asks to confirm a force push.
     */
    public Optional<Element> render(Model model) {
        return model.forcePushTarget()
                .map(branch -> dialog(Messages.get("dialog.forcePush.title", branch),
                        widget(Paragraph.builder().text(Messages.get("dialog.forcePush.message")).overflow(Overflow.WRAP_WORD).build())
                                .length(MESSAGE_HEIGHT))
                        .id(ID)
                        .focusable()
                        .rounded()
                        .width(WIDTH)
                        .length(HEIGHT)
                        .padding(1)
                        .onConfirm(() -> dispatch.accept(new PushMsg.ForceConfirmed()))
                        .onCancel(() -> dispatch.accept(new PushMsg.ForceCancelled())));
    }
}
