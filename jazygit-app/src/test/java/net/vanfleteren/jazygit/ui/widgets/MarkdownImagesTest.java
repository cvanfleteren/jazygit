package net.vanfleteren.jazygit.ui.widgets;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * The images of Markdown as a short text that says one is there (ADR 82).
 */
class MarkdownImagesTest {

    private static final String SPINNER = "<img src=\"https://github.com/user-attachments/assets/5ac3\" width=\"14px\""
            + " height=\"14px\" style=\"vertical-align: middle; margin-left: 4px;\" />";

    @Test
    void anImgTagInTheTextIsALinkToItsSource() {
        assertEquals(
                "Claude Code is working… [\\[image\\]](<https://github.com/user-attachments/assets/5ac3>)",
                MarkdownImages.asLinks("Claude Code is working… " + SPINNER));
    }

    @Test
    void anImageSaysItsAltTextUnlessItSaysNoMoreThanImage() {
        assertEquals(
                "[\\[image: a red button\\]](<a.png>) and [\\[image\\]](<b.png>)",
                MarkdownImages.asLinks("<img alt='a red button' src=a.png> and <img alt=\"Image\" src=\"b.png\">"));
    }

    @Test
    void aMarkdownImageIsALinkToItsDestination() {
        assertEquals(
                "See [\\[image: the diff\\]](<https://x.io/d.png>).",
                MarkdownImages.asLinks("See ![the *diff*](https://x.io/d.png \"title\")."));
    }

    @Test
    void anImageInALinkIsOnlyText() {
        assertEquals(
                "[\\[image: build\\]](https://ci.io)",
                MarkdownImages.asLinks("[![build](https://ci.io/b.svg)](https://ci.io)"));
    }

    @Test
    void imgTagsOnLinesOfTheirOwnBecomeAParagraph() {
        assertEquals(
                "before\n\n[\\[image\\]](<a.png>)\n[\\[image\\]](<b.png>)\n\nafter",
                MarkdownImages.asLinks("before\n\n<img src=\"a.png\">\n<img src=\"b.png\">\n\nafter"));
    }

    @Test
    void otherHtmlAndAnImgTagWithItAreLeftAsTheyAre() {
        String markdown = "<p align=\"center\">\n<img src=\"a.png\">\n</p>";

        assertEquals(markdown, MarkdownImages.asLinks(markdown));
    }

    @Test
    void anImgTagInCodeIsLeftAsItIs() {
        String markdown = "Write `<img src=\"a.png\">`:\n\n```html\n<img src=\"a.png\">\n```";

        assertEquals(markdown, MarkdownImages.asLinks(markdown));
    }

    @Test
    void anImageWithoutASourceIsOnlyText() {
        assertEquals("\\[image\\]", MarkdownImages.asLinks("<img width=\"4\">"));
    }

    @Test
    void asTextAnImageIsOnlyWhatSaysItIsOne() {
        assertEquals(
                "Working… [image], see [image: the diff]",
                MarkdownImages.asText("Working… " + SPINNER + ", see ![the diff](d.png)"));
    }
}
