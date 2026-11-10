package net.vanfleteren.jazygit.feature.branch.stack;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.FeatureAction;
import net.vanfleteren.jazygit.keys.Keymap;
import net.vanfleteren.jazygit.keys.Keymap.Binding;
import net.vanfleteren.jazygit.keys.Scopes;
import net.vanfleteren.jazygit.state.Msg;

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
                        Scopes.STACKS, Binding.onSelected(Keymap.ENTER, "help.stacks.toggle", StackMsg.Toggled::new))
                .add(
                        Scopes.STACKS,
                        Binding.onSelected(
                                "s",
                                "help.stacks.menu",
                                stack -> new StackMsg.MenuRequested(Option.none(), Option.of(stack))))
                .add(Scopes.STACKS, Binding.always("S", "help.stacks.view", StackMsg.ViewToggled::new))
                .add(
                        Scopes.BRANCHES,
                        new Keymap.Binding<>(
                                "s",
                                "help.branches.stack",
                                branch -> branch.<Msg>map(
                                        name -> new StackMsg.MenuRequested(Option.of(name), Option.none()))))
                .add(Scopes.BRANCHES, Binding.always("S", "help.stacks.view", StackMsg.ViewToggled::new));
        return Scopes.PULL_REQUESTS.foldLeft(
                branches,
                (added, scope) -> added.add(
                        scope, Binding.onSelected("s", "help.prs.stack", StackMsg.PullRequestMenuRequested::new)));
    }
}
