package net.vanfleteren.jazygit.core.process;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.Tuple;
import io.vavr.control.Option;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

/**
 * Running two pieces of work at the same time, and waiting for both (ADR 188).
 */
class ParallelTest {

    @Test
    void bothAreGivenInOrder() {
        assertEquals(Option.of(Tuple.of("a", 2)), Parallel.both(() -> "a", () -> 2));
    }

    @Test
    void theTwoRunAtTheSameTime() {
        CountDownLatch started = new CountDownLatch(2);
        // Each waits for the other to have started: run one after the other, neither would see it.
        Option<?> both = Parallel.both(() -> meet(started), () -> meet(started));

        assertEquals(Option.of(Tuple.of(true, true)), both);
    }

    @Test
    void whatOneThrowsIsThrownOnceBothAreDone() {
        AtomicBoolean secondDone = new AtomicBoolean();

        IllegalArgumentException thrown = assertThrows(
                IllegalArgumentException.class,
                () -> Parallel.both(
                        () -> {
                            throw new IllegalArgumentException("bug");
                        },
                        () -> {
                            sleep(50);
                            secondDone.set(true);
                            return 1;
                        }));

        assertEquals("bug", thrown.getMessage());
        assertTrue(secondDone.get());
    }

    @Test
    void anInterruptedWaitGivesNoneAndKeepsTheInterrupt() {
        Thread.currentThread().interrupt();
        try {
            assertEquals(Option.none(), Parallel.both(() -> sleep(1_000), () -> 1));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    private static boolean meet(CountDownLatch started) {
        started.countDown();
        try {
            return started.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static boolean sleep(long millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
