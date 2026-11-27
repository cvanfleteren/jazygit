package net.vanfleteren.jazygit.features.github.stack;

import io.vavr.control.Option;
import java.util.function.Predicate;
import net.vanfleteren.jazygit.core.gh.model.StackId;
import net.vanfleteren.jazygit.features.github.prs.PrScopes;
import net.vanfleteren.jazygit.features.github.stack.StackCheckout.Landing;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.ui.FeatureAction;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap.Binding;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;

/**
 * Opening and closing the stack whose header is highlighted in the branches pane (ADR 72), and the menu of what can be
 * done with the stack of a branch or with a stack (ADR 73), also of a pull request on the pull requests screen
 * (ADR 99); and showing the stacks or listing their branches as any other branch (ADR 72).
 */
public final class StackAction {

    /**
     * The number of the stack whose header is highlighted in the local branches (ADR 72): the keys of its group there
     * (ADR 167).
     */
    public static final Keymap.Scope<StackId> STACKS = new Keymap.Scope<>("stacks", "github.panel.stacks.title");

    public static final FeatureAction ACTION = new FeatureAction(StackAction::keys, StackDialogs::popups);

    /**
     * The row highlighted in the popup picking the branches of a stack to make (ADR 73): the keys that make it the
     * bottom or the top. Moving, Enter and Escape stay the popup's own.
     */
    static final Keymap.Scope<Integer> PICK = new Keymap.Scope<>("stackPick", "github.dialog.makeStack.keys");

    /**
     * The checkout of a stack the popup asks about (ADR 99): the keys that choose what is checked out after it.
     * Enter and Escape stay the popup's own.
     */
    static final Keymap.Scope<StackCheckout> CHECKOUT =
            new Keymap.Scope<>("stackCheckout", "github.dialog.checkoutStack.keys");

    private StackAction() {}

    /**
     * What the branches pane knows the group of stack {@code number} by, and keeps it closed by (ADR 167): next to
     * the scope its header's keys act on, so the stack's update and the pane's groups both find it here.
     */
    public static String groupKey(StackId number) {
        return "stack:" + number.value();
    }

    private static Keymap keys(Keymap keys) {
        Keymap branches = keys.add(
                        STACKS, Binding.onSelected(Keymap.ENTER, "github.help.stacks.toggle", StackMsg.Toggled::new))
                .add(
                        STACKS,
                        Binding.onSelected(
                                "s",
                                "github.help.stacks.menu",
                                stack -> new StackMsg.MenuRequested(Option.none(), Option.of(stack))))
                .add(STACKS, Binding.always("S", "github.help.stacks.view", StackMsg.ViewToggled::new))
                .add(
                        Scopes.BRANCHES,
                        new Keymap.Binding<>(
                                "s",
                                "github.help.branches.stack",
                                branch -> branch.<Msg>map(
                                        name -> new StackMsg.MenuRequested(Option.of(name), Option.none()))))
                .add(Scopes.BRANCHES, Binding.always("S", "github.help.stacks.view", StackMsg.ViewToggled::new))
                .add(
                        PICK,
                        Binding.onSelected("b", "github.help.stackPick.bottom", StackMsg.BottomChosen::new),
                        Binding.onSelected("t", "github.help.stackPick.top", StackMsg.TopChosen::new))
                .add(
                        CHECKOUT,
                        landing(
                                "t",
                                "github.help.stackCheckout.top",
                                Landing.TOP,
                                checkout -> checkout.top().isDefined()),
                        landing(
                                "h",
                                "github.help.stackCheckout.asked",
                                Landing.ASKED,
                                checkout -> checkout.asked().isDefined()),
                        landing("n", "github.help.stackCheckout.stay", Landing.STAY, checkout -> true));
        return PrScopes.ALL.foldLeft(
                branches,
                (added, scope) -> added.add(
                        scope,
                        Binding.onSelected("s", "github.help.prs.stack", StackMsg.PullRequestMenuRequested::new)));
    }

    /**
     * The key {@code key} checking out a stack that can be, then {@code landing}, when {@code offered} says the
     * checkout offers it.
     */
    private static Binding<StackCheckout> landing(
            String key, String descriptionKey, Landing landing, Predicate<StackCheckout> offered) {
        return new Binding<>(
                key,
                descriptionKey,
                checkout -> checkout.filter(StackCheckout::startable)
                        .filter(offered)
                        .<Msg>map(startable -> new StackMsg.CheckoutConfirmed(landing)));
    }
}
