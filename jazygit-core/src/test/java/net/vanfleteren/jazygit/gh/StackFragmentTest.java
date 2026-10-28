package net.vanfleteren.jazygit.gh;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.State;
import net.vanfleteren.jazygit.gh.model.PullRequestStack;
import org.junit.jupiter.api.Test;

/**
 * Where a pull request sits in its stack, as {@link StackFragment} decodes it (ADR 24).
 */
class StackFragmentTest {

    private static Either<GhError.UnexpectedOutput, Option<PullRequestStack>> stack(String json) {
        return GhProcess.decoded(json, StackFragment.DECODER);
    }

    @Test
    void aPullRequestThatIsNotStackedHasNoStack() {
        assertEquals(Either.right(Option.none()), stack("{\"stackEntry\": null, \"stack\": null}"));
    }

    @Test
    void aStackedPullRequestSaysWhereItSitsAndWhatTheStackHolds() {
        assertEquals(
                Either.right(Option.of(new PullRequestStack(
                        7,
                        2,
                        2,
                        List.of(
                                new PullRequestStack.Entry(1, 94, State.MERGED),
                                new PullRequestStack.Entry(2, 95, State.OPEN))))),
                stack("""
                        { "stackEntry": { "position": 2 }, "stack": { "number": 7, "size": 2, "entries": { "nodes": [
                          { "position": 1, "pullRequest": { "number": 94, "state": "MERGED" } },
                          { "position": 2, "pullRequest": { "number": 95, "state": "OPEN" } } ] } } }"""));
    }

    @Test
    void anEntryWhosePullRequestCannotBeReadIsLeftOut() {
        assertEquals(
                Either.right(Option.of(
                        new PullRequestStack(7, 2, 3, List.of(new PullRequestStack.Entry(2, 95, State.OPEN))))),
                stack("""
                        { "stackEntry": { "position": 2 }, "stack": { "number": 7, "size": 3, "entries": { "nodes": [
                          { "position": 1, "pullRequest": null }, null,
                          { "position": 2, "pullRequest": { "number": 95, "state": "OPEN" } } ] } } }"""));
    }

    @Test
    void aStackWithoutAPlaceInItIsUnexpected() {
        assertEquals(
                Either.left(new GhError.UnexpectedOutput("expected both a stack and a place in it, or neither")),
                stack("""
                        { "stackEntry": null, "stack": { "number": 7, "size": 2, "entries": { "nodes": [] } } }"""));
        assertEquals(
                Either.left(new GhError.UnexpectedOutput("expected both a stack and a place in it, or neither")),
                stack("{\"stackEntry\": { \"position\": 2 }, \"stack\": null}"));
    }

    @Test
    void anEntryOfAStateThatIsNotKnownIsUnexpected() {
        assertEquals(
                Either.left(
                        new GhError.UnexpectedOutput(
                                "stack.entries.nodes[0].pullRequest.state: expected one of OPEN, CLOSED, MERGED, got \"DRAFT\"")),
                stack("""
                        { "stackEntry": { "position": 1 }, "stack": { "number": 7, "size": 1, "entries": { "nodes": [
                          { "position": 1, "pullRequest": { "number": 95, "state": "DRAFT" } } ] } } }"""));
    }
}
