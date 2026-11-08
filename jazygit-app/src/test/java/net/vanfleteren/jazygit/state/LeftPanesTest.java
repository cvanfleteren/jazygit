package net.vanfleteren.jazygit.state;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.feature.layout.LayoutMsg;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.model.Stash;
import net.vanfleteren.jazygit.settings.Settings;
import org.junit.jupiter.api.Test;

class LeftPanesTest {

    private static Model withStashes(int count) {
        List<Stash> stashes = List.range(0, count)
                .map(i -> new Stash("stash@{" + i + "}", new Sha("s" + i), "wip " + i, Option.none(), Instant.EPOCH));
        return Update.update(TestModels.loaded(), new LoadMsg.StashesLoaded(stashes))
                .model();
    }

    private static int stash(Model model, int column) {
        return LeftPanes.shown(model, Screen.REPOSITORY, column).last();
    }

    private static Model maximized(Model model, int pane) {
        return model.mapPreferences(preferences -> preferences.withMaximizedPane(Screen.REPOSITORY, Option.of(pane)));
    }

    @Test
    void theStashIsAsTallAsItsRowsInABorder() {
        // 100 rows leave 100 - 3 - 75 = 22 for the stash.
        assertEquals(4, stash(withStashes(2), 100));
    }

    @Test
    void withoutStashesTheStashIsABlankLineInABorder() {
        assertEquals(3, stash(withStashes(0), 100));
    }

    @Test
    void theStashTakesAtMostWhatTheOtherPanesLeave() {
        // 50 rows leave 50 - 3 - (13 + 10 + 15) = 9 for the stash.
        assertEquals(9, stash(withStashes(30), 50));
    }

    @Test
    void theStashTakesAtMostWhatTheDraggedPanesLeave() {
        Model dragged = Update.update(withStashes(30), new LayoutMsg.PaneResized(Screen.REPOSITORY, 2, 95))
                .model();

        // The commits leave the stash its least share, 10 percent: 100 rows leave 100 - 3 - (25 + 20 + 45) = 7.
        assertEquals(7, stash(dragged, 100));
    }

    @Test
    void theStashIsAtLeastAnEmptyPaneInATinyColumn() {
        assertEquals(3, stash(withStashes(5), 10));
    }

    @Test
    void theCommitsTakeWhatTheStashLeavesOfItsShare() {
        assertEquals(List.of(25, 20, 48, 4), LeftPanes.shown(withStashes(2), Screen.REPOSITORY, 100));
    }

    @Test
    void eachPaneTakesItsShareOfTheColumnAndTheLastWhatIsLeft() {
        assertEquals(List.of(25, 20, 30, 22), LeftPanes.rows(List.of(25, 20, 30), 100));
        assertEquals(List.of(14, 14, 13), LeftPanes.rows(List.of(32, 32), 44));
        assertEquals(List.of(1, 0), LeftPanes.rows(List.of(50), 2));
        // The borders are rounded, at 11.1 and 22.2 rows, not the panes, of 11.1 rows each.
        assertEquals(List.of(11, 11, 12), LeftPanes.rows(List.of(30, 30), 37));
    }

    @Test
    void aMaximizedPaneTakesWhatTheOthersLeaveOfARowInABorderEach() {
        // 40 rows leave 40 - 3 - 3 * 3 = 28 for the branches.
        assertEquals(List.of(3, 28, 3, 3), LeftPanes.shown(maximized(withStashes(2), 1), Screen.REPOSITORY, 40));
    }

    @Test
    void aMaximizedStashTakesTheRowsItHasNoStashesFor() {
        assertEquals(List.of(3, 3, 3, 28), LeftPanes.shown(maximized(withStashes(2), 3), Screen.REPOSITORY, 40));
    }

    @Test
    void aColumnTooShortForTheMaximizedPaneShowsThePanesAsTheyAre() {
        Model model = maximized(withStashes(2), 1);

        // 14 rows leave 14 - 3 - 3 * 3 = 2 for the branches, less than a row in a border.
        assertEquals(
                LeftPanes.sized(model, Screen.REPOSITORY, Settings.DEFAULT_REPOSITORY_HEIGHTS, 14),
                LeftPanes.shown(model, Screen.REPOSITORY, 14));
        assertEquals(Option.none(), LeftPanes.maximized(model, Screen.REPOSITORY, 14));
    }

    @Test
    void theMaximizedPaneOfAnotherScreenIsNotShown() {
        Model model = withStashes(2)
                .mapPreferences(preferences -> preferences.withMaximizedPane(Screen.PULL_REQUESTS, Option.of(0)));

        assertEquals(List.of(25, 20, 48, 4), LeftPanes.shown(model, Screen.REPOSITORY, 100));
    }

    @Test
    void theHeightsShownWhileAPaneIsMaximizedKeepEachPaneAtItsLeastHeight() {
        Model model =
                maximized(withStashes(2), 0).mapPreferences(preferences -> preferences.withColumnRows(Option.of(40)));

        // The files take 28 of 40 rows, 70 percent, and the others 3 rows each, 7.5 percent, which is raised to the
        // least height, 10 percent, with what that takes taken from the files.
        List<Integer> heights = LeftPanes.shownHeights(model, Screen.REPOSITORY);
        assertEquals(List.of(65, 10, 10), heights);
        assertTrue(100 - heights.sum().intValue() >= Settings.MIN_HEIGHT);
    }

    @Test
    void theHeightsShownAreTheHeightsWhenNothingIsMaximized() {
        Model model = withStashes(2).mapPreferences(preferences -> preferences.withColumnRows(Option.of(40)));

        assertEquals(Settings.DEFAULT_REPOSITORY_HEIGHTS, LeftPanes.shownHeights(model, Screen.REPOSITORY));
    }

    @Test
    void theHeightsOfRowsRoundBackToTheSameRows() {
        List<Integer> rows = List.of(7, 13, 9, 8);

        assertEquals(rows, LeftPanes.rows(LeftPanes.heights(rows, 40), 40));
    }
}
