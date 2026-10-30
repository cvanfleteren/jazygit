package net.vanfleteren.jazygit.git.model.syntax;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.git.model.ChangeType;
import net.vanfleteren.jazygit.git.model.UnifiedDiff;
import net.vanfleteren.jazygit.git.model.UnifiedDiff.FileDiff;
import net.vanfleteren.jazygit.git.model.syntax.Lexer.Lexed;
import net.vanfleteren.jazygit.git.model.syntax.Lexer.Outside;
import net.vanfleteren.jazygit.git.model.syntax.Lexer.State;
import org.junit.jupiter.api.Test;

class LexerTest {

    @Test
    void findsTheKeywordsStringsCommentsAndNumbersOfALine() {
        assertEquals(
                List.of("KEYWORD final", "KEYWORD int", "NUMBER 42", "STRING \"a // b\"", "COMMENT // the answer"),
                tokens(Language.JAVA, "final int answer = 42 + \"a // b\".length(); // the answer"));
    }

    @Test
    void anEscapedQuoteDoesNotEndAString() {
        assertEquals(List.of("STRING \"say \\\"hi\\\"\""), tokens(Language.JAVA, "\"say \\\"hi\\\"\";"));
    }

    @Test
    void aWordThatStartsWithAKeywordIsNoKeyword() {
        assertEquals(List.of(), tokens(Language.JAVA, "format(interval, newer);"));
    }

    @Test
    void aNumberTakesItsLettersAndItsPoint() {
        assertEquals(
                List.of("NUMBER 0x1F", "NUMBER 1_000L", "NUMBER 2.5e10"),
                tokens(Language.JAVA, "f(0x1F, 1_000L, 2.5e10, v2)"));
    }

    @Test
    void aBlockCommentGoesOnOverTheNextLines() {
        Lexed first = line(Language.JAVA, new Outside(), "int a; /* starts");
        Lexed second = line(Language.JAVA, first.state(), "  still a comment */ return a;");

        assertEquals(List.of("KEYWORD int", "COMMENT /* starts"), shown("int a; /* starts", first));
        assertEquals(
                List.of("COMMENT   still a comment */", "KEYWORD return"),
                shown("  still a comment */ return a;", second));
        assertEquals(new Outside(), second.state());
    }

    @Test
    void aStringOnOneLineEndsWithItsLine() {
        Lexed open = line(Language.JAVA, new Outside(), "String s = \"unclosed");

        assertEquals(List.of("KEYWORD return"), shown("return", line(Language.JAVA, open.state(), "return")));
    }

    @Test
    void aTextBlockGoesOnOverTheNextLines() {
        Lexed open = line(Language.JAVA, new Outside(), "String s = \"\"\"");

        assertEquals(List.of("STRING   if done"), shown("  if done", line(Language.JAVA, open.state(), "  if done")));
    }

    @Test
    void aQuoteRightAfterALetterStartsNoString() {
        assertEquals(List.of("COMMENT # it's"), tokens(Language.YAML, "note: don't # it's"));
    }

    @Test
    void aHashStartsAShellCommentOnlyAfterASpace() {
        assertEquals(
                List.of("KEYWORD if", "NUMBER 0", "KEYWORD then", "COMMENT # none"),
                tokens(Language.SHELL, "if [ $# -eq 0 ]; then # none"));
    }

    @Test
    void pythonKnowsItsPrefixedStrings() {
        assertEquals(
                List.of("KEYWORD return", "STRING f'{name}'", "KEYWORD if", "KEYWORD else", "STRING rb\"\\d\""),
                tokens(Language.PYTHON, "return f'{name}' if x else rb\"\\d\""));
    }

    @Test
    void sqlKeywordsAreMatchedIgnoringCase() {
        assertEquals(
                List.of("KEYWORD SELECT", "KEYWORD from", "KEYWORD Where", "STRING 'x'", "COMMENT -- all"),
                tokens(Language.SQL, "SELECT a from t Where b = 'x' -- all"));
    }

