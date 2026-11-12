package net.vanfleteren.jazygit.git.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Duration;
import net.vanfleteren.jazygit.git.model.ParsedDiff.ParsedLine;
import net.vanfleteren.jazygit.git.model.UnifiedDiff.DiffLine;
import org.junit.jupiter.api.Test;

class LinePairsTest {

    private static final String HEADER = """
            diff --git a/pom.xml b/pom.xml
            --- a/pom.xml
            +++ b/pom.xml
            """;

    @Test
    void showsABlockOfAsManyAddedLinesAsRemovedOnesAPairAtATime() {
        List<ParsedLine> lines = lines("""
                @@ -1,4 +1,4 @@
                 <spotless>3.10.4</spotless>
                -<checkstyle>14.3.0</checkstyle>
                -<errorprone>2.51.0</errorprone>
                -<native>1.1.17</native>
                +<checkstyle>14.4.0</checkstyle>
                +<errorprone>2.52.0</errorprone>
                +<native>1.1.18</native>
                """);

        assertEquals(List.of(List.of(0), List.of(1, 4), List.of(2, 5), List.of(3, 6)), LinePairs.groups(lines));
    }

    @Test
    void showsABlockOfMoreRemovedLinesThanAddedOnesAsGitGivesIt() {
        List<ParsedLine> lines = lines("""
                @@ -1,2 +1,1 @@
                -<checkstyle>14.3.0</checkstyle>
                -<errorprone>2.51.0</errorprone>
                +<checkstyle>14.4.0</checkstyle>
                """);

        assertEquals(List.of(List.of(0), List.of(1), List.of(2)), LinePairs.groups(lines));
    }

    @Test
    void showsABlockWithARewrittenPairAsGitGivesIt() {
        List<ParsedLine> lines = lines("""
                @@ -1,2 +1,2 @@
                -<checkstyle>14.3.0</checkstyle>
                -int count = 1;
                +<checkstyle>14.4.0</checkstyle>
                +return List.of(everything, else);
                """);

        assertEquals(List.of(List.of(0), List.of(1), List.of(2), List.of(3)), LinePairs.groups(lines));
    }

    @Test
    void showsABlockWhoseWordsWereNotComparedAsGitGivesIt() {
        List<UnifiedDiff.DiffLine> diffLines =
                UnifiedDiff.parse(HEADER + """
                        @@ -1,1 +1,1 @@
                        -int count = 1;
                        +int count = 42;
                        """).files().head().hunks().head().lines();
        // With no budget left, the pair is not compared.
        List<ParsedLine> lines = diffLines.zipWith(
                WordDiff.segments(List.of(diffLines), 0).head(),
                (line, words) -> ParsedLine.of(line, words, Option.none()));

        assertEquals(List.of(List.of(0), List.of(1)), LinePairs.groups(lines));
    }

    @Test
    void putsTheNotesOfAPairAfterIt() {
        List<ParsedLine> lines = lines("""
                @@ -1,2 +1,2 @@
                -int count = 1;
                -int total = 2;
                \\ No newline at end of file
                +int count = 42;
                +int total = 43;
                """);

        assertEquals(List.of(List.of(0, 3), List.of(1, 4, 2)), LinePairs.groups(lines));
    }

    @Test
    void showsABlockWithAKeptLineAsGitGivesIt() {
        List<ParsedLine> lines = lines("""
                @@ -1,2 +1,2 @@
                -int count = 1;
                +int count = 42;
                 int between;
                -int total = 2;
                +int total = 43;
                """);

        assertEquals(
                List.of(List.of(0), List.of(1), List.of(2), List.of(3, 4)),
                LinePairs.groups(lines, index -> index == 1));
    }

    @Test
    void showsLinesOnlyAddedOneByOne() {
        List<ParsedLine> lines = lines("""
                @@ -1,1 +1,3 @@
                 int count = 1;
                +int total = 2;
                +int other = 3;
                """);

        assertEquals(List.of(List.of(0), List.of(1), List.of(2)), LinePairs.groups(lines));
    }

    @Test
    void aBlockOfTensOfThousandsOfPairsIsCutQuickly() {
        int pairs = 40_000;
        List<ParsedLine> removed = List.range(0, pairs).map(at -> changed(DiffLine.Kind.REMOVED, at));
        List<ParsedLine> added = List.range(0, pairs).map(at -> changed(DiffLine.Kind.ADDED, at));
        List<ParsedLine> lines = removed.append(
                        ParsedLine.of(DiffLine.note("\\ No newline at end of file"), List.empty(), Option.none()))
                .appendAll(added);

        // Each line takes a step; a pass over the block for each pair took tens of seconds.
        List<List<Integer>> groups = assertTimeoutPreemptively(Duration.ofSeconds(5), () -> LinePairs.groups(lines));

        assertEquals(pairs, groups.size());
        assertEquals(List.of(pairs - 1, 2 * pairs, pairs), groups.last());
    }

    @Test
    void aHunkOfTensOfThousandsOfShortBlocksIsCutQuickly() {
        int blocks = 20_000;
        List<ParsedLine> lines = List.range(0, blocks)
                .flatMap(at ->
                        List.of(context(at), changed(DiffLine.Kind.REMOVED, at), changed(DiffLine.Kind.ADDED, at)));

        // Each block takes a step; a pass over the rest of the hunk for each block took tens of seconds.
        List<List<Integer>> groups = assertTimeoutPreemptively(Duration.ofSeconds(5), () -> LinePairs.groups(lines));

        assertEquals(2 * blocks, groups.size());
        assertEquals(List.of(3 * blocks - 2, 3 * blocks - 1), groups.last());
    }

    /**
     * A context line numbered {@code at}.
     */
    private static ParsedLine context(int at) {
        return ParsedLine.of(
                new DiffLine(DiffLine.Kind.CONTEXT, "same " + at, Option.some(at + 1), Option.some(at + 1)),
                List.of(new WordDiff.Segment("same " + at, false)),
                Option.none());
    }

    /**
     * A line of {@code kind} numbered {@code at}, whose one segment changed.
     */
    private static ParsedLine changed(DiffLine.Kind kind, int at) {
        Option<Integer> number = Option.some(at + 1);
        return ParsedLine.of(
                new DiffLine(
                        kind,
                        "line " + at,
                        kind == DiffLine.Kind.REMOVED ? number : Option.none(),
                        kind == DiffLine.Kind.ADDED ? number : Option.none()),
                List.of(new WordDiff.Segment("line " + at, true)),
                Option.none());
    }

    private static List<ParsedLine> lines(String hunk) {
        return ParsedDiff.of(HEADER + hunk).files().head().hunks().head().lines();
    }
}
