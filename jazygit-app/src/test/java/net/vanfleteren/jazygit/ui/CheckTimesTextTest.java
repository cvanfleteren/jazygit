package net.vanfleteren.jazygit.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.control.Option;
import java.time.Duration;
import java.time.Instant;
import net.vanfleteren.jazygit.gh.model.CheckRun;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Check;
import net.vanfleteren.jazygit.gh.timing.Eta;
import org.junit.jupiter.api.Test;

class CheckTimesTextTest {

    private static final Instant NOW = Instant.parse("2026-10-25T10:00:00Z");

    @Test
    void howLongDownToFiveSecondsUnderFiveMinutes() {
        assertEquals("<5s", CheckTimesText.duration(Duration.ofSeconds(4)));
        assertEquals("<5s", CheckTimesText.duration(Duration.ofSeconds(-5)));
        assertEquals("5s", CheckTimesText.duration(Duration.ofSeconds(5)));
        assertEquals("45s", CheckTimesText.duration(Duration.ofSeconds(49)));
        assertEquals("55s", CheckTimesText.duration(Duration.ofSeconds(59)));
        assertEquals("1m", CheckTimesText.duration(Duration.ofSeconds(60)));
        assertEquals("2m 5s", CheckTimesText.duration(Duration.ofSeconds(129)));
        assertEquals("4m 55s", CheckTimesText.duration(Duration.ofSeconds(299)));
    }

    @Test
    void howLongDownToFiveSecondsAlsoFromFiveMinutesOn() {
        assertEquals("5m", CheckTimesText.duration(Duration.ofSeconds(300)));
        assertEquals("7m 40s", CheckTimesText.duration(Duration.ofSeconds(7 * 60 + 41)));
        assertEquals("59m 55s", CheckTimesText.duration(Duration.ofSeconds(3599)));
    }

    @Test
    void howLongDownToTheMinuteFromAnHourOn() {
        assertEquals("1h", CheckTimesText.duration(Duration.ofSeconds(3600)));
        assertEquals("1h 5m", CheckTimesText.duration(Duration.ofSeconds(65 * 60 + 59)));
    }

    @Test
    void howLongStillToTheNearestStep() {
        assertEquals("~5s", CheckTimesText.left(Duration.ofSeconds(3)));
        assertEquals("~1m 30s", CheckTimesText.left(Duration.ofSeconds(89)));
        assertEquals("~4m 55s", CheckTimesText.left(Duration.ofSeconds(297)));
        assertEquals("~5m", CheckTimesText.left(Duration.ofSeconds(298)));
        assertEquals("~8m", CheckTimesText.left(Duration.ofSeconds(7 * 60 + 40)));
        assertEquals("~1h", CheckTimesText.left(Duration.ofSeconds(3610)));
        assertEquals("~1h 15m", CheckTimesText.left(Duration.ofSeconds(4500)));
    }

    @Test
    void whenTheChecksOfABranchAreDoneInBriefInWholeMinutes() {
        assertEquals(Option.none(), CheckTimesText.brief(new Eta.Done(), false, NOW));
        assertEquals(Option.of("?"), CheckTimesText.brief(new Eta.Unknown(), false, NOW));
        assertEquals(Option.of("…"), CheckTimesText.brief(new Eta.Unknown(), true, NOW));
        assertEquals(Option.of("~4m"), CheckTimesText.brief(new Eta.Expected(NOW.plusSeconds(240)), false, NOW));
        assertEquals(Option.of("~1h"), CheckTimesText.brief(new Eta.Expected(NOW.plusSeconds(3600)), false, NOW));
        assertEquals(Option.of("~1h 15m"), CheckTimesText.brief(new Eta.Expected(NOW.plusSeconds(4500)), false, NOW));
        assertEquals(Option.of("~2m"), CheckTimesText.brief(new Eta.Expected(NOW.plusSeconds(90)), false, NOW));
        assertEquals(Option.of("<1m"), CheckTimesText.brief(new Eta.Expected(NOW.plusSeconds(59)), false, NOW));
        assertEquals(Option.of("<1m"), CheckTimesText.brief(new Eta.Expected(NOW.minusSeconds(30)), false, NOW));
        assertEquals(Option.of("late"), CheckTimesText.brief(new Eta.Expected(NOW.minusSeconds(61)), false, NOW));
    }

    @Test
    void whenTheChecksAreDoneTogether() {
        assertEquals(Option.none(), CheckTimesText.together(new Eta.Done(), false, NOW));
        assertEquals(Option.of("done in ?"), CheckTimesText.together(new Eta.Unknown(), false, NOW));
        assertEquals(Option.of("done in …"), CheckTimesText.together(new Eta.Unknown(), true, NOW));
        assertEquals(
                Option.of("done in ~3m"), CheckTimesText.together(new Eta.Expected(NOW.plusSeconds(180)), false, NOW));
        assertEquals(
                Option.of("done in ~5s"), CheckTimesText.together(new Eta.Expected(NOW.plusSeconds(3)), false, NOW));
        assertEquals(
                Option.of("done any moment"),
                CheckTimesText.together(new Eta.Expected(NOW.plusSeconds(2)), false, NOW));
        assertEquals(
                Option.of("done any moment"),
                CheckTimesText.together(new Eta.Expected(NOW.minusSeconds(60)), false, NOW));
        assertEquals(
                Option.of("taking longer than usual"),
                CheckTimesText.together(new Eta.Expected(NOW.minusSeconds(61)), false, NOW));
    }

    @Test
    void howACheckIsDoingAroundWhenItIsDue() {
        Check running = check(Option.of(NOW.minusSeconds(90)));

        assertEquals(
                "running 1m 30s · ~5s left",
                CheckTimesText.of(running, new Eta.Expected(NOW.plusSeconds(3)), false, NOW));
        assertEquals(
                "running 1m 30s · due", CheckTimesText.of(running, new Eta.Expected(NOW.plusSeconds(2)), false, NOW));
        // Still due within the grace of a minute, overdue after it.
        assertEquals(
                "running 1m 30s · due", CheckTimesText.of(running, new Eta.Expected(NOW.minusSeconds(60)), false, NOW));
        assertEquals(
                "running 1m 30s · overdue",
                CheckTimesText.of(running, new Eta.Expected(NOW.minusSeconds(61)), false, NOW));
    }

    @Test
    void aQueuedCheckCanBeDue() {
        assertEquals(
                "queued 2m · due",
                CheckTimesText.of(check(Option.none()), new Eta.Expected(NOW.minusSeconds(10)), false, NOW));
    }

    @Test
    void aQueuedCheckSaysHowLongSinceItsRunWasCreatedOnlyOfAFirstAttempt() {
        Check rerun = new Check(
                "verify",
                Checks.PENDING,
                Option.of(new CheckRun(
                        1, "Build", "", Option.none(), Option.none(), 1, NOW.minusSeconds(200), 2, "push")));

        assertEquals("queued 2m · ? left", CheckTimesText.of(check(Option.none()), new Eta.Unknown(), false, NOW));
        assertEquals("queued · ? left", CheckTimesText.of(rerun, new Eta.Unknown(), false, NOW));
    }

    /**
     * A check of an Actions run that is not done, which started at {@code started}, or is queued without.
     */
    private static Check check(Option<Instant> started) {
        return new Check(
                "verify",
                Checks.PENDING,
                Option.of(new CheckRun(1, "Build", "", started, Option.none(), 1, NOW.minusSeconds(120), 1, "push")));
    }
}
