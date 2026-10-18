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
import java.util.Locale;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.ui.Theme;
import net.vanfleteren.jazygit.ui.edit.LineEdit;

/**
 * The filter of a list in a popup. Typing {@code /} opens a line above the list to type it in, and only the rows
 * that contain it stay shown, until Escape closes it again. It ignores case, unless it has an uppercase letter.
 * The list keeps the focus while the filter is open, and passes it the keys that edit it.
 */
final class ListFilter {

    private static final char OPEN_KEY = '/';
    private final TextInputState input = new TextInputState();
    private boolean open;

    /**
     * Whether {@code text} contains {@code filter}: ignoring case, unless {@code filter} has an uppercase letter.
     */
    static boolean matches(String filter, String text) {
        return filter.chars().anyMatch(Character::isUpperCase)
                ? text.contains(filter)
                : text.toLowerCase(Locale.ROOT).contains(filter.toLowerCase(Locale.ROOT));
    }

    /**
     * Whether a row showing {@code text} stays shown: always while the filter is closed.
     */
    boolean matches(String text) {
        return !open || matches(input.text(), text);
    }

    boolean isOpen() {
        return open;
    }

    /**
     * Whether {@code event} is the key that opens the filter.
     */
    static boolean opens(KeyEvent event) {
        return event.isChar(OPEN_KEY);
    }

    void open() {
        open = true;
    }

    /**
     * Closes the filter and forgets its text, so all rows show again.
     */
    void close() {
        open = false;
        input.clear();
    }

    /**
     * Edits the text with {@code event}, while the filter is open: any character goes in it, the arrows to the
     * sides move in it, and the {@link LineEdit readline keys} work. Home and End are left to the list.
     *
     * @return whether it did
     */
    boolean edit(KeyEvent event) {
        return open
                && (LineEdit.handle(input, event)
                        || (event.code() != KeyCode.HOME
                                && event.code() != KeyCode.END
                                && handleTextInputKey(input, event)));
    }

    /**
     * The line to type the filter in, above the list.
     */
    StyledElement<?> field() {
        return row(
                        text(OPEN_KEY + " ").addClass(Theme.Classes.DIM).length(2),
                        textInput(input)
                                .focusable(false)
                                .cursorRequiresFocus(false)
                                .constraint(Constraint.fill()))
                .length(1);
    }

    /**
     * The dimmed line shown instead of the rows when none of them contains the filter.
     */
    static StyledElement<?> noMatches() {
        return text(Messages.get("dialog.filter.noMatches")).addClass(Theme.Classes.DIM);
    }
}
