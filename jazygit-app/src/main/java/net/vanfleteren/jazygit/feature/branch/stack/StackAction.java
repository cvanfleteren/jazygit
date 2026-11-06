package net.vanfleteren.jazygit.feature.branch.stack;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.FeatureAction;
import net.vanfleteren.jazygit.keys.Keymap;
import net.vanfleteren.jazygit.keys.Keymap.Binding;
import net.vanfleteren.jazygit.keys.Scopes;
import net.vanfleteren.jazygit.state.Msg;

/**
 * Opening and closing the stack whose header is highlighted in the branches pane (ADR 72), and the menu of what can be
 * done with the stack of a branch or with a stack (ADR 73); and showing the stacks or listing their branches as any other
 * branch (ADR 87).
 */
public final class StackAction {

    public static final FeatureAction ACTION = new FeatureAction(
            keys -> keys.add(
                            Scopes.STACKS,
                            Binding.onSelected(Keymap.ENTER, "help.stacks.toggle", StackMsg.Toggled::new))
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
                    .add(Scopes.BRANCHES, Binding.always("S", "help.stacks.view", StackMsg.ViewToggled::new)),
            StackDialogs::popups);

    private StackAction() {}
}
