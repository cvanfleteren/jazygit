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
    void everyThemeStylesThePanelBorders() {
        for (String name : Theme.NAMES) {
            var engine = Theme.createEngine().get();
            Theme.activate(engine, name);

            assertTrue(engine.resolve(new Node("Panel", Set.of())).borderColor().isPresent(), name);
        }
    }

    @Test
    void ctrlTIsTheToggleKey() {
        assertTrue(Theme.isThemeKey(KeyEvent.ofChar('t', KeyModifiers.CTRL)));
        assertFalse(Theme.isThemeKey(KeyEvent.ofChar('t')));
        assertFalse(Theme.isThemeKey(KeyEvent.ofChar('x', KeyModifiers.CTRL)));
    }
}
