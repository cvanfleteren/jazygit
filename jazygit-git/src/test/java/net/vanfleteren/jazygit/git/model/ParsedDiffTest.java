package net.vanfleteren.jazygit.git.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.Array;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.ParsedDiff.ParsedFile;
import net.vanfleteren.jazygit.git.model.ParsedDiff.ParsedLine;
import net.vanfleteren.jazygit.git.model.UnifiedDiff.DiffLine;
import net.vanfleteren.jazygit.git.model.UnifiedDiff.Hunk;
import net.vanfleteren.jazygit.syntax.Lexer;
import net.vanfleteren.jazygit.syntax.Token;
import org.junit.jupiter.api.Test;

class ParsedDiffTest {

    private static final String NOTE = "… truncated";

    @Test
    void aNoteAfterADiffIsWhatParsingItAsTheLastLineOfTheTextGives() {
        List<String> diffs = List.of(
                // Cut off in a hunk, after a block of changed lines that are compared.
                """
                diff --git a/a.txt b/a.txt
                --- a/a.txt
                +++ b/a.txt
                @@ -1,2 +1,2 @@
                 same
                -count = 1
                +count = 2
                """,
                // Cut off in a block comment of a file whose code is lexed.
                """
                diff --git a/A.java b/A.java
                --- a/A.java
                +++ b/A.java
                @@ -1,2 +1,2 @@
                 int same;
                -/* old
                +/* new
                """,
                // Cut off in the header of a file whose code is lexed, after another one.
                """
                diff --git a/A.java b/A.java
                --- a/A.java
                +++ b/A.java
                @@ -1 +1 @@
                -int a;
                +long a;
                diff --git a/B.java b/B.java
                index 1234567..89abcde 100644
                """,
                // Cut off in a string over lines of a file whose language its shebang tells.
                """
                diff --git a/bin/tool b/bin/tool
                new file mode 100755
                --- /dev/null
                +++ b/bin/tool
                @@ -0,0 +1,2 @@
                +#!/usr/bin/env python3
                +s = \"""starts
                """,
                // Cut off after removed lines, before the added ones they would be paired with.
                """
                diff --git a/a.txt b/a.txt
                --- a/a.txt
                +++ b/a.txt
                @@ -1 +1 @@
                -count = 1
                """,
                // Cut off in the header of a file, after a file with hunks.
                """
                diff --git a/a.txt b/a.txt
                --- a/a.txt
                +++ b/a.txt
                @@ -1 +1 @@
                -a
                +b
                diff --git a/b.txt b/b.txt
                index 1234567..89abcde 100644
                """,
                // Cut off in a header with lines the diff does not know, which are a hunk of their own.
                """
                diff --git a/a.txt b/a.txt
                unknown header line
                """,
                // A binary file, which has no hunks.
                """
                diff --git a/a.bin b/a.bin
                Binary files a/a.bin and b/a.bin differ
                """,
                // Only what comes before the first file, like a stat.
                """
                 a.txt | 2 +-
                """,
                "");

        diffs.forEach(diff -> assertEquals(
                ParsedDiff.of(diff + NOTE + "\n"), ParsedDiff.of(diff).withNote(NOTE), diff));
        // Each part of the tree wraps the part of the diff it is in: drawing takes a line with its words and tokens.
        diffs.flatMap(diff -> List.of(ParsedDiff.of(diff), ParsedDiff.of(diff).withNote(NOTE)))
                .forEach(parsed -> assertEquals(
                        parsed.diff().files().map(file -> file.hunks().map(Hunk::lines)),
                        parsed.files()
                                .map(file ->
                                        file.hunks().map(hunk -> hunk.lines().map(ParsedLine::line))),
                        parsed.diff().toString()));
    }

