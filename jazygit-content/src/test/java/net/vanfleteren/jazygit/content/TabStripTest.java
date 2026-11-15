package net.vanfleteren.jazygit.content;

import static org.junit.jupiter.api.Assertions.*;

import dev.tamboui.text.Span;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.model.Build.Outcome;
import net.vanfleteren.jazygit.ui.theme.Theme;
import org.junit.jupiter.api.Test;

/**
 * The tabs of the workflows of a branch's builds on one line (ADR 131).
 */
class TabStripTest {

    private static final List<TabStrip.Tab> TABS = List.of(
            new TabStrip.Tab("Build", Option.of(Outcome.PASSED)),
            new TabStrip.Tab("Lint", Option.of(Outcome.FAILED)),
            new TabStrip.Tab("E2E", Option.of(Outcome.PASSED)),
            new TabStrip.Tab("Docker", Option.of(Outcome.RUNNING)),
            new TabStrip.Tab("Docs", Option.of(Outcome.PASSED)),
            new TabStrip.Tab("Release", Option.of(Outcome.FAILED)));

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

    @Test
    void aColumnIsOnATabOrBetweenThem() {
        // "✓ Build │ ✗ Lint │ …": Build from 0 to 6, the line between, Lint from 10.
        assertEquals(Option.of(0), TabStrip.at(TABS, 0, 80, 0));
        assertEquals(Option.none(), TabStrip.at(TABS, 0, 80, 8));
        assertEquals(Option.of(1), TabStrip.at(TABS, 0, 80, 10));
    }

    @Test
    void aCountLeadsToTheFirstTabItLeavesOut() {
        // "‹ 3 ✗1 │ ○ Docker │ ✓ Docs │ 1 ✗1 ›"
        assertEquals(Option.of(2), TabStrip.at(TABS, 3, 36, 1));
        assertEquals(Option.of(3), TabStrip.at(TABS, 3, 36, 9));
        assertEquals(Option.of(5), TabStrip.at(TABS, 3, 36, 32));
    }
}