    @Test
    void aJavascriptTemplateIsAStringWithWhatItHolds() {
        assertEquals(
                List.of("KEYWORD const", "STRING `${a + 1} more`"),
                tokens(Language.JAVASCRIPT, "const s = `${a + 1} more`;"));
    }

    @Test
    void aTagHasItsNameAndTheValuesOfItsAttributes() {
        assertEquals(
                List.of("KEYWORD a", "STRING \"x.html\"", "KEYWORD a", "COMMENT <!-- link -->"),
                tokens(Language.XML, "<a href=\"x.html\">it's 1 < 2</a> <!-- link -->"));
    }

    @Test
    void aTagGoesOnOverTheNextLines() {
        Lexed open = line(Language.XML, new Outside(), "<dependency");

        assertEquals(
                List.of("STRING \"compile\"", "KEYWORD scope"),
                shown(" scope=\"compile\"><scope>", line(Language.XML, open.state(), " scope=\"compile\"><scope>")));
    }

    @Test
    void markdownHasHeadingsMarkersCodeAndComments() {
        assertEquals(List.of("KEYWORD ## Usage"), tokens(Language.MARKDOWN, "## Usage"));
        assertEquals(
                List.of("KEYWORD >", "KEYWORD -", "STRING `mvn verify`", "COMMENT <!-- why -->"),
                tokens(Language.MARKDOWN, "> - run `mvn verify` first <!-- why -->"));
        assertEquals(List.of("KEYWORD 12."), tokens(Language.MARKDOWN, "12. step"));
        assertEquals(List.of(), tokens(Language.MARKDOWN, "#hashtag and 3.5 and -minus"));
    }

    @Test
    void aFencedCodeBlockIsAStringUntilItsFence() {
        Lexed open = line(Language.MARKDOWN, new Outside(), "```java");
        Lexed code = line(Language.MARKDOWN, open.state(), "## not a heading");
        Lexed close = line(Language.MARKDOWN, code.state(), "```");

        assertEquals(List.of("STRING ```java"), shown("```java", open));
        assertEquals(List.of("STRING ## not a heading"), shown("## not a heading", code));
        assertEquals(List.of("STRING ```"), shown("```", close));
        assertEquals(new Outside(), close.state());
    }

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
                new Lexer().tokens(file));
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
                new Lexer().tokens(file));
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
                        List.of(new Token(0, 3, Token.Kind.KEYWORD)),
                        List.<Token>of(),
                        List.of(new Token(0, 3, Token.Kind.KEYWORD)))),
                new Lexer().tokens(file));
    }

    @Test
    void aFileOfNoLanguageKnownHasNoTokens() {
        FileDiff file = file("notes.txt", "@@ -1 +1 @@\n-int a;\n+int b;\n");

        assertEquals(List.of(List.of(List.<Token>of(), List.<Token>of())), new Lexer().tokens(file));
    }

    @Test
    void aShebangOnTheFirstLineTellsTheLanguage() {
        FileDiff file = file("bin/tool", "@@ -0,0 +1,2 @@\n+#!/bin/sh\n+exit 0\n");

        assertEquals(
                List.of(List.of(
                        List.of(new Token(0, 9, Token.Kind.COMMENT)),
                        List.of(new Token(0, 4, Token.Kind.KEYWORD), new Token(5, 1, Token.Kind.NUMBER)))),
                new Lexer().tokens(file));
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
        assertEquals(List.of(List.of(List.<Token>of())), new Lexer().tokens(file));
    }

    private static FileDiff file(String path, String hunks) {
        return UnifiedDiff.parse(
                        "diff --git a/" + path + " b/" + path + "\n--- a/" + path + "\n+++ b/" + path + "\n" + hunks)
                .files()
                .head();
    }

    private static Lexed line(Language language, State state, String text) {
        return Lexer.line(language.grammar(), state, text);
    }

    private static List<String> tokens(Language language, String text) {
        return shown(text, line(language, new Outside(), text));
    }

    /**
     * The tokens of {@code text}, each as its kind and its text.
     */
    private static List<String> shown(String text, Lexed lexed) {
        return lexed.tokens().map(token -> token.kind() + " " + text.substring(token.start(), token.end()));
    }
}
