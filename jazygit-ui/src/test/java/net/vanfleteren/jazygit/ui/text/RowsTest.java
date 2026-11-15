package net.vanfleteren.jazygit.ui.text;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.tamboui.text.Line;
import io.vavr.collection.List;
import io.vavr.collection.Vector;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RowsTest {

    @Test
    void aLineIsDrawnOnlyWhenItIsAskedFor() {
        AtomicInteger drawn = new AtomicInteger();
        Rows rows = counted(drawn, 1_000);

        assertEquals(1_000, rows.size());
        assertEquals(0, drawn.get());
        assertEquals("007", rows.get(7).rawContent());
        assertEquals(1, drawn.get());
    }

    @Test
    void theLinesKeptAreNotDrawnAgainAndTheOthersAreDropped() {
        AtomicInteger drawn = new AtomicInteger();
        Rows kept = counted(drawn, 100).keeping(10, 20);
        assertEquals(10, drawn.get());

        kept.get(15);
        Rows moved = kept.keeping(15, 25);
        assertEquals(15, drawn.get());
        assertEquals(10, moved.drawn().size());
        moved.get(12);
        assertEquals(16, drawn.get());
    }

    @Test
    void anUpdatedLineIsDrawnFromWhatItWas() {
        Rows rows = Rows.of(List.of(Line.from("a"), Line.from("b"))).keeping(0, 2);

        Rows updated = rows.update(1, line -> Line.from(line.rawContent() + "!"))
                .update(1, line -> Line.from(line.rawContent() + "?"));

        assertEquals(List.of("a", "b!?"), updated.all().map(Line::rawContent));
        assertEquals(List.of("a", "b"), rows.all().map(Line::rawContent));
    }

    @Test
    void theWidestLineIsMeasuredWithoutKeepingAny() {
        Rows rows = Rows.of(List.of(Line.from("ab"), Line.from("abcd"), Line.from("")));

        assertEquals(4, rows.widest());
        assertEquals(0, rows.drawn().size());
    }

    @Test
    void linesAfterOthersComeAfterThem() {
        Rows rows = Rows.of(List.of(Line.from("c"))).after(Rows.of(List.of(Line.from("a"), Line.from("b"))));

        assertEquals(List.of("a", "b", "c"), rows.all().map(Line::rawContent));
    }

    @Test
    void aLineDrawnAlreadyIsSearchedInAllOfItsText() {
        Rows rows = Rows.of(List.of(Line.from("a b")));

        assertEquals(new Rows.Source("a b", 0), rows.source(0));
    }

    @Test
    void aLineIsSearchedInWhatItIsDrawnFromWithoutDrawingIt() {
        AtomicInteger drawn = new AtomicInteger();
        Rows rows = Rows.drawn(List.of(() -> {
                    drawn.incrementAndGet();
                    return Line.from("+x");
                }))
                .update(0, line -> line);
        Rows given = Rows.drawn(List.of(() -> Line.from("+x")), List.of(() -> new Rows.Source("x", 1)));

        assertEquals(new Rows.Source("x", 1), given.after(rows).source(1));
        assertEquals(0, drawn.get());
    }

    @Test
    void theLinesOfAHunkAreSearchedWithoutTheirPrefix() {
        Vector<Rows.Source> sources = Rows.Source.ofDiff(List.of(
                "diff --git a/a b/a",
                "+++ b/a",
                "@@ -1 +1 @@ heading",
                "+added",
                "diff --cc c",
                "@@@ -1,1 -1,1 +1,1 @@@",
                "++both",
                ""));

        assertEquals(
                List.of(
                        new Rows.Source("diff --git a/a b/a", 0),
                        new Rows.Source("+++ b/a", 0),
                        new Rows.Source("@@ -1 +1 @@ heading", 0),
                        new Rows.Source("added", 1),
                        new Rows.Source("diff --cc c", 0),
                        new Rows.Source("@@@ -1,1 -1,1 +1,1 @@@", 0),
                        new Rows.Source("both", 2),
                        new Rows.Source("", 0)),
                sources.toList());
    }

    private static Rows counted(AtomicInteger drawn, int size) {
        return Rows.drawn(Vector.range(0, size).map(index -> () -> {
            drawn.incrementAndGet();
            return Line.from("%03d".formatted(index));
        }));
    }
}
