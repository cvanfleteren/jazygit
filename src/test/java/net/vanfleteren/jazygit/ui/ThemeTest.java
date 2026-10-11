package net.vanfleteren.jazygit.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.tamboui.css.Styleable;
import dev.tamboui.style.Color;
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
}
