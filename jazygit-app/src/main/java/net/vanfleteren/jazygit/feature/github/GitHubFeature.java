package net.vanfleteren.jazygit.feature.github;

import io.vavr.collection.List;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.feature.FeatureAction;
import net.vanfleteren.jazygit.feature.github.browse.BrowseAction;
import net.vanfleteren.jazygit.feature.github.checkout.CheckoutPullRequestAction;
import net.vanfleteren.jazygit.feature.github.checks.ShowChecksAction;
import net.vanfleteren.jazygit.feature.github.create.CreatePullRequestAction;
import net.vanfleteren.jazygit.feature.github.show.ShowPullRequestAction;
import net.vanfleteren.jazygit.keys.Keymap;
import net.vanfleteren.jazygit.keys.Keymap.Binding;
import net.vanfleteren.jazygit.keys.Scopes;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.prs.PrOverviewMsg;
import net.vanfleteren.jazygit.ui.PopupSlot;

/**
 * The GitHub operations: showing a pull request and what is done from there, showing the checks of a branch,
 * creating a pull request, checking one out, and opening a branch or pull request in the browser. Adding an operation is adding its subpackage, and listing its
 * action here.
 */
public final class GitHubFeature {

    /**
     * Reading the pull requests of the pull requests screen again.
     */
    private static final FeatureAction REFRESH = FeatureAction.ofKeys(keys -> Scopes.PULL_REQUESTS.foldLeft(
            keys,
            (added, scope) -> added.add(scope, Binding.always("r", "help.prs.refresh", PrOverviewMsg.Refresh::new))));

    /**
     * The operations, in the order their keys are listed in a panel.
     */
    private static final List<FeatureAction> ACTIONS = List.of(
            ShowPullRequestAction.ACTION,
            ShowChecksAction.ACTION,
            CheckoutPullRequestAction.ACTION,
            REFRESH,
            CreatePullRequestAction.ACTION,
            BrowseAction.ACTION);

    private GitHubFeature() {}

    public static Keymap register(Keymap keys) {
        return FeatureAction.register(ACTIONS, keys);
    }

    /**
     * The popups of the GitHub operations, which send what the user does to {@code dispatch}.
     */
    public static List<PopupSlot> popups(Consumer<Msg> dispatch) {
        return FeatureAction.popups(ACTIONS, dispatch);
    }
}
