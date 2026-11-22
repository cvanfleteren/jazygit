package net.vanfleteren.jazygit.kernel.ui.widgets;

import static dev.tamboui.toolkit.Toolkit.text;

import dev.tamboui.toolkit.element.StyledElement;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;
import net.vanfleteren.jazygit.kernel.ui.text.TextWidth;
import net.vanfleteren.jazygit.kernel.ui.theme.Theme;

/**
 * How the checks of a commit are doing, drawn the same by every forge (ADR 48, 175): the symbol of each state, how
 * many are in each, and a check by its name.
 */
public final class CheckSymbols {

    public static final Symbol PASSED = new Symbol("✓", Theme.Classes.CHECKS_PASSED);
    public static final Symbol FAILED = new Symbol("✗", Theme.Classes.CHECKS_FAILED);
    public static final Symbol RUNNING = new Symbol("●", Theme.Classes.CHECKS_RUNNING);
    public static final Symbol QUEUED = new Symbol("◌", Theme.Classes.DIM);

    private CheckSymbols() {}

    /**
     * How many checks failed, are running and passed, each after its symbol, those in none of them left out:
     * {@code ✗ 1 failed · ● 2 running}; nothing when none are in any.
     */
    public static List<StyledElement<?>> counts(int failed, int running, int passed) {
        return joined(List.of(
                        count(FAILED, failed, "checks.failed"),
                        count(RUNNING, running, "checks.running"),
                        count(PASSED, passed, "checks.passed"))
                .flatMap(o -> o));
    }

    /**
     * {@code parts}, each a run of pieces, with {@code " · "} between them.
     */
    public static List<StyledElement<?>> joined(List<List<StyledElement<?>>> parts) {
        return parts.zipWithIndex().flatMap(indexed -> indexed._2 > 0 ? indexed._1.prepend(text(" · ")) : indexed._1);
    }

    private static Option<List<StyledElement<?>>> count(Symbol symbol, int count, String key) {
        return Option.when(
                count > 0, () -> List.of(symbol.element(), text(" " + Messages.get(key, String.valueOf(count)))));
    }

    /**
     * A check by its name, after {@code symbol}, taking at most {@code width} columns: the name is cut to leave room.
     */
    public static List<StyledElement<?>> named(Symbol symbol, String name, int width) {
        return List.of(symbol.element(), text(" " + TextWidth.cut(name, width - TextWidth.of(symbol.text()) - 1)));
    }
}
