package net.vanfleteren.jazygit.gh.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.State;
import org.junit.jupiter.api.Test;

class PullRequestStackTest {

    /**
     * Pull request 43 at position 4, on top of 40, 41 and 42 in those states.
     */
    private static PullRequestStack onTopOf(State first, State second, State third) {
        return new PullRequestStack(
                7,
                4,
                4,
                List.of(
                        new PullRequestStack.Entry(1, 40, first),
                        new PullRequestStack.Entry(2, 41, second),
                        new PullRequestStack.Entry(3, 42, third),
                        new PullRequestStack.Entry(4, 43, State.OPEN)));
    }

    @Test
    void mergingAPullRequestMergesTheOpenOnesBelowIt() {
        assertEquals(3, onTopOf(State.OPEN, State.OPEN, State.OPEN).below());
    }

    @Test
    void theOnesBelowThatAreMergedOrClosedAreNotMergedAgain() {
        assertEquals(1, onTopOf(State.MERGED, State.CLOSED, State.OPEN).below());
        assertEquals(0, onTopOf(State.MERGED, State.MERGED, State.CLOSED).below());
    }

    @Test
    void theOnesAboveAreNotMerged() {
        PullRequestStack bottom = new PullRequestStack(
                7,
                1,
                2,
                List.of(new PullRequestStack.Entry(1, 40, State.OPEN), new PullRequestStack.Entry(2, 41, State.OPEN)));

        assertEquals(0, bottom.below());
    }
}
