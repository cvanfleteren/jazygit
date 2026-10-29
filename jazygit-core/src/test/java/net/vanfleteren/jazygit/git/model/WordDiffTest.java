package net.vanfleteren.jazygit.git.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.Tuple;
import io.vavr.collection.List;
import io.vavr.control.Option;
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
                WordDiff.segments(lines));
    }

    @Test
    void anAddedLineWithoutARemovedOneBeforeItIsWhole() {
        List<DiffLine> lines = List.of(line(Kind.ADDED, "new"), line(Kind.REMOVED, "old"));

        assertEquals(List.of(List.of(changed(false, "new")), List.of(changed(false, "old"))), WordDiff.segments(lines));
    }

    private static Segment changed(boolean changed, String text) {
        return new Segment(text, changed);
    }

    private static DiffLine line(Kind kind, String text) {
        return new DiffLine(kind, text, Option.none(), Option.none());
    }
}
