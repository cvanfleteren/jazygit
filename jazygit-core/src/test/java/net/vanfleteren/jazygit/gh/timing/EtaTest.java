package net.vanfleteren.jazygit.gh.timing;

import static net.vanfleteren.jazygit.gh.timing.CheckTimingFixtures.T0;
import static net.vanfleteren.jazygit.gh.timing.CheckTimingFixtures.at;
import static net.vanfleteren.jazygit.gh.timing.CheckTimingFixtures.check;
import static net.vanfleteren.jazygit.gh.timing.CheckTimingFixtures.queued;
import static net.vanfleteren.jazygit.gh.timing.CheckTimingFixtures.running;
import static net.vanfleteren.jazygit.gh.timing.CheckTimingFixtures.samples;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Duration;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Check;
import org.junit.jupiter.api.Test;

class EtaTest {

    // package runs 60s from the start of its run; acceptance waits for it, then runs 190s, done 254s in.
    private static final CheckHistory HISTORY = CheckHistory.EMPTY
            .with(samples("package", new int[] {58, 60, 62}, new int[] {60, 60, 60}, T0), T0)
            .with(samples("acceptance", new int[] {190, 190, 190}, new int[] {254, 254, 254}, T0), T0);

    @Test
    void aQueuedCheckIsExpectedWhenItIsUsuallyDoneAfterItsRunStarted() {
        Check acceptance = queued("acceptance");

        assertEquals(new Eta.Expected(at(254)), Eta.of(acceptance, List.of(acceptance), HISTORY));
    }

    @Test
    void aRunningCheckIsExpectedAtTheLaterOfItsOwnStartAndItsRun() {
        Check early = running("acceptance", 30);
        Check late = running("acceptance", 120);

        assertEquals(new Eta.Expected(at(254)), Eta.of(early, List.of(early), HISTORY));
        assertEquals(new Eta.Expected(at(310)), Eta.of(late, List.of(late), HISTORY));
    }

    @Test
    void aCheckWithTooLittleHistoryIsNotKnown() {
        Check verify = running("verify", 2);

        assertEquals(new Eta.Unknown(), Eta.of(verify, List.of(verify), HISTORY));
    }

    @Test
    void aCommitStatusIsNotKnown() {
        Check status = new Check("ci/external", Checks.PENDING);

        assertEquals(new Eta.Unknown(), Eta.of(status, List.of(status), HISTORY));
    }

    @Test
    void aDoneCheckIsDone() {
        Check done = check("package", Option.of(2), Option.of(60));

        assertEquals(new Eta.Done(), Eta.of(done, List.of(done), HISTORY));
    }

    @Test
    void theChecksTogetherAreExpectedWhenTheLastIsDone() {
        List<Check> checks =
                List.of(check("package", Option.of(2), Option.of(60)), running("package", 2), queued("acceptance"));

        assertEquals(new Eta.Expected(at(254)), Eta.of(checks, HISTORY));
    }

    @Test
    void theChecksTogetherAreNotKnownWhenOneIsNot() {
        assertEquals(new Eta.Unknown(), Eta.of(List.of(queued("acceptance"), running("verify", 2)), HISTORY));
    }

    @Test
    void theChecksTogetherAreDoneWhenAllAre() {
        assertEquals(new Eta.Done(), Eta.of(List.of(check("package", Option.of(2), Option.of(60))), HISTORY));
        assertEquals(new Eta.Done(), Eta.of(List.empty(), HISTORY));
    }

    @Test
    void aReRunStartsWhenItsFirstCheckStarted() {
        Check pack = check("package", 9, 2, Option.of(1000), Option.none());
        Check acceptance = check("acceptance", 9, 2, Option.none(), Option.none());
        List<Check> checks = List.of(pack, acceptance);

        assertEquals(new Eta.Expected(at(1254)), Eta.of(acceptance, checks, HISTORY));
        assertEquals(new Eta.Expected(at(1060)), Eta.of(pack, checks, HISTORY));
    }

    @Test
    void aQueuedReRunIsNotKnownWhileNoneOfItsChecksStarted() {
        Check acceptance = check("acceptance", 9, 2, Option.none(), Option.none());

        assertEquals(new Eta.Unknown(), Eta.of(acceptance, List.of(acceptance), HISTORY));
    }

    @Test
    void overdueOnlyAMinutePastTheEstimate() {
        Eta.Expected expected = new Eta.Expected(at(254));

        assertEquals(Duration.ofSeconds(54), expected.left(at(200)));
        assertFalse(expected.overdue(at(254 + 60)));
        assertTrue(expected.overdue(at(254 + 61)));
    }
}
