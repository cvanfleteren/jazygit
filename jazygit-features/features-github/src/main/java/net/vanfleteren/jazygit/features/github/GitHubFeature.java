package net.vanfleteren.jazygit.features.github;

import io.vavr.control.Option;
import java.time.Duration;
import net.vanfleteren.jazygit.core.gh.timing.CheckTimings;
import net.vanfleteren.jazygit.features.forge.checks.ChecksMsg;
import net.vanfleteren.jazygit.features.github.badges.BadgeLegend;
import net.vanfleteren.jazygit.features.github.badges.GitHubBadge;
import net.vanfleteren.jazygit.features.github.browse.BrowseAction;
import net.vanfleteren.jazygit.features.github.builds.content.BuildsScreenSource;
import net.vanfleteren.jazygit.features.github.builds.screen.BuildsScreen;
import net.vanfleteren.jazygit.features.github.builds.screen.BuildsScreenAction;
import net.vanfleteren.jazygit.features.github.builds.screen.BuildsScreenMsg;
import net.vanfleteren.jazygit.features.github.builds.screen.BuildsScreenUpdate;
import net.vanfleteren.jazygit.features.github.checkout.CheckoutPullRequestAction;
import net.vanfleteren.jazygit.features.github.checks.ChecksDialog;
import net.vanfleteren.jazygit.features.github.checks.GitHubChecksPort;
import net.vanfleteren.jazygit.features.github.checks.ShowChecksAction;
import net.vanfleteren.jazygit.features.github.comments.CommentsDialog;
import net.vanfleteren.jazygit.features.github.create.GitHubCreatePullRequestAction;
import net.vanfleteren.jazygit.features.github.dialog.PullRequestDialog;
import net.vanfleteren.jazygit.features.github.dialog.ShowPullRequestAction;
import net.vanfleteren.jazygit.features.github.panes.BuildsRunsPanel;
import net.vanfleteren.jazygit.features.github.panes.BuildsScreenSpec;
import net.vanfleteren.jazygit.features.github.panes.BuildsWorkflowsPanel;
import net.vanfleteren.jazygit.features.github.panes.PullRequestsPanel;
import net.vanfleteren.jazygit.features.github.panes.PullRequestsScreen;
import net.vanfleteren.jazygit.features.github.panes.StackGroups;
import net.vanfleteren.jazygit.features.github.prs.PrOverview;
import net.vanfleteren.jazygit.features.github.prs.PrOverviewMsg;
import net.vanfleteren.jazygit.features.github.prs.PrOverviewUpdate;
import net.vanfleteren.jazygit.features.github.prs.PrScopes;
import net.vanfleteren.jazygit.features.github.prs.content.PullRequestSource;
import net.vanfleteren.jazygit.features.github.remarks.RemarksState;
import net.vanfleteren.jazygit.features.github.review.ReviewAction;
import net.vanfleteren.jazygit.features.github.settings.GitHubSettings;
import net.vanfleteren.jazygit.features.github.settings.GitHubSettingsDialogs;
import net.vanfleteren.jazygit.features.github.show.PullRequestMsg;
import net.vanfleteren.jazygit.features.github.stack.StackAction;
import net.vanfleteren.jazygit.features.github.stack.StackMsg;
import net.vanfleteren.jazygit.features.github.verdict.VerdictAction;
import net.vanfleteren.jazygit.kernel.state.FeatureId;
import net.vanfleteren.jazygit.kernel.state.preferences.ScreenMsg;
import net.vanfleteren.jazygit.kernel.ui.BranchBadges;
import net.vanfleteren.jazygit.kernel.ui.BranchGroups;
import net.vanfleteren.jazygit.kernel.ui.Feature;
import net.vanfleteren.jazygit.kernel.ui.FeatureAction;
import net.vanfleteren.jazygit.kernel.ui.KernelSlots;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap.Binding;
import net.vanfleteren.jazygit.kernel.ui.keys.Legend;

/**
 * The GitHub operations: showing a pull request and what is done from there, showing the checks and the builds of a branch,
 * creating a pull request, checking one out, the stacks of pull requests and what is done with them (ADR 72, 73, 99),
 * and opening a branch or pull request in the browser. Adding an operation is adding its subpackage, and listing its
 * action here.
 */
public final class GitHubFeature {

    /**
     * The id of the feature, by which it is on or off for a repository (ADR 171).
     */
    public static final FeatureId ID = GitHubId.ID;

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
    private static final FeatureAction REFRESH = FeatureAction.ofKeys(keys -> PrScopes.ALL.foldLeft(
            keys,
            (added, scope) ->
                    added.add(scope, Binding.always("r", "github.help.prs.refresh", PrOverviewMsg.Refresh::new))));

