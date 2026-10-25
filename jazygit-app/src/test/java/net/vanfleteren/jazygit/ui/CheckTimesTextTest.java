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
    void howLongInWholeMinutes() {
        assertEquals("<1m", CheckTimesText.duration(Duration.ofSeconds(59)));
        assertEquals("1m", CheckTimesText.duration(Duration.ofSeconds(119)));
        assertEquals("59m", CheckTimesText.duration(Duration.ofMinutes(59)));
        assertEquals("1h 5m", CheckTimesText.duration(Duration.ofMinutes(65)));
        assertEquals("<1m", CheckTimesText.duration(Duration.ofSeconds(-5)));
    }

    @Test
    void howLongStillToTheNearestMinute() {
        assertEquals("<1m", CheckTimesText.left(Duration.ofSeconds(59)));
        assertEquals("<1m", CheckTimesText.left(Duration.ofSeconds(-30)));
        assertEquals("~1m", CheckTimesText.left(Duration.ofSeconds(89)));
        assertEquals("~2m", CheckTimesText.left(Duration.ofSeconds(90)));
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
                Option.of("done in <1m"), CheckTimesText.together(new Eta.Expected(NOW.minusSeconds(60)), false, NOW));
        assertEquals(
                Option.of("taking longer than usual"),
                CheckTimesText.together(new Eta.Expected(NOW.minusSeconds(61)), false, NOW));
    }
}
