package net.vanfleteren.jazygit.features.github;

import java.time.Duration;
import net.vanfleteren.jazygit.core.gh.timing.CheckTimings;
import net.vanfleteren.jazygit.features.github.badges.GitHubBadge;
import net.vanfleteren.jazygit.features.github.browse.BrowseAction;
import net.vanfleteren.jazygit.features.github.builds.BuildsAction;
import net.vanfleteren.jazygit.features.github.builds.content.BuildsSource;
import net.vanfleteren.jazygit.features.github.checkout.CheckoutPullRequestAction;
import net.vanfleteren.jazygit.features.github.checks.ChecksMsg;
import net.vanfleteren.jazygit.features.github.checks.ShowChecksAction;
import net.vanfleteren.jazygit.features.github.create.CreatePullRequestAction;
import net.vanfleteren.jazygit.features.github.review.ReviewAction;
import net.vanfleteren.jazygit.features.github.settings.GitHubSettings;
import net.vanfleteren.jazygit.features.github.settings.GitHubSettingsDialogs;
import net.vanfleteren.jazygit.features.github.show.PullRequestMsg;
import net.vanfleteren.jazygit.features.github.show.ShowPullRequestAction;
import net.vanfleteren.jazygit.features.github.stack.StackAction;
import net.vanfleteren.jazygit.features.github.stack.StackMsg;
import net.vanfleteren.jazygit.features.github.verdict.VerdictAction;
import net.vanfleteren.jazygit.kernel.state.github.prs.PrOverviewMsg;
import net.vanfleteren.jazygit.kernel.ui.Feature;
import net.vanfleteren.jazygit.kernel.ui.FeatureAction;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap.Binding;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;

/**
 * The GitHub operations: showing a pull request and what is done from there, showing the checks and the builds of a branch,
 * creating a pull request, checking one out, the stacks of pull requests and what is done with them (ADR 72, 73, 87, 99),
 * and opening a branch or pull request in the browser. Adding an operation is adding its subpackage, and listing its
 * action here.
 */
public final class GitHubFeature {

    /**
     * How often the pull requests screen reads what GitHub has again.
     */
    private static final Duration OVERVIEW_INTERVAL = Duration.ofMinutes(1);

    /**
     * How often GitHub is asked how the merges it does in the background are going (ADR 24).
     */
    private static final Duration MERGE_CHECK_INTERVAL = Duration.ofSeconds(2);

    /**
     * How often the pull requests are read again while they do not show a change made to the stacks yet (ADR 73).
     */
    private static final Duration STACK_CHECK_INTERVAL = Duration.ofSeconds(2);

    /**
     * Reading the pull requests of the pull requests screen again.
     */
    private static final FeatureAction REFRESH = FeatureAction.ofKeys(keys -> Scopes.PULL_REQUESTS.foldLeft(
            keys,
            (added, scope) ->
                    added.add(scope, Binding.always("r", "github.help.prs.refresh", PrOverviewMsg.Refresh::new))));

    /**
     * The shortcut that switches between the repository and the pull requests, wherever the focus is.
     */
    private static final FeatureAction SCREEN = FeatureAction.ofKeys(keys -> keys.global(
            Keymap.Global.Dispatching.ofCtrl('p', "github.help.global.screen", PrOverviewMsg.Toggled::new)));

    /**
     * The operations, in the order their keys are listed in a panel, the builds of a branch in the content panel
     * (ADR 131), and how gh runs in the settings panel (ADR 163). It reads from GitHub as the timers say, and as what
     * happens in the kernel makes it do, and shows how each branch is doing on GitHub in the branches pane (ADR 167).
     */
    public static final Feature FEATURE = Feature.of(
                    ShowPullRequestAction.ACTION,
                    ShowChecksAction.ACTION,
                    BuildsAction.ACTION,
                    CheckoutPullRequestAction.ACTION,
                    REFRESH,
                    SCREEN,
                    CreatePullRequestAction.ACTION,
                    StackAction.ACTION,
                    BrowseAction.ACTION,
                    ReviewAction.ACTION,
                    VerdictAction.ACTION,
                    FeatureAction.ofPopups(GitHubSettingsDialogs::popups))
            .showing(new BuildsSource())
            .keeping(GitHubSettings.GH_EXECUTABLE, GitHubSettings.BROWSERS)
            .withSettings(GitHubSettingsDialogs.SECTION)
            .every(OVERVIEW_INTERVAL, PrOverviewMsg.Tick::new)
            // The dialogs that list checks read them only while one is not done, or once a minute (ADR 34).
            .every(CheckTimings.READ_WHILE_PENDING, PullRequestMsg.Tick::new)
            .every(CheckTimings.READ_WHILE_PENDING, ChecksMsg.Tick::new)
            .every(MERGE_CHECK_INTERVAL, PullRequestMsg.MergeTick::new)
            .every(STACK_CHECK_INTERVAL, StackMsg.Tick::new)
            .reacting(GitHubReactions::to)
            .badging(GitHubBadge::of);

    private GitHubFeature() {}
}
