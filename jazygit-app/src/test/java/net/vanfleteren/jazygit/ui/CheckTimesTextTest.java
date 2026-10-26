package net.vanfleteren.jazygit.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.control.Option;
import java.time.Duration;
import java.time.Instant;
import net.vanfleteren.jazygit.gh.timing.Eta;
import org.junit.jupiter.api.Test;

class CheckTimesTextTest {

    private static final Instant NOW = Instant.parse("2026-10-25T10:00:00Z");

    @Test
    void howLongToTheNearestFiveSecondsUnderFiveMinutes() {
        assertEquals("0s", CheckTimesText.duration(Duration.ofSeconds(2)));
        assertEquals("5s", CheckTimesText.duration(Duration.ofSeconds(3)));
        assertEquals("45s", CheckTimesText.duration(Duration.ofSeconds(44)));
        assertEquals("1m", CheckTimesText.duration(Duration.ofSeconds(58)));
        assertEquals("2m 5s", CheckTimesText.duration(Duration.ofSeconds(126)));
        assertEquals("4m 55s", CheckTimesText.duration(Duration.ofSeconds(297)));
        assertEquals("0s", CheckTimesText.duration(Duration.ofSeconds(-5)));
    }

    @Test
    void howLongToTheNearestMinuteFromFiveMinutesOn() {
        assertEquals("5m", CheckTimesText.duration(Duration.ofSeconds(298)));
        assertEquals("7m", CheckTimesText.duration(Duration.ofSeconds(7 * 60 + 29)));
        assertEquals("8m", CheckTimesText.duration(Duration.ofSeconds(7 * 60 + 30)));
        assertEquals("1h 5m", CheckTimesText.duration(Duration.ofMinutes(65)));
    }

    @Test
    void howLongStillRoundedAsHowLong() {
        assertEquals("~5s", CheckTimesText.left(Duration.ofSeconds(3)));
        assertEquals("~1m 30s", CheckTimesText.left(Duration.ofSeconds(89)));
        assertEquals("~7m", CheckTimesText.left(Duration.ofSeconds(7 * 60 + 10)));
    }

    @Test
    void whenTheChecksOfABranchAreDoneInBriefInWholeMinutes() {
        assertEquals(Option.none(), CheckTimesText.brief(new Eta.Done(), false, NOW));
        assertEquals(Option.of("?"), CheckTimesText.brief(new Eta.Unknown(), false, NOW));
        assertEquals(Option.of("…"), CheckTimesText.brief(new Eta.Unknown(), true, NOW));
        assertEquals(Option.of("~4m"), CheckTimesText.brief(new Eta.Expected(NOW.plusSeconds(240)), false, NOW));
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
}
