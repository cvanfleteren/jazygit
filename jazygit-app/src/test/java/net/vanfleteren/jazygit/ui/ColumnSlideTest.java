package net.vanfleteren.jazygit.ui;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.state.preferences.Screen;
import org.junit.jupiter.api.Test;

class ColumnSlideTest {

    private static final Instant NOW = Instant.parse("2026-11-05T12:00:00Z");
    private static final ColumnSlide RESTING = ColumnSlide.at(Screen.REPOSITORY, 160, 48);

    private static Instant after(long millis) {
        return NOW.plusMillis(millis);
    }

    @Test
    void atRestTheColumnStaysAsWideAndDoesNotMove() {
        assertEquals(48, RESTING.columnsAt(NOW));
        assertFalse(RESTING.moving(NOW));
    }

    @Test
    void theColumnEasesOutToTheWidthChosenAndIsThereAfterItsLength() {
        ColumnSlide wider = RESTING.toward(Screen.REPOSITORY, 160, 56, true, NOW);

        assertEquals(48, wider.columnsAt(NOW));
        assertTrue(wider.moving(NOW));
        // Half the time takes it seven eighths of the way: fast at first, slowing down.
        assertEquals(55, wider.columnsAt(after(75)));
        assertEquals(56, wider.columnsAt(after(ColumnSlide.LENGTH.toMillis())));
        assertFalse(wider.moving(after(ColumnSlide.LENGTH.toMillis())));
        assertEquals(56, wider.columnsAt(after(1000)));
    }

    @Test
    void theColumnGoesNarrowerTheSameWay() {
        ColumnSlide narrower = RESTING.toward(Screen.REPOSITORY, 160, 40, true, NOW);

        assertEquals(48, narrower.columnsAt(NOW));
        assertEquals(41, narrower.columnsAt(after(75)));
        assertEquals(40, narrower.columnsAt(after(150)));
    }

    @Test
    void onItsWayTheColumnKeepsOnItsWay() {
        ColumnSlide wider = RESTING.toward(Screen.REPOSITORY, 160, 56, true, NOW);

        assertSame(wider, wider.toward(Screen.REPOSITORY, 160, 56, true, after(50)));
    }

    @Test
    void anotherWidthChosenOnItsWayGoesOnFromWhereTheColumnIs() {
        ColumnSlide wider = RESTING.toward(Screen.REPOSITORY, 160, 56, true, NOW);
        int at = wider.columnsAt(after(30));

        ColumnSlide widerStill = wider.toward(Screen.REPOSITORY, 160, 64, true, after(30));

        assertEquals(at, widerStill.columnsAt(after(30)));
        assertTrue(at > 48 && at < 56, String.valueOf(at));
        assertEquals(64, widerStill.columnsAt(after(30 + 150)));
    }

    @Test
    void notToSlideTheColumnIsThereAtOnce() {
        // As when the animations are off, or the border is dragged.
        ColumnSlide jumped = RESTING.toward(Screen.REPOSITORY, 160, 56, false, NOW);

        assertEquals(56, jumped.columnsAt(NOW));
        assertFalse(jumped.moving(NOW));
    }

    @Test
    void stoppedOnItsWayTheColumnIsThereAtOnce() {
        ColumnSlide wider = RESTING.toward(Screen.REPOSITORY, 160, 56, true, NOW);

        assertEquals(
                56, wider.toward(Screen.REPOSITORY, 160, 56, false, after(30)).columnsAt(after(30)));
    }

    @Test
    void inTheFirstFrameTheColumnIsAtRestAtTheColumnsChosen() {
        ColumnSlide first = ColumnSlide.next(Option.none(), Screen.REPOSITORY, 160, 48, true, false, NOW);

        assertEquals(48, first.columnsAt(NOW));
        assertFalse(first.moving(NOW));
    }

    @Test
    void otherColumnsChosenSlideThereWhileTheAnimationsAreOn() {
        ColumnSlide wider = ColumnSlide.next(Option.of(RESTING), Screen.REPOSITORY, 160, 56, true, false, NOW);

        assertEquals(48, wider.columnsAt(NOW));
        assertTrue(wider.moving(NOW));
        assertEquals(56, wider.columnsAt(after(ColumnSlide.LENGTH.toMillis())));
    }

    @Test
    void aDraggedColumnIsThereAtOnce() {
        ColumnSlide dragged = ColumnSlide.next(Option.of(RESTING), Screen.REPOSITORY, 160, 56, true, true, NOW);

        assertEquals(56, dragged.columnsAt(NOW));
        assertFalse(dragged.moving(NOW));
    }

    @Test
    void theAnimationsTurnedOffOnItsWayPutTheColumnThereAtOnce() {
        ColumnSlide wider = ColumnSlide.next(Option.of(RESTING), Screen.REPOSITORY, 160, 56, true, false, NOW);

        ColumnSlide off = ColumnSlide.next(Option.of(wider), Screen.REPOSITORY, 160, 56, false, false, after(30));

        assertEquals(56, off.columnsAt(after(30)));
        assertFalse(off.moving(after(30)));
    }

    @Test
    void onceThereTheColumnAsksForNoMoreFrames() {
        ColumnSlide wider = ColumnSlide.next(Option.of(RESTING), Screen.REPOSITORY, 160, 56, true, false, NOW);
        Instant there = after(ColumnSlide.LENGTH.toMillis());

        ColumnSlide after = ColumnSlide.next(Option.of(wider), Screen.REPOSITORY, 160, 56, true, false, there);

        assertSame(wider, after);
        assertFalse(after.moving(there));
        assertFalse(after.moving(there.plusSeconds(60)));
    }

    @Test
    void anotherScreenOrAScreenOfAnotherWidthDoesNotSlide() {
        assertEquals(
                64, RESTING.toward(Screen.PULL_REQUESTS, 160, 64, true, NOW).columnsAt(NOW));
        assertEquals(60, RESTING.toward(Screen.REPOSITORY, 200, 60, true, NOW).columnsAt(NOW));
    }
}
