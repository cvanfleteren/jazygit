package net.vanfleteren.jazygit.features.github.stack;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.features.github.prs.PrScopes;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.ui.FeatureAction;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap.Binding;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;

/**
 * Opening and closing the stack whose header is highlighted in the branches pane (ADR 72), and the menu of what can be
 * done with the stack of a branch or with a stack (ADR 73), also of a pull request on the pull requests screen
 * (ADR 99); and showing the stacks or listing their branches as any other branch (ADR 87).
 */
public final class StackAction {

    /**
     * The number of the stack whose header is highlighted in the local branches (ADR 72): the keys of its group there
     * (ADR 167).
     */
    public static final Keymap.Scope<Integer> STACKS = new Keymap.Scope<>("stacks", "github.panel.stacks.title");

    public static final FeatureAction ACTION = new FeatureAction(StackAction::keys, StackDialogs::popups);

    private StackAction() {}

    /**
     * What the branches pane knows the group of stack {@code number} by, and keeps it closed by (ADR 167): next to
     * the scope its header's keys act on, so the stack's update and the pane's groups both find it here.
     */
    public static String groupKey(int number) {
        return "stack:" + number;
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
                .add(Scopes.BRANCHES, Binding.always("S", "github.help.stacks.view", StackMsg.ViewToggled::new));
        return PrScopes.ALL.foldLeft(
                branches,
                (added, scope) -> added.add(
                        scope,
                        Binding.onSelected("s", "github.help.prs.stack", StackMsg.PullRequestMenuRequested::new)));
    }
}
