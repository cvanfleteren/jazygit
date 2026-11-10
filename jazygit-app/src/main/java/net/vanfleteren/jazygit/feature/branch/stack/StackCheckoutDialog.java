package net.vanfleteren.jazygit.feature.branch.stack;

import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.feature.branch.stack.StackCheckout.Landing;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.ui.widgets.DialogFrame;
import net.vanfleteren.jazygit.ui.widgets.PopupView;

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

    StackCheckoutDialog(Consumer<Msg> dispatch) {
        this.dispatch = dispatch;
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
                            Messages.get("dialog.checkoutStack.title", String.valueOf(checkout.stack())),
                            WIDTH,
                            checkout.startable() ? "dialog.checkoutStack.hint" : "dialog.hint.close",
                            List.of(DialogFrame.wrapped(text, WIDTH, Integer.MAX_VALUE)))
                    .id(ID)
                    .focusable()
                    .length(DialogFrame.wrappedHeight(text, WIDTH, Integer.MAX_VALUE) + EXTRA_HEIGHT)
                    .onKeyEvent(event -> keys(event, checkout));
        });
    }

    private EventResult keys(KeyEvent event, StackCheckout checkout) {
        if (event.code() == KeyCode.ESCAPE || (!checkout.startable() && event.code() == KeyCode.ENTER)) {
            dispatch.accept(new Msg.PopupDismissed(StackPopups.Checkout.class));
            return EventResult.HANDLED;
        }
        Option<Landing> landing = landing(event, checkout);
        if (landing.isEmpty()) {
            return EventResult.UNHANDLED;
        }
        dispatch.accept(new StackMsg.CheckoutConfirmed(landing.get()));
        return EventResult.HANDLED;
    }

    private static Option<Landing> landing(KeyEvent event, StackCheckout checkout) {
        if (!checkout.startable()) {
            return Option.none();
        }
        if (event.code() == KeyCode.ENTER) {
            return Option.of(checkout.preferred());
        }
        if (event.isChar('t')) {
            return Option.of(Landing.TOP).filter(top -> checkout.top().isDefined());
        }
        if (event.isChar('h')) {
            return Option.of(Landing.ASKED).filter(asked -> checkout.asked().isDefined());
        }
        return Option.when(event.isChar('n'), Landing.STAY);
    }
}
