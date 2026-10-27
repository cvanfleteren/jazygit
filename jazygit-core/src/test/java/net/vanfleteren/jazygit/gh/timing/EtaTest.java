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
import net.vanfleteren.jazygit.gh.model.CheckRun;
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
    void aRunWaitsForTheChecksItHasThatDoNotShowYet() {
        // acceptance needs package, so GitHub makes it only once package is done.
        assertEquals(new Eta.Expected(at(254)), Eta.of(List.of(running("package", 2)), HISTORY));
    }

    @Test
    void aRunDoesNotWaitForTheChecksOfAnotherEvent() {
        CheckHistory history = HISTORY.with(
                samples("Build", "push", "snapshot", new int[] {170, 170, 170}, new int[] {458, 458, 458}, T0), T0);

        assertEquals(new Eta.Expected(at(254)), Eta.of(List.of(running("package", 2)), history));
    }

    @Test
    void aRunDoesNotWaitForAMissingCheckWithTooLittleHistory() {
        CheckHistory history = HISTORY.with(samples("snapshot", new int[] {170, 170}, new int[] {458, 458}, T0), T0);

        assertEquals(new Eta.Expected(at(254)), Eta.of(List.of(queued("acceptance")), history));
    }

    @Test
    void aMissingCheckIsNotComingOnceTheChecksOfItsRunAreDone() {
        assertEquals(new Eta.Done(), Eta.of(List.of(check("package", Option.of(2), Option.of(60))), HISTORY));
    }

    @Test
    void aSkippedCheckIsNotMissing() {
        Check skipped = new Check(
                "acceptance",
                Checks.PASSING,
                queued("acceptance")
                        .run()
                        .map(run -> new CheckRun(
                                run.id(),
                                run.workflow(),
                                "SKIPPED",
                                Option.of(at(60)),
                                Option.of(at(60)),
                                run.runId(),
                                run.runCreatedAt(),
                                run.runAttempt(),
                                run.event())));

        assertEquals(new Eta.Expected(at(62)), Eta.of(List.of(running("package", 2), skipped), HISTORY));
    }

    @Test
    void aReRunWaitsForItsMissingChecksFromWhenItsFirstCheckStarted() {
        assertEquals(
                new Eta.Expected(at(1254)),
                Eta.of(List.of(check("package", 9, 2, Option.of(1000), Option.none())), HISTORY));
    }

    @Test
    void eachRunOfACommitWaitsForItsOwnMissingChecks() {
        CheckHistory history = HISTORY.with(
                        samples("Review", "pull_request", "review", new int[] {30, 30, 30}, new int[] {35, 35, 35}, T0),
                        T0)
                .with(
                        samples(
                                "Review",
                                "pull_request",
                                "summary",
                                new int[] {9, 9, 9},
                                new int[] {400, 400, 400},
                                T0),
                        T0);
        List<Check> buildDone = List.of(
                check("package", Option.of(2), Option.of(60)), check("acceptance", Option.of(62), Option.of(250)));
        Check reviewing = check("Review", "pull_request", "review", 2, 1, Option.of(5), Option.none());
        Check summarized = check("Review", "pull_request", "summary", 2, 1, Option.of(5), Option.of(20));

        // Build is done, so it waits for nothing; Review still waits for its summary.
        assertEquals(new Eta.Expected(at(400)), Eta.of(buildDone.append(reviewing), history));
        // Review is done without its review, so it waits for nothing; Build still waits for acceptance.
        assertEquals(new Eta.Expected(at(254)), Eta.of(List.of(running("package", 2), summarized), history));
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
