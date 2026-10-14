package net.vanfleteren.jazygit.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tamboui.css.Styleable;
import dev.tamboui.style.Color;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.KeyModifiers;
import org.junit.jupiter.api.Test;

import java.util.Set;

class ThemeTest {

    private record Node(String styleType, Set<String> cssClasses) implements Styleable {
        public java.util.Optional<String> cssId() {
            return java.util.Optional.empty();
        }

        public java.util.Optional<Styleable> cssParent() {
            return java.util.Optional.empty();
        }
    }

    @Test
    void focusedPanelBorderUsesTheAccentColor() {
        var engine = Theme.createEngine().get();

        var plain = engine.resolve(new Node("Panel", Set.of()));
        var focused = engine.resolve(new Node("Panel", Set.of(Theme.Classes.FOCUSED)));

        assertEquals(java.util.Optional.of(Color.WHITE), plain.borderColor());
        assertEquals(java.util.Optional.of(Color.CYAN), focused.borderColor());
    }

    @Test
    void activateSwitchesTheStyles() {
        var engine = Theme.createEngine().get();
        assertEquals(java.util.Optional.of(Theme.DARK), engine.getActiveStylesheet());

        Theme.activate(engine, Theme.LIGHT);
        assertEquals(java.util.Optional.of(Theme.LIGHT), engine.getActiveStylesheet());
        Theme.activate(engine, Theme.DARCULA);
        assertEquals(java.util.Optional.of(Theme.DARCULA), engine.getActiveStylesheet());
        Theme.activate(engine, "nonsense");
        assertEquals(java.util.Optional.of(Theme.DARCULA), engine.getActiveStylesheet());
    }

    @Test
    void theColourBlindThemeShowsGoodAndBadInBlueAndVermillion() {
        var engine = Theme.createEngine().get();
        Theme.activate(engine, Theme.DARK_COLORBLIND);
        Theme.SpanStyles styles = Theme.spanStyles(engine);

        assertEquals(java.util.Optional.of(Color.hex("#56b4e9")), styles.of(Theme.Classes.DIFF_ADDED).fg());
        assertEquals(java.util.Optional.of(Color.hex("#d55e00")), styles.of(Theme.Classes.DIFF_REMOVED).fg());
    }

    @Test
    void everyThemeStylesThePanelBorders() {
        for (String name : Theme.NAMES) {
            var engine = Theme.createEngine().get();
            Theme.activate(engine, name);

            assertTrue(engine.resolve(new Node("Panel", Set.of())).borderColor().isPresent(), name);
        }
    }
}
