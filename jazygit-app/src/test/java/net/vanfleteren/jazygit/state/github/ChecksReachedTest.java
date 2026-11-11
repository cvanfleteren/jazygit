package net.vanfleteren.jazygit.state.github;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.gh.model.CheckRun;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Check;
import org.junit.jupiter.api.Test;

/**
 * How far checks were shown to be, so the bar of how far they are does not go back (ADR 89).
 */
class ChecksReachedTest {

    private static final Instant NOW = Instant.parse("2026-11-09T12:00:00Z");

    /**
     * A check of attempt {@code attempt} of workflow run 7 that is done when {@code done}, else running.
     */
    private static Check check(String name, boolean done, int attempt) {
        return new Check(
                name,
                done ? Checks.PASSING : Checks.PENDING,
                Option.of(new CheckRun(
                        1,
                        "Build",
                        done ? "SUCCESS" : "",
                        Option.of(NOW.minusSeconds(60)),
                        done ? Option.of(NOW) : Option.none(),
                        7,
                        NOW.minusSeconds(60),
                        attempt,
                        "push")));
    }

    // Half of them done; with nothing learned of how long they take, the share of those done counts.
    private static final List<Check> HALF = List.of(check("build", true, 1), check("test", false, 1));

    @Test
    void theChecksOfTheSameRunsDoNotGoBack() {
        double now = ChecksReached.progress(Option.none(), HALF, Option.none(), NOW);
        Option<ChecksReached> reached = Option.of(new ChecksReached(ChecksReached.runs(HALF), 0.9));

        assertEquals(0.9, ChecksReached.progress(reached, HALF, Option.none(), NOW));
        assertEquals(0.475, now, 1e-9);
    }

    @Test
    void aWorkflowRunAgainStartsFromWhereItIs() {
        List<Check> again = List.of(check("build", true, 2), check("test", false, 2));
        Option<ChecksReached> reached = Option.of(new ChecksReached(ChecksReached.runs(HALF), 0.9));

        assertEquals(0.475, ChecksReached.progress(reached, again, Option.none(), NOW), 1e-9);
    }

    @Test
    void nothingIsKeptOnceDoneNorOfChecksWithoutAWorkflowRun() {
        assertEquals(Option.none(), ChecksReached.keeping(HALF, 1));
        assertEquals(Option.none(), ChecksReached.keeping(List.of(new Check("ci/status", Checks.PENDING)), 0.5));
        assertEquals(Option.of(new ChecksReached(ChecksReached.runs(HALF), 0.5)), ChecksReached.keeping(HALF, 0.5));
    }
}
