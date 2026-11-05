package net.vanfleteren.jazygit.git.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.ParsedDiff.ParsedLine;
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
                (line, words) -> new ParsedLine(line, words, Option.none()));

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

    private static List<ParsedLine> lines(String hunk) {
        return ParsedDiff.of(HEADER + hunk).files().head().hunks().head().lines();
    }
}
