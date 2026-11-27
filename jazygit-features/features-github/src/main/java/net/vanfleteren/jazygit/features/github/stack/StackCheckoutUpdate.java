package net.vanfleteren.jazygit.features.github.stack;

import static net.vanfleteren.jazygit.features.github.stack.StackMenuUpdate.chosen;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.features.branches.branch.checkout.CheckoutMsg;
import net.vanfleteren.jazygit.features.branches.branch.checkout.CheckoutRequest;
import net.vanfleteren.jazygit.features.branches.branch.checkout.CheckoutUpdate;
import net.vanfleteren.jazygit.features.github.stack.StackMsg.CaughtUp;
import net.vanfleteren.jazygit.features.github.stack.StackMsg.CheckoutChosen;
import net.vanfleteren.jazygit.features.github.stack.StackMsg.CheckoutConfirmed;
import net.vanfleteren.jazygit.features.github.stack.StackMsg.StackFetched;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.operation.OperationUpdate;

/**
 * Checking out a stack from its menu, or updating its branches (ADR 99): its branches are fetched, what checking them
 * out does is asked about, then they are made and fast-forwarded.
 */
final class StackCheckoutUpdate {

    private StackCheckoutUpdate() {}

    static Next update(Model model, StackMsg.CheckingOut msg) {
        return switch (msg) {
            case CheckoutChosen() -> checkoutChosen(model);
            case StackFetched(var cmd, var commands, StackCheckout checkout) -> fetched(model, cmd, commands, checkout);
            case CheckoutConfirmed(StackCheckout.Landing landing) -> checkoutConfirmed(model, landing);
            case CaughtUp(var cmd, var commands) -> caughtUp(model, cmd, commands);
        };
    }

    /**
     * Fetches the branches of the stack of the menu, to check it out, where that applies.
     */
    private static Next checkoutChosen(Model model) {
        return chosen(
                model,
                menu -> menu.canCheckOut()
                        ? OperationUpdate.start(
                                model.withoutPopup(StackPopups.Menu.class).clearError(),
                                new StackCheckoutCmd.Fetch(
                                        menu.stack().get(),
                                        StackCheckout.remote(model, menu.stack().get()),
                                        menu.on()))
                        : Next.of(model));
    }

    /**
     * Asked about once fetched; not in the place of a dialog opened meanwhile.
     */
    private static Next fetched(
            Model model, StackCheckoutCmd.Fetch cmd, List<ExecutedCommand> commands, StackCheckout checkout) {
        return OperationUpdate.succeeded(model, cmd, commands)
                .map(done -> done.openPopupIfFree(new StackPopups.Checkout(checkout)));
    }

    /**
     * Checks out the stack asked about, landing where {@code landing} says, if it can land there.
     */
    private static Next checkoutConfirmed(Model model, StackCheckout.Landing landing) {
        return model.popup(StackPopups.Checkout.class)
                .map(StackPopups.Checkout::checkout)
                .filter(StackCheckout::startable)
                .filter(checkout -> landing == StackCheckout.Landing.STAY
                        || checkout.target(landing).isDefined())
                .map(checkout -> OperationUpdate.start(
                        model.withoutPopup(StackPopups.Checkout.class).clearError(),
                        new StackCheckoutCmd.CatchUp(checkout, landing)))
                .getOrElse(() -> Next.of(model));
    }

    /**
     * Once the branches of a stack are made and fast-forwarded: says what was done, then checks out the branch the
     * user chose, as any checkout of a local branch goes (ADR 68), where the user chose one. One the model knew of
     * before goes as asked for from the branches pane, which switches to the worktree that has it checked out, if
     * another has.
     */
    private static Next caughtUp(Model model, StackCheckoutCmd.CatchUp cmd, List<ExecutedCommand> commands) {
        StackCheckout checkout = cmd.checkout();
        return OperationUpdate.succeeded(model, cmd, commands)
                .then(done -> checkout.target(cmd.landing())
                        .map(branch -> model.repo().branch(branch).isDefined()
                                ? CheckoutUpdate.update(done, new CheckoutMsg.Requested(branch))
                                : CheckoutUpdate.place(done, new CheckoutRequest.Local(branch)))
                        .getOrElse(() -> Next.of(done)))
                // Said once the checkout is started, which clears what was said; not in the place of why it was
                // refused.
                .map(switched -> switched.outcome().error().isDefined()
                        ? switched
                        : switched.inform(new StackNotice.CheckedOut(
                                checkout.stack(),
                                checkout.count(StackCheckout.Kind.MADE),
                                checkout.count(StackCheckout.Kind.FAST_FORWARDED),
                                checkout.kept())));
    }
}
