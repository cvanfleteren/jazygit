package net.vanfleteren.jazygit.feature.github.builds;

import net.vanfleteren.jazygit.feature.FeatureAction;
import net.vanfleteren.jazygit.keys.Keymap.Binding;
import net.vanfleteren.jazygit.keys.Scopes;

/**
 * Showing the builds of the selected local branch on GitHub in the content panel, in place of its log (ADR 131).
 */
public final class BuildsAction {

    public static final FeatureAction ACTION = new FeatureAction(
            keys -> keys.add(Scopes.BRANCHES, Binding.onSelected("B", "help.branches.builds", BuildsMsg.Toggled::new))
                    .add(Scopes.BUILDS, Binding.always("[", "help.builds.previous", () -> new BuildsMsg.Stepped(-1)))
                    .add(Scopes.BUILDS, Binding.always("]", "help.builds.next", () -> new BuildsMsg.Stepped(1)))
                    .add(Scopes.BUILDS, Binding.always("w", "help.builds.pick", BuildsMsg.PickRequested::new))
                    .add(Scopes.BUILDS, Binding.always("a", "help.builds.allBranches", BuildsMsg.AllToggled::new))
                    .add(Scopes.BUILDS, Binding.always("B", "help.builds.close", BuildsMsg.Closed::new)),
            BuildsDialog::popups);

    private BuildsAction() {}
}
