package net.vanfleteren.jazygit.ui.widgets;

import io.vavr.collection.Iterator;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.Objects;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import net.vanfleteren.jazygit.i18n.Messages;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.Image;
import org.commonmark.node.Link;
import org.commonmark.node.Node;
import org.commonmark.node.SourceSpan;
import org.commonmark.node.Text;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;

/**
 * The images of Markdown, which the terminal does not show, as a short text that says one is there: {@code [image]},
 * or {@code [image: …]} with its alt text, in place of the URL that tamboui shows of an image and the tag it shows of
 * an {@code <img>} (ADR 82). Only what Markdown reads as an image is replaced, not what is in code.
 */
public final class MarkdownImages {

    private static final Pattern IMG = Pattern.compile("<img\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern ATTRIBUTE =
            Pattern.compile("\\s([a-z-]+)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s\"'>]+))", Pattern.CASE_INSENSITIVE);
    // What Markdown could read as more than text in the text of a link.
    private static final Pattern SPECIAL = Pattern.compile("[\\\\`*_\\[\\]<>!&~]");
    // The alt text GitHub gives an image that is pasted, which says no more than the placeholder does.
    private static final String IMAGE = "image";

    private MarkdownImages() {}

    /**
     * {@code markdown} with each image a link to it that says it is an image.
     */
    public static String asLinks(String markdown) {
        return replaced(markdown, As.LINK);
    }

    /**
     * {@code markdown} with each image only the text that says it is an image, for a line that shows the Markdown as
     * it is written.
     */
    public static String asText(String markdown) {
        return replaced(markdown, As.TEXT);
    }

    private static String replaced(String markdown, As as) {
        Node document = Parser.builder()
                .includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES)
                .build()
                .parse(markdown);
        // From the end, so what is replaced first leaves where the others are as it was.
        return replacements(document, markdown, as)
                .sortBy(Replacement::start)
                .reverse()
                .foldLeft(
                        markdown,
                        (text, replacement) -> text.substring(0, replacement.start())
                                + replacement.text()
                                + text.substring(replacement.end()));
    }

    /**
     * The images under {@code node}, parsed from {@code markdown}, with what replaces them {@code as} it says; in a
     * link no link, as no link can be in another.
     */
    private static List<Replacement> replacements(Node node, String markdown, As as) {
        return List.ofAll(Iterator.iterate(node.getFirstChild(), Node::getNext).takeWhile(Objects::nonNull))
                .flatMap(child -> switch (child) {
                    case Image image -> whole(image, placeholder(alt(image), Option.of(image.getDestination()), as));
                    case HtmlInline html
                    when IMG.matcher(html.getLiteral()).matches() -> whole(html, tagPlaceholder(html.getLiteral(), as));
                    case HtmlBlock html
                    when IMG.matcher(html.getLiteral()).replaceAll("").isBlank() -> tags(html, markdown, as);
                    default -> replacements(child, markdown, child instanceof Link && as == As.LINK ? As.MARKDOWN : as);
                });
    }

    /**
     * {@code node}, from where its source starts to where it ends, replaced by {@code text}.
     */
    private static List<Replacement> whole(Node node, String text) {
        List<SourceSpan> spans = List.ofAll(node.getSourceSpans());
        return spans.isEmpty()
                ? List.empty()
                : List.of(new Replacement(
                        spans.head().getInputIndex(),
                        spans.last().getInputIndex() + spans.last().getLength(),
                        text));
    }

    /**
     * Each {@code <img>} of a block of HTML in {@code markdown} that has nothing else: a line of only those becomes a
     * paragraph.
     */
    private static List<Replacement> tags(HtmlBlock html, String markdown, As as) {
        return List.ofAll(html.getSourceSpans())
                .flatMap(span -> List.ofAll(IMG.matcher(markdown)
                                .region(span.getInputIndex(), span.getInputIndex() + span.getLength())
                                .results()
                                .toList())
                        .map(tag -> new Replacement(tag.start(), tag.end(), tagPlaceholder(tag.group(), as))));
    }

    /**
     * The placeholder of an {@code <img>} {@code tag}, from its alt text and where it is.
     */
    private static String tagPlaceholder(String tag, As as) {
        List<MatchResult> attributes =
                List.ofAll(ATTRIBUTE.matcher(tag).results().toList());
        return placeholder(attribute(attributes, "alt").getOrElse(""), attribute(attributes, "src"), as);
    }

    private static Option<String> attribute(List<MatchResult> attributes, String name) {
        return attributes
                .find(attribute -> attribute.group(1).equalsIgnoreCase(name))
                .map(attribute -> Option.of(attribute.group(2))
                        .orElse(() -> Option.of(attribute.group(3)))
                        .getOrElse(() -> attribute.group(4)));
    }

    /**
     * What says an image with {@code alt} text is there, {@code as} it says: a link to its {@code source}, when it can
     * be one.
     */
    private static String placeholder(String alt, Option<String> source, As as) {
        String said = alt.isBlank() || alt.strip().equalsIgnoreCase(IMAGE)
                ? Messages.get("markdown.image")
                : Messages.get("markdown.imageAlt", alt.strip().replaceAll("\\s+", " "));
        if (as == As.TEXT) {
            return said;
        }
        String escaped = SPECIAL.matcher(said).replaceAll("\\\\$0");
        return source.filter(url -> as == As.LINK && !url.isBlank() && !url.matches("(?s).*[<>\\n].*"))
                .map(url -> "[" + escaped + "](<" + url + ">)")
                .getOrElse(escaped);
    }

    /**
     * The alt text of {@code image}: the text it holds.
     */
    private static String alt(Node image) {
        return List.ofAll(Iterator.iterate(image.getFirstChild(), Node::getNext).takeWhile(Objects::nonNull))
                .map(child -> child instanceof Text text ? text.getLiteral() : alt(child))
                .mkString();
    }

    /**
     * What an image is replaced by: a link to it, Markdown that only says it is there, or text that does.
     */
    private enum As {
        LINK,
        MARKDOWN,
        TEXT
    }

    private record Replacement(int start, int end, String text) {}
}
