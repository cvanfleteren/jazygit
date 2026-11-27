package net.vanfleteren.jazygit.features.github.builds.screen;

import net.vanfleteren.jazygit.core.gh.model.Build;
import net.vanfleteren.jazygit.kernel.state.preferences.ScreenMsg;
import net.vanfleteren.jazygit.kernel.ui.FeatureAction;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap.Binding;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap.Scope;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;

/**
 * The builds screen (ADR 212): {@code ctrl+b} switches to it, and its keys choose which runs show and act on one.
 */
public final class BuildsScreenAction {

    public static final FeatureAction ACTION = new FeatureAction(BuildsScreenAction::keys, BuildsScreenDialogs::popups);

    private BuildsScreenAction() {}

    private static Keymap keys(Keymap keymap) {
        Keymap global = keymap.global(Keymap.Global.Dispatching.ofCtrl(
                'b', "github.help.global.buildsScreen", () -> new ScreenMsg.Toggled(BuildsScreen.SCREEN)));
        Keymap chosen =
                choosing(choosing(choosing(global, BuildsScopes.WORKFLOWS), BuildsScopes.RUNS), BuildsScopes.CONTENT);
        return acting(acting(chosen, BuildsScopes.RUNS), BuildsScopes.CONTENT);
    }

    /**
     * {@code keymap} with the keys of {@code scope} that choose which runs show, how many the chart shows, and read
     * more.
     */
    private static <S> Keymap choosing(Keymap keymap, Scope<S> scope) {
        return keymap.add(
                scope,
                Binding.always("b", "github.help.buildsScreen.filter", BuildsScreenMsg.FilterRequested::new),
                Binding.always("c", "github.help.buildsScreen.chart", BuildsScreenMsg.WindowRequested::new),
                Binding.always("m", "github.help.buildsScreen.more", BuildsScreenMsg.MoreRequested::new));
    }

    /**
     * {@code keymap} with the keys of {@code scope} that act on its run: open it, run its failed jobs again, and those
     * other features add to a failed build (ADR 161).
     */
    private static Keymap acting(Keymap keymap, Scope<Build> scope) {
        return keymap.add(
                        scope,
                        Binding.onSelected("o", "github.help.buildsScreen.open", BuildsScreenMsg.BrowseRequested::new),
                        new Binding<>(
                                "r",
                                "github.help.buildsScreen.rerun",
                                run -> run.filter(BuildsScreenUpdate::rerunnable)
                                        .map(BuildsScreenMsg.RerunRequested::new)))
                .host(scope, Scopes.FAILED_BUILD);
    }
}
