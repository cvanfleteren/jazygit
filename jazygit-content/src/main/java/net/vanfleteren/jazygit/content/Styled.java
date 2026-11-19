package net.vanfleteren.jazygit.content;

import io.vavr.collection.Vector;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.content.ContentView.Body;
import net.vanfleteren.jazygit.content.diff.DiffCursor;
import net.vanfleteren.jazygit.content.diff.Drawn;
import net.vanfleteren.jazygit.settings.ThemeName;
import net.vanfleteren.jazygit.state.diff.Anchor;
import net.vanfleteren.jazygit.state.diff.DiffStops;
import net.vanfleteren.jazygit.state.diff.Found;
import net.vanfleteren.jazygit.ui.text.Rows;

/**
 * {@code body}, styled in {@code theme} as {@code lines}, each drawn when it shows ({@link Rows}), with where the stops
 * of its cursor start, if it has one.
 *
 * @param asGiven the hunk shown as git gives it, as a range is open in it (ADR 83)
 * @param widest  how wide the widest of {@code lines} is, which the tint of the cursor never makes wider
 * @param places  where each line of a version rendered is drawn, in order (ADR 103)
 * @param width   how wide a version rendered was drawn
 * @param hits    the matches of the search in {@code lines}, while one is kept (ADR 126)
 * @param split   where the halves of the rows of the split view are, and what the title says of it (ADR 139)
 */
record Styled(
        Body body,
        ThemeName theme,
        Option<DiffStops.HunkAt> asGiven,
        Rows lines,
        Vector<Drawn.Stop> stops,
        int widest,
        Vector<Drawn.Place> places,
        int width,
        Option<SearchHits> hits,
        Drawn split) {

    static Styled of(Body body, ThemeName theme, Option<DiffStops.HunkAt> asGiven, Drawn drawn, int width) {
        Rows lines = drawn.rows();
        Vector<Drawn.Stop> stops = drawn.stops().toVector();
        return new Styled(
                body,
                theme,
                asGiven,
                lines,
                stops,
                // The render thread waits for it, as it does for the lines to be drawn; the lines of a diff are not
                // drawn for it.
                drawn.widest().getAsInt(),
                drawn.places().toVector(),
                width,
                Option.none(),
                drawn);
    }

    /**
     * This body with {@code kept}, its lines with others kept drawn.
     */
    Styled withLines(Rows kept) {
        return new Styled(body, theme, asGiven, kept, stops, widest, places, width, hits, split);
    }

    /**
     * This body with the matches of {@code query} in it, found again only for another query.
     */
    Styled searched(Option<String> query) {
        Option<SearchHits> found = query.map(
                text -> hits.filter(those -> those.query().equals(text)).getOrElse(() -> SearchHits.of(lines, text)));
        return new Styled(body, theme, asGiven, lines, stops, widest, places, width, found, split);
    }

    /**
     * The index among {@code hits} of the match {@code found}, if it is still one of this body: the {@code nth} of the
     * rows of its stop, whose stop is looked for from the stop at {@code near}, or of its line.
     */
    Option<Integer> match(SearchHits hits, Found found, int near) {
        return switch (found) {
            case Found.InStop(Anchor stop, int nth) ->
                DiffCursor.index(stops, stop, near)
                        .flatMap(index -> hits.at(
                                stops.get(index).row(),
                                index + 1 < stops.size() ? stops.get(index + 1).row() : lines.size(),
                                nth));
            case Found.OnLine(int line, int nth) -> hits.at(line, line + 1, nth);
        };
    }

    /**
     * Whether line {@code row} is drawn to the edge of the area (ADR 80): the bar of each file, the lines of the diff
     * and the markers of the lines it leaves out, not the threads on them. The stops are in the order of their lines,
     * so those that start on it are looked up, not kept apart for each line.
     */
    boolean filling(int row) {
        return stops.drop(firstFrom(row, 0, stops.size()))
                .takeWhile(stop -> stop.row() == row)
                .exists(stop -> switch (stop.mark().anchor()) {
                    case Anchor.File _, Anchor.OnLine _, Anchor.Gap _, Anchor.Filtered _, Anchor.Node _ -> true;
                    case Anchor.Lead _, Anchor.Thread _, Anchor.Comment _ -> false;
                });
    }

    /**
     * The index of the first of the stops from {@code low} up to {@code high} that starts on or after {@code row},
     * found by halving.
     */
    private int firstFrom(int row, int low, int high) {
        if (low >= high) {
            return low;
        }
        int middle = (low + high) >>> 1;
        return stops.get(middle).row() < row ? firstFrom(row, middle + 1, high) : firstFrom(row, low, middle);
    }

    /**
     * The hunk of the line {@code anchor}, if it is a line of this body, looked for from the stop at {@code near}
     * ({@link DiffCursor#index}).
     */
    Option<DiffStops.HunkAt> hunk(Anchor anchor, int near) {
        return DiffCursor.index(stops, anchor, near)
                .flatMap(index -> stops.get(index).mark().hunk());
    }

    /**
     * The line the stop {@code anchor} starts on, if it is a stop of this body, looked for from the stop at
     * {@code near} ({@link DiffCursor#index}).
     */
    Option<Integer> row(Anchor anchor, int near) {
        return DiffCursor.index(stops, anchor, near)
                .map(index -> stops.get(index).row())
                .orElse(() ->
                        places.find(place -> place.anchor().equals(anchor)).map(Drawn.Place::row));
    }

    /**
     * The first line of the block of a version rendered at the top of the area scrolled to {@code row}, or of its
     * first block when none is at or above it; none for a body that is no version rendered (ADR 103).
     */
    Option<Anchor> top(int row) {
        return places.filter(place -> place.row() <= row)
                .lastOption()
                // Above the first block, as with the bar of the file at the top, the first block.
                .orElse(places.headOption())
                .flatMap(last -> places.find(place -> place.row() == last.row()))
                .map(Drawn.Place::anchor);
    }

    /**
     * Where in the text of its line {@code hit} starts.
     */
    int from(SearchHits.Hit hit) {
        return Math.min(
                lines.get(hit.row()).rawContent().length(),
                lines.source(hit.row()).from() + hit.start());
    }
}
