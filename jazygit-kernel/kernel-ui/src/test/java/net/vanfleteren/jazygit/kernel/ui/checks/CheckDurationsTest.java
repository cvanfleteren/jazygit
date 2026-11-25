package net.vanfleteren.jazygit.kernel.ui.checks;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class CheckDurationsTest {

    @Test
    void underAMinuteIsInSeconds() {
        assertEquals("47s", CheckDurations.exact(Duration.ofSeconds(47)));
    }

    @Test
    void aWholeMinuteLeavesOutItsSeconds() {
        assertEquals("2m", CheckDurations.exact(Duration.ofMinutes(2)));
    }

    @Test
    void underAnHourIsToTheSecond() {
        assertEquals("7m 41s", CheckDurations.exact(Duration.ofSeconds(7 * 60 + 41)));
    }

    @Test
    void fromAnHourOnIsToTheMinute() {
        assertEquals("1h 5m", CheckDurations.exact(Duration.ofSeconds(3600 + 5 * 60 + 59)));
        assertEquals("2h", CheckDurations.exact(Duration.ofHours(2)));
    }

    @Test
    void aTimeBeforeItsStartIsNoTime() {
        assertEquals("0s", CheckDurations.exact(Duration.ofSeconds(-3)));
    }
}
