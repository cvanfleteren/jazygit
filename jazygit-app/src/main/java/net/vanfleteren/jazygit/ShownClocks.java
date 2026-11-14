package net.vanfleteren.jazygit;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.feature.github.checks.ChecksDialog;
import net.vanfleteren.jazygit.feature.github.comments.CommentsDialog;
import net.vanfleteren.jazygit.feature.github.show.PullRequestDialog;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.ui.FetchLine;
import net.vanfleteren.jazygit.ui.branches.BranchesPanel;
import net.vanfleteren.jazygit.ui.content.ContentView;
import net.vanfleteren.jazygit.ui.github.PullRequestsPanel;
import net.vanfleteren.jazygit.ui.text.ShownTime;

/**
 * What the app is woken for, as Elm's subscriptions are (ADR 124): the moment a time the screen shows changes next.
 */
final class ShownClocks {

    private ShownClocks() {}

    /**
     * When a time a view shows in {@code model} changes next, after {@code now}; none when none changes within
     * {@link ShownTime#HORIZON}. While away, no sooner than {@link Model#AWAY_CLOCK} after {@code now}.
     */
    static Option<Instant> next(Model model, Instant now) {
        // The dialogs first: what moves on every second keeps the others from looking far ahead.
        List<ShownTime> shown = ChecksDialog.shownTimes(model)
                .appendAll(PullRequestDialog.shownTimes(model))
                .appendAll(CommentsDialog.shownTimes(model))
                .appendAll(FetchLine.shownTimes(model))
                .appendAll(ContentView.shownTimes(model))
                .appendAll(BranchesPanel.shownTimes(model))
                .appendAll(PullRequestsPanel.shownTimes(model));
        Instant soonest = model.jobs().away() ? now.plus(Model.AWAY_CLOCK) : now;
        return ShownTime.next(shown, now).map(next -> next.isBefore(soonest) ? soonest : next);
    }
}
