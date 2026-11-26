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
 * The fields of a form show what the model has, and the boxes tick on Space or Enter.
 */
class FormFieldsTest {

    private final AtomicReference<List<String>> done = new AtomicReference<>(List.empty());
    private final FormKeys keys = new FormKeys(() -> did("cancel"), () -> did("submit"));

    @Test
    void theTitleShowsWhatTheModelHas() {
        TitleField title = new TitleField("title", "label", "placeholder");

        title.render("Fix the parser", keys, text -> {});

        assertEquals("Fix the parser", title.text());
    }

    @Test
    void theBodyShowsWhatTheModelHas() {
        BodyField body = new BodyField("body", "label", "placeholder");

        body.render("Line one\nLine two", keys, text -> {});

        assertEquals("Line one\nLine two", body.text());
    }

    @Test
    void aBoxSaysWhetherItIsTicked() {
        assertEquals("[x]", CheckBox.box(true));
        assertEquals("[ ]", CheckBox.box(false));
    }

    @Test
    void spaceAndEnterTickABox() {
        var box = CheckBox.of("draft", false, () -> did("toggled"), keys);

        assertEquals(EventResult.HANDLED, box.handleKeyEvent(KeyEvent.ofChar(' '), true));
        assertEquals(EventResult.HANDLED, box.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER), true));
        assertEquals(List.of("toggled", "toggled"), done.get());
    }

    @Test
    void aBoxTakesTheKeysOfTheForm() {
        var box = CheckBox.of("draft", false, () -> did("toggled"), keys);

        box.handleKeyEvent(KeyEvent.ofKey(KeyCode.ESCAPE), true);
        box.handleKeyEvent(KeyEvent.ofChar('s', KeyModifiers.CTRL), true);

        assertEquals(List.of("cancel", "submit"), done.get());
    }

    private void did(String what) {
        done.updateAndGet(list -> list.append(what));
    }
}
