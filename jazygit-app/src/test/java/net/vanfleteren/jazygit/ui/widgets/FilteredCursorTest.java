package net.vanfleteren.jazygit.ui.widgets;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.state.ListCursor;
import org.junit.jupiter.api.Test;

class FilteredCursorTest {

    // A heading starts with "#", which cannot be highlighted.
    private static final List<String> LINES = List.of("# local", "main", "develop", "# remote", "origin/main");

    @Test
    void untilTheUserMovesItOpensAtTheFirstLineThatCanBeHighlighted() {
        assertEquals(Option.of("main"), list(ListCursor.START).highlighted());
        assertEquals(1, list(ListCursor.START).selected());
    }

    @Test
    void itCanOpenAtAnotherLine() {
        FilteredCursor<String> list = FilteredCursor.of(
                FilteredCursorTest::lines,
                FilteredCursorTest::highlightable,
                lines -> Option.of(lines.indexOf("develop")),
                ListCursor.START);

        assertEquals(Option.of("develop"), list.highlighted());
    }

    @Test
    void aLineToOpenAtThatIsNotShownOpensAtTheFirstLineThatCanBeHighlighted() {
        FilteredCursor<String> list = FilteredCursor.of(
                FilteredCursorTest::lines,
                FilteredCursorTest::highlightable,
                lines -> Option.of(lines.indexOf("feature")),
                ListCursor.START);

        assertEquals(1, list.selected());
        assertEquals(Option.of("main"), list.highlighted());
    }

    @Test
    void theLineOfTheCursorIsKeptWithinTheLinesShown() {
        assertEquals(4, list(ListCursor.START.at(9)).selected());
        assertEquals(0, list(ListCursor.START.at(-1)).selected());
    }

    @Test
    void onlyTheLinesThatContainTheFilterShow() {
        FilteredCursor<String> list = list(new ListCursor(Option.of(0), Option.of("main")));

        assertEquals(List.of("main", "origin/main"), list.lines());
    }

    @Test
    void withoutLinesNothingIsHighlighted() {
        FilteredCursor<String> list = list(new ListCursor(Option.of(3), Option.of("xyz")));

        assertEquals(0, list.selected());
        assertEquals(Option.none(), list.highlighted());
    }

    @Test
    void aHeadingIsNotHighlighted() {
        assertEquals(Option.none(), list(ListCursor.START.at(0)).highlighted());
    }

    @Test
    void theHighlightedLineStaysHighlightedWhileItStillShows() {
        FilteredCursor<String> list = list(ListCursor.START.at(4));

        assertEquals(new ListCursor(Option.of(1), Option.of("main")), list.refiltered(Option.of("main")));
    }

    @Test
    void whenTheFilterHidesTheHighlightedLineTheFirstOneThatCanBeIsHighlighted() {
        FilteredCursor<String> list = list(ListCursor.START.at(2));

        assertEquals(new ListCursor(Option.of(0), Option.of("in")), list.refiltered(Option.of("in")));
    }

    @Test
    void refilteringCanKeepAnotherLine() {
        FilteredCursor<String> list = list(new ListCursor(Option.of(0), Option.of("xyz")));

        assertEquals(new ListCursor(Option.of(2), Option.none()), list.refiltered(Option.none(), Option.of("develop")));
    }

    @Test
    void openingTheFilterKeepsTheHighlightedLine() {
        assertEquals(
                Option.of(new ListCursor(Option.of(1), Option.of(""))),
                list(ListCursor.START).after(new ListKey.OpenFilter()));
    }

    @Test
    void editingOrClosingTheFilterRefiltersTheList() {
        // "ma" shows "main" and "origin/main", of which the latter is highlighted.
        FilteredCursor<String> list = list(new ListCursor(Option.of(1), Option.of("ma")));

        assertEquals(
                Option.of(new ListCursor(Option.of(1), Option.of("main"))), list.after(new ListKey.EditFilter("main")));
        assertEquals(
                Option.of(new ListCursor(Option.of(0), Option.of("origin"))),
                list.after(new ListKey.EditFilter("origin")));
        assertEquals(Option.of(new ListCursor(Option.of(4), Option.none())), list.after(new ListKey.CloseFilter()));
    }

    @Test
    void movingChangesTheCursorOnlyWhenTheLineChanges() {
        FilteredCursor<String> list = list(ListCursor.START.at(1));

        assertEquals(Option.of(ListCursor.START.at(2)), list.after(new ListKey.Move(2)));
        assertEquals(Option.none(), list.after(new ListKey.Move(1)));
    }

    @Test
    void atAnotherCursorTheLinesAreThoseOfItsFilter() {
        FilteredCursor<String> list = list(ListCursor.START).at(new ListCursor(Option.of(0), Option.of("dev")));

        assertEquals(Option.of("develop"), list.highlighted());
    }

    private static FilteredCursor<String> list(ListCursor cursor) {
        return FilteredCursor.of(FilteredCursorTest::lines, FilteredCursorTest::highlightable, cursor);
    }

    /**
     * The lines that contain {@code filter}, with the headings when there is none.
     */
    private static List<String> lines(Option<String> filter) {
        return filter.map(typed -> LINES.filter(line -> highlightable(line) && line.contains(typed)))
                .getOrElse(LINES);
    }

    private static boolean highlightable(String line) {
        return !line.startsWith("#");
    }
}
