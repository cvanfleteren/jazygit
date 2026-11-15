package net.vanfleteren.jazygit.branches.branch.stack;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.ui.FeatureAction;
import net.vanfleteren.jazygit.ui.keys.Keymap;
import net.vanfleteren.jazygit.ui.keys.Keymap.Binding;
import net.vanfleteren.jazygit.ui.keys.Scopes;

/**
 * Opening and closing the stack whose header is highlighted in the branches pane (ADR 72), and the menu of what can be
 * done with the stack of a branch or with a stack (ADR 73), also of a pull request on the pull requests screen
 * (ADR 99); and showing the stacks or listing their branches as any other branch (ADR 87).
 */
public final class StackAction {

    public static final FeatureAction ACTION = new FeatureAction(StackAction::keys, StackDialogs::popups);

    private StackAction() {}

    private static Keymap keys(Keymap keys) {
        Keymap branches = keys.add(
                        Scopes.STACKS,
                        Binding.onSelected(Keymap.ENTER, "branches.help.stacks.toggle", StackMsg.Toggled::new))
                .add(
                        Scopes.STACKS,
                        Binding.onSelected(
                                "s",
                                "branches.help.stacks.menu",
                                stack -> new StackMsg.MenuRequested(Option.none(), Option.of(stack))))
                .add(Scopes.STACKS, Binding.always("S", "branches.help.stacks.view", StackMsg.ViewToggled::new))
                .add(
                        Scopes.BRANCHES,
                        new Keymap.Binding<>(
                                "s",
                                "branches.help.branches.stack",
                                branch -> branch.<Msg>map(
                                        name -> new StackMsg.MenuRequested(Option.of(name), Option.none()))))
                .add(Scopes.BRANCHES, Binding.always("S", "branches.help.stacks.view", StackMsg.ViewToggled::new));
        return Scopes.PULL_REQUESTS.foldLeft(
                branches,
                (added, scope) -> added.add(
                        scope,
                        Binding.onSelected("s", "branches.help.prs.stack", StackMsg.PullRequestMenuRequested::new)));
    }
}
