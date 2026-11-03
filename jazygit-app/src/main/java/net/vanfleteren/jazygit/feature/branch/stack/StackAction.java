package net.vanfleteren.jazygit.feature.branch.stack;

import net.vanfleteren.jazygit.feature.FeatureAction;
import net.vanfleteren.jazygit.keys.Keymap;
import net.vanfleteren.jazygit.keys.Keymap.Binding;
import net.vanfleteren.jazygit.keys.Scopes;

/**
 * Opening and closing the stack whose header is highlighted in the branches pane (ADR 72).
 */
public final class StackAction {

    public static final FeatureAction ACTION = FeatureAction.ofKeys(keys ->
            keys.add(Scopes.STACKS, Binding.onSelected(Keymap.ENTER, "help.stacks.toggle", StackMsg.Toggled::new)));

    private StackAction() {}
}
