package net.vanfleteren.jazygit.ui;

import static org.junit.jupiter.api.Assertions.*;

import dev.tamboui.css.Styleable;
import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import java.util.Set;
import net.vanfleteren.jazygit.settings.ThemeName;
import org.junit.jupiter.api.Test;

class ThemeTest {

    @Test
    void focusedPanelBorderIsHighlightedAndTheOthersDimmed() {
        var engine = Theme.createEngine().get();

        var plain = engine.resolve(new Node("Panel", Set.of()));
        var focused = engine.resolve(new Node("Panel", Set.of(Theme.Classes.FOCUSED)));

        assertEquals(java.util.Optional.of(Color.DARK_GRAY), plain.borderColor());
        assertEquals(java.util.Optional.of(Color.CYAN), focused.borderColor());
    }

    @Test
    void activateSwitchesTheStyles() {
        var engine = Theme.createEngine().get();
        assertEquals(java.util.Optional.of("dark"), engine.getActiveStylesheet());

        Theme.activate(engine, ThemeName.LIGHT);
        assertEquals(java.util.Optional.of("light"), engine.getActiveStylesheet());
        Theme.activate(engine, ThemeName.DARCULA);
        assertEquals(java.util.Optional.of("darcula"), engine.getActiveStylesheet());
    }

    @Test
    void theColourBlindThemeShowsGoodAndBadInBlueAndVermillion() {
        var engine = Theme.createEngine().get();
        Theme.activate(engine, ThemeName.DARK_COLORBLIND);
        Theme.SpanStyles styles = Theme.spanStyles(engine);

        assertEquals(
                java.util.Optional.of(Color.hex("#56b4e9")),
                styles.of(Theme.Classes.DIFF_ADDED).fg());
        assertEquals(
                java.util.Optional.of(Color.hex("#d55e00")),
                styles.of(Theme.Classes.DIFF_REMOVED).fg());
    }

    @Test
    void everyThemeBordersPopupsLikeTheFocusedPanel() {
        for (ThemeName name : ThemeName.values()) {
            var engine = Theme.createEngine().get();
            Theme.activate(engine, name);

            var popup = engine.resolve(new Node("DialogElement", Set.of())).borderColor();
            assertTrue(popup.isPresent(), name.id());
            assertEquals(
                    engine.resolve(new Node("Panel", Set.of(Theme.Classes.FOCUSED)))
                            .borderColor(),
                    popup,
                    name.id());
        }
    }

    @Test
    void everyThemeStylesThePanelBorders() {
        for (ThemeName name : ThemeName.values()) {
            var engine = Theme.createEngine().get();
            Theme.activate(engine, name);

            assertTrue(engine.resolve(new Node("Panel", Set.of())).borderColor().isPresent(), name.id());
        }
    }

    @Test
    void everyThemeHidesTheSelectionOfThePanesNotInUseAndOnlyColoursTheBackgroundOfTheOne() {
        for (ThemeName name : ThemeName.values()) {
            var engine = Theme.createEngine().get();
            Theme.activate(engine, name);
            Theme.Highlights highlights = Theme.highlights(engine);

            assertEquals(Style.EMPTY, highlights.of(false), name.id());
            // A colour of its own would hide the colours of the pieces of the row.
            assertTrue(highlights.of(true).bg().isPresent(), name.id());
            assertFalse(highlights.of(true).fg().isPresent(), name.id());
        }
    }

    @Test
    void everyThemeGivesPlainTextBehindAPopupAColourOfItsOwn() {
        for (ThemeName name : ThemeName.values()) {
            var engine = Theme.createEngine().get();
            Theme.activate(engine, name);
            Theme.Backdrop backdrop = Theme.backdrop(engine);

            assertTrue(backdrop.plain().fg().isPresent(), name.id());
            assertFalse(backdrop.plainFg().contains(backdrop.plain().fg().get()), name.id());
        }
    }

    @Test
    void theDarkThemesBrightenPlainTextOnTheHighlightedRow() {
        for (ThemeName name : java.util.List.of(
                ThemeName.DARK,
                ThemeName.DARK_COLORBLIND,
                ThemeName.DARCULA,
                ThemeName.MONO,
                ThemeName.EMBER,
                ThemeName.POWERLINE)) {
            var engine = Theme.createEngine().get();
            Theme.activate(engine, name);
            Theme.SelectedText selectedText = Theme.selectedText(engine);

            assertEquals(
                    Theme.highlights(engine).of(true).bg(), selectedText.rowBg().toJavaOptional(), name.id());
            assertTrue(selectedText.fg().isDefined(), name.id());
            assertNotEquals(selectedText.plainFg(), selectedText.fg(), name.id());
        }
    }

    @Test
    void theLightThemeKeepsPlainTextOnTheHighlightedRowAsItIs() {
        var engine = Theme.createEngine().get();
        Theme.activate(engine, ThemeName.LIGHT);

        assertTrue(Theme.selectedText(engine).fg().isEmpty());
    }

    @Test
    void aThemeWithoutHighlightRulesReversesTheSelectionOfThePaneInUse() {
        var engine = dev.tamboui.css.engine.StyleEngine.create();
        engine.addStylesheet("bare", "* { color: white; }");
        engine.setActiveStylesheet("bare");

        assertEquals(Style.EMPTY.reversed(), Theme.highlights(engine).of(true));
        assertEquals(Style.EMPTY, Theme.highlights(engine).of(false));
    }

    @Test
    void aThemeCanShowTheSelectionOfThePanesNotInUse() {
        var engine = dev.tamboui.css.engine.StyleEngine.create();
        engine.addStylesheet("dimmed", "* { color: white; } .inactive-selection { text-style: dim; }");
        engine.setActiveStylesheet("dimmed");

        assertEquals(Style.EMPTY.dim(), Theme.highlights(engine).of(false));
    }

    private record Node(String styleType, Set<String> cssClasses) implements Styleable {
        public java.util.Optional<String> cssId() {
            return java.util.Optional.empty();
        }

        public java.util.Optional<Styleable> cssParent() {
            return java.util.Optional.empty();
        }
    }
}
