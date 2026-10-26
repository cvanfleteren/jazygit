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
    void howLongToTheNearestFiveSeconds() {
        assertEquals("0s", CheckTimesText.duration(Duration.ofSeconds(2)));
        assertEquals("5s", CheckTimesText.duration(Duration.ofSeconds(3)));
        assertEquals("1m", CheckTimesText.duration(Duration.ofSeconds(59)));
        assertEquals("1m 55s", CheckTimesText.duration(Duration.ofSeconds(117)));
        assertEquals("59m", CheckTimesText.duration(Duration.ofMinutes(59)));
        assertEquals("1h 5m", CheckTimesText.duration(Duration.ofMinutes(65)));
        assertEquals("0s", CheckTimesText.duration(Duration.ofSeconds(-5)));
    }

    @Test
    void howLongStillToTheNearestFiveSeconds() {
        assertEquals("<5s", CheckTimesText.left(Duration.ofSeconds(2)));
        assertEquals("<5s", CheckTimesText.left(Duration.ofSeconds(-30)));
        assertEquals("~5s", CheckTimesText.left(Duration.ofSeconds(3)));
        assertEquals("~1m 30s", CheckTimesText.left(Duration.ofSeconds(89)));
    }

    @Test
    void whenTheChecksOfABranchAreDoneInBrief() {
        assertEquals(Option.none(), CheckTimesText.brief(new Eta.Done(), false, NOW));
        assertEquals(Option.of("?"), CheckTimesText.brief(new Eta.Unknown(), false, NOW));
        assertEquals(Option.of("…"), CheckTimesText.brief(new Eta.Unknown(), true, NOW));
        assertEquals(Option.of("~4m"), CheckTimesText.brief(new Eta.Expected(NOW.plusSeconds(240)), false, NOW));
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
                Option.of("done in <5s"), CheckTimesText.together(new Eta.Expected(NOW.minusSeconds(60)), false, NOW));
        assertEquals(
                Option.of("taking longer than usual"),
                CheckTimesText.together(new Eta.Expected(NOW.minusSeconds(61)), false, NOW));
    }
}
