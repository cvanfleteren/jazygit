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
 * letter. A filter with an uppercase letter also matches the first letters of words following each other (ADR 106):
 * {@code CCM} matches {@code CapitalCaseMatching.java} and {@code can-can-mama.txt}. Whether it is open, and its text,
 * live in the {@link net.vanfleteren.jazygit.state.ListCursor}.
 */
public final class ListFilter {

    static final char OPEN_KEY = '/';

    private ListFilter() {}

    /**
     * Whether {@code text} contains {@code filter}: ignoring case, unless {@code filter} has an uppercase letter. One
     * with an uppercase letter also matches the first letters of words following each other in {@code text}.
     */
    static boolean matches(String filter, String text) {
        return filter.chars().anyMatch(Character::isUpperCase)
                ? text.contains(filter) || matchesInitials(filter, text)
                : text.toLowerCase(Locale.ROOT).contains(filter.toLowerCase(Locale.ROOT));
    }

    /**
     * Whether a row showing {@code text} stays shown: always while there is no filter.
     */
    public static boolean matches(Option<String> filter, String text) {
        return filter.forAll(typed -> matches(typed, text));
    }

    /**
     * Whether {@code filter}, ignoring case, is the first letters of words following each other in {@code text}.
     */
    static boolean matchesInitials(String filter, String text) {
        return initials(text).contains(filter.toLowerCase(Locale.ROOT));
    }

    /**
     * The first letter of each word of {@code text}, in lowercase. A word starts after anything other than a letter or
     * digit, such as {@code -}, {@code _}, {@code .} or {@code /}; at an uppercase letter after a lowercase one or a
     * digit; and at the last of a run of uppercase letters when a lowercase one follows: {@code HTTPServer} has two.
     */
    static String initials(String text) {
        var initials = new StringBuilder();
        for (var index = 0; index < text.length(); index++) {
            if (startsWord(text, index)) {
                initials.append(Character.toLowerCase(text.charAt(index)));
            }
        }
        return initials.toString();
    }

    private static boolean startsWord(String text, int index) {
        var current = text.charAt(index);
        if (!Character.isLetterOrDigit(current)) {
            return false;
        }
        if (index == 0) {
            return true;
        }
        var previous = text.charAt(index - 1);
        if (!Character.isLetterOrDigit(previous)) {
            return true;
        }
        if (!Character.isUpperCase(current)) {
            return false;
        }
        return !Character.isUpperCase(previous)
                || index + 1 < text.length() && Character.isLowerCase(text.charAt(index + 1));
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
