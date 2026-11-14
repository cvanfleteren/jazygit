package net.vanfleteren.jazygit.feature.github;

import net.vanfleteren.jazygit.feature.Feature;
import net.vanfleteren.jazygit.feature.FeatureAction;
import net.vanfleteren.jazygit.feature.github.browse.BrowseAction;
import net.vanfleteren.jazygit.feature.github.checkout.CheckoutPullRequestAction;
import net.vanfleteren.jazygit.feature.github.checks.ShowChecksAction;
import net.vanfleteren.jazygit.feature.github.create.CreatePullRequestAction;
import net.vanfleteren.jazygit.feature.github.review.ReviewAction;
import net.vanfleteren.jazygit.feature.github.show.ShowPullRequestAction;
import net.vanfleteren.jazygit.feature.github.verdict.VerdictAction;
import net.vanfleteren.jazygit.keys.Keymap.Binding;
import net.vanfleteren.jazygit.keys.Scopes;
import net.vanfleteren.jazygit.state.github.prs.PrOverviewMsg;

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
    public static final Feature FEATURE = Feature.of(
            ShowPullRequestAction.ACTION,
            ShowChecksAction.ACTION,
            CheckoutPullRequestAction.ACTION,
            REFRESH,
            CreatePullRequestAction.ACTION,
            BrowseAction.ACTION,
            ReviewAction.ACTION,
            VerdictAction.ACTION);

    private GitHubFeature() {}
}
