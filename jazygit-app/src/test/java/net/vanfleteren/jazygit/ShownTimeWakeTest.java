package net.vanfleteren.jazygit;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ShownTimeWakeTest {

    private static final Instant NOW = Instant.parse("2026-11-14T10:00:00Z");

    /**
     * What was scheduled, how long after, and whether it was cancelled since.
     */
    private static final class Scheduled {

        final Runnable action;
        final Duration delay;
        boolean cancelled;

        Scheduled(Runnable action, Duration delay) {
            this.action = action;
            this.delay = delay;
        }
    }

    private final java.util.List<Scheduled> scheduled = new ArrayList<>();
    private final AtomicInteger woken = new AtomicInteger();
    private final ShownTimeWake wake = new ShownTimeWake(
            (action, delay) -> {
                Scheduled one = new Scheduled(action, delay);
                scheduled.add(one);
                return () -> one.cancelled = true;
            },
            woken::incrementAndGet);

    private List<Boolean> cancelled() {
        return List.ofAll(scheduled).map(one -> one.cancelled);
    }

    @Test
    void itWakesJustPastTheMomentATimeShownChanges() {
        wake.at(Option.of(NOW.plusSeconds(7)), NOW);
        scheduled.get(0).action.run();

        assertEquals(Duration.ofSeconds(7).plusMillis(1), scheduled.get(0).delay);
        assertEquals(1, woken.get());
    }

    @Test
    void theSameMomentKeepsTheWakeScheduled() {
        wake.at(Option.of(NOW.plusSeconds(7)), NOW);
        wake.at(Option.of(NOW.plusSeconds(7)), NOW.plusSeconds(2));

        assertEquals(List.of(false), cancelled());
    }

    @Test
    void anotherMomentTakesThePlaceOfTheWakeScheduled() {
        wake.at(Option.of(NOW.plusSeconds(7)), NOW);
        wake.at(Option.of(NOW.plusSeconds(3)), NOW);

        assertEquals(List.of(true, false), cancelled());
    }

    @Test
    void noMomentCancelsTheWake() {
        wake.at(Option.of(NOW.plusSeconds(7)), NOW);
        wake.at(Option.none(), NOW);

        assertEquals(List.of(true), cancelled());
    }

    @Test
    void aWakeOnItsWayWhenAnotherTookItsPlaceDoesNothing() {
        wake.at(Option.of(NOW.plusSeconds(7)), NOW);
        // It came, but a frame drawn before it reached the render thread scheduled the next.
        wake.at(Option.of(NOW.plusSeconds(60)), NOW.plusSeconds(7));
        scheduled.get(0).action.run();

        assertEquals(0, woken.get());
        // The next is still the one scheduled: telling the same moment again keeps it, and it wakes.
        wake.at(Option.of(NOW.plusSeconds(60)), NOW.plusSeconds(8));
        scheduled.get(1).action.run();

        assertEquals(List.of(true, false), cancelled());
        assertEquals(1, woken.get());
    }

    @Test
    void aMomentPastWakesAtOnce() {
        wake.at(Option.of(NOW.minusSeconds(1)), NOW);

        assertEquals(Duration.ZERO, scheduled.get(0).delay);
    }
}
