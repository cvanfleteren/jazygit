package net.vanfleteren.jazygit.kernel.ui.widgets;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.KeyModifiers;
import io.vavr.collection.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * The keys every field of a form takes, and those of the form's own after them.
 */
class FormKeysTest {

    private final AtomicReference<List<String>> done = new AtomicReference<>(List.empty());

    private final FormKeys keys = new FormKeys(() -> did("cancel"), () -> did("submit"), event -> {
        if (event.hasCtrl() && event.isChar('o')) {
            did("editor");
            return EventResult.HANDLED;
        }
        return EventResult.UNHANDLED;
    });

    @Test
    void escapeCancels() {
        assertEquals(EventResult.HANDLED, keys.handle(KeyEvent.ofKey(KeyCode.ESCAPE)));
        assertEquals(List.of("cancel"), done.get());
    }

    @Test
    void ctrlSSubmits() {
        assertEquals(EventResult.HANDLED, keys.handle(KeyEvent.ofChar('s', KeyModifiers.CTRL)));
        assertEquals(List.of("submit"), done.get());
    }

    @Test
    void anSWithoutCtrlIsLeftToTheField() {
        assertEquals(EventResult.UNHANDLED, keys.handle(KeyEvent.ofChar('s')));
        assertEquals(List.empty(), done.get());
    }

    @Test
    void theFormsOwnKeysComeAfter() {
        assertEquals(EventResult.HANDLED, keys.handle(KeyEvent.ofChar('o', KeyModifiers.CTRL)));
        assertEquals(List.of("editor"), done.get());
    }

    @Test
    void aFormWithoutKeysOfItsOwnLeavesTheOthers() {
        FormKeys plain = new FormKeys(() -> did("cancel"), () -> did("submit"));

        assertEquals(EventResult.UNHANDLED, plain.handle(KeyEvent.ofChar('o', KeyModifiers.CTRL)));
        assertEquals(List.empty(), done.get());
    }

    private void did(String what) {
        done.updateAndGet(list -> list.append(what));
    }
}
