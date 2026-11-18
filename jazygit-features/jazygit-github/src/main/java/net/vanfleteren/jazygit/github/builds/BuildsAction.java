package net.vanfleteren.jazygit.github.builds;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.state.github.builds.BuildsAt;
import net.vanfleteren.jazygit.ui.FeatureAction;
import net.vanfleteren.jazygit.ui.keys.Keymap.Binding;
import net.vanfleteren.jazygit.ui.keys.Scopes;

/**
 * Showing the builds of the selected local branch on GitHub in the content panel, in place of its log (ADR 131).
 */
public final class BuildsAction {

    public static final FeatureAction ACTION = new FeatureAction(
            keys -> keys.add(
                            Scopes.BRANCHES,
                            Binding.onSelected("B", "github.help.branches.builds", BuildsMsg.Toggled::new))
                    .add(
                            Scopes.BUILDS,
                            Binding.always("[", "github.help.builds.previous", () -> new BuildsMsg.Stepped(-1)))
                    .add(Scopes.BUILDS, Binding.always("]", "github.help.builds.next", () -> new BuildsMsg.Stepped(1)))
                    .add(Scopes.BUILDS, Binding.always("w", "github.help.builds.pick", BuildsMsg.PickRequested::new))
                    .add(
                            Scopes.BUILDS,
                            Binding.always("a", "github.help.builds.allBranches", BuildsMsg.AllToggled::new))
                    .add(
                            Scopes.BUILDS,
                            Binding.always("p", "github.help.builds.pickBuild", BuildsMsg.BuildPickRequested::new))
                    .add(Scopes.BUILDS, Binding.always("m", "github.help.builds.more", BuildsMsg.MoreRequested::new))
                    .add(Scopes.BUILDS, stepping("n", "github.help.builds.nextBuild", 1))
                    .add(Scopes.BUILDS, stepping("N", "github.help.builds.previousBuild", -1))
                    .add(
                            Scopes.BUILDS,
                            Binding.always("c", "github.help.builds.window", BuildsMsg.WindowRequested::new))
                    .add(Scopes.BUILDS, Binding.always("B", "github.help.builds.close", BuildsMsg.Closed::new)),
            BuildsDialog::popups);

    private BuildsAction() {}

    /**
     * {@code key}, which picks the build {@code by} rows after the one on the time line (ADR 133); not while a search
     * is kept, when the area takes the key to its matches (ADR 126), so the help does not list it as acting then.
     */
    private static Binding<BuildsAt> stepping(String key, String descriptionKey, int by) {
        return new Binding<>(
                key,
                descriptionKey,
                at -> Option.when(!at.exists(BuildsAt::searching), () -> new BuildsMsg.BuildStepped(by)));
    }
}
