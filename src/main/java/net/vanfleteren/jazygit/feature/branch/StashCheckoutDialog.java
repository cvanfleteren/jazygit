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
 * Popup shown when checking out a branch while there are uncommitted changes: Enter stashes the
 * changes, checks out and pops the stash, Escape cancels the checkout. Whether it is open lives in
 * the {@link Model}.
 */
public final class StashCheckoutDialog {

    public static final String ID = "stash-checkout";

    private static final int WIDTH = 60;

    // The message wrapped at the popup's inner width.
    private static final int MESSAGE_HEIGHT = 3;
    private static final int HEIGHT = MESSAGE_HEIGHT + 2 + 2;

    private final Consumer<Msg> dispatch;

    /**
     * @param dispatch receives the messages for the user's actions, on the render thread
     */
    public StashCheckoutDialog(Consumer<Msg> dispatch) {
        this.dispatch = dispatch;
    }

    /**
     * The popup, while the model asks whether to stash for a checkout.
     */
    public Optional<Element> render(Model model) {
        return model.popup(StashCheckoutPopup.class).map(StashCheckoutPopup::branch)
                .map(branch -> dialog(Messages.get("dialog.stashCheckout.title", branch),
                        widget(Paragraph.builder().text(Messages.get("dialog.stashCheckout.message"))
                                .overflow(Overflow.WRAP_WORD).build())
                                .length(MESSAGE_HEIGHT))
                        .id(ID)
                        .focusable()
                        .rounded()
                        .width(WIDTH)
                        .length(HEIGHT)
                        .padding(1)
                        .onConfirm(() -> dispatch.accept(new CheckoutMsg.StashConfirmed()))
                        .onCancel(() -> dispatch.accept(new CheckoutMsg.StashCancelled())));
    }
}
