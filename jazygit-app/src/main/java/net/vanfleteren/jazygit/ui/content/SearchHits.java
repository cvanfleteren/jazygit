package net.vanfleteren.jazygit.ui.content;

import dev.tamboui.style.Style;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import io.vavr.Tuple;
import io.vavr.collection.List;
import io.vavr.collection.Vector;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.ui.text.Rows;

/**
 * The matches of the search of the content panel in what an area shows (ADR 126), found once in the text each of its
 * rows is drawn from, without drawing any, and laid over the rows that show.
 *
 * @param query what was searched for
 * @param hits  each match, in the order of the rows and in a row of where it starts
 */
public record SearchHits(String query, Vector<Hit> hits) {

    /**
     * The matches of {@code query} in {@code rows}, each row searched on a thread of the common pool, as a body has up
     * to 50 000 of them.
     */
    public static SearchHits of(Rows rows, String query) {
        Vector<Hit> hits = Vector.range(0, rows.size())
                .toJavaParallelStream()
                .map(row -> starts(rows.source(row).text(), query).map(start -> new Hit(row, start)))
                .collect(List.collector())
                .flatMap(found -> found)
                .toVector();
        return new SearchHits(query, hits);
    }

    /**
     * Where {@code query} is found in {@code text}, one match after the other: ignoring case, unless the query has an
     * uppercase letter, as the filters of lists match. Nothing for an empty query.
     */
    public static List<Integer> starts(String text, String query) {
        if (query.isEmpty()) {
            return List.empty();
        }
        boolean ignoreCase = query.chars().noneMatch(Character::isUpperCase);
        return List.unfold(
                        0,
                        from -> nextStart(text, query, ignoreCase, from)
                                .map(start -> Tuple.of(start + query.length(), start)))
                .reverse();
    }

    /**
     * Where {@code query} is first found in {@code text} from {@code from} on. A loop, as a minified file has lines of
     * many thousands of characters to look through.
     */
    private static Option<Integer> nextStart(String text, String query, boolean ignoreCase, int from) {
        for (int at = from; at + query.length() <= text.length(); at++) {
            if (text.regionMatches(ignoreCase, at, query, 0, query.length())) {
                return Option.some(at);
            }
        }
        return Option.none();
    }

    public int size() {
        return hits.size();
    }

    public boolean isEmpty() {
        return hits.isEmpty();
    }

    public Hit get(int index) {
        return hits.get(index);
    }

    /**
     * The index of the first match on row {@code row} or after it, found by halving; {@link #size} when there is none.
     */
    public int firstFrom(int row) {
        // A loop, not a recursion, as a frame looks up the matches of the rows that show in up to tens of thousands.
        int low = 0;
        int high = hits.size();
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (hits.get(middle).row() < row) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        return low;
    }

    /**
     * The index of the match {@code n} goes to from row {@code row} when none is current: the first on it or after it,
     * else the first of all, which wraps.
     */
    public Step forwardFrom(int row) {
        int first = firstFrom(row);
        return first < size() ? new Step(first, false) : new Step(0, true);
    }

    /**
     * The index of the match {@code N} goes to from row {@code row} when none is current: the last before it, else the
     * last of all, which wraps.
     */
    public Step backwardFrom(int row) {
        int first = firstFrom(row);
        return first > 0 ? new Step(first - 1, false) : new Step(size() - 1, true);
    }

    /**
     * The index of the match after {@code current}, or before it, past the last the first and past the first the last.
     */
    public Step from(int current, boolean forward) {
        int next = current + (forward ? 1 : -1);
        return next < 0 ? new Step(size() - 1, true) : next >= size() ? new Step(0, true) : new Step(next, false);
    }

    /**
     * Which match, from 0, the one at {@code index} is among those on the rows from {@code from}.
     */
    public int nth(int index, int from) {
        return index - firstFrom(from);
    }

    /**
     * The index of the {@code nth} match, from 0, on the rows from {@code from} up to {@code until}, if there is one.
     */
    public Option<Integer> at(int from, int until, int nth) {
        int index = firstFrom(from) + nth;
        return Option.when(nth >= 0 && index < size() && hits.get(index).row() < until, index);
    }

    /**
     * {@code rows} with the matches on those from {@code from} up to {@code until} styled {@code match}, the one at
     * {@code current} {@code currentStyle}, over what they are styled already. Only those rows are drawn again, once
     * they show.
     */
    public Rows tinted(Rows rows, int from, int until, Option<Integer> current, Style match, Style currentStyle) {
        return Vector.range(firstFrom(from), firstFrom(Math.min(until, rows.size())))
                .foldLeft(rows, (done, index) -> {
                    Hit hit = hits.get(index);
                    int start = rows.source(hit.row()).from() + hit.start();
                    Style style = current.contains(index) ? currentStyle : match;
                    return done.update(hit.row(), line -> over(line, start, start + query.length(), style));
                });
    }

    /**
     * {@code line} with its characters from {@code from} up to {@code until} patched with {@code style}, the spans
     * they are part of cut where they start and end.
     */
    static Line over(Line line, int from, int until, Style style) {
        List<Span> spans = List.ofAll(line.spans());
        List<Integer> starts =
                spans.scanLeft(0, (at, span) -> at + span.content().length());
        Line cut = Line.from(spans.zip(starts)
                .flatMap(at -> cut(at._1(), from - at._2(), until - at._2(), style))
                .toJavaList());
        return line.alignment().map(cut::alignment).orElse(cut);
    }

    /**
     * {@code span} with its characters from {@code from} up to {@code until}, as far as it has them, in a span of
     * their own patched with {@code style}.
     */
    private static List<Span> cut(Span span, int from, int until, Style style) {
        String text = span.content();
        int start = Math.clamp(from, 0, text.length());
        int end = Math.clamp(until, 0, text.length());
        return start >= end
                ? List.of(span)
                : List.of(
                                new Span(text.substring(0, start), span.style()),
                                new Span(
                                        text.substring(start, end), span.style().patch(style)),
                                new Span(text.substring(end), span.style()))
                        .filter(part -> !part.content().isEmpty());
    }

    /**
     * A match: the row it is on, and where it starts in the text that row is searched in ({@link Rows.Source}).
     */
    public record Hit(int row, int start) {}

    /**
     * Where {@code n} or {@code N} goes: the index of a match, and whether it went past the last or the first.
     */
    public record Step(int index, boolean wrapped) {}
}
