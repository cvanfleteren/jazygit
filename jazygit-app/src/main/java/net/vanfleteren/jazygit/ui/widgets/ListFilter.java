package net.vanfleteren.jazygit.ui.widgets;

import static dev.tamboui.toolkit.Toolkit.text;

import dev.tamboui.toolkit.element.StyledElement;
import dev.tamboui.tui.event.KeyEvent;
import io.vavr.collection.Array;
import io.vavr.control.Option;
import java.util.Locale;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.ui.Theme;

/**
 * The filter of a list in a popup. Typing {@code /} opens a {@link FilterLine} above the list to type it in, and only
 * the rows that contain it stay shown, until Escape closes it again. It ignores case, unless it has an uppercase
 * letter. A filter of two words or more, each started by an uppercase letter, also matches the starts of words
 * following each other (ADR 106): {@code CCM} and {@code CCMat} match {@code CapitalCaseMatching.java}, {@code CCM}
 * matches {@code can-can-mama.txt}. Whether it is open, and its text, live in the
 * {@link net.vanfleteren.jazygit.state.ListCursor}.
 */
public final class ListFilter {

    static final char OPEN_KEY = '/';

    private ListFilter() {}

    /**
     * Whether {@code text} contains {@code filter}: ignoring case, unless {@code filter} has an uppercase letter. One
     * of two words or more also matches the starts of words following each other in {@code text}.
     */
    static boolean matches(String filter, String text) {
        return filter.chars().anyMatch(Character::isUpperCase)
                ? text.contains(filter) || matchesWordStarts(filter, text)
                : text.toLowerCase(Locale.ROOT).contains(filter.toLowerCase(Locale.ROOT));
    }

    /**
     * Whether a row showing {@code text} stays shown: always while there is no filter.
     */
    public static boolean matches(Option<String> filter, String text) {
        return filter.forAll(typed -> matches(typed, text));
    }

    /**
     * Whether the words typed in {@code filter} start words following each other in {@code text}. A word typed
     * starts at each uppercase letter; it takes two or more, of letters and digits only. The first letter of each
     * matches ignoring case, the others as typed: {@code CCMat} matches {@code CapitalCaseMatching}, {@code CCM}
     * matches {@code can-can-mama}.
     */
    static boolean matchesWordStarts(String filter, String text) {
        var typed = wordsTyped(filter);
        var starts = wordStarts(text);
        return typed.size() >= 2
                && filter.chars().allMatch(Character::isLetterOrDigit)
                && Array.rangeClosed(0, starts.size() - typed.size())
                        .exists(first -> typed.zipWithIndex()
                                .forAll(word -> startsWith(text, starts.get(first + word._2), word._1)));
    }

    /**
     * The words typed in {@code filter}: a new one starts at each uppercase letter.
     */
    static Array<String> wordsTyped(String filter) {
        var starts = Array.range(0, filter.length())
                .filter(index -> index == 0 || Character.isUpperCase(filter.charAt(index)));
        return starts.zipWith(starts.drop(1).append(filter.length()), filter::substring);
    }

    /**
     * Where the words of {@code text} start. A word starts after anything other than a letter or digit, such as
     * {@code -}, {@code _}, {@code .} or {@code /}; at an uppercase letter after a lowercase one or a digit; and at the
     * last of a run of uppercase letters when a lowercase one follows: {@code HTTPServer} has two.
     */
    static Array<Integer> wordStarts(String text) {
        return Array.range(0, text.length()).filter(index -> startsWord(text, index));
    }

    private static boolean startsWith(String text, int start, String word) {
        return Character.toLowerCase(text.charAt(start)) == Character.toLowerCase(word.charAt(0))
                && text.startsWith(word.substring(1), start + 1);
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
