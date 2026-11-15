package net.vanfleteren.jazygit.ui.content;

import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.model.Build.Outcome;
import net.vanfleteren.jazygit.ui.text.TextWidth;
import net.vanfleteren.jazygit.ui.theme.Theme;

/**
 * The tabs of the workflows of a branch's builds on one line (ADR 131): each with how its last build ended, the one
 * shown highlighted. When they do not all fit, the line shows as many around the one shown as fit, and a count of
 * those left out on either side, with how many of those failed, so that a failure is never out of sight.
 */
final class TabStrip {

    private static final String BETWEEN = " │ ";

    private TabStrip() {}

    /**
     * The line of {@code tabs}, {@code shown} the one highlighted, in {@code width} columns.
     */
    static Line line(List<Tab> tabs, int shown, int width, Theme.SpanStyles styles) {
        Window window = fit(tabs, shown, width, new Window(shown, shown));
        List<Span> left = window.from() > 0
                ? List.of(
                        counter("‹ ", tabs.take(window.from()), "", styles),
                        Span.styled(BETWEEN, styles.of(Theme.Classes.DIM)))
                : List.empty();
        List<Span> right = window.to() < tabs.size() - 1
                ? List.of(
                        Span.styled(BETWEEN, styles.of(Theme.Classes.DIM)),
                        counter("", tabs.drop(window.to() + 1), " ›", styles))
                : List.empty();
        List<Span> middle = List.range(window.from(), window.to() + 1)
                .map(index -> tab(tabs.get(index), index == shown, styles))
                .intersperse(Span.styled(BETWEEN, styles.of(Theme.Classes.DIM)));
        return Line.from(left.appendAll(middle).appendAll(right).toJavaList());
    }

    /**
     * The index of the tab at {@code column} of the line of {@code tabs}, {@code shown} the one highlighted, in
     * {@code width} columns: of a tab, or of the first one left out past a count. None between the tabs.
     */
    static Option<Integer> at(List<Tab> tabs, int shown, int width, int column) {
        Window window = fit(tabs, shown, width, new Window(shown, shown));
        List<Tuple2<Integer, Option<Integer>>> left = window.from() > 0
                ? List.of(
                        Tuple.of(
                                TextWidth.of(counted("‹ ", tabs.take(window.from()), "")),
                                Option.of(window.from() - 1)),
                        Tuple.of(BETWEEN.length(), Option.none()))
                : List.empty();
        List<Tuple2<Integer, Option<Integer>>> right = window.to() < tabs.size() - 1
                ? List.of(
                        Tuple.of(BETWEEN.length(), Option.none()),
                        Tuple.of(
                                TextWidth.of(counted("", tabs.drop(window.to() + 1), " ›")),
                                Option.of(window.to() + 1)))
                : List.empty();
        List<Tuple2<Integer, Option<Integer>>> middle = List.range(window.from(), window.to() + 1)
                .map(index -> Tuple.of(TextWidth.of(tabs.get(index).text()), Option.of(index)))
                .intersperse(Tuple.of(BETWEEN.length(), Option.none()));
        List<Tuple2<Integer, Option<Integer>>> parts = left.appendAll(middle).appendAll(right);
        List<Integer> starts = parts.scanLeft(0, (at, part) -> at + part._1()).init();
        return parts.zip(starts)
                .find(part ->
                        column >= part._2() && column < part._2() + part._1()._1())
                .flatMap(part -> part._1()._2());
    }

    /**
     * The tabs that fit in {@code width} columns around {@code shown}: from {@code window}, one more after it while
     * that fits, else one more before it, until neither does.
     */
    static Window fit(List<Tab> tabs, int shown, int width, Window window) {
        Window after = new Window(window.from(), window.to() + 1);
        Window before = new Window(window.from() - 1, window.to());
        if (after.to() < tabs.size() && width(tabs, after) <= width) {
            return fit(tabs, shown, width, after);
        }
        if (before.from() >= 0 && width(tabs, before) <= width) {
            return fit(tabs, shown, width, before);
        }
        return window;
    }

    /**
     * How wide the line is that shows {@code window} of {@code tabs}, with the counts of those left out.
     */
    private static int width(List<Tab> tabs, Window window) {
        int shown = List.range(window.from(), window.to() + 1)
                        .map(index -> TextWidth.of(tabs.get(index).text()))
                        .sum()
                        .intValue()
                + BETWEEN.length() * (window.to() - window.from());
        int left = window.from() > 0 ? TextWidth.of(counted("‹ ", tabs.take(window.from()), "")) + BETWEEN.length() : 0;
        int right = window.to() < tabs.size() - 1
                ? TextWidth.of(counted("", tabs.drop(window.to() + 1), " ›")) + BETWEEN.length()
                : 0;
        return shown + left + right;
    }

    private static String counted(String before, List<Tab> hidden, String after) {
        long failed = hidden.count(tab -> tab.last().contains(Outcome.FAILED));
        return before + hidden.size() + (failed > 0 ? " ✗" + failed : "") + after;
    }

    private static Span counter(String before, List<Tab> hidden, String after, Theme.SpanStyles styles) {
        boolean failed = hidden.exists(tab -> tab.last().contains(Outcome.FAILED));
        return Span.styled(
                counted(before, hidden, after), styles.of(failed ? Theme.Classes.CHECKS_FAILED : Theme.Classes.DIM));
    }

    private static Span tab(Tab tab, boolean shown, Theme.SpanStyles styles) {
        return Span.styled(
                tab.text(),
                styles.of(
                        shown
                                ? Theme.Classes.SELECTION
                                : tab.last().map(TabStrip::style).getOrElse(Theme.Classes.DIM)));
    }

    private static String style(Outcome outcome) {
        return switch (outcome) {
            case PASSED -> Theme.Classes.CHECKS_PASSED;
            case FAILED -> Theme.Classes.CHECKS_FAILED;
            case CANCELLED -> Theme.Classes.DIM;
            case RUNNING -> Theme.Classes.CHECKS_RUNNING;
        };
    }

    /**
     * A tab: the workflow it shows, as it is named, and how its last build ended, as far as that is read.
     */
    record Tab(String label, Option<Outcome> last) {

        String text() {
            return symbol() + " " + label;
        }

        private String symbol() {
            return last.map(outcome -> switch (outcome) {
                        case PASSED -> "✓";
                        case FAILED -> "✗";
                        case CANCELLED -> "⊘";
                        case RUNNING -> "○";
                    })
                    .getOrElse("·");
        }
    }

    /**
     * The tabs shown, from the index {@code from} up to the index {@code to}, both shown.
     */
    record Window(int from, int to) {}
}
