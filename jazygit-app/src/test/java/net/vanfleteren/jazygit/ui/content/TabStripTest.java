package net.vanfleteren.jazygit.ui.content;

import static org.junit.jupiter.api.Assertions.*;

import dev.tamboui.text.Span;
import io.vavr.collection.List;
import net.vanfleteren.jazygit.gh.model.Build.Outcome;
import net.vanfleteren.jazygit.ui.theme.Theme;
import org.junit.jupiter.api.Test;

/**
 * The tabs of the workflows of a branch's builds on one line (ADR 131).
 */
class TabStripTest {

    private static final List<TabStrip.Tab> TABS = List.of(
            new TabStrip.Tab("Build", Outcome.PASSED),
            new TabStrip.Tab("Lint", Outcome.FAILED),
            new TabStrip.Tab("E2E", Outcome.PASSED),
            new TabStrip.Tab("Docker", Outcome.RUNNING),
            new TabStrip.Tab("Docs", Outcome.PASSED),
            new TabStrip.Tab("Release", Outcome.FAILED));

    private static String line(int shown, int width) {
        return List.ofAll(
                        TabStrip.line(TABS, shown, width, Theme.SpanStyles.NONE).spans())
                .map(Span::content)
                .mkString();
    }

    @Test
    void allTabsShowWhenTheyFit() {
        assertEquals("✓ Build │ ✗ Lint │ ✓ E2E │ ○ Docker │ ✓ Docs │ ✗ Release", line(0, 80));
    }

    @Test
    void thoseThatDoNotFitAreCountedWithHowManyFailed() {
        assertEquals("‹ 3 ✗1 │ ○ Docker │ ✓ Docs │ 1 ✗1 ›", line(3, 36));
        assertEquals("✓ Build │ ✗ Lint │ ✓ E2E │ 3 ✗1 ›", line(0, 36));
        assertEquals("‹ 3 ✗1 │ ○ Docker │ ✓ Docs │ ✗ Release", line(5, 40));
    }

    @Test
    void theTabShownShowsHoweverNarrow() {
        assertTrue(line(3, 5).contains("○ Docker"), line(3, 5));
    }
}
