package net.vanfleteren.jazygit.ui.widgets;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Vector;
import net.vanfleteren.jazygit.image.Picture;
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

    @Test
    void aPictureOfOneRowIsDrawnInTheTextAsAColumnForEachOfItsCells() {
        Picture icon = new Picture(4, 4, Vector.fill(16, 0xffe07828));

        MarkdownImages.Drawn drawn = MarkdownImages.drawn(
                "Working… " + SPINNER, HashMap.of("https://github.com/user-attachments/assets/5ac3", icon), 40);

        // 14 pixels wide and high, as its tag says: two columns of one row.
        assertEquals("Working… \uE000\uE000", drawn.markdown());
        assertEquals(List.of(icon.fit(2, 2, 40, Picture.MAX_ROWS)), drawn.pictures());
    }

    @Test
    void aPictureOfMoreRowsIsDrawnOnlyForAnImageThatIsAllThereIsOfItsParagraph() {
        Picture shot = new Picture(6, 4, Vector.fill(24, 0xff112233));
        HashMap<String, Picture> pictures = HashMap.of("a.png", shot, "b.png", shot);

        MarkdownImages.Drawn drawn = MarkdownImages.drawn("![shot](a.png)\n\nSee ![it](b.png) here.", pictures, 40);

        assertEquals(
                "\uE000".repeat(6) + "\\\n" + "\uE000".repeat(6) + "\n\nSee [\\[image: it\\]](<b.png>) here.",
                drawn.markdown());
        assertEquals(List.of(shot), drawn.pictures());
    }

    @Test
    void picturesInABlockOfHtmlAreParagraphsOfTheirOwnAndNoWiderThanTheRoomTheyHave() {
        Picture shot = new Picture(8, 2, Vector.fill(16, 0xff112233));

        MarkdownImages.Drawn drawn = MarkdownImages.drawn(
                "<img src=\"a.png\">\n<img src=\"b.png\">", HashMap.of("a.png", shot, "b.png", shot), 4);

        assertEquals("\n\n\uE000\uE000\uE000\uE000\n\n\n\n\n\uE001\uE001\uE001\uE001\n\n", drawn.markdown());
        assertEquals(2, drawn.pictures().size());
        assertEquals(4, drawn.pictures().head().width());
    }
}
