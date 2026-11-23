package net.vanfleteren.jazygit.kernel.ui.markdown;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.buffer.Cell;
import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.RenderContext;
import dev.tamboui.toolkit.element.Size;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.toolkit.markdown.MarkdownElement;
import dev.tamboui.tui.event.MouseEvent;
import dev.tamboui.tui.event.MouseEventKind;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.HashMap;
import io.vavr.collection.Iterator;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import java.util.Locale;
import java.util.function.IntFunction;
import java.util.regex.Pattern;
import net.vanfleteren.jazygit.lib.image.Picture;

/**
 * Markdown that scrolls, as far as its end: how far that is is only known once it is rendered, at a width. Where it
 * is scrolled to is view state, kept here; {@link #toTop()} starts it over, as for other text. So is where its links
 * were drawn, for a click to open them (ADR 96).
 */
public final class MarkdownScroll {

    private static final int WHEEL_LINES = 3;
    // Bots fill their comments with these; tamboui would show them as text.
    private static final Pattern HTML_COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);

    private int offset;
    // As far as it scrolled, and how many lines it showed, when it was last rendered.
    private int furthest;
    private int page = 1;
    // The link drawn in each cell that has one, by where it is on the screen, when it was last rendered.
    private Map<Spot, String> links = HashMap.empty();

    /**
     * {@code markdown} without its HTML comments.
     */
    public static String withoutHtmlComments(String markdown) {
        return HTML_COMMENT.matcher(markdown).replaceAll("");
    }

    /**
     * {@code markdown}, without its HTML comments and with its images as links that say so (ADR 82), scrolled as far
     * as it was, filling the area it gets.
     */
    public Element render(String markdown) {
        return render(markdown, HashMap.empty());
    }

    /**
     * {@code markdown}, as {@link #render(String)} renders it, with each image of which {@code pictures} has a picture,
     * by where it is, drawn in half blocks as it is in a review remark (ADR 84, 187).
     */
    public Element render(String markdown, Map<String, Picture> pictures) {
        String shown = withoutHtmlComments(markdown);
        return new Scrolled(width -> pictures.isEmpty()
                ? new MarkdownImages.Drawn(MarkdownImages.asLinks(shown), List.empty())
                : MarkdownImages.drawn(shown, pictures, width));
    }

    /**
     * Scrolls {@code lines} further, or back when negative, not past either end.
     */
    public void scrollBy(int lines) {
        offset = Math.clamp((long) offset + lines, 0, furthest);
    }

    /**
     * Scrolls {@code pages} of what was shown further, keeping a line of the last one in view.
     */
    public void scrollPages(int pages) {
        scrollBy(pages * Math.max(1, page - 1));
    }

    /**
     * Starts over at the top, for other markdown, and forgets the links drawn, until it is rendered again.
     */
    public void toTop() {
        offset = 0;
        links = HashMap.empty();
    }

    /**
     * How far it is scrolled, in lines.
     */
    public int offset() {
        return offset;
    }

    /**
     * Scrolls as the wheel turns in {@code event}.
     */
    public EventResult wheel(MouseEvent event) {
        int direction =
                event.kind() == MouseEventKind.SCROLL_UP ? -1 : event.kind() == MouseEventKind.SCROLL_DOWN ? 1 : 0;
        if (direction == 0) {
            return EventResult.UNHANDLED;
        }
        scrollBy(direction * WHEEL_LINES);
        return EventResult.HANDLED;
    }

    /**
     * The link to the web, http or https, drawn at column {@code x} and row {@code y} of the screen when it was last
     * rendered; none where it drew no link, or one to elsewhere, which a browser would not know where to find.
     */
    public Option<String> linkAt(int x, int y) {
        return links.get(new Spot(x, y)).filter(MarkdownScroll::isWeb);
    }

    // A scheme is the same in any case (RFC 3986): HTTPS://… is a link to the web too.
    private static boolean isWeb(String url) {
        String scheme = url.toLowerCase(Locale.ROOT);
        return scheme.startsWith("https://") || scheme.startsWith("http://");
    }

    /**
     * The links drawn in {@code area} of {@code buffer}, by where they are. It is looked through on every render, so
     * only a cell with a link makes an entry.
     */
    private static Map<Spot, String> linksIn(Buffer buffer, Rect area) {
        Rect drawn = area.intersection(buffer.area());
        return HashMap.ofEntries(Iterator.range(drawn.top(), drawn.bottom())
                .flatMap(y -> Iterator.range(drawn.left(), drawn.right())
                        .flatMap(x -> Option.ofOptional(buffer.get(x, y).style().hyperlink())
                                .map(link -> Tuple.of(new Spot(x, y), link.url())))));
    }

    /**
     * Each cell of {@code buffer} that stands for a cell of one of {@code pictures} (ADR 84) as that cell: the next of
     * the picture, row by row, in the order they are read. A cell the picture leaves blank keeps the background it had.
     */
    private static void paint(Buffer buffer, List<Picture> pictures) {
        Rect area = buffer.area();
        // Where each cell of a picture is, and the picture it is of; then which cell of that picture each is.
        List<Tuple2<Spot, Integer>> cells = Iterator.range(area.top(), area.bottom())
                .flatMap(y -> Iterator.range(area.left(), area.right()).map(x -> new Spot(x, y)))
                .flatMap(at ->
                        pictureOf(buffer.get(at.x(), at.y()), pictures.size()).map(index -> Tuple.of(at, index)))
                .toList();
        List<Tuple2<Spot, Integer>> numbered = cells.foldLeft(
                        Tuple.of(List.<Tuple2<Spot, Integer>>empty(), HashMap.<Integer, Integer>empty()),
                        (done, cell) -> {
                            int n = done._2().get(cell._2()).getOrElse(0);
                            return Tuple.of(
                                    done._1().prepend(Tuple.of(cell._1(), n)),
                                    done._2().put(cell._2(), n + 1));
                        })
                ._1()
                .reverse();
        cells.zip(numbered).forEach(cell -> {
            Spot at = cell._1()._1();
            Cell was = buffer.get(at.x(), at.y());
            Tuple2<Style, String> pixel =
                    HalfBlocks.cell(pictures.get(cell._1()._2()), cell._2()._2());
            Style style = pixel._1().bg().isPresent()
                    ? pixel._1()
                    : was.style().bg().map(pixel._1()::bg).orElse(pixel._1());
            buffer.set(at.x(), at.y(), new Cell(pixel._2(), style));
        });
    }

    /**
     * Which of {@code count} pictures {@code cell} stands for a cell of, if any.
     */
    private static Option<Integer> pictureOf(Cell cell, int count) {
        String symbol = cell.symbol();
        return Option.when(
                        !cell.isContinuation() && symbol.length() == 1, () -> symbol.charAt(0) - MarkdownImages.PICTURE)
                .filter(index -> index >= 0 && index < count);
    }

    /**
     * The markdown, made for the width it gets and measured at it, so it scrolls no further than its end.
     */
    private final class Scrolled implements Element {

        private final IntFunction<MarkdownImages.Drawn> markdown;

        Scrolled(IntFunction<MarkdownImages.Drawn> markdown) {
            this.markdown = markdown;
        }

        @Override
        public void render(Frame frame, Rect area, RenderContext context) {
            MarkdownImages.Drawn drawn = markdown.apply(area.width());
            MarkdownElement element = MarkdownElement.markdown(drawn.markdown());
            int height =
                    element.preferredSize(area.width(), area.height(), context).heightOr(area.height());
            furthest = Math.max(0, height - area.height());
            page = area.height();
            offset = Math.min(offset, furthest);
            if (drawn.pictures().isEmpty()) {
                context.renderChild(element.scroll(offset), frame, area);
            } else {
                withPictures(element, drawn.pictures(), height, frame, area, context);
            }
            links = linksIn(frame.buffer(), area);
        }

        /**
         * {@code element} with {@code pictures} in it, scrolled to {@link #offset}: drawn whole, {@code height} high,
         * off the screen over what {@code area} shows, so that a picture is drawn from its first cell even when that
         * is scrolled out of view, and only what is in view copied to the screen.
         */
        private void withPictures(
                MarkdownElement element,
                List<Picture> pictures,
                int height,
                Frame frame,
                Rect area,
                RenderContext context) {
            Rect whole = new Rect(0, 0, area.width(), Math.max(height, offset + area.height()));
            Rect shown = area.intersection(frame.buffer().area());
            Buffer off = Buffer.empty(whole);
            List<Spot> inView = Iterator.range(shown.top(), shown.bottom())
                    .flatMap(y -> Iterator.range(shown.left(), shown.right()).map(x -> new Spot(x, y)))
                    .toList();
            inView.forEach(at -> off.set(
                    at.x() - area.left(),
                    at.y() - area.top() + offset,
                    frame.buffer().get(at.x(), at.y())));
            element.render(Frame.forTesting(off), whole, context);
            paint(off, pictures);
            inView.forEach(at ->
                    frame.buffer().set(at.x(), at.y(), off.get(at.x() - area.left(), at.y() - area.top() + offset)));
        }

        @Override
        public Constraint constraint() {
            return Constraint.fill();
        }

        @Override
        public Size preferredSize(int availableWidth, int availableHeight, RenderContext context) {
            return Size.of(availableWidth, availableHeight);
        }
    }

    private record Spot(int x, int y) {}
}
