package net.vanfleteren.jazygit.kernel.ui.markdown;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import io.vavr.control.Option;
import org.junit.jupiter.api.Test;

/**
 * The images of Markdown, where they are and how large their tags say they are shown (ADR 82).
 */
class MarkdownImageTest {

    @Test
    void anImgTagSaysWhereItIsAndHowLargeItIsShownInPixels() {
        MarkdownImage image = MarkdownImage.in("Hi <img src=\"a.png\" width=\"14px\" height='20' alt=x>")
                .single();

        assertEquals(Option.of("a.png"), image.source());
        assertEquals(Option.of(14), image.width());
        assertEquals(Option.of(20), image.height());
        assertEquals("x", image.alt());
        assertEquals(false, image.alone());
    }

    @Test
    void aWidthInPercentSaysNothing() {
        assertEquals(
                Option.none(),
                MarkdownImage.in("<img src=\"a.png\" width=\"50%\">").single().width());
    }

    @Test
    void anImageIsAloneWhenItIsAllThereIsOfItsParagraphOrInABlockOfHtml() {
        assertEquals(
                List.of(true, false, true),
                MarkdownImage.in("![a](a.png)\n\nsee ![b](b.png)\n\n<img src=\"c.png\">")
                        .map(MarkdownImage::alone));
    }
}
