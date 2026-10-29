package net.vanfleteren.jazygit.git.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import io.vavr.Tuple;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Duration;
import net.vanfleteren.jazygit.git.model.UnifiedDiff.DiffLine;
import net.vanfleteren.jazygit.git.model.UnifiedDiff.DiffLine.Kind;
import net.vanfleteren.jazygit.git.model.WordDiff.Segment;
import org.junit.jupiter.api.Test;

class WordDiffTest {

    @Test
    void highlightsTheWordsAPairDoesNotShare() {
        assertEquals(
                Tuple.of(
                        List.of(changed(false, "int count = "), changed(true, "1"), changed(false, ";")),
                        List.of(changed(false, "int count = "), changed(true, "42"), changed(false, ";"))),
                WordDiff.compared("int count = 1;", "int count = 42;"));
    }

    @Test
    void highlightsNothingInALineThatWasRewritten() {
        assertEquals(
                Tuple.of(List.of(changed(false, "alpha beta")), List.of(changed(false, "gamma delta"))),
                WordDiff.compared("alpha beta", "gamma delta"));
    }

    @Test
    void pairsTheRemovedLinesOfABlockWithTheAddedOnesInOrder() {
        List<DiffLine> lines = List.of(
                line(Kind.CONTEXT, "same"),
                line(Kind.REMOVED, "a = 1"),
                line(Kind.REMOVED, "b = 2"),
                line(Kind.ADDED, "a = 3"),
                line(Kind.CONTEXT, "same"));

        assertEquals(
                List.of(
                        List.of(changed(false, "same")),
                        List.of(changed(false, "a = "), changed(true, "1")),
                        List.of(changed(false, "b = 2")),
                        List.of(changed(false, "a = "), changed(true, "3")),
                        List.of(changed(false, "same"))),
                segments(lines));
    }

    @Test
    void anAddedLineWithoutARemovedOneBeforeItIsWhole() {
        List<DiffLine> lines = List.of(line(Kind.ADDED, "new"), line(Kind.REMOVED, "old"));

        assertEquals(List.of(List.of(changed(false, "new")), List.of(changed(false, "old"))), segments(lines));
    }

    @Test
    void aNoteThatTheLastLineHasNoNewlineDoesNotEndTheBlock() {
        String note = "\\ No newline at end of file";
        List<DiffLine> lines = List.of(
                line(Kind.REMOVED, "int x = 1;"),
                line(Kind.NOTE, note),
                line(Kind.ADDED, "int x = 2;"),
                line(Kind.NOTE, note));

        assertEquals(
                List.of(
                        List.of(changed(false, "int x = "), changed(true, "1"), changed(false, ";")),
                        List.of(changed(false, note)),
                        List.of(changed(false, "int x = "), changed(true, "2"), changed(false, ";")),
                        List.of(changed(false, note))),
                segments(lines));
    }

    @Test
    void theHunksOfADiffShareABudgetAndOnceItIsSpentTheirLinesAreWhole() {
        // Each pair has five tokens a line, so fills twenty-five cells: the budget covers the first pair only.
        List<DiffLine> hunk = List.of(line(Kind.REMOVED, "a = 1"), line(Kind.ADDED, "a = 2"));

        assertEquals(
                List.of(
                        List.of(
                                List.of(changed(false, "a = "), changed(true, "1")),
                                List.of(changed(false, "a = "), changed(true, "2"))),
                        List.of(List.of(changed(false, "a = 1")), List.of(changed(false, "a = 2")))),
                WordDiff.segments(List.of(hunk, hunk), 30));
    }

    @Test
    void manyLongSimilarLinesAreDrawnQuickly() {
        // 499 tokens a line, so each pair is just small enough to be compared on its own.
        String words = List.range(0, 249).map(i -> "word" + i).mkString(" ");
        List<DiffLine> hunk = List.range(0, 2000)
                .map(i -> line(Kind.REMOVED, words + " " + i))
                .appendAll(List.range(0, 2000).map(i -> line(Kind.ADDED, words + " x" + i)));

        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> WordDiff.segments(List.of(hunk)));
    }

    private static List<List<Segment>> segments(List<DiffLine> lines) {
        return WordDiff.segments(List.of(lines)).head();
    }

    private static Segment changed(boolean changed, String text) {
        return new Segment(text, changed);
    }

    private static DiffLine line(Kind kind, String text) {
        return new DiffLine(kind, text, Option.none(), Option.none());
    }
}
