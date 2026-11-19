package net.vanfleteren.jazygit.features.github.builds;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.kernel.ui.FeatureAction;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap.Binding;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap.Scope;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;

/**
 * Showing the builds of the selected local branch on GitHub in the content panel, in place of its log (ADR 131).
 */
public final class BuildsAction {

    /**
     * The builds of a branch in the content panel, which have nothing selected (ADR 131): whether a search is kept.
     */
    public static final Scope<BuildsAt> SCOPE = new Scope<>("builds", "github.builds.keys");

    public static final FeatureAction ACTION = new FeatureAction(
            keys -> keys.add(
                            Scopes.BRANCHES,
                            Binding.onSelected("B", "github.help.branches.builds", BuildsMsg.Toggled::new))
                    .add(SCOPE, Binding.always("[", "github.help.builds.previous", () -> new BuildsMsg.Stepped(-1)))
                    .add(SCOPE, Binding.always("]", "github.help.builds.next", () -> new BuildsMsg.Stepped(1)))
                    .add(SCOPE, Binding.always("w", "github.help.builds.pick", BuildsMsg.PickRequested::new))
                    .add(SCOPE, Binding.always("a", "github.help.builds.allBranches", BuildsMsg.AllToggled::new))
                    .add(SCOPE, Binding.always("p", "github.help.builds.pickBuild", BuildsMsg.BuildPickRequested::new))
                    .add(SCOPE, Binding.always("m", "github.help.builds.more", BuildsMsg.MoreRequested::new))
                    .add(SCOPE, stepping("n", "github.help.builds.nextBuild", 1))
                    .add(SCOPE, stepping("N", "github.help.builds.previousBuild", -1))
                    .add(SCOPE, Binding.always("c", "github.help.builds.window", BuildsMsg.WindowRequested::new))
                    .add(SCOPE, Binding.always("B", "github.help.builds.close", BuildsMsg.Closed::new)),
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