    @Test
    void aLineWithoutChangedWordsKeepsNoSegmentButGivesItselfWhole() {
        ParsedDiff diff = ParsedDiff.of("""
                diff --git a/a.txt b/a.txt
                --- a/a.txt
                +++ b/a.txt
                @@ -1,3 +1,2 @@
                 same
                -ten = 1
                +ten = 2
                -gone
                """);
        List<ParsedLine> lines = diff.files().head().hunks().head().lines();

        // Only the pair whose words were compared keeps its segments.
        assertEquals(
                List.of(true, false, false, true),
                lines.map(line -> line.words()
                        .equals(List.of(new WordDiff.Segment(line.line().text(), false)))));
        assertEquals(
                List.of(new WordDiff.Segment("ten = ", false), new WordDiff.Segment("1", true)),
                lines.get(1).words());
        // The whole line is not kept: a line made with it is the line made with none.
        assertEquals(
                List.of(true, false, false, true),
                lines.map(line -> line.compared().isEmpty()));
        assertEquals(
                ParsedLine.of(lines.head().line(), List.empty(), Option.none()),
                ParsedLine.of(lines.head().line(), lines.head().words(), Option.none()));
    }

    @Test
    void theCodeOfEachFileIsLexedInItsLanguage() {
        ParsedDiff diff = ParsedDiff.of("""
                diff --git a/A.java b/A.java
                --- a/A.java
                +++ b/A.java
                @@ -1 +1 @@
                -int a;
                +long a;
                diff --git a/notes.txt b/notes.txt
                --- a/notes.txt
                +++ b/notes.txt
                @@ -1 +1 @@
                -int a;
                +long a;
                """);

        assertEquals(
                List.of(
                        List.of(
                                Option.of(Array.of(new Token(0, 3, Token.Kind.KEYWORD))),
                                Option.of(Array.of(new Token(0, 4, Token.Kind.KEYWORD)))),
                        List.of(Option.<Array<Token>>none(), Option.<Array<Token>>none())),
                diff.files().flatMap(ParsedFile::hunks).map(hunk -> hunk.lines().map(ParsedLine::tokens)));
    }

    @Test
    void aLineThatWasNotLexedIsToldFromALexedLineWithoutTokens() {
        DiffLine line = new DiffLine(DiffLine.Kind.CONTEXT, "  ", 1, 1);

        assertEquals(
                Option.none(), ParsedLine.of(line, List.empty(), Option.none()).tokens());
        assertEquals(
                Option.of(Array.empty()),
                ParsedLine.of(line, List.empty(), Option.of(Array.empty())).tokens());
        assertEquals(
                ParsedLine.of(line, List.empty(), Option.none()), ParsedLine.of(line, List.empty(), Option.none()));
    }

    @Test
    void eachTokenIsKeptInAnIntAndMadeAgainAsItWas() {
        DiffLine line = new DiffLine(DiffLine.Kind.ADDED, "x".repeat(Lexer.MAX_LINE), DiffLine.NONE, 1);
        // Each kind, at even and odd starts and lengths, and one that ends where the longest line lexed does.
        Array<Token> tokens = Array.of(Token.Kind.values())
                .zipWithIndex((kind, at) -> new Token(10 * at + at % 2, 2 + at % 2, kind))
                .append(new Token(Lexer.MAX_LINE - Lexer.MAX_LINE / 2, Lexer.MAX_LINE / 2, Token.Kind.ANNOTATION));

        ParsedLine lexed = ParsedLine.of(line, List.empty(), Option.of(tokens));

        assertEquals(Option.of(tokens), lexed.tokens());
        assertEquals(tokens.size(), lexed.lexed().length);
    }

    @Test
    void whereATokenStartsAndHowLongItIsFitInAPackedTokenForEveryLineThatIsLexed() {
        assertTrue(Lexer.MAX_LINE < 1 << ParsedLine.BITS);
    }

    @Test
    void theLexedLinesWithoutATokenShareOneEmptyArray() {
        DiffLine line = new DiffLine(DiffLine.Kind.CONTEXT, "  ", 1, 1);

        assertSame(
                ParsedLine.of(line, List.empty(), Option.of(List.empty())).lexed(),
                ParsedLine.of(line, List.empty(), Option.of(Array.empty())).lexed());
    }

    @Test
    void aDiffIsParsedWhereItsTextIsMade() {
        DiffText diff = DiffText.of("""
                diff --git a/a.txt b/a.txt
                --- a/a.txt
                +++ b/a.txt
                @@ -1 +1 @@
                -count = 1
                +count = 2
                """);

        assertEquals(ParsedDiff.of(diff.text()), diff.parsed());
    }
}
