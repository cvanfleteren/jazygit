package net.vanfleteren.jazygit.git.model;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.collection.Stream;
import org.junit.jupiter.api.Test;

class DiffContextTest {

    @Test
    void wideningStepsFromNoContextToTheWholeFileAndStaysThere() {
        List<Integer> steps = Stream.iterate(new DiffContext(0), DiffContext::wider)
                .take(9)
                .map(DiffContext::lines)
                .toList();

        assertEquals(
                List.of(0, 1, 3, 5, 10, 20, 50, DiffContext.WHOLE_FILE.lines(), DiffContext.WHOLE_FILE.lines()), steps);
    }

    @Test
    void narrowingStepsBackToNoContextAndStaysThere() {
        List<Integer> steps = Stream.iterate(DiffContext.WHOLE_FILE, DiffContext::narrower)
                .take(9)
                .map(DiffContext::lines)
                .toList();

        assertEquals(List.of(DiffContext.WHOLE_FILE.lines(), 50, 20, 10, 5, 3, 1, 0, 0), steps);
    }

    @Test
    void anAmountBetweenTheStepsStepsToTheNearestOneEitherWay() {
        assertEquals(new DiffContext(10), new DiffContext(7).wider());
        assertEquals(new DiffContext(5), new DiffContext(7).narrower());
    }

    @Test
    void onlyTheLargestAmountIsTheWholeFile() {
        assertTrue(DiffContext.WHOLE_FILE.wholeFile());
        assertFalse(new DiffContext(50).wholeFile());
        assertEquals("-U3", DiffContext.DEFAULT.flag());
    }
}
