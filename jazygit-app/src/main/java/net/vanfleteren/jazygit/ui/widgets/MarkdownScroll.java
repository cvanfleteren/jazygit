package net.vanfleteren.jazygit.ui.widgets;

import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.RenderContext;
import dev.tamboui.toolkit.element.Size;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.toolkit.markdown.MarkdownElement;
import dev.tamboui.tui.event.MouseEvent;
import dev.tamboui.tui.event.MouseEventKind;
import java.util.regex.Pattern;

/**
 * Markdown that scrolls, as far as its end: how far that is is only known once it is rendered, at a width. Where it
 * is scrolled to is view state, kept here; {@link #toTop()} starts it over, as for other text.
 */
public final class MarkdownScroll {

    private static final int WHEEL_LINES = 3;
    // Bots fill their comments with these; tamboui would show them as text.
    private static final Pattern HTML_COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);

    private int offset;
    // As far as it scrolled, and how many lines it showed, when it was last rendered.
    private int furthest;
    private int page = 1;

    /**
     * {@code markdown} without its HTML comments.
     */
    public static String withoutHtmlComments(String markdown) {
        return HTML_COMMENT.matcher(markdown).replaceAll("");
    }

    /**
     * {@code markdown}, without its HTML comments, scrolled as far as it was, filling the area it gets.
     */
    public Element render(String markdown) {
        return new Scrolled(MarkdownElement.markdown(withoutHtmlComments(markdown)));
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

    public void toTop() {
        offset = 0;
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
     * The markdown, measured at the width it gets, so it scrolls no further than its end.
     */
    private final class Scrolled implements Element {

        private final MarkdownElement markdown;

        Scrolled(MarkdownElement markdown) {
            this.markdown = markdown;
        }

        @Override
        public void render(Frame frame, Rect area, RenderContext context) {
            int height =
                    markdown.preferredSize(area.width(), area.height(), context).heightOr(area.height());
            furthest = Math.max(0, height - area.height());
            page = area.height();
            offset = Math.min(offset, furthest);
            context.renderChild(markdown.scroll(offset), frame, area);
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
}
