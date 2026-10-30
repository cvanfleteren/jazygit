package net.vanfleteren.jazygit.git.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import io.vavr.Tuple;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Duration;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
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
    void highlightsChangedWordsWithOnlyWhitespaceBetweenThemAsOne() {
        assertEquals(
                Tuple.of(
                        List.of(changed(false, "the "), changed(true, "quick  brown"), changed(false, " fox jumps")),
                        List.of(changed(false, "the "), changed(true, "slow  red"), changed(false, " fox jumps"))),
                WordDiff.compared("the quick  brown fox jumps", "the slow  red fox jumps"));
    }

    @Test
    void highlightsChangedWordsWithMoreThanWhitespaceBetweenThemApart() {
        assertEquals(
                Tuple.of(
                        List.of(
                                changed(false, "the "),
                                changed(true, "quick"),
                                changed(false, ", "),
                                changed(true, "brown"),
                                changed(false, " fox jumps")),
                        List.of(
                                changed(false, "the "),
                                changed(true, "slow"),
                                changed(false, ", "),
                                changed(true, "red"),
                                changed(false, " fox jumps"))),
                WordDiff.compared("the quick, brown fox jumps", "the slow, red fox jumps"));
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
        // The pairs differ in their last token only, so each fills one cell: the budget covers the first pair only.
        List<DiffLine> hunk = List.of(line(Kind.REMOVED, "a = 1"), line(Kind.ADDED, "a = 2"));

        assertEquals(
                List.of(
                        List.of(
                                List.of(changed(false, "a = "), changed(true, "1")),
                                List.of(changed(false, "a = "), changed(true, "2"))),
                        List.of(List.of(changed(false, "a = 1")), List.of(changed(false, "a = 2")))),
                WordDiff.segments(List.of(hunk, hunk), 1));
    }

    @Test
    void aRewrittenPairLeavesTheBudgetToThePairsAfterIt() {
        // The rewritten pair would fill 9 cells, all of the budget, were it compared; the pair after it fills one.
        List<DiffLine> hunk = List.of(
                line(Kind.REMOVED, "alpha beta"),
                line(Kind.REMOVED, "a = 1"),
                line(Kind.ADDED, "gamma delta"),
                line(Kind.ADDED, "a = 2"));

        assertEquals(
                List.of(List.of(
                        List.of(changed(false, "alpha beta")),
                        List.of(changed(false, "a = "), changed(true, "1")),
                        List.of(changed(false, "gamma delta")),
                        List.of(changed(false, "a = "), changed(true, "2")))),
                WordDiff.segments(List.of(hunk), 9));
    }

    @Test
    void aPairWhoseWordsMovedIsComparedAndDrawnWholeIfTooLittleIsKept() {
        // Every word is in both lines, so only comparing them in order shows that too little is kept.
        assertEquals(
                Tuple.of(List.of(changed(false, "one two three four")), List.of(changed(false, "four three two one"))),
                WordDiff.compared("one two three four", "four three two one"));
    }

    @Test
    void aPairWhoseWordsMovedSpendsItsCells() {
        // The moved pair keeps every token by count, so it is compared and fills its 7 x 7 cells, all of the budget.
        List<DiffLine> hunk = List.of(
                line(Kind.REMOVED, "one two three four"),
                line(Kind.REMOVED, "a = 1"),
                line(Kind.ADDED, "four three two one"),
                line(Kind.ADDED, "a = 2"));

        assertEquals(
                List.of(List.of(
                        List.of(changed(false, "one two three four")),
                        List.of(changed(false, "a = 1")),
                        List.of(changed(false, "four three two one")),
                        List.of(changed(false, "a = 2")))),
                WordDiff.segments(List.of(hunk), 49));
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

    @Test
    void aLineIsSplitIntoWordsRunsOfWhitespaceAndSingleOtherCharactersAsUnicodesWordPatternHasThem() {
        Pattern token = Pattern.compile("\\w+|\\s+|.", Pattern.UNICODE_CHARACTER_CLASS);
        for (String line : List.of(
                "int count = compute(a_b, 42) + x;",
                "  \t leading and trailing  ",
                "naïve café — Ⅻ ①, déjà-vu 😀 é x y",
                "",
                "!!==>>")) {
            assertEquals(
                    List.ofAll(token.matcher(line).results().map(MatchResult::group)),
                    List.of(WordDiff.tokens(line)),
                    line);
        }
    }

    @Test
    void everyCodePointIsAWordWhitespaceOrNeitherAsUnicodesPatternsHaveIt() {
        Pattern word = Pattern.compile("\\w", Pattern.UNICODE_CHARACTER_CLASS);
        Pattern space = Pattern.compile("\\s", Pattern.UNICODE_CHARACTER_CLASS);
        List<String> wrong = List.ofAll(IntStream.rangeClosed(0, Character.MAX_CODE_POINT)
                        .filter(codePoint -> Character.getType(codePoint) != Character.SURROGATE)
                        .boxed())
                .filter(codePoint -> {
                    String character = Character.toString(codePoint);
                    int expected = word.matcher(character).matches()
                            ? WordDiff.WORD
                            : space.matcher(character).matches() ? WordDiff.SPACE : WordDiff.OTHER;
                    return WordDiff.kind(codePoint) != expected;
                })
                .map(codePoint -> "U+%04X".formatted(codePoint));

        assertEquals(List.empty(), wrong);
    }

    @Test
    void aPairThatDiffersInAnInsertionOnlyIsHighlightedOnceTheBudgetIsSpent() {
        List<DiffLine> hunk = List.of(line(Kind.REMOVED, "foo(a)"), line(Kind.ADDED, "foo(a, b)"));

        assertEquals(
                List.of(List.of(
                        List.of(changed(false, "foo(a)")),
                        List.of(changed(false, "foo(a"), changed(true, ", b"), changed(false, ")")))),
                WordDiff.segments(List.of(hunk), 0));
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
