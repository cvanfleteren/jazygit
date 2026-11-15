package net.vanfleteren.jazygit.github;

import net.vanfleteren.jazygit.github.browse.BrowseAction;
import net.vanfleteren.jazygit.github.builds.BuildsAction;
import net.vanfleteren.jazygit.github.checkout.CheckoutPullRequestAction;
import net.vanfleteren.jazygit.github.checks.ShowChecksAction;
import net.vanfleteren.jazygit.github.create.CreatePullRequestAction;
import net.vanfleteren.jazygit.github.review.ReviewAction;
import net.vanfleteren.jazygit.github.show.ShowPullRequestAction;
import net.vanfleteren.jazygit.github.verdict.VerdictAction;
import net.vanfleteren.jazygit.state.github.prs.PrOverviewMsg;
import net.vanfleteren.jazygit.ui.Feature;
import net.vanfleteren.jazygit.ui.FeatureAction;
import net.vanfleteren.jazygit.ui.keys.Keymap.Binding;
import net.vanfleteren.jazygit.ui.keys.Scopes;

/**
 * The GitHub operations: showing a pull request and what is done from there, showing the checks and the builds of a branch,
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
            BuildsAction.ACTION,
            CheckoutPullRequestAction.ACTION,
            REFRESH,
            CreatePullRequestAction.ACTION,
            BrowseAction.ACTION,
            ReviewAction.ACTION,
            VerdictAction.ACTION);

    private GitHubFeature() {}
}
