package net.vanfleteren.jazygit.ui.text;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ShownTimeTest {

    private static final Instant NOW = Instant.parse("2026-11-14T10:00:00Z");

    /**
     * An age shown in the lists, of what happened at {@code at}.
     */
    private static ShownTime age(Instant at) {
        return ShownTime.reading(now -> TimeText.age(Duration.between(at, now)));
    }

    @Test
    void anAgeChangesTheMillisecondItTurnsAMinute() {
        Instant at = NOW.minusMillis(59_500);

        assertEquals(Option.of(NOW.plusMillis(500)), age(at).changes(NOW, NOW.plus(ShownTime.HORIZON)));
    }

    @Test
    void anAgeInMinutesChangesAMinuteAfterItsLastChange() {
        Instant at = NOW.minusSeconds(3 * 60 + 20);

        assertEquals(Option.of(NOW.plusSeconds(40)), age(at).changes(NOW, NOW.plus(ShownTime.HORIZON)));
    }

    @Test
    void howLongAgoTheRemotesWereFetchedChangesAtItsNextStepOf10s() {
        Instant fetched = NOW.minusSeconds(23);
        ShownTime ago = ShownTime.reading(now -> TimeText.readAgo(Duration.between(fetched, now)));

        assertEquals(Option.of(NOW.plusSeconds(7)), ago.changes(NOW, NOW.plus(ShownTime.HORIZON)));
    }

    @Test
    void whatDoesNotChangeBeforeTheFirstChangeFoundIsNotLookedIntoFurther() {
        Instant at = NOW.minusSeconds(3 * 60);

        assertEquals(Option.none(), age(at).changes(NOW, NOW.plusSeconds(30)));
    }

    @Test
    void whatMovesOnEverySecondChangesASecondFromNow() {
        assertEquals(
                Option.of(NOW.plusSeconds(1)),
                ShownTime.every(Duration.ofSeconds(1)).changes(NOW, NOW.plus(ShownTime.HORIZON)));
    }

    @Test
    void theFirstChangeOfAllTheTimesShownIsTheNext() {
        List<ShownTime> shown = List.of(
                age(NOW.minusSeconds(10 * 60 + 15)), age(NOW.minusSeconds(2 * 60 + 50)), age(NOW.minusSeconds(30)));

        assertEquals(Option.of(NOW.plusSeconds(10)), ShownTime.next(shown, NOW));
    }

    @Test
    void whatChangesNoSoonerThanADayFromNowIsNoNextChange() {
        // 400 days and a second ago: 400d until it is 401 days ago, a day but a second from now.
        Instant longAgo = NOW.minus(Duration.ofDays(400)).minusSeconds(1);

        assertEquals(Option.none(), ShownTime.next(List.of(ShownTime.reading(now -> "fixed")), NOW));
        assertEquals(
                Option.of(NOW.plus(Duration.ofDays(1)).minusSeconds(1)), ShownTime.next(List.of(age(longAgo)), NOW));
        assertEquals(Option.none(), ShownTime.next(List.empty(), NOW));
    }
}