    /**
     * The shortcut that switches between the repository and the pull requests, wherever the focus is.
     */
    private static final FeatureAction SCREEN =
            FeatureAction.ofKeys(keys -> keys.global(Keymap.Global.Dispatching.ofCtrl(
                    'p', "github.help.global.screen", () -> new ScreenMsg.Toggled(PrOverview.SCREEN))));

    /**
     * The operations, in the order their keys are listed in a panel, and how gh runs in the settings panel (ADR
     * 163). It reads from GitHub as the timers say, and as what
     * happens in the kernel makes it do, shows how each branch is doing on GitHub in the branches pane (ADR 167), with
     * what that means in its help, and brings the screens of the pull requests (ADR 168) and of the builds (ADR 212).
     */
    public static final Feature FEATURE = Feature.of(
                    ShowPullRequestAction.ACTION,
                    ShowChecksAction.ACTION,
                    BuildsScreenAction.ACTION,
                    CheckoutPullRequestAction.ACTION,
                    REFRESH,
                    SCREEN,
                    GitHubCreatePullRequestAction.ACTION,
                    StackAction.ACTION,
                    BrowseAction.ACTION,
                    ReviewAction.ACTION,
                    VerdictAction.ACTION,
                    FeatureAction.ofPopups(GitHubSettingsDialogs::popups))
            .showing(new PullRequestSource(), new BuildsScreenSource())
            .keeping(
                    GitHubSettings.GH_EXECUTABLE,
                    GitHubSettings.BROWSERS,
                    PrOverview.WIDTH,
                    PrOverview.HEIGHTS,
                    BuildsScreen.WIDTH,
                    BuildsScreen.HEIGHTS)
            .withSettings(GitHubSettingsDialogs.SECTION)
            .every(OVERVIEW_INTERVAL, PrOverviewMsg.Tick::new)
            .every(OVERVIEW_INTERVAL, BuildsScreenMsg.Tick::new)
            // The builds screen reads its newest runs again while one of them runs, as the checks are (ADR 34, 212).
            .every(CheckTimings.READ_WHILE_PENDING, BuildsScreenMsg.RunningTick::new)
            // The dialogs that list checks read them only while one is not done, or once a minute (ADR 34).
            .every(CheckTimings.READ_WHILE_PENDING, PullRequestMsg.Tick::new)
            .every(CheckTimings.READ_WHILE_PENDING, () -> new ChecksMsg.Tick(GitHubChecksPort.PORT))
            .every(MERGE_CHECK_INTERVAL, PullRequestMsg.MergeTick::new)
            .every(STACK_CHECK_INTERVAL, StackMsg.Tick::new)
            .reacting(GitHubReactions.REACTION)
            .reacting(PrOverviewUpdate::to)
            .reacting(BuildsScreenUpdate::to)
            .filling(BranchBadges.KEY, model -> Option.some(GitHubBadge::of))
            .filling(BranchGroups.KEY, model -> Option.some(StackGroups::of))
            .screen(PullRequestsScreen.SPEC)
            .pane(PrOverview.SCREEN, PullRequestsPanel::repository)
            .pane(PrOverview.SCREEN, PullRequestsPanel::reviewRequested)
            .pane(PrOverview.SCREEN, PullRequestsPanel::authored)
            .screen(BuildsScreenSpec.SPEC)
            .pane(BuildsScreen.SCREEN, BuildsWorkflowsPanel::of)
            .pane(BuildsScreen.SCREEN, BuildsRunsPanel::of)
            .explaining(BadgeLegend.BRANCHES.toJavaArray(Legend[]::new))
            .running(GitHubServices::running)
            // Its dialogs that read what GitHub has, and the times they show (ADR 124).
            .filling(KernelSlots.SPINNING, model -> Option.when(ChecksDialog.showsSpinner(model), true))
            .filling(KernelSlots.SPINNING, model -> Option.when(PullRequestDialog.showsSpinner(model), true))
            .filling(KernelSlots.SPINNING, model -> Option.when(CommentsDialog.showsSpinner(model), true))
            .filling(KernelSlots.CLOCKING, ChecksDialog::shownTimes)
            .filling(KernelSlots.CLOCKING, PullRequestDialog::shownTimes)
            .filling(KernelSlots.CLOCKING, CommentsDialog::shownTimes)
            .filling(KernelSlots.PICTURING, RemarksState::pictures)
            .filling(KernelSlots.PICTURING, CommentsDialog::pictures)
            // On with a remote on GitHub and gh found (ADR 171).
            .applying(ID, DetectGitHub::in);

    private GitHubFeature() {}
}
