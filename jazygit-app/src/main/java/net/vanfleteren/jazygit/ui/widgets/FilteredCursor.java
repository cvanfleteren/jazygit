package net.vanfleteren.jazygit.ui.widgets;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.Function;
import java.util.function.Predicate;
import net.vanfleteren.jazygit.state.ListCursor;

/**
 * What a list in a popup shows for its {@link ListCursor} in the model: the lines that contain its filter, and the
 * one it highlights. Until the user moves, that is the line the list opens at, or the first that can be highlighted.
 * When the filter changes, the highlighted line stays highlighted while it still shows.
 *
 * @param <L> a line of the list
 */
public final class FilteredCursor<L> implements FilteredList {

    private final Function<Option<String>, List<L>> linesFor;
    private final Predicate<L> highlightable;
    private final Function<List<L>, Option<Integer>> opensAt;
    private final ListCursor cursor;
    private final List<L> lines;
    private final int selected;

    private FilteredCursor(
            Function<Option<String>, List<L>> linesFor,
            Predicate<L> highlightable,
            Function<List<L>, Option<Integer>> opensAt,
            ListCursor cursor) {
        this.linesFor = linesFor;
        this.highlightable = highlightable;
        this.opensAt = opensAt;
        this.cursor = cursor;
        this.lines = linesFor.apply(cursor.filter());
        this.selected = cursor.line()
                .map(line -> Math.clamp(line, 0, Math.max(0, lines.size() - 1)))
                .orElse(() -> opensAt.apply(lines).filter(line -> line >= 0 && line < lines.size()))
                .getOrElse(() -> first(lines));
    }

    /**
     * The list at {@code cursor}, opening at its first line that can be highlighted.
     *
     * @param linesFor      the lines shown for a filter, all of them without one; when the filter changes, the
     *                      highlighted line is found again among them by {@code equals}
     * @param highlightable whether a line can be highlighted, unlike a heading
     */
    public static <L> FilteredCursor<L> of(
            Function<Option<String>, List<L>> linesFor, Predicate<L> highlightable, ListCursor cursor) {
        return of(linesFor, highlightable, lines -> Option.none(), cursor);
    }

    /**
     * The list at {@code cursor}.
     *
     * @param opensAt the line the list opens at among its lines, if not the first that can be highlighted; one
     *                outside the lines is the first that can be
     */
    public static <L> FilteredCursor<L> of(
            Function<Option<String>, List<L>> linesFor,
            Predicate<L> highlightable,
            Function<List<L>, Option<Integer>> opensAt,
            ListCursor cursor) {
        return new FilteredCursor<>(linesFor, highlightable, opensAt, cursor);
    }

    @Override
    public ListCursor cursor() {
        return cursor;
    }

    /**
     * The lines shown.
     */
    public List<L> lines() {
        return lines;
    }

    @Override
    public int selected() {
        return selected;
    }

    @Override
    public int size() {
        return lines.size();
    }

    @Override
    public boolean highlightable(int line) {
        return highlightable.test(lines.get(line));
    }

    /**
     * The highlighted line, unless there is none that can be.
     */
    public Option<L> highlighted() {
        return lines.asPartialFunction().lift().apply(selected).filter(highlightable);
    }

    /**
     * The same list at {@code other}.
     */
    public FilteredCursor<L> at(ListCursor other) {
        return new FilteredCursor<>(linesFor, highlightable, opensAt, other);
    }

    /**
     * The cursor on {@code line}.
     */
    public ListCursor at(int line) {
        return cursor.at(line);
    }

    /**
     * The cursor with the filter open and empty, on the highlighted line.
     */
    public ListCursor opened() {
        return at(selected).withFilter(Option.of(""));
    }

    /**
     * The cursor with {@code filter}, keeping the highlighted line when it still shows.
     */
    public ListCursor refiltered(Option<String> filter) {
        return refiltered(filter, highlighted());
    }

    /**
     * The cursor with {@code filter}, on {@code kept} when it shows, else on the first line that can be highlighted.
     * The line is found by {@code equals}, so the first of two equal lines is kept.
     */
    public ListCursor refiltered(Option<String> filter, Option<L> kept) {
        List<L> after = linesFor.apply(filter);
        int line = kept.map(after::indexOf).filter(index -> index >= 0).getOrElse(() -> first(after));
        return new ListCursor(Option.of(line), filter);
    }

    /**
     * The cursor after {@code key}; none when it stays as it is, as for a move to the line already highlighted.
     */
    public Option<ListCursor> after(ListKey.CursorKey key) {
        return switch (key) {
            case ListKey.CloseFilter() -> Option.of(refiltered(Option.none()));
            case ListKey.OpenFilter() -> Option.of(opened());
            case ListKey.EditFilter(String text) -> Option.of(refiltered(Option.of(text)));
            case ListKey.Move(int line) -> Option.when(line != selected, () -> at(line));
        };
    }

    /**
     * The first of {@code lines} that can be highlighted, or the first line when there is none.
     */
    private int first(List<L> lines) {
        return Math.max(0, lines.indexWhere(highlightable));
    }
}
