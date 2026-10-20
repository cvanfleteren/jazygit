package net.vanfleteren.jazygit.feature.github.prototype;

import static dev.tamboui.toolkit.Toolkit.column;
import static dev.tamboui.toolkit.Toolkit.list;
import static dev.tamboui.toolkit.Toolkit.panel;
import static dev.tamboui.toolkit.Toolkit.row;
import static dev.tamboui.toolkit.Toolkit.text;

import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.RenderContext;
import dev.tamboui.toolkit.element.Size;
import dev.tamboui.toolkit.element.StyledElement;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.toolkit.markdown.MarkdownElement;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import io.vavr.collection.List;
import io.vavr.control.Option;
import io.vavr.control.Try;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;
import net.vanfleteren.jazygit.gh.model.PrototypeComment;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.ReviewerState;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.ui.ErrorText;
import net.vanfleteren.jazygit.ui.PullRequestSymbols;
import net.vanfleteren.jazygit.ui.Theme;
import net.vanfleteren.jazygit.ui.TimeText;
import net.vanfleteren.jazygit.ui.widgets.DialogFrame;
import net.vanfleteren.jazygit.ui.widgets.PopupView;

/**
 * PROTOTYPE (option C): the comments of a pull request in a popup as big as the screen allows, as a list of comments
 * and the highlighted one in full, as markdown. {@code [} and {@code ]} switch between three layouts of it. Throwaway:
 * the cursor, the scroll and the layout live here, and nothing is tested.
 */
public final class CommentsPrototypeDialog implements PopupView {

    public static final String ID = "pull-request-comments-prototype";

