package net.vanfleteren.jazygit.features.github.stack;

import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.KeyEvent;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.popup.PopupMsg;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap;
import net.vanfleteren.jazygit.kernel.ui.widgets.DialogFrame;
import net.vanfleteren.jazygit.kernel.ui.widgets.PopupView;

/**
 * Asks before checking out a stack (ADR 99): what happens to the branch of each of its open pull requests, then which
 * of them is checked out. {@code t} checks out the top, {@code h} the pull request it was asked for on, when it was,
 * {@code n} none, and Enter the first of those offered; Escape cancels. A stack that cannot be checked out only closes.
 */
final class StackCheckoutDialog implements PopupView {

    static final String ID = "stack-checkout";

    private static final int WIDTH = 76;
    // The blank line and the hint below the message, plus the top and bottom border.
    private static final int EXTRA_HEIGHT = 2 + 2;

    private final Consumer<Msg> dispatch;

    private final Supplier<Keymap> keymap;

    StackCheckoutDialog(Consumer<Msg> dispatch) {
        this(dispatch, Keymap::standard);
    }

    /**
     * @param keymap the keys, asked for when a key is pressed, as the app makes it of every feature
     */
    StackCheckoutDialog(Consumer<Msg> dispatch, Supplier<Keymap> keymap) {
        this.dispatch = dispatch;
        this.keymap = keymap;
    }

    @Override
    public List<String> ids() {
        return List.of(ID);
    }

    @Override
    public Option<Element> render(Model model) {
        return model.popup(StackPopups.Checkout.class).map(popup -> {
            StackCheckout checkout = popup.checkout();
            String text = StackDialogs.checkedOut(checkout);
            return DialogFrame.withHint(
                            Messages.get(
                                    "github.dialog.checkoutStack.title",
                                    String.valueOf(checkout.stack().value())),
                            WIDTH,
                            checkout.startable() ? "github.dialog.checkoutStack.hint" : "changes.dialog.hint.close",
                            List.of(DialogFrame.wrapped(text, WIDTH, Integer.MAX_VALUE)))
                    .id(ID)
                    .focusable()
                    .length(DialogFrame.wrappedHeight(text, WIDTH, Integer.MAX_VALUE) + EXTRA_HEIGHT)
                    .onKeyEvent(event -> keys(event, checkout));
        });
    }

    private EventResult keys(KeyEvent event, StackCheckout checkout) {
        Option<Msg> msg = message(event, checkout);
        msg.forEach(dispatch);
        return msg.isDefined() ? EventResult.HANDLED : EventResult.UNHANDLED;
    }

    /**
     * What {@code event} asks of the app: Escape cancels, Enter checks out the stack and the first of what is offered
     * after it, or closes the popup when it cannot be checked out, and the keys of {@link StackAction#CHECKOUT}
     * choose what else is checked out after it.
     */
    private Option<Msg> message(KeyEvent event, StackCheckout checkout) {
        if (event.isCancel() || (!checkout.startable() && event.isConfirm())) {
            return Option.of(new PopupMsg.Dismissed(StackPopups.Checkout.class));
        }
        if (event.isConfirm()) {
            return Option.of(new StackMsg.CheckoutConfirmed(checkout.preferred()));
        }
        return keymap.get().handle(StackAction.CHECKOUT, event, Option.of(checkout));
    }
}
