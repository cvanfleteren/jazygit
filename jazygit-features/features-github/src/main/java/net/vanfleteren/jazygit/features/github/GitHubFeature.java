package net.vanfleteren.jazygit.features.github;

import net.vanfleteren.jazygit.features.github.browse.BrowseAction;
import net.vanfleteren.jazygit.features.github.builds.BuildsAction;
import net.vanfleteren.jazygit.features.github.builds.content.BuildsSource;
import net.vanfleteren.jazygit.features.github.checkout.CheckoutPullRequestAction;
import net.vanfleteren.jazygit.features.github.checks.ShowChecksAction;
import net.vanfleteren.jazygit.features.github.create.CreatePullRequestAction;
import net.vanfleteren.jazygit.features.github.review.ReviewAction;
import net.vanfleteren.jazygit.features.github.settings.GitHubSettingsDialogs;
import net.vanfleteren.jazygit.features.github.show.ShowPullRequestAction;
import net.vanfleteren.jazygit.features.github.verdict.VerdictAction;
import net.vanfleteren.jazygit.kernel.state.github.prs.PrOverviewMsg;
import net.vanfleteren.jazygit.kernel.ui.Feature;
import net.vanfleteren.jazygit.kernel.ui.FeatureAction;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap.Binding;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;

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
            (added, scope) ->
                    added.add(scope, Binding.always("r", "github.help.prs.refresh", PrOverviewMsg.Refresh::new))));

    /**
     * The operations, in the order their keys are listed in a panel, the builds of a branch in the content panel
     * (ADR 131), and how gh runs in the settings panel (ADR 163).
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
                    VerdictAction.ACTION,
                    FeatureAction.ofPopups(GitHubSettingsDialogs::popups))
            .showing(new BuildsSource())
            .withSettings(GitHubSettingsDialogs.SECTION);

    private GitHubFeature() {}
}
