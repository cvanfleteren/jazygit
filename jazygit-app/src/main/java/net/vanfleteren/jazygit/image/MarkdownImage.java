package net.vanfleteren.jazygit.image;

import io.vavr.collection.Iterator;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.net.URI;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.Image;
import org.commonmark.node.Link;
import org.commonmark.node.Node;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SourceSpan;
import org.commonmark.node.Text;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;

/**
 * An image in Markdown, as commonmark reads it: a Markdown image, an {@code <img>} in the text, or one in a block of
 * HTML of only {@code <img>} tags; what is in code is none (ADR 82).
 *
 * @param start  where its source starts in the Markdown
 * @param end    where its source ends
 * @param alt    its alt text, maybe empty
 * @param source where it is
 * @param width  how wide its tag says it is shown, in pixels
 * @param height how high its tag says it is shown, in pixels
 * @param inLink whether it is in a link
 * @param block  whether it is in a block of HTML, which becomes a paragraph of its own
 * @param alone  whether it is all there is of its paragraph, or in a block of HTML
 */
public record MarkdownImage(
        int start,
        int end,
        String alt,
        Option<String> source,
        Option<Integer> width,
        Option<Integer> height,
        boolean inLink,
        boolean block,
        boolean alone) {

    private static final Pattern IMG = Pattern.compile("<img\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern ATTRIBUTE =
            Pattern.compile("\\s([a-z-]+)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s\"'>]+))", Pattern.CASE_INSENSITIVE);
    private static final Pattern PIXELS = Pattern.compile("\\s*(\\d{1,5})\\s*(?:px)?\\s*", Pattern.CASE_INSENSITIVE);

    /**
     * The images of {@code markdown}, in the order they are in.
     */
    public static List<MarkdownImage> in(String markdown) {
        Node document = Parser.builder()
                .includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES)
                .build()
                .parse(markdown);
        return found(document, markdown, false).sortBy(MarkdownImage::start);
    }

    /**
     * Where the images of {@code markdown} are that GitHub keeps, over https, which are the ones read to be shown
     * (ADR 84): an image elsewhere would tell its server who reads the comment.
     */
    public static List<String> onGitHub(String markdown) {
        return in(markdown)
                .flatMap(MarkdownImage::source)
                .filter(MarkdownImage::isOnGitHub)
                .distinct();
    }

    private static boolean isOnGitHub(String url) {
        return Option.of(url)
                .toTry()
                .map(URI::create)
                .filter(uri -> "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null)
                .map(uri -> uri.getHost().toLowerCase(Locale.ROOT))
                .filter(host -> host.equals("github.com") || host.endsWith(".githubusercontent.com"))
                .isSuccess();
    }

    private static List<MarkdownImage> found(Node node, String markdown, boolean inLink) {
        return children(node).flatMap(child -> switch (child) {
            case Image image ->
                whole(image, alt(image), Option.of(image.getDestination()), Option.none(), Option.none(), inLink);
            case HtmlInline html when IMG.matcher(html.getLiteral()).matches() -> tag(html, html.getLiteral(), inLink);
            case HtmlBlock html
            when IMG.matcher(html.getLiteral()).replaceAll("").isBlank() -> tags(html, markdown);
            default -> found(child, markdown, inLink || child instanceof Link);
        });
    }

    private static List<Node> children(Node node) {
        return List.ofAll(Iterator.iterate(node.getFirstChild(), Node::getNext).takeWhile(Objects::nonNull));
    }

    private static List<MarkdownImage> tag(Node node, String tag, boolean inLink) {
        List<MatchResult> attributes =
                List.ofAll(ATTRIBUTE.matcher(tag).results().toList());
        return whole(
                node,
                attribute(attributes, "alt").getOrElse(""),
                attribute(attributes, "src"),
                pixels(attribute(attributes, "width")),
                pixels(attribute(attributes, "height")),
                inLink);
    }

    /**
     * {@code node}, from where its source starts to where it ends.
     */
    private static List<MarkdownImage> whole(
            Node node,
            String alt,
            Option<String> source,
            Option<Integer> width,
            Option<Integer> height,
            boolean inLink) {
        List<SourceSpan> spans = List.ofAll(node.getSourceSpans());
        boolean alone = node.getParent() instanceof Paragraph paragraph
                && children(paragraph).forAll(child -> child == node || blank(child));
        return spans.isEmpty()
                ? List.empty()
                : List.of(new MarkdownImage(
                        spans.head().getInputIndex(),
                        spans.last().getInputIndex() + spans.last().getLength(),
                        alt,
                        source,
                        width,
                        height,
                        inLink,
                        false,
                        alone));
    }

    private static boolean blank(Node node) {
        return node instanceof Text text && text.getLiteral().isBlank();
    }

    /**
     * Each {@code <img>} of a block of HTML in {@code markdown} that has nothing else.
     */
    private static List<MarkdownImage> tags(HtmlBlock html, String markdown) {
        return List.ofAll(html.getSourceSpans())
                .flatMap(span -> List.ofAll(IMG.matcher(markdown)
                                .region(span.getInputIndex(), span.getInputIndex() + span.getLength())
                                .results()
                                .toList())
                        .map(match -> {
                            List<MatchResult> attributes = List.ofAll(
                                    ATTRIBUTE.matcher(match.group()).results().toList());
                            return new MarkdownImage(
                                    match.start(),
                                    match.end(),
                                    attribute(attributes, "alt").getOrElse(""),
                                    attribute(attributes, "src"),
                                    pixels(attribute(attributes, "width")),
                                    pixels(attribute(attributes, "height")),
                                    false,
                                    true,
                                    true);
                        }));
    }

    private static Option<String> attribute(List<MatchResult> attributes, String name) {
        return attributes
                .find(attribute -> attribute.group(1).equalsIgnoreCase(name))
                .map(attribute -> Option.of(attribute.group(2))
                        .orElse(() -> Option.of(attribute.group(3)))
                        .getOrElse(() -> attribute.group(4)));
    }

    // A size in pixels, such as 14 or 14px; not a percentage.
    private static Option<Integer> pixels(Option<String> value) {
        return value.map(PIXELS::matcher)
                .filter(Matcher::matches)
                .map(matcher -> Integer.parseInt(matcher.group(1)))
                .filter(pixels -> pixels > 0);
    }

    /**
     * The alt text of {@code image}: the text it holds.
     */
    private static String alt(Node image) {
        return children(image)
                .map(child -> child instanceof Text text ? text.getLiteral() : alt(child))
                .mkString();
    }
}
