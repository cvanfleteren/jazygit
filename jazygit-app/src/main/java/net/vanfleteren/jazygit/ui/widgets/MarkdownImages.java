package net.vanfleteren.jazygit.ui.widgets;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import java.util.function.Function;
import java.util.regex.Pattern;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.image.MarkdownImage;
import net.vanfleteren.jazygit.image.Picture;

/**
 * The images of Markdown, which the terminal does not show as such: a picture of one that was read, drawn in half
 * blocks (ADR 84), or else a short text that says one is there, {@code [image]}, or {@code [image: …]} with its alt
 * text, in place of the URL that tamboui shows of an image and the tag it shows of an {@code <img>} (ADR 82). Only
 * what Markdown reads as an image is replaced, not what is in code.
 */
public final class MarkdownImages {

    /**
     * The first of the characters that stand for the cells of a picture while tamboui draws the Markdown: one of the
     * Unicode private use area, a column wide, the first for the first picture, the next for the next.
     */
    public static final char PICTURE = '';
    // As many pictures at most, so the characters stay in the private use area.
    private static final int MAX_PICTURES = 1024;
    // What Markdown could read as more than text in the text of a link.
    private static final Pattern SPECIAL = Pattern.compile("[\\\\`*_\\[\\]<>!&~]");
    // The alt text GitHub gives an image that is pasted, which says no more than the placeholder does.
    private static final String IMAGE = "image";

    private MarkdownImages() {}

    /**
     * {@code markdown} with each image a link to it that says it is an image.
     */
    public static String asLinks(String markdown) {
        return drawn(markdown, HashMap.empty(), 0).markdown();
    }

    /**
     * {@code markdown} with each image only the text that says it is an image, for a line that shows the Markdown as
     * it is written.
     */
    public static String asText(String markdown) {
        return replaced(markdown, image -> said(image.alt()));
    }

    /**
     * {@code markdown} with each image of which {@code pictures} has a picture, by where it is, that picture as large
     * as its tag says it is shown, or as the image is, but no wider than {@code columns}: a line of characters
     * {@link #PICTURE} for each row of cells, in a paragraph of its own if it is more than one high, which only an
     * image that is all there is of its paragraph can be. Each other image is a link that says it is one.
     */
    public static Drawn drawn(String markdown, Map<String, Picture> pictures, int columns) {
        List<MarkdownImage> images = MarkdownImage.in(markdown);
        List<Option<Picture>> shown = images.map(image -> image.source()
                .flatMap(pictures::get)
                .map(picture -> fitted(image, picture, columns))
                .filter(picture -> image.alone() || picture.rows() == 1));
        // Each picture its own character, in the order they are in: the text of each image, and how many came before.
        List<String> texts = images.zip(shown)
                .foldLeft(
                        Tuple.of(List.<String>empty(), 0),
                        (done, image) -> image._2()
                                .filter(picture -> done._2() < MAX_PICTURES)
                                .map(picture -> Tuple.of(
                                        done._1().append(cells(image._1(), picture, done._2())), done._2() + 1))
                                .getOrElse(() -> Tuple.of(done._1().append(link(image._1())), done._2())))
                ._1();
        return new Drawn(
                replace(markdown, images.zip(texts)),
                shown.flatMap(Function.identity()).take(MAX_PICTURES));
    }

    private static String replaced(String markdown, Function<MarkdownImage, String> text) {
        return replace(markdown, MarkdownImage.in(markdown).map(image -> Tuple.of(image, text.apply(image))));
    }

    // From the end, so what is replaced first leaves where the others are as it was.
    private static String replace(String markdown, List<Tuple2<MarkdownImage, String>> replaced) {
        return replaced.sortBy(image -> image._1().start())
                .reverse()
                .foldLeft(
                        markdown,
                        (text, image) -> text.substring(0, image._1().start())
                                + image._2()
                                + text.substring(image._1().end()));
    }

    /**
     * {@code picture} as large as {@code image} says it is shown, the side it leaves out as the picture has it, or as
     * it is, no wider than {@code columns} less what quotes and lists indent it by, and no higher than
     * {@link Picture#MAX_ROWS}.
     */
    private static Picture fitted(MarkdownImage image, Picture picture, int columns) {
        Option<Integer> tagWidth = image.width().map(pixels -> Math.ceilDiv(pixels, Picture.SCALE));
        Option<Integer> tagHeight = image.height().map(pixels -> Math.ceilDiv(pixels, Picture.SCALE));
        int width = tagWidth.getOrElse(() -> tagHeight
                .map(height -> Math.max(1, (int) Math.round((double) height * picture.width() / picture.height())))
                .getOrElse(picture.width()));
        int height = tagHeight.getOrElse(() -> tagWidth.isDefined()
                ? Math.max(1, (int) Math.round((double) width * picture.height() / picture.width()))
                : picture.height());
        return picture.fit(width, height, Math.max(1, columns - image.indent()), Picture.MAX_ROWS);
    }

    /**
     * The characters that stand for the cells of {@code picture}, the {@code index}th: a row of them for each row of
     * cells, each row a line of its own. A block of HTML at the top becomes a paragraph of its own; in a quote or a
     * list it stays in its item, and a line of its own keeps it apart from an image on the next line.
     */
    private static String cells(MarkdownImage image, Picture picture, int index) {
        String row = String.valueOf((char) (PICTURE + index)).repeat(picture.width());
        String rows = List.fill(picture.rows(), row).mkString("\\\n");
        if (image.block() && image.indent() == 0) {
            return "\n\n" + rows + "\n\n";
        }
        return image.breakAfter() ? rows + "\\" : rows;
    }

    /**
     * What says {@code image} is there: a link to it, unless it is in a link already or is nowhere.
     */
    private static String link(MarkdownImage image) {
        String escaped = SPECIAL.matcher(said(image.alt())).replaceAll("\\\\$0");
        return image.source()
                .filter(url -> !image.inLink() && !url.isBlank() && !url.matches("(?s).*[<>\\n].*"))
                .map(url -> "[" + escaped + "](<" + url + ">)")
                .getOrElse(escaped);
    }

    private static String said(String alt) {
        return alt.isBlank() || alt.strip().equalsIgnoreCase(IMAGE)
                ? Messages.get("markdown.image")
                : Messages.get("markdown.imageAlt", alt.strip().replaceAll("\\s+", " "));
    }

    /**
     * Markdown with pictures in it, as {@link #drawn} makes it.
     *
     * @param markdown the Markdown, a run of characters for each row of cells of a picture
     * @param pictures the pictures, the first for {@link #PICTURE}, the next for the character after it
     */
    public record Drawn(String markdown, List<Picture> pictures) {}
}
