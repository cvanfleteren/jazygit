package net.vanfleteren.jazygit.app;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.app.keys.Features;
import net.vanfleteren.jazygit.features.branches.panes.branches.BranchBadges;
import net.vanfleteren.jazygit.features.branches.panes.branches.BranchesPanel;
import net.vanfleteren.jazygit.kernel.content.ContentView;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.ui.FetchLine;
import net.vanfleteren.jazygit.kernel.ui.files.FilesPanel;
import net.vanfleteren.jazygit.kernel.ui.screen.Screens;
import net.vanfleteren.jazygit.kernel.ui.text.ShownTime;

/**
 * What the app is woken for, as Elm's subscriptions are (ADR 124): the moment a time the screen shows changes next.
 */
final class ShownClocks {

    /**
     * What the features show of a branch in the branches pane, whose times change too.
     */
    private static final BranchBadges BADGES = BranchBadges.filled(Features.FILLS);

    private ShownClocks() {}

    /**
     * When a time a view shows in {@code model} changes next, after {@code now}; none when none changes within
     * {@link ShownTime#HORIZON}. While away, no sooner than {@link Model#AWAY_CLOCK} after {@code now}.
     */
    static Option<Instant> next(Model model, Instant now) {
        // The features' popups first: what moves on every second keeps the others from looking far ahead.
        List<ShownTime> shown = Features.ALL
                .flatMap(feature -> feature.clocking().apply(model))
                .appendAll(FetchLine.shownTimes(model))
                .appendAll(ContentView.shownTimes(model))
                .appendAll(BranchesPanel.shownTimes(model, BADGES))
                .appendAll(FilesPanel.shownTimes(model))
                // Those of a screen only while it is shown (ADR 168).
                .appendAll(Screens.standard().spec(model.screen()).times().apply(model));
        Instant soonest = model.jobs().away() ? now.plus(Model.AWAY_CLOCK) : now;
        return ShownTime.next(shown, now).map(next -> next.isBefore(soonest) ? soonest : next);
    }
}
