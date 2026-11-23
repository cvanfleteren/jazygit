package net.vanfleteren.jazygit.core.process;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.control.Either;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

/**
 * Running two pieces of work at the same time, and waiting for both (ADR 188): how either fails is a value, never
 * thrown.
 */
class ParallelTest {

    @Test
    void bothAreGivenInOrder() {
        assertEquals(Either.right(Tuple.of("a", 2)), Parallel.both(() -> "a", () -> 2));
    }

    @Test
    void theTwoRunAtTheSameTime() {
        CountDownLatch started = new CountDownLatch(2);
        // Each waits for the other to have started: run one after the other, neither would see it.
        Either<Throwable, Tuple2<Boolean, Boolean>> both = Parallel.both(() -> meet(started), () -> meet(started));

        assertEquals(Either.right(Tuple.of(true, true)), both);
    }

    @Test
    void whatOneThrowsIsItsFailureOnceBothAreDone() {
        AtomicBoolean secondDone = new AtomicBoolean();
        IllegalArgumentException bug = new IllegalArgumentException("bug");

        Either<Throwable, Tuple2<Object, Integer>> both = Parallel.both(
                () -> {
                    throw bug;
                },
                () -> {
                    sleep(50);
                    secondDone.set(true);
                    return 1;
                });

        assertEquals(bug, both.getLeft());
        assertTrue(secondDone.get());
    }

    @Test
    void anInterruptedWaitFailsAndKeepsTheInterrupt() {
        Thread.currentThread().interrupt();
        try {
            Either<Throwable, Tuple2<Boolean, Integer>> both = Parallel.both(() -> sleep(1_000), () -> 1);

            assertInstanceOf(InterruptedException.class, both.getLeft());
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
