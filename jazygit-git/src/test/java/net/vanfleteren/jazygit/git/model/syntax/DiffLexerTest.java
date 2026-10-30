package net.vanfleteren.jazygit.git.model.syntax;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.ChangeType;
import net.vanfleteren.jazygit.git.model.UnifiedDiff;
import net.vanfleteren.jazygit.git.model.UnifiedDiff.FileDiff;
import net.vanfleteren.jazygit.syntax.Lexer;
import net.vanfleteren.jazygit.syntax.Token;
import org.junit.jupiter.api.Test;

class DiffLexerTest {

    @Test
    void anOldAndANewTextAreLexedApart() {
        FileDiff file = file("A.java", """
                @@ -1,3 +1,3 @@
                -/* old
                +int added;
                 int context;
                 */ int after;
                """);

        assertEquals(
                List.of(List.of(
                        List.of(new Token(0, 6, Token.Kind.COMMENT)),
                        List.of(new Token(0, 3, Token.Kind.KEYWORD)),
                        List.of(new Token(0, 3, Token.Kind.KEYWORD)),
                        List.of(new Token(3, 3, Token.Kind.KEYWORD)))),
                lexed(file));
    }

    @Test
    void eachHunkStartsOutsideOfEverything() {
        FileDiff file = file("A.java", """
                @@ -1 +1 @@
                 /* open
                @@ -9 +9 @@
                 int x;
                """);

        assertEquals(
                List.of(
                        List.of(List.of(new Token(0, 7, Token.Kind.COMMENT))),
                        List.of(List.of(new Token(0, 3, Token.Kind.KEYWORD)))),
                lexed(file));
    }

    @Test
    void aNoteIsNotLexed() {
        FileDiff file = file("A.java", """
                @@ -1 +1 @@
                -int a;
                \\ No newline at end of file
                +int b;
                """);

        assertEquals(
                List.of(List.of(
                        Option.of(List.of(new Token(0, 3, Token.Kind.KEYWORD))),
                        Option.<List<Token>>none(),
                        Option.of(List.of(new Token(0, 3, Token.Kind.KEYWORD))))),
                new DiffLexer().tokens(file));
    }

    @Test
    void aFileOfNoLanguageKnownHasNoTokens() {
        FileDiff file = file("notes.txt", "@@ -1 +1 @@\n-int a;\n+int b;\n");

        assertEquals(List.of(List.of(Option.none(), Option.none())), new DiffLexer().tokens(file));
    }

    @Test
    void aShebangOnTheFirstLineTellsTheLanguage() {
        FileDiff file = file("bin/tool", "@@ -0,0 +1,2 @@\n+#!/bin/sh\n+exit 0\n");

        assertEquals(
                List.of(List.of(
                        List.of(new Token(0, 9, Token.Kind.COMMENT)),
                        List.of(new Token(0, 4, Token.Kind.KEYWORD), new Token(5, 1, Token.Kind.NUMBER)))),
                lexed(file));
    }

    @Test
    void aConflictedFileIsNotLexed() {
        FileDiff file = UnifiedDiff.parse("""
                        diff --cc A.java
                        index 1,2..0
                        --- a/A.java
                        +++ b/A.java
                        @@@ -1,1 -1,1 +1,1 @@@
                        ++int a;
                        """).files().head();

        assertEquals(ChangeType.CONFLICTED, file.type());
        assertEquals(List.of(List.of(Option.none())), new DiffLexer().tokens(file));
    }

    @Test
    void aHunkWhoseLinesAreNotNumberedIsNotLexed() {
        FileDiff file = file("A.java", """
                @@ unreadable @@
                 int a;
                @@ -1 +1 @@
                 int b;
                """);

        assertEquals(
                List.of(List.of(Option.none()), List.of(Option.of(List.of(new Token(0, 3, Token.Kind.KEYWORD))))),
                new DiffLexer().tokens(file));
    }

    @Test
    void aLineTooLongIsNotLexedAndTheLineAfterItStartsOutside() {
        String tooLong = "/* " + "x".repeat(Lexer.MAX_LINE);
        FileDiff file = file("A.js", "@@ -0,0 +1,2 @@\n+" + tooLong + "\n+let a;\n");

        assertEquals(
                List.of(List.of(Option.none(), Option.of(List.of(new Token(0, 3, Token.Kind.KEYWORD))))),
                new DiffLexer().tokens(file));
    }

    @Test
    void aLineAsLongAsMayBeIsLexed() {
        String longest = "let " + "x".repeat(Lexer.MAX_LINE - 4);
        FileDiff file = file("A.js", "@@ -0,0 +1 @@\n+" + longest + "\n");

        assertEquals(List.of(List.of(List.of(new Token(0, 3, Token.Kind.KEYWORD)))), lexed(file));
    }

    /**
     * The tokens of each line of each hunk of {@code file}, all of which are lexed.
     */
    private static List<List<List<Token>>> lexed(FileDiff file) {
        return new DiffLexer().tokens(file).map(hunk -> hunk.map(Option::get));
    }

    private static FileDiff file(String path, String hunks) {
        return UnifiedDiff.parse(
                        "diff --git a/" + path + " b/" + path + "\n--- a/" + path + "\n+++ b/" + path + "\n" + hunks)
                .files()
                .head();
    }
}
