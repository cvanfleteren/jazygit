package net.vanfleteren.jazygit.gh.timing;

import static net.vanfleteren.jazygit.gh.timing.CheckTimingFixtures.at;
import static net.vanfleteren.jazygit.gh.timing.CheckTimingFixtures.check;
import static net.vanfleteren.jazygit.gh.timing.CheckTimingFixtures.queued;
import static net.vanfleteren.jazygit.gh.timing.CheckTimingFixtures.running;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Check;
import org.junit.jupiter.api.Test;

class CheckProgressTest {

    private static final double DELTA = 1e-9;
    // Created at T0, expected done 200s in.
    private static final List<Check> RUNNING = List.of(running("verify", 10));
    private static final Eta IN_200S = new Eta.Expected(at(200));

    @Test
    void theChecksGoTo95PercentAsTheTimeGoesByTowardsWhenTheyAreExpected() {
        assertEquals(0, CheckProgress.of(RUNNING, IN_200S, at(0)), DELTA);
        assertEquals(0.475, CheckProgress.of(RUNNING, IN_200S, at(100)), DELTA);
        assertEquals(0.95, CheckProgress.of(RUNNING, IN_200S, at(200)), DELTA);
    }

    @Test
    void takingLongerTheyGoOnSlowerAndSlowerWithoutReaching99Percent() {
        double later = CheckProgress.of(RUNNING, IN_200S, at(400));
        double muchLater = CheckProgress.of(RUNNING, IN_200S, at(4000));

        assertEquals(0.95 + 0.04 * (1 - Math.exp(-1)), later, DELTA);
        assertTrue(later < muchLater, later + " then " + muchLater);
        assertTrue(muchLater < 0.99, String.valueOf(muchLater));
    }

    @Test
    void aWorkflowRunAgainGoesFromItsOwnStartNotFromRunsDoneLongBefore() {
        // lint was done long ago; verify, run again from 3000s in, is expected 200s after that.
        List<Check> checks = List.of(
                check("lint", 1, 1, Option.of(5), Option.of(40)),
                check("verify", 2, 2, Option.of(3000), Option.none()));

        assertEquals(0.475, CheckProgress.of(checks, new Eta.Expected(at(3200)), at(3100)), DELTA);
    }

    @Test
    void theyAreFullOnlyOnceTheyAreDone() {
        List<Check> done = List.of(check("verify", Option.of(10), Option.of(90)));

        assertEquals(1, CheckProgress.of(done, new Eta.Done(), at(90)), DELTA);
    }

    @Test
    void notKnowingWhenTheyAreDoneTheyAreAsFarAsTheShareOfThemDone() {
        List<Check> checks = List.of(
                check("package", Option.of(2), Option.of(60)),
                running("verify", 10),
                queued("acceptance"),
                new Check("ci/status", Checks.PENDING));

        assertEquals(0.95 / 4, CheckProgress.of(checks, new Eta.Unknown(), at(100)), DELTA);
    }

    @Test
    void aCheckRunsFromItsOwnStartTowardsWhenItIsExpected() {
        Check verify = running("verify", 100);

        assertEquals(Option.of(0.475), CheckProgress.of(verify, IN_200S, at(150)));
    }

    @Test
    void aQueuedCheckIsEmptyAndADoneOneFull() {
        assertEquals(Option.of(0.0), CheckProgress.of(queued("acceptance"), IN_200S, at(150)));
        assertEquals(
                Option.of(1.0),
                CheckProgress.of(check("verify", Option.of(10), Option.of(90)), new Eta.Done(), at(150)));
    }

    @Test
    void aCheckNotKnownToBeExpectedNorTimedHasNoProgress() {
        assertEquals(Option.none(), CheckProgress.of(running("verify", 100), new Eta.Unknown(), at(150)));
        assertEquals(Option.none(), CheckProgress.of(new Check("ci/status", Checks.PENDING), IN_200S, at(150)));
    }
}
