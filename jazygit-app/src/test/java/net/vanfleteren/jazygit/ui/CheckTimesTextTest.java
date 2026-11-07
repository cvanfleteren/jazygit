package net.vanfleteren.jazygit.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void howLongToTheSecondUnderAnHour() {
        assertEquals("0s", CheckTimesText.duration(Duration.ofMillis(999)));
        assertEquals("0s", CheckTimesText.duration(Duration.ofSeconds(-5)));
        assertEquals("4s", CheckTimesText.duration(Duration.ofSeconds(4)));
        assertEquals("49s", CheckTimesText.duration(Duration.ofSeconds(49)));
        assertEquals("1m", CheckTimesText.duration(Duration.ofSeconds(60)));
        assertEquals("2m 9s", CheckTimesText.duration(Duration.ofSeconds(129)));
        assertEquals("7m 41s", CheckTimesText.duration(Duration.ofSeconds(7 * 60 + 41)));
        assertEquals("59m 59s", CheckTimesText.duration(Duration.ofMillis(3_599_999)));
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
        assertEquals(Option.none(), CheckTimesText.together(new Eta.Done(), Option.none(), false, NOW));
        assertEquals(Option.of("done in ?"), CheckTimesText.together(new Eta.Unknown(), Option.none(), false, NOW));
        assertEquals(Option.of("done in …"), CheckTimesText.together(new Eta.Unknown(), Option.none(), true, NOW));
        assertEquals(
                Option.of("done in ~3m"),
                CheckTimesText.together(new Eta.Expected(NOW.plusSeconds(180)), Option.none(), false, NOW));
        assertEquals(
                Option.of("done in ~5s"),
                CheckTimesText.together(new Eta.Expected(NOW.plusSeconds(3)), Option.none(), false, NOW));
        assertEquals(
                Option.of("done any moment"),
                CheckTimesText.together(new Eta.Expected(NOW.plusSeconds(2)), Option.none(), false, NOW));
        assertEquals(
                Option.of("done any moment"),
                CheckTimesText.together(new Eta.Expected(NOW.minusSeconds(60)), Option.none(), false, NOW));
        assertEquals(
                Option.of("taking longer than usual"),
                CheckTimesText.together(new Eta.Expected(NOW.minusSeconds(61)), Option.none(), false, NOW));
        // Started 4m 1s ago, expected to take 3m.
        assertEquals(
                Option.of("taking longer than usual (~3m)"),
                CheckTimesText.together(
                        new Eta.Expected(NOW.minusSeconds(61)), Option.of(NOW.minusSeconds(241)), false, NOW));
    }

    @Test
    void howACheckIsDoingAroundWhenItIsDue() {
        Check running = check(Option.of(NOW.minusSeconds(90)));

        assertEquals(
                "running 1m 30s  ·     ~5s left",
                CheckTimesText.lined(running, new Eta.Expected(NOW.plusSeconds(3)), false, NOW));
        assertEquals(
                "running 1m 30s  ·          due",
                CheckTimesText.lined(running, new Eta.Expected(NOW.plusSeconds(2)), false, NOW));
        // Still due within the grace of a minute, overdue after it.
        assertEquals(
                "running 1m 30s  ·          due",
                CheckTimesText.lined(running, new Eta.Expected(NOW.minusSeconds(60)), false, NOW));
        assertEquals(
                "running 1m 30s  ·      overdue",
                CheckTimesText.lined(running, new Eta.Expected(NOW.minusSeconds(61)), false, NOW));
    }

    @Test
    void howChecksUnderEachOtherAreDoingLinesUpInColumnsAsWideAsTheyGet() {
        String shortest = CheckTimesText.lined(
                check(Option.of(NOW.minusSeconds(45))), new Eta.Expected(NOW.plusSeconds(60)), false, NOW);
        String longer = CheckTimesText.lined(
                check(Option.of(NOW.minusSeconds(111))), new Eta.Expected(NOW.plusSeconds(70)), false, NOW);

        assertEquals("running 45s     ·     ~1m left", shortest);
        assertEquals("running 1m 51s  · ~1m 10s left", longer);
        assertEquals(TextWidth.of(CheckTimesText.widestRunning()), TextWidth.of(shortest));
    }

    @Test
    void howLongChecksWaitedInTheBranchesPaneInWholeMinutes() {
        assertEquals("<1m", CheckTimesText.waited(Duration.ofSeconds(59)));
        assertEquals("3m", CheckTimesText.waited(Duration.ofSeconds(3 * 60 + 59)));
        assertEquals("1h 5m", CheckTimesText.waited(Duration.ofMinutes(65)));
    }

    @Test
    void aQueuedCheckCanBeDue() {
        assertEquals(
                "queued 2m       ·          due",
                CheckTimesText.lined(check(Option.none()), new Eta.Expected(NOW.minusSeconds(10)), false, NOW));
    }

    @Test
    void aQueuedCheckSaysHowLongSinceItsRunWasCreatedOnlyOfAFirstAttempt() {
        Check rerun = new Check(
                "verify",
                Checks.PENDING,
                Option.of(new CheckRun(
                        1, "Build", "", Option.none(), Option.none(), 1, NOW.minusSeconds(200), 2, "push")));

        assertEquals(
                "queued 2m       ·       ? left",
                CheckTimesText.lined(check(Option.none()), new Eta.Unknown(), false, NOW));
        assertEquals("queued          ·       ? left", CheckTimesText.lined(rerun, new Eta.Unknown(), false, NOW));
    }

    @Test
    void aRunningCheckSaysHowLongItRunsOfHowLongItIsExpectedToTakeInAll() {
        Check running = check(Option.of(NOW.minusSeconds(59)));

        assertEquals("59s/~1m 30s", CheckTimesText.ofTotal(running, new Eta.Expected(NOW.plusSeconds(31)), false, NOW));
        assertEquals("59s/?", CheckTimesText.ofTotal(running, new Eta.Unknown(), false, NOW));
        assertEquals("59s/…", CheckTimesText.ofTotal(running, new Eta.Unknown(), true, NOW));
        // Once it takes longer, it runs longer than it takes in all.
        assertEquals(
                "4m 2s/~3m",
                CheckTimesText.ofTotal(
                        check(Option.of(NOW.minusSeconds(242))), new Eta.Expected(NOW.minusSeconds(62)), false, NOW));
        // From 5m on, the estimate is to the minute.
        assertEquals(
                "1m/~8m",
                CheckTimesText.ofTotal(
                        check(Option.of(NOW.minusSeconds(60))), new Eta.Expected(NOW.plusSeconds(440)), false, NOW));
    }

    @Test
    void aQueuedCheckSaysHowLongItIsQueuedAndACommitStatusThatItRuns() {
        Check rerun = new Check(
                "verify",
                Checks.PENDING,
                Option.of(new CheckRun(
                        1, "Build", "", Option.none(), Option.none(), 1, NOW.minusSeconds(200), 2, "push")));
        Check status = new Check("ci/status", Checks.PENDING);

        assertEquals("queued 2m", CheckTimesText.ofTotal(check(Option.none()), new Eta.Expected(NOW), false, NOW));
        assertEquals("queued", CheckTimesText.ofTotal(rerun, new Eta.Unknown(), false, NOW));
        assertEquals("running", CheckTimesText.ofTotal(status, new Eta.Unknown(), false, NOW));
    }

    @Test
    void howLongACheckRunsOfItsTotalIsNeverWiderThanAtItsWidest() {
        Check longest = check(Option.of(NOW.minusSeconds(3599)));
        int widest = TextWidth.of(CheckTimesText.widestOfTotal());

        assertEquals("59m 59s/~4m 55s", CheckTimesText.widestOfTotal());
        for (Eta eta : java.util.List.of(
                new Eta.Expected(NOW.minusSeconds(3599 - 297)),
                new Eta.Expected(NOW.plusSeconds(1)),
                new Eta.Unknown())) {
            for (boolean learning : new boolean[] {false, true}) {
                assertTrue(TextWidth.of(CheckTimesText.ofTotal(longest, eta, learning, NOW)) <= widest);
            }
        }
        assertTrue(TextWidth.of(CheckTimesText.ofTotal(
                        new Check(
                                "verify",
                                Checks.PENDING,
                                Option.of(new CheckRun(
                                        1,
                                        "Build",
                                        "",
                                        Option.none(),
                                        Option.none(),
                                        1,
                                        NOW.minusSeconds(3599),
                                        1,
                                        "push"))),
                        new Eta.Unknown(),
                        false,
                        NOW))
                <= widest);
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