    private static final int MARGIN_X = 4;
    private static final int MARGIN_Y = 1;
    private static final int AUTHOR_WIDTH = 16;
    private static final List<String> LAYOUTS = List.of("side by side", "stacked", "accordion");
    private static final Pattern HTML_COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);
    private static final Pattern LEADING_MARKUP = Pattern.compile("^[#>*\\-\\s]+");

    private final Consumer<Msg> dispatch;
    private int layout = 0;
    private int number = -1;
    private int cursor = -1;
    private int scroll = 0;
    // How far the highlighted comment scrolls, as last rendered.
    private int maxScroll = 0;
    private int size = 0;

    public CommentsPrototypeDialog(Consumer<Msg> dispatch) {
        this.dispatch = dispatch;
    }

    @Override
    public List<String> ids() {
        return List.of(ID);
    }

    @Override
    public Option<Element> render(Model model) {
        return model.popup(CommentsPrototypePopup.class).map(popup -> {
            if (popup.number() != number) {
                number = popup.number();
                cursor = -1;
                scroll = 0;
            }
            List<PrototypeComment> comments =
                    popup.comments().flatMap(read -> read.toOption()).getOrElse(List.empty());
            size = comments.size();
            // Opens on the last comment, the newest.
            if (cursor < 0 && !comments.isEmpty()) {
                cursor = comments.size() - 1;
            }
            Instant now = model.now();
            return new Sized(area -> {
                int contentWidth = area.width() - DialogFrame.FRAME;
                // The border, the blank line and the hint.
                int contentHeight = area.height() - 2 - 2;
                Element body = popup.comments()
                        .<Element>map(read -> read.fold(
                                failure -> DialogFrame.wrapped(ErrorText.of(failure), area.width(), 5)
                                        .addClass(Theme.Classes.ERROR),
                                read2 -> comments.isEmpty()
                                        ? text("No comments").addClass(Theme.Classes.DIM)
                                        : layout(comments, now, contentWidth, contentHeight)))
                        .getOrElse(() -> text("Reading the comments…").addClass(Theme.Classes.DIM));
                return DialogFrame.of(
                                " #" + popup.number() + " · "
                                        + popup.shown().pullRequest().headBranch() + " · Comments ("
                                        + comments.size() + ") · layout " + (layout + 1) + "/" + LAYOUTS.size()
                                        + ": " + LAYOUTS.get(layout) + " ",
                                area.width(),
                                column(body).length(contentHeight),
                                text(""),
                                text("j/k comment · J/K or PgUp/PgDn scroll"
                                                + (maxScroll > 0 ? " (" + scroll + "/" + maxScroll + ")" : "")
                                                + " · [ ] layout · esc back")
                                        .addClass(Theme.Classes.DIM))
                        .id(ID)
                        .focusable()
                        .onKeyEvent(this::keys)
                        .length(area.height());
            });
        });
    }

    private Element layout(List<PrototypeComment> comments, Instant now, int width, int height) {
        PrototypeComment selected = comments.get(cursor);
        return switch (layout) {
            case 0 -> {
                int listWidth = Math.min(56, width * 2 / 5);
                yield row(
                                panel(commentList(comments, now, listWidth - 2))
                                        .rounded()
                                        .length(listWidth),
                                comment(selected, now).fill())
                        .fill();
            }
            case 1 -> {
                int listHeight = Math.min(comments.size(), height / 3) + 2;
                yield column(
                                panel(commentList(comments, now, width - 2))
                                        .rounded()
                                        .length(listHeight),
                                comment(selected, now).fill())
                        .fill();
            }
            default -> accordion(comments, now, width, height);
        };
    }

    /**
     * Every comment a line, the highlighted one opened under its line.
     */
    private Element accordion(List<PrototypeComment> comments, Instant now, int width, int height) {
        int others = Math.min(comments.size() - 1, height / 3);
        int before = Math.min(cursor, Math.max(others / 2, others - (comments.size() - 1 - cursor)));
        int after = others - before;
        List<Element> rows = List.<Element>empty()
                .appendAll(comments.subSequence(cursor - before, cursor).map(c -> line(c, now, width)))
                .append(line(comments.get(cursor), now, width).addClass(Theme.Classes.SELECTION))
                .append(markdown(comments.get(cursor)).fill())
                .appendAll(comments.subSequence(cursor + 1, Math.min(comments.size(), cursor + 1 + after))
                        .map(c -> line(c, now, width)));
        return column(rows.toJavaArray(Element[]::new)).fill();
    }

    private StyledElement<?> commentList(List<PrototypeComment> comments, Instant now, int width) {
        return list().highlightSymbol("")
                .elements(comments.map(c -> line(c, now, width)).toJavaArray(StyledElement[]::new))
                .selected(cursor);
    }

    private static StyledElement<?> line(PrototypeComment comment, Instant now, int width) {
        StyledElement<?> symbol = symbol(comment.state())
                .<StyledElement<?>>map(s -> text(s.text()).addClass(s.cssClass()))
                .getOrElse(() -> text(" "));
        return row(
                        symbol.length(2),
                        text(cut("@" + comment.author(), AUTHOR_WIDTH)).length(AUTHOR_WIDTH + 1),
                        text(age(comment, now)).addClass(Theme.Classes.DIM).length(5),
                        text(cut(firstLine(comment), Math.max(1, width - AUTHOR_WIDTH - 8))))
                .length(1);
    }

    private StyledElement<?> comment(PrototypeComment comment, Instant now) {
        return panel(markdown(comment))
                .title(" @" + comment.author() + " · " + age(comment, now) + " ago"
                        + (comment.state().isEmpty() ? "" : " · " + comment.state()) + " ")
                .rounded();
    }

    private Scrolled markdown(PrototypeComment comment) {
        return new Scrolled(MarkdownElement.markdown(clean(comment.body())));
    }

    private static Option<PullRequestSymbols.Symbol> symbol(String state) {
        return Try.of(() -> ReviewerState.valueOf(state))
                .toOption()
                .flatMap(PullRequestSymbols::reviewer)
                .orElse(() ->
                        Option.when(state.equals("COMMENTED"), new PullRequestSymbols.Symbol("●", Theme.Classes.DIM)));
    }

    private static String age(PrototypeComment comment, Instant now) {
        return TimeText.age(Duration.between(comment.createdAt(), now));
    }

    static String clean(String body) {
        String cleaned = HTML_COMMENT.matcher(body).replaceAll("").strip();
        return cleaned.isEmpty() ? "_(no text)_" : cleaned;
    }

    private static String firstLine(PrototypeComment comment) {
        return List.of(clean(comment.body()).split("\\R"))
                .map(line -> LEADING_MARKUP
                        .matcher(line)
                        .replaceAll("")
                        .replace("**", "")
                        .replace("`", ""))
                .find(line -> !line.isBlank())
                .getOrElse("");
    }

    private static String cut(String text, int width) {
        return text.length() <= width ? text : text.substring(0, Math.max(0, width - 1)) + "…";
    }

    private EventResult keys(KeyEvent event) {
        if (event.code() == KeyCode.ESCAPE) {
            dispatch.accept(new CommentsPrototypeMsg.Closed());
        } else if (event.isChar('j') || event.code() == KeyCode.DOWN) {
            select(cursor + 1);
        } else if (event.isChar('k') || event.code() == KeyCode.UP) {
            select(cursor - 1);
        } else if (event.isChar('J') || event.code() == KeyCode.PAGE_DOWN) {
            scroll = Math.min(maxScroll, scroll + 5);
        } else if (event.isChar('K') || event.code() == KeyCode.PAGE_UP) {
            scroll = Math.max(0, scroll - 5);
        } else if (event.isChar(']')) {
            layout = (layout + 1) % LAYOUTS.size();
        } else if (event.isChar('[')) {
            layout = (layout + LAYOUTS.size() - 1) % LAYOUTS.size();
        } else {
            return EventResult.UNHANDLED;
        }
        return EventResult.HANDLED;
    }

    private void select(int line) {
        int clamped = Math.clamp(line, 0, Math.max(0, size - 1));
        if (clamped != cursor) {
            cursor = clamped;
            scroll = 0;
        }
    }

    /**
     * The markdown, scrolled as far as it goes; tells the dialog how far that is.
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
            maxScroll = Math.max(0, height - area.height());
            scroll = Math.min(scroll, maxScroll);
            context.renderChild(markdown.scroll(scroll), frame, area);
        }

        @Override
        public Size preferredSize(int availableWidth, int availableHeight, RenderContext context) {
            return Size.of(availableWidth, availableHeight);
        }

        @Override
        public Constraint constraint() {
            return Constraint.fill();
        }

        Scrolled fill() {
            return this;
        }
    }

    /**
     * What {@code dialog} makes for the area the popup gets, less a margin.
     */
    private record Sized(Function<Rect, Element> dialog) implements Element {

        @Override
        public void render(Frame frame, Rect area, RenderContext context) {
            Rect inner = new Rect(
                    area.x() + MARGIN_X,
                    area.y() + MARGIN_Y,
                    Math.max(0, area.width() - 2 * MARGIN_X),
                    Math.max(0, area.height() - 2 * MARGIN_Y));
            context.renderChild(dialog.apply(inner), frame, inner);
        }

        @Override
        public Constraint constraint() {
            return Constraint.fill();
        }

        @Override
        public Size preferredSize(int availableWidth, int availableHeight, RenderContext context) {
            return Size.UNKNOWN;
        }
    }
}
