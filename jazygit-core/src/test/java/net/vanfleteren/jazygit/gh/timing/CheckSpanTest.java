package net.vanfleteren.jazygit.gh.timing;

import static net.vanfleteren.jazygit.gh.timing.CheckTimingFixtures.T0;
import static net.vanfleteren.jazygit.gh.timing.CheckTimingFixtures.at;
import static net.vanfleteren.jazygit.gh.timing.CheckTimingFixtures.check;
import static net.vanfleteren.jazygit.gh.timing.CheckTimingFixtures.queued;
import static net.vanfleteren.jazygit.gh.timing.CheckTimingFixtures.running;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Duration;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Check;
import org.junit.jupiter.api.Test;

class CheckSpanTest {

    @Test
    void theChecksStartWhenTheirRunWasCreated() {
        assertEquals(Option.of(new CheckSpan(T0, Option.none())), CheckSpan.of(List.of(running("verify", 12))));
    }

    @Test
    void aQueuedCheckStartsThemToo() {
        assertEquals(Option.of(new CheckSpan(T0, Option.none())), CheckSpan.of(List.of(queued("verify"))));
    }

    @Test
    void aReRunStartsWhenTheFirstOfItsChecksStarted() {
        List<Check> checks = List.of(
                check("package", 1, 2, Option.of(300), Option.of(360)),
                check("verify", 1, 2, Option.of(320), Option.none()));

        assertEquals(Option.of(new CheckSpan(at(300), Option.none())), CheckSpan.of(checks));
    }

    @Test
    void aReRunOfWhichNoneStartedIsNotTimed() {
        assertEquals(Option.none(), CheckSpan.of(List.of(check("verify", 1, 2, Option.none(), Option.none()))));
    }

    @Test
    void twoRunsStartAtTheEarliest() {
        List<Check> checks = List.of(
                check("package", 1, 2, Option.of(300), Option.of(360)),
                check("lint", 2, 1, Option.of(5), Option.of(40)));

        assertEquals(Option.of(new CheckSpan(T0, Option.of(at(360)))), CheckSpan.of(checks));
    }

    @Test
    void theyEndWhenTheLastIsDoneAFailedOneToo() {
        Check failed = new Check(
                "lint",
                Checks.FAILING,
                check("lint", Option.of(5), Option.of(400)).run());
        List<Check> checks = List.of(check("package", Option.of(2), Option.of(60)), failed);

        assertEquals(Option.of(new CheckSpan(T0, Option.of(at(400)))), CheckSpan.of(checks));
    }

    @Test
    void theyDoNotEndWhileOneIsNotDone() {
        List<Check> checks = List.of(check("package", Option.of(2), Option.of(60)), running("acceptance", 62));

        assertEquals(Option.of(new CheckSpan(T0, Option.none())), CheckSpan.of(checks));
    }

    @Test
    void aPendingCommitStatusKeepsThemFromEnding() {
        List<Check> checks =
                List.of(check("package", Option.of(2), Option.of(60)), new Check("ci/external", Checks.PENDING));

        assertEquals(Option.of(new CheckSpan(T0, Option.none())), CheckSpan.of(checks));
    }

    @Test
    void commitStatusesAloneAreNotTimed() {
        assertEquals(Option.none(), CheckSpan.of(List.of(new Check("ci/external", Checks.PASSING))));
        assertEquals(Option.none(), CheckSpan.of(List.empty()));
    }

    @Test
    void theyTookFromTheStartToTheEndOrToNow() {
        assertEquals(Duration.ofSeconds(271), new CheckSpan(T0, Option.of(at(271))).took(at(500)));
        assertEquals(Duration.ofSeconds(500), new CheckSpan(T0, Option.none()).took(at(500)));
    }
}
