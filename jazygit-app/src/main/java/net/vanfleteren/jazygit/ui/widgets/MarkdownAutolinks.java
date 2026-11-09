package net.vanfleteren.jazygit.ui.widgets;

import io.vavr.collection.Iterator;
import io.vavr.collection.List;
import io.vavr.control.Option;
import io.vavr.control.Try;
import java.net.URI;
import java.util.Objects;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.commonmark.node.Image;
import org.commonmark.node.Link;
import org.commonmark.node.Node;
import org.commonmark.node.SourceSpan;
import org.commonmark.node.Text;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;

/**
 * What GitHub links in the text of a comment that Markdown does not (ADR 98): a reference to an issue or pull request,
 * {@code #381} or {@code owner/repo#381}, and a URL that is only text. Each becomes a Markdown link that shows it as it
 * is written. Only text is linked, as commonmark reads it: not code, HTML, a link or the text of an image.
 */
public final class MarkdownAutolinks {

    /*
     * A URL that follows the start, a space, an opening of emphasis, a bracket or a quote; up to a space or what
     * cannot be in it. What it ends with is taken off after.
     */
    private static final Pattern URL =
            Pattern.compile("(?<![^\\s*_~(\"'])https?://[^\\s<>|\\[\\]`\\\\]+", Pattern.CASE_INSENSITIVE);
    /*
     * #N or owner/repo#N, not after a word, a path, an escape or an entity, and with no word right after it. An _
     * between it and a word keeps it a word, as an _ that opens or closes emphasis does not.
     */
    private static final Pattern REFERENCE = Pattern.compile("(?<![\\p{L}\\p{N}/\\\\&#]|[\\p{L}\\p{N}]_)"
            + "(?:([A-Za-z0-9][A-Za-z0-9-]{0,38})/([A-Za-z0-9._-]{1,100}))?#([1-9][0-9]{0,8})"
            + "(?![\\p{L}\\p{N}]|_[\\p{L}\\p{N}])");
    private static final String TRAILING = "?!.,:;*_~'\"";
    // What Markdown could read as more than text in the text of a link.
    private static final Pattern SPECIAL = Pattern.compile("[\\\\`*_\\[\\]<>!&~]");

    private MarkdownAutolinks() {}

    /**
     * {@code markdown} with each bare URL a link to itself and, when {@code repository} is known, each reference to an
     * issue or pull request a link to it there.
     */
    public static String linked(String markdown, Option<Repository> repository) {
        Node document = Parser.builder()
                .includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES)
                .build()
                .parse(markdown);
        List<Found> found = texts(document).flatMap(span -> in(markdown, span, repository));
        StringBuilder linked = new StringBuilder(markdown.length());
        int end = found.foldLeft(0, (from, link) -> {
            linked.append(markdown, from, link.start())
                    .append('[')
                    .append(SPECIAL.matcher(markdown.substring(link.start(), link.end()))
                            .replaceAll("\\\\$0"))
                    .append("](<")
                    .append(link.url())
                    .append(">)");
            return link.end();
        });
        return linked.append(markdown, end, markdown.length()).toString();
    }

    /**
     * Where the text of {@code node} is in the Markdown, outside links and images, in the order it is in.
     */
    private static List<SourceSpan> texts(Node node) {
        return children(node).flatMap(child -> switch (child) {
            case Text text -> List.ofAll(text.getSourceSpans());
            case Link link -> List.<SourceSpan>empty();
            case Image image -> List.<SourceSpan>empty();
            default -> texts(child);
        });
    }

    private static List<Node> children(Node node) {
        return List.ofAll(Iterator.iterate(node.getFirstChild(), Node::getNext).takeWhile(Objects::nonNull));
    }

    /**
     * The links in {@code span} of {@code markdown}: its URLs, and its references outside them.
     */
    private static List<Found> in(String markdown, SourceSpan span, Option<Repository> repository) {
        int from = span.getInputIndex();
        int to = from + span.getLength();
        List<Found> urls = matches(URL, markdown, from, to).flatMap(match -> url(markdown, match));
        List<Found> references = repository
                .toList()
                .flatMap(site -> matches(REFERENCE, markdown, from, to)
                        .filter(match -> urls.forAll(url -> match.end() <= url.start() || match.start() >= url.end()))
                        .map(match -> new Found(
                                match.start(),
                                match.end(),
                                site.issue(
                                        Option.of(match.group(1))
                                                .map(owner -> owner + "/" + match.group(2))
                                                .getOrElse(site.nameWithOwner()),
                                        match.group(3)))));
        return urls.appendAll(references).sortBy(Found::start);
    }

    // The matches in from..to of markdown, which sees what is around it, as its lookbehinds and lookaheads need.
    private static List<MatchResult> matches(Pattern pattern, String markdown, int from, int to) {
        Matcher matcher = pattern.matcher(markdown).region(from, to).useTransparentBounds(true);
        return List.ofAll(matcher.results().toList());
    }

    /**
     * The URL of {@code match}, without what it ends with that GitHub does not take as part of it; none when it then
     * has no host.
     */
    private static Option<Found> url(String markdown, MatchResult match) {
        int end = Iterator.iterate(match.end(), at -> at - 1)
                .find(at -> at <= match.start() || !trailing(markdown.substring(match.start(), at)))
                .get();
        String url = markdown.substring(match.start(), end);
        return Try.of(() -> URI.create(url).getHost())
                .toOption()
                .flatMap(Option::of)
                .map(host -> new Found(match.start(), end, url));
    }

    // Whether url ends with what is not taken as part of it: punctuation, or a ) that no ( in it opens.
    private static boolean trailing(String url) {
        char last = url.charAt(url.length() - 1);
        return TRAILING.indexOf(last) >= 0
                || last == ')'
                        && url.chars().filter(c -> c == ')').count()
                                > url.chars().filter(c -> c == '(').count();
    }

    /**
     * The repository a reference without one is to, as GitHub's site has it.
     *
     * @param host          the host it is on, like {@code github.com}
     * @param nameWithOwner the repository, as {@code owner/name}
     */
    public record Repository(String host, String nameWithOwner) {

        /**
         * The repository, when both its host and name are known.
         */
        public static Option<Repository> of(String host, String nameWithOwner) {
            return Option.when(!host.isBlank() && !nameWithOwner.isBlank(), () -> new Repository(host, nameWithOwner));
        }

        String issue(String nameWithOwner, String number) {
            return "https://" + host + "/" + nameWithOwner + "/issues/" + number;
        }
    }

    private record Found(int start, int end, String url) {}
}
