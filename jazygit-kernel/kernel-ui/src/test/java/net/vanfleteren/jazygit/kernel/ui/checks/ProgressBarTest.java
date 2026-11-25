package net.vanfleteren.jazygit.kernel.ui.checks;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ProgressBarTest {

    @Test
    void sayAHundredPercentOnlyOnceFull() {
        assertEquals(99, ProgressBar.percent(0.999));
        assertEquals(100, ProgressBar.percent(1));
    }

    @Test
    void isFullOnlyOnceDone() {
        assertEquals(2, ProgressBar.of(0.999, 10, "done").size());
        assertEquals(1, ProgressBar.of(1, 10, "done").size());
    }

    @Test
    void hasNoDonePartAtTheStart() {
        assertEquals(1, ProgressBar.of(0, 10, "done").size());
    }
}
