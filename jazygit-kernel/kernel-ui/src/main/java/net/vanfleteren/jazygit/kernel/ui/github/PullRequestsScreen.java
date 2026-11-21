package net.vanfleteren.jazygit.kernel.ui.github;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.github.prs.PrOverview;
import net.vanfleteren.jazygit.kernel.ui.screen.ScreenSpec;
import net.vanfleteren.jazygit.kernel.ui.theme.Theme;
import net.vanfleteren.jazygit.kernel.ui.widgets.LoadableList;

/**
 * The pull requests screen, as GitHub's feature brings it (ADR 168): its three lists, how many pull requests of the
 * repository were opened and not seen yet after its name (ADR 29), and when the pull requests were read last. It is
 * still declared in the kernel.
 */
public final class PullRequestsScreen {

    /**
     * The screen, as the UI draws it.
     */
    public static final ScreenSpec SPEC = new ScreenSpec(
            PrOverview.SCREEN,
            "screen.pullRequests",
            PullRequestsPanel::all,
            PullRequestsScreen::unseen,
            model -> model.prOverview().freshness(),
            "status.read",
            PullRequestsPanel::shownTimes);

    private PullRequestsScreen() {}

    /**
     * How many pull requests of the repository were opened and not seen yet, in their own style, when there are any.
     */
    static Option<LoadableList.Seg> unseen(Model model) {
        int unseen = model.prOverview().unseenCount();
        return Option.when(
                unseen > 0,
                () -> LoadableList.Seg.styled(" " + PullRequestsPanel.UNSEEN_MARK + unseen, Theme.Classes.UNSEEN));
    }
}
