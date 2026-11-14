package net.vanfleteren.jazygit.ui;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.state.preferences.Screen;
import org.junit.jupiter.api.Test;

class CommandLogSlideTest {

    private static final Instant NOW = Instant.parse("2026-11-14T12:00:00Z");
    private static final CommandLogSlide RESTING = CommandLogSlide.at(Screen.REPOSITORY, 10);

    private static Instant after(long millis) {
        return NOW.plusMillis(millis);
    }

    @Test
    void inTheFirstFrameTheLogIsThereAtRest() {
        CommandLogSlide first = CommandLogSlide.next(Option.none(), Screen.REPOSITORY, 10, true, NOW);

        assertEquals(10, first.rowsAt(NOW));
        assertFalse(first.moving(NOW));
    }

    @Test
    void theLogShrinksOnItsWayAndIsThereAfterTheLengthOfASlide() {
        CommandLogSlide shrinking = CommandLogSlide.next(Option.of(RESTING), Screen.REPOSITORY, 2, true, NOW);

        assertEquals(10, shrinking.rowsAt(NOW));
        assertTrue(shrinking.moving(NOW));
        int halfway = shrinking.rowsAt(after(Slide.LENGTH.toMillis() / 2));
        assertTrue(halfway > 2 && halfway < 10, String.valueOf(halfway));
        assertEquals(2, shrinking.rowsAt(after(Slide.LENGTH.toMillis())));
        assertFalse(shrinking.moving(after(Slide.LENGTH.toMillis())));
    }

    @Test
    void onItsWayTheLogKeepsOnItsWay() {
        CommandLogSlide shrinking = RESTING.toward(Screen.REPOSITORY, 2, true, NOW);

        assertSame(shrinking, shrinking.toward(Screen.REPOSITORY, 2, true, after(50)));
    }

    @Test
    void growingBackOnItsWayGoesOnFromWhereTheLogIs() {
        CommandLogSlide shrinking = RESTING.toward(Screen.REPOSITORY, 2, true, NOW);
        int at = shrinking.rowsAt(after(30));

        CommandLogSlide growing = shrinking.toward(Screen.REPOSITORY, 10, true, after(30));

        assertEquals(at, growing.rowsAt(after(30)));
        assertEquals(10, growing.rowsAt(after(30 + Slide.LENGTH.toMillis())));
    }

    @Test
    void withoutAnimationsTheLogIsThereAtOnce() {
        CommandLogSlide shrunk = RESTING.toward(Screen.REPOSITORY, 2, false, NOW);

        assertEquals(2, shrunk.rowsAt(NOW));
        assertFalse(shrunk.moving(NOW));
    }

    @Test
    void onAnotherScreenTheLogIsThereAtOnce() {
        CommandLogSlide other = RESTING.toward(Screen.PULL_REQUESTS, 2, true, NOW);

        assertEquals(2, other.rowsAt(NOW));
        assertFalse(other.moving(NOW));
    }
}
