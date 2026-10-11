package net.vanfleteren.jazygit.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tamboui.css.Styleable;
import dev.tamboui.style.Color;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.KeyModifiers;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ThemeTest {

    private record Node(String styleType, Set<String> cssClasses) implements Styleable {
        public Optional<String> cssId() {
            return Optional.empty();
        }

        public Optional<Styleable> cssParent() {
            return Optional.empty();
        }
    }

    @Test
    void focusedPanelBorderUsesTheAccentColor() {
        var engine = Theme.createEngine();

        var plain = engine.resolve(new Node("Panel", Set.of()));
        var focused = engine.resolve(new Node("Panel", Set.of(Theme.Classes.FOCUSED)));

        assertEquals(Optional.of(Color.WHITE), plain.borderColor());
        assertEquals(Optional.of(Color.CYAN), focused.borderColor());
    }

    @Test
    void activateSwitchesTheStyles() {
        var engine = Theme.createEngine();
        assertEquals(Optional.of(Theme.DARK), engine.getActiveStylesheet());

        Theme.activate(Theme.LIGHT);
        assertEquals(Optional.of(Theme.LIGHT), engine.getActiveStylesheet());
        Theme.activate(Theme.DARCULA);
        assertEquals(Optional.of(Theme.DARCULA), engine.getActiveStylesheet());
        Theme.activate("nonsense");
        assertEquals(Optional.of(Theme.DARCULA), engine.getActiveStylesheet());
    }

    @Test
    void everyThemeStylesThePanelBorders() {
        for (String name : Theme.NAMES) {
            var engine = Theme.createEngine();
            Theme.activate(name);

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
