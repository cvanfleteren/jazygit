package net.vanfleteren.jazygit.ui.widgets;

import static dev.tamboui.toolkit.Toolkit.handleTextInputKey;
import static dev.tamboui.toolkit.Toolkit.row;
import static dev.tamboui.toolkit.Toolkit.text;
import static dev.tamboui.toolkit.Toolkit.textInput;

import dev.tamboui.layout.Constraint;
import dev.tamboui.toolkit.element.StyledElement;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.input.TextInputState;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.ui.Theme;
import net.vanfleteren.jazygit.ui.edit.LineEdit;

/**
 * The line above a list to type what it shows in, after a dimmed {@code /}. The list keeps the focus while the line
 * is open, and passes it the keys that edit it. Its text lives in the model; the line only keeps where its cursor
 * is.
 */
public final class FilterLine {

    private final TextInputState input = new TextInputState();

    /**
     * The text of the line, {@code text} before, after {@code event}, if it edits it: any character goes in it, the
     * arrows to the sides move in it, and the {@link LineEdit readline keys} work. Home and End are left to the
     * list.
     */
    public Option<String> edit(String text, KeyEvent event) {
        sync(text);
        boolean edited = LineEdit.handle(input, event)
                || (event.code() != KeyCode.HOME && event.code() != KeyCode.END && handleTextInputKey(input, event));
        return Option.when(edited, input::text);
    }

    /**
     * The line, showing {@code text}.
     */
    public StyledElement<?> field(String text) {
        sync(text);
        return row(
                        text(ListFilter.OPEN_KEY + " ")
                                .addClass(Theme.Classes.DIM)
                                .length(2),
                        textInput(input)
                                .focusable(false)
                                .cursorRequiresFocus(false)
                                .constraint(Constraint.fill()))
                .length(1);
    }

    /**
     * Makes the line show {@code text}, with the cursor at its end when it was typed elsewhere.
     */
    private void sync(String text) {
        if (!input.text().equals(text)) {
            input.setText(text);
            input.moveCursorToEnd();
        }
    }
}
