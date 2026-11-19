package net.vanfleteren.jazygit.ui;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.state.preferences.Screen;
import org.junit.jupiter.api.Test;

class PaneSlideTest {

    private static final Instant NOW = Instant.parse("2026-11-07T12:00:00Z");
    private static final PaneSlide.Chosen KEPT = new PaneSlide.Chosen(List.of(25, 20, 30), Option.none());
    private static final PaneSlide.Chosen MAXIMIZED = new PaneSlide.Chosen(List.of(25, 20, 30), Option.of(1));
    private static final List<Integer> ROWS = List.of(10, 8, 12, 7);
    private static final List<Integer> BRANCHES_MAXIMIZED = List.of(3, 28, 3, 3);
    private static final PaneSlide RESTING = PaneSlide.at(Screen.REPOSITORY, 40, KEPT, ROWS);

    private static Instant after(long millis) {
        return NOW.plusMillis(millis);
    }

    @Test
    void atRestThePanesStayAsTallAndDoNotMove() {
        assertEquals(ROWS, RESTING.rowsAt(NOW));
        assertFalse(RESTING.moving(NOW));
    }

    @Test
    void thePanesEaseOutToTheHeightsChosenAndAreThereAfterTheLengthOfASlide() {
        PaneSlide maximizing = RESTING.toward(Screen.REPOSITORY, 40, MAXIMIZED, BRANCHES_MAXIMIZED, true, NOW);

        assertEquals(ROWS, maximizing.rowsAt(NOW));
        assertTrue(maximizing.moving(NOW));
        List<Integer> halfway = maximizing.rowsAt(after(75));
        assertNotEquals(ROWS, halfway);
        assertNotEquals(BRANCHES_MAXIMIZED, halfway);
        assertEquals(BRANCHES_MAXIMIZED, maximizing.rowsAt(after(Slide.LENGTH.toMillis())));
        assertFalse(maximizing.moving(after(Slide.LENGTH.toMillis())));
    }

    @Test
    void onTheirWayThePanesFillTheRowsTheyHad() {
        PaneSlide maximizing = RESTING.toward(Screen.REPOSITORY, 40, MAXIMIZED, BRANCHES_MAXIMIZED, true, NOW);

        for (long millis = 0; millis <= Slide.LENGTH.toMillis(); millis += 10) {
            assertEquals(37, maximizing.rowsAt(after(millis)).sum().intValue());
        }
    }

    @Test
    void onTheirWayThePanesKeepOnTheirWay() {
        PaneSlide maximizing = RESTING.toward(Screen.REPOSITORY, 40, MAXIMIZED, BRANCHES_MAXIMIZED, true, NOW);

        assertSame(
                maximizing, maximizing.toward(Screen.REPOSITORY, 40, MAXIMIZED, BRANCHES_MAXIMIZED, true, after(50)));
    }

    @Test
    void rowsThatChangeThoughTheHeightsChosenDoNotAreThereAtOnce() {
        // The stash grows with its stashes.
        List<Integer> moreStashes = List.of(10, 8, 10, 9);

        PaneSlide grown = RESTING.toward(Screen.REPOSITORY, 40, KEPT, moreStashes, true, NOW);

        assertEquals(moreStashes, grown.rowsAt(NOW));
        assertFalse(grown.moving(NOW));
    }

    @Test
    void anotherScreenAnotherColumnOrNoAnimationsAreThereAtOnce() {
        assertFalse(RESTING.toward(Screen.PULL_REQUESTS, 40, MAXIMIZED, BRANCHES_MAXIMIZED, true, NOW)
                .moving(NOW));
        assertFalse(RESTING.toward(Screen.REPOSITORY, 50, MAXIMIZED, List.of(3, 38, 3, 3), true, NOW)
                .moving(NOW));
        assertFalse(RESTING.toward(Screen.REPOSITORY, 40, MAXIMIZED, BRANCHES_MAXIMIZED, false, NOW)
                .moving(NOW));
    }

    @Test
    void draggedPanesAreThereAtOnce() {
        PaneSlide dragged = PaneSlide.next(
                Option.of(RESTING),
                Screen.REPOSITORY,
                40,
                new PaneSlide.Chosen(List.of(30, 15, 30), Option.none()),
                List.of(12, 6, 12, 7),
                true,
                true,
                NOW);

        assertFalse(dragged.moving(NOW));
    }

    @Test
    void theFirstFrameIsAtRest() {
        PaneSlide first =
                PaneSlide.next(Option.none(), Screen.REPOSITORY, 40, MAXIMIZED, BRANCHES_MAXIMIZED, true, false, NOW);

        assertEquals(BRANCHES_MAXIMIZED, first.rowsAt(NOW));
        assertFalse(first.moving(NOW));
    }
}
