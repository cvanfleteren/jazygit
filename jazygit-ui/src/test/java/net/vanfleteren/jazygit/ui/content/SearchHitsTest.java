package net.vanfleteren.jazygit.ui.content;

import static org.junit.jupiter.api.Assertions.*;

import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import io.vavr.collection.List;
import io.vavr.collection.Vector;
import io.vavr.control.Option;
import java.util.concurrent.atomic.AtomicInteger;
import net.vanfleteren.jazygit.ui.text.Rows;
import org.junit.jupiter.api.Test;

class SearchHitsTest {

    private static final Style MATCH = Style.EMPTY.bg(Color.YELLOW);
    private static final Style CURRENT = Style.EMPTY.bg(Color.RED);

    @Test
    void aQueryMatchesIgnoringCaseUnlessItHasAnUppercaseLetter() {
        assertEquals(List.of(0, 8), SearchHits.starts("Foo and foo", "foo"));
        assertEquals(List.of(0), SearchHits.starts("Foo and foo", "Foo"));
        assertEquals(List.empty(), SearchHits.starts("foo", ""));
    }

    @Test
    void matchesFollowEachOtherWithoutOverlapping() {
        assertEquals(List.of(0, 2), SearchHits.starts("aaaaa", "aa"));
    }

    @Test
    void theRowsAreSearchedInWhatTheyAreDrawnFromWithoutDrawingThem() {
        AtomicInteger drawn = new AtomicInteger();
        Rows rows = Rows.drawn(
                Vector.range(0, 1_000).map(index -> () -> {
                    drawn.incrementAndGet();
                    return Line.from("+" + index);
                }),
                Vector.range(0, 1_000).map(index -> () -> new Rows.Source(String.valueOf(index), 1)));

        SearchHits hits = SearchHits.of(rows, "99");

        assertEquals(0, drawn.get());
        assertEquals(
                List.of(new SearchHits.Hit(99, 0), new SearchHits.Hit(199, 1), new SearchHits.Hit(299, 1)),
                hits.hits().take(3).toList());
        assertEquals(19, hits.size());
    }

    @Test
    void nAndShiftNGoOnFromARowOrAMatchWrappingAtEitherEnd() {
        SearchHits hits = new SearchHits(
                "x", Vector.of(new SearchHits.Hit(2, 0), new SearchHits.Hit(2, 4), new SearchHits.Hit(7, 0)));

        assertEquals(new SearchHits.Step(0, false), hits.forwardFrom(0));
        assertEquals(new SearchHits.Step(2, false), hits.forwardFrom(3));
        assertEquals(new SearchHits.Step(0, true), hits.forwardFrom(8));
        assertEquals(new SearchHits.Step(1, false), hits.backwardFrom(7));
        assertEquals(new SearchHits.Step(2, true), hits.backwardFrom(2));
        assertEquals(new SearchHits.Step(0, true), hits.from(2, true));
        assertEquals(new SearchHits.Step(2, true), hits.from(0, false));
        assertEquals(new SearchHits.Step(1, false), hits.from(0, true));
    }

    @Test
    void aMatchIsFoundAgainByWhichItIsOfTheRowsOfItsStop() {
        SearchHits hits = new SearchHits(
                "x", Vector.of(new SearchHits.Hit(2, 0), new SearchHits.Hit(2, 4), new SearchHits.Hit(7, 0)));

        assertEquals(1, hits.nth(1, 2));
        assertEquals(Option.of(1), hits.at(0, 5, 1));
        assertEquals(Option.none(), hits.at(0, 5, 2));
        assertEquals(Option.of(2), hits.at(7, 8, 0));
    }

    @Test
    void theMatchesOnTheRowsThatShowAreStyledOverWhatTheyAreStyledAlready() {
        Rows rows = Rows.of(
                List.of(Line.from(Span.raw(" 1 │ "), Span.styled("+ a foo", Style.EMPTY.bold())), Line.from("foo")),
                List.of(new Rows.Source("a foo", 7), new Rows.Source("foo", 0)));
        SearchHits hits = SearchHits.of(rows, "foo");

        Rows tinted = hits.tinted(rows, 0, 1, Option.of(0), MATCH, CURRENT);

        List<Span> spans = List.ofAll(tinted.get(0).spans());
        assertEquals(List.of(" 1 │ ", "+ a ", "foo"), spans.map(Span::content));
        assertEquals(Style.EMPTY.bold().patch(CURRENT), spans.get(2).style());
        // The row that does not show is left as it is.
        assertEquals(Line.from("foo"), tinted.get(1));
    }

    @Test
    void aMatchCanBeCutOverSeveralSpans() {
        Line line = Line.from(Span.raw("ab"), Span.raw("cd"));

        Line over = SearchHits.over(line, 1, 3, MATCH);

        assertEquals(List.of("a", "b", "c", "d"), List.ofAll(over.spans()).map(Span::content));
        assertEquals(
                List.of(Style.EMPTY, MATCH, MATCH, Style.EMPTY),
                List.ofAll(over.spans()).map(Span::style));
    }
}
