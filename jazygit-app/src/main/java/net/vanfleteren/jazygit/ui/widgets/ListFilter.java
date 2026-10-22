package net.vanfleteren.jazygit.ui.widgets;

import static dev.tamboui.toolkit.Toolkit.text;

import dev.tamboui.toolkit.element.StyledElement;
import dev.tamboui.tui.event.KeyEvent;
import io.vavr.control.Option;
import java.util.Locale;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.ui.Theme;

/**
 * The filter of a list in a popup. Typing {@code /} opens a {@link FilterLine} above the list to type it in, and only
 * the rows that contain it stay shown, until Escape closes it again. It ignores case, unless it has an uppercase
 * letter. Whether it is open, and its text, live in the {@link net.vanfleteren.jazygit.state.ListCursor}.
 */
public final class ListFilter {

    static final char OPEN_KEY = '/';

    private ListFilter() {}

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
    public static boolean matches(Option<String> filter, String text) {
        return filter.forAll(typed -> matches(typed, text));
    }

    /**
     * Whether {@code event} is the key that opens the filter.
     */
    static boolean opens(KeyEvent event) {
        return event.isChar(OPEN_KEY);
    }

    /**
     * The dimmed line shown instead of the rows when none of them contains the filter.
     */
    public static StyledElement<?> noMatches() {
        return text(Messages.get("dialog.filter.noMatches")).addClass(Theme.Classes.DIM);
    }
}
