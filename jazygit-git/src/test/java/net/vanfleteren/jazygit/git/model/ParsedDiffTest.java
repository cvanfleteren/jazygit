package net.vanfleteren.jazygit.git.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import io.vavr.control.Option;
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
        // Drawing a diff takes the words and the tokens of a line together.
        diffs.flatMap(diff -> List.of(ParsedDiff.of(diff), ParsedDiff.of(diff).withNote(NOTE)))
                .forEach(parsed -> assertEquals(
                        parsed.words().map(List::size),
                        parsed.tokens().map(List::size),
                        parsed.diff().toString()));
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
                                Option.of(List.of(new Token(0, 3, Token.Kind.KEYWORD))),
                                Option.of(List.of(new Token(0, 4, Token.Kind.KEYWORD)))),
                        List.of(Option.<List<Token>>none(), Option.<List<Token>>none())),
                diff.tokens());
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
