package net.vanfleteren.jazygit.feature.github.builds;

import net.vanfleteren.jazygit.feature.FeatureAction;
import net.vanfleteren.jazygit.keys.Keymap.Binding;
import net.vanfleteren.jazygit.keys.Scopes;

/**
 * Showing the builds of the selected local branch on GitHub in the content panel, in place of its log (ADR 131).
 */
public final class BuildsAction {

    public static final FeatureAction ACTION = FeatureAction.ofKeys(
            keys -> keys.add(Scopes.BRANCHES, Binding.onSelected("B", "help.branches.builds", BuildsMsg.Toggled::new)));

    private BuildsAction() {}
}
