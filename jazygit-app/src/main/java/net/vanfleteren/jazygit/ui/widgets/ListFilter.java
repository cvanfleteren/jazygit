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
import java.util.Locale;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.ui.Theme;
import net.vanfleteren.jazygit.ui.edit.LineEdit;

/**
 * The filter of a list in a popup. Typing {@code /} opens a line above the list to type it in, and only the rows
 * that contain it stay shown, until Escape closes it again. It ignores case, unless it has an uppercase letter.
 * The list keeps the focus while the filter is open, and passes it the keys that edit it. Whether it is open, and
 * its text, live in the {@link net.vanfleteren.jazygit.state.ListCursor}; the line only keeps where its cursor is.
 */
final class ListFilter {

    private static final char OPEN_KEY = '/';
    private final TextInputState input = new TextInputState();

    /**
     * Whether {@code text} contains {@code filter}: ignoring case, unless {@code filter} has an uppercase letter.
     */
    static boolean matches(String filter, String text) {
        return filter.chars().anyMatch(Character::isUpperCase)
                ? text.contains(filter)
                : text.toLowerCase(Locale.ROOT).contains(filter.toLowerCase(Locale.ROOT));
    }

    /**
     * Whether a row showing {@code text} stays shown: always while there is no filter.
     */
    static boolean matches(Option<String> filter, String text) {
        return filter.forAll(typed -> matches(typed, text));
    }

    /**
     * Whether {@code event} is the key that opens the filter.
     */
    static boolean opens(KeyEvent event) {
        return event.isChar(OPEN_KEY);
    }

    /**
     * The text of the open {@code filter} after {@code event}, if it edits it: any character goes in it, the arrows
     * to the sides move in it, and the {@link LineEdit readline keys} work. Home and End are left to the list.
     */
    Option<String> edit(String filter, KeyEvent event) {
        sync(filter);
        boolean edited = LineEdit.handle(input, event)
                || (event.code() != KeyCode.HOME && event.code() != KeyCode.END && handleTextInputKey(input, event));
        return Option.when(edited, input::text);
    }

    /**
     * The line to type {@code filter} in, above the list.
     */
    StyledElement<?> field(String filter) {
        sync(filter);
        return row(
                        text(OPEN_KEY + " ").addClass(Theme.Classes.DIM).length(2),
                        textInput(input)
                                .focusable(false)
                                .cursorRequiresFocus(false)
                                .constraint(Constraint.fill()))
                .length(1);
    }

    /**
     * Makes the line show {@code filter}, with the cursor at its end when it was typed elsewhere.
     */
    private void sync(String filter) {
        if (!input.text().equals(filter)) {
            input.setText(filter);
            input.moveCursorToEnd();
        }
    }

    /**
     * The dimmed line shown instead of the rows when none of them contains the filter.
     */
    static StyledElement<?> noMatches() {
        return text(Messages.get("dialog.filter.noMatches")).addClass(Theme.Classes.DIM);
    }
}
