package net.vanfleteren.jazygit.syntax;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Duration;
import net.vanfleteren.jazygit.syntax.Lexer.Lexed;
import net.vanfleteren.jazygit.syntax.Lexer.Outside;
import net.vanfleteren.jazygit.syntax.Lexer.State;
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
    void anEscapedBackslashDoesNotEscapeTheQuoteAfterIt() {
        assertEquals(List.of("STRING \"\\\\\"", "KEYWORD this"), tokens(Language.JAVA, "s = \"\\\\\" + this;"));
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
    void wordsThatAreKeywordsOnlyInSomePlacesAreNone() {
        assertEquals(List.of(), tokens(Language.JAVA, "Builder to = with(open, module, requires);"));
        assertEquals(List.of("KEYWORD val"), tokens(Language.KOTLIN, "val value = data.get(out).set(by)"));
        assertEquals(List.of(), tokens(Language.PYTHON, "x = type(y) + match(z)"));
        assertEquals(List.of(), tokens(Language.JAVASCRIPT, "map.get(key).set(type, from, of)"));
    }

    @Test
    void pythonKnowsEveryCaseOfItsPrefixes() {
        assertEquals(
                List.of("STRING RB\"a\"", "STRING rB'b'", "STRING Rb'c'"),
                tokens(Language.PYTHON, "x = RB\"a\" + rB'b' + Rb'c'"));
    }

    @Test
    void aTokenCountsTheCharsOfItsLine() {
        String line = "s = \"😀\"; int x;";

        assertEquals(List.of("STRING \"😀\"", "KEYWORD int"), tokens(Language.JAVA, line));
        assertEquals(
                List.of(new Token(4, 4, Token.Kind.STRING), new Token(10, 3, Token.Kind.KEYWORD)),
                line(Language.JAVA, new Outside(), line).tokens());
    }

    /**
     * The code in a script of HTML is scanned as text, as ADR 53 leaves it: a {@code <} before a letter is taken for
     * a tag, which runs on to the next {@code >}, so the closing tag is not coloured.
     */
    @Test
    void theCodeInAScriptIsScannedAsText() {
        assertEquals(
                List.of("KEYWORD script", "KEYWORD b", "STRING \"y\""),
                tokens(Language.XML, "<script>if (a<b) { x = \"y\"; }</script>"));
    }

    @Test
    void aStringOfManyEscapesIsReadOnce() {
        String line = "x = \"" + "\\n".repeat(80_000) + "\";";

        Lexed lexed = assertTimeoutPreemptively(Duration.ofMillis(500), () -> line(Language.JSON, new Outside(), line));

        assertEquals(List.of(new Token(4, line.length() - 5, Token.Kind.STRING)), lexed.tokens());
    }

    @Test
    void aLineLongerThanMayBeIsNotLexed() {
        String longest = "x".repeat(Lexer.MAX_LINE);

        assertEquals(
                Option.of(List.<Token>empty()),
                Lexer.lexed(Language.JAVA, new Outside(), longest).map(Lexed::tokens));
        assertEquals(Option.none(), Lexer.lexed(Language.JAVA, new Outside(), longest + "x"));
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
