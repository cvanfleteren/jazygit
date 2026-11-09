package net.vanfleteren.jazygit.ui.widgets;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.control.Option;
import org.junit.jupiter.api.Test;

/**
 * References to issues and pull requests, and bare URLs, as links in the text of a comment (ADR 98).
 */
class MarkdownAutolinksTest {

    private static final Option<MarkdownAutolinks.Repository> REPOSITORY =
            MarkdownAutolinks.Repository.of("github.com", "cvf/jazygit");

    private static String linked(String markdown) {
        return MarkdownAutolinks.linked(markdown, REPOSITORY);
    }

    private static String unchanged(String markdown) {
        assertEquals(markdown, linked(markdown));
        return markdown;
    }

    @Test
    void aReferenceLinksToTheIssuesOfTheRepository() {
        assertEquals("Closes [#290](<https://github.com/cvf/jazygit/issues/290>).", linked("Closes #290."));
    }

    @Test
    void aReferenceToAnotherRepositoryLinksThereOnTheSameHost() {
        assertEquals(
                "See [tamboui/tamboui\\_x#12](<https://github.com/tamboui/tamboui_x/issues/12>)",
                linked("See tamboui/tamboui_x#12"));
    }

    @Test
    void aReferenceLinksInBracketsEmphasisAndAtTheStartOfALine() {
        assertEquals(
                "([#1](<https://github.com/cvf/jazygit/issues/1>)) **[#2](<https://github.com/cvf/jazygit/issues/2>)**"
                        + " _[#3](<https://github.com/cvf/jazygit/issues/3>)_\n"
                        + "[#4](<https://github.com/cvf/jazygit/issues/4>) at the start",
                linked("(#1) **#2** _#3_\n#4 at the start"));
    }

    @Test
    void whatOnlyLooksLikeAReferenceStaysText() {
        unchanged("page#12 #12abc a/b/c#3 #0 #012 #1234567890 \\#5 &#35; snake_#7 #8_x");
    }

    @Test
    void aReferenceInCodeOrALinkStaysAsItIs() {
        unchanged("`#1`\n\n```\n#2\n```\n\n    #3\n\n[#4](https://x.io) <!-- #5 --> ![#6](a.png)");
    }

    @Test
    void withoutARepositoryAReferenceStaysTextButAURLLinks() {
        assertEquals(
                "#1 and [https://x.io](<https://x.io>)",
                MarkdownAutolinks.linked("#1 and https://x.io", Option.none()));
    }

    @Test
    void aRepositoryNeedsAHostAndAName() {
        assertEquals(Option.none(), MarkdownAutolinks.Repository.of("", "cvf/jazygit"));
        assertEquals(Option.none(), MarkdownAutolinks.Repository.of("github.com", ""));
    }

    @Test
    void aBareURLLinksToItselfWithoutThePunctuationItEndsWith() {
        assertEquals(
                "See [https://x.io/a\\_b](<https://x.io/a_b>), ([https://x.io/c](<https://x.io/c>)) or"
                        + " [https://en.wikipedia.org/wiki/A\\_(b)](<https://en.wikipedia.org/wiki/A_(b)>).",
                linked("See https://x.io/a_b, (https://x.io/c) or https://en.wikipedia.org/wiki/A_(b)."));
    }

    @Test
    void aReferenceInAURLIsPartOfTheURL() {
        assertEquals("[https://x.io/page#12](<https://x.io/page#12>)", linked("https://x.io/page#12"));
    }

    @Test
    void whatOnlyLooksLikeAURLStaysText() {
        unchanged("xhttps://x.io https:// `https://x.io` <https://x.io> [x](https://x.io)");
    }

    @Test
    void aURLKeepsItsCase() {
        assertEquals("[HTTPS://X.io/A](<HTTPS://X.io/A>)", linked("HTTPS://X.io/A"));
    }
}
