package net.vanfleteren.jazygit.feature.github.comments;

import static dev.tamboui.toolkit.Toolkit.column;
import static dev.tamboui.toolkit.Toolkit.list;
import static dev.tamboui.toolkit.Toolkit.panel;
import static dev.tamboui.toolkit.Toolkit.row;
import static dev.tamboui.toolkit.Toolkit.text;

import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.StyledElement;
import dev.tamboui.toolkit.elements.ListElement;
import dev.tamboui.toolkit.elements.Panel;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.MouseEvent;
import dev.tamboui.tui.event.MouseEventKind;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.feature.github.comment.CommentMsg;
import net.vanfleteren.jazygit.gh.model.PullRequestComment;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.ui.ErrorText;
import net.vanfleteren.jazygit.ui.PullRequestSymbols;
import net.vanfleteren.jazygit.ui.TextWidth;
import net.vanfleteren.jazygit.ui.Theme;
import net.vanfleteren.jazygit.ui.TimeText;
import net.vanfleteren.jazygit.ui.widgets.DialogFrame;
import net.vanfleteren.jazygit.ui.widgets.LargeDialog;
import net.vanfleteren.jazygit.ui.widgets.ListKeys;
import net.vanfleteren.jazygit.ui.widgets.MarkdownScroll;
import net.vanfleteren.jazygit.ui.widgets.PopupView;

/**
 * Popup showing the conversation on a pull request, as big as the screen allows: a list of its description, comments
 * and reviews on the left, and the highlighted one in full on the right, as Markdown. On a narrow screen the list is
 * above it instead. j/k and the arrows move through the list, J/K and Page Up/Down scroll the comment, c comments,
 * o opens the pull request in the browser and Escape goes back to the pull request dialog. The comments and which one
 * is highlighted live in the {@link Model}, as a {@link CommentsPopup}; how far the comment is scrolled lives here.
 */
public final class CommentsDialog implements PopupView {

    public static final String ID = "pull-request-comments";

    private static final int MARGIN_X = 4;
    private static final int MARGIN_Y = 1;
    private static final int MAX_WIDTH = 160;
    // A screen narrower than this shows the list above the comment.
    private static final int NARROW = 100;
    private static final int LIST_MAX_WIDTH = 56;
    // The border of the dialog, and the blank line and the hint under what it shows.
    private static final int FRAME_HEIGHT = 2 + 2;
    // The border of a pane.
    private static final int PANE_BORDER = 2;
    // A symbol takes one or two columns, as an emoji does, and a space after it.
    private static final int SYMBOL_WIDTH = 3;
    private static final int AUTHOR_WIDTH = 16;
    private static final int AGE_WIDTH = 5;
    private static final DateTimeFormatter WRITTEN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final Consumer<Msg> dispatch;
    private final Clock clock;
    // Scrolls to keep the highlighted comment in view, when they do not all fit.
    private final ListElement<?> list = list().highlightSymbol("").autoScroll();
    // The rows the list was last given, which know where they were drawn.
    private List<StyledElement<?>> rows = List.empty();
    private final MarkdownScroll scroll = new MarkdownScroll();
    // The comment the scroll is of; another one starts at its top.
    private Option<PullRequestComment.Id> scrolled = Option.none();

    /**
     * @param dispatch receives the messages for the user's actions, on the render thread
     */
    public CommentsDialog(Consumer<Msg> dispatch) {
        this(dispatch, Clock.systemDefaultZone());
    }

    /**
     * @param clock tells the time a comment was written in, and animates the spinner; how long ago is told by the time
     *              of the model
     */
    CommentsDialog(Consumer<Msg> dispatch, Clock clock) {
        this.dispatch = dispatch;
        this.clock = clock;
    }

    @Override
    public List<String> ids() {
        return List.of(ID);
    }

    /**
     * Whether the dialog shows a spinner in {@code model}: while the comments are read.
     */
    public static boolean showsSpinner(Model model) {
        return model.popup(CommentsPopup.class).exists(popup -> popup.comments().isEmpty());
    }

    @Override
    public Option<Element> render(Model model) {
        return model.popup(CommentsPopup.class).map(popup -> {
            Option<PullRequestComment.Id> highlighted =
                    popup.highlightedComment().map(PullRequestComment::id);
            if (!highlighted.equals(scrolled)) {
                scroll.toTop();
                scrolled = highlighted;
            }
            return new LargeDialog(
                    MARGIN_X, MARGIN_Y, MAX_WIDTH, (width, height) -> dialog(popup, model.now(), width, height));
        });
    }

    private Element dialog(CommentsPopup popup, Instant now, int width, int height) {
        int contentWidth = width - DialogFrame.FRAME;
        int contentHeight = Math.max(0, height - FRAME_HEIGHT);
        boolean narrow = width + 2 * MARGIN_X < NARROW;
        List<PullRequestComment> all = popup.all();
        int listWidth = narrow ? contentWidth : Math.min(LIST_MAX_WIDTH, contentWidth * 2 / 5);
        int highlightedIndex = popup.highlightedIndex().getOrElse(0);
        rows = all.map(comment -> line(comment, now, listWidth - PANE_BORDER));
        list.elements(rows.toJavaArray(StyledElement[]::new))
                .selected(highlightedIndex)
                .onMouseEvent(event -> listMouse(popup, event));
        StyledElement<?> listPane = panel(list).rounded();
        StyledElement<?> comment = comment(popup).onMouseEvent(scroll::wheel);
        Element body = narrow
                ? column(
                                listPane.length(Math.min(Math.max(1, all.size()), contentHeight / 3) + PANE_BORDER),
                                comment.fill())
                        .fill()
                : row(listPane.length(listWidth), comment.fill()).fill();
        return DialogFrame.withHint(
                        Messages.get("dialog.comments.title", String.valueOf(popup.number())),
                        width,
                        "dialog.comments.hint",
                        List.of(column(body).length(contentHeight)))
                .id(ID)
                .focusable()
                .onKeyEvent(event -> keys(popup, event))
                .length(height);
    }

    /**
     * The pane with the highlighted comment, or what there is to say while there is none.
     */
    private Panel comment(CommentsPopup popup) {
        Option<PullRequestComment> highlighted = popup.highlightedComment();
        Element content = popup.comments()
                .<Element>map(read -> read.fold(
                        failure -> DialogFrame.wrapped(
                                        Messages.get("dialog.comments.failed", ErrorText.of(failure)),
                                        Integer.MAX_VALUE,
                                        Integer.MAX_VALUE)
                                .addClass(Theme.Classes.ERROR),
                        comments -> highlighted
                                .<Element>map(comment -> scroll.render(body(comment)))
                                .getOrElse(() -> text(Messages.get("dialog.comments.none"))
                                        .addClass(Theme.Classes.DIM))))
                .getOrElse(() -> row(
                        text(Messages.get("dialog.comments.loading") + " ").addClass(Theme.Classes.DIM),
                        text(TimeText.spinnerFrame(clock.instant())).addClass(Theme.Classes.SPINNER)));
        Panel pane = panel(content).rounded();
        return highlighted.map(comment -> pane.title(title(comment))).getOrElse(pane);
    }

    private static String body(PullRequestComment comment) {
        return comment.body().isBlank() ? Messages.get("dialog.comments.noText") : comment.body();
    }

    /**
     * Who wrote {@code comment} and when, how the review went, and whether it was edited.
     */
    private String title(PullRequestComment comment) {
        String written = WRITTEN.format(LocalDateTime.ofInstant(comment.at(), clock.getZone()));
        return " "
                + List.of(author(comment), written)
                        .appendAll(state(comment.kind()))
                        .appendAll(Option.when(comment.edited(), () -> Messages.get("dialog.comments.edited")))
                        .mkString(" · ")
                + " ";
    }

    private static String author(PullRequestComment comment) {
        return "@" + (comment.author().isEmpty() ? Messages.get("dialog.comments.ghost") : comment.author());
    }

    private static Option<String> state(PullRequestComment.Kind kind) {
        return switch (kind) {
            case DESCRIPTION -> Option.of(Messages.get("dialog.comments.description"));
            case COMMENT -> Option.none();
            case APPROVED -> Option.of(Messages.get("dialog.comments.approved"));
            case CHANGES_REQUESTED -> Option.of(Messages.get("dialog.comments.changesRequested"));
            case COMMENTED -> Option.of(Messages.get("dialog.comments.reviewed"));
            case DISMISSED -> Option.of(Messages.get("dialog.comments.dismissed"));
        };
    }

    /**
     * The row of {@code comment} in the list, {@code width} wide: its symbol, author, age and first line.
     */
    static StyledElement<?> line(PullRequestComment comment, Instant now, int width) {
        StyledElement<?> symbol = PullRequestSymbols.comment(comment.kind())
                .<StyledElement<?>>map(found -> text(found.text()).addClass(found.cssClass()))
                .getOrElse(() -> text(""));
        String age = TimeText.age(Duration.between(comment.at(), now));
        int rest = Math.max(1, width - SYMBOL_WIDTH - AUTHOR_WIDTH - AGE_WIDTH);
        return row(
                symbol.length(SYMBOL_WIDTH),
                text(TextWidth.cut(author(comment), AUTHOR_WIDTH - 1)).length(AUTHOR_WIDTH),
                text(age).addClass(Theme.Classes.DIM).length(AGE_WIDTH),
                summary(comment, rest));
    }

    private static StyledElement<?> summary(PullRequestComment comment, int width) {
        if (comment.hidden().isDefined()) {
            String reason = comment.hidden().get();
            return text(TextWidth.cut(
                            reason.isEmpty()
                                    ? Messages.get("dialog.comments.hidden")
                                    : Messages.get("dialog.comments.hiddenBecause", reason.toLowerCase()),
                            width))
                    .addClass(Theme.Classes.DIM);
        }
        if (comment.kind() == PullRequestComment.Kind.DESCRIPTION) {
            return text(TextWidth.cut(Messages.get("dialog.comments.description"), width))
                    .addClass(Theme.Classes.DIM);
        }
        return text(TextWidth.cut(firstLine(comment.body()), width));
    }

    /**
     * The first line of {@code markdown} with text, without what marks it as a heading, quote or list item: a marker
     * followed by a space, so {@code **Note**} and {@code +1} keep theirs.
     */
    static String firstLine(String markdown) {
        return List.of(MarkdownScroll.withoutHtmlComments(markdown).split("\\R"))
                .map(line -> line.replaceFirst("^\\s*(?:(?:#{1,6}|>|[*+-])\\s+)*", "")
                        .strip())
                .find(line -> !line.isEmpty())
                .getOrElse("");
    }

    private EventResult keys(CommentsPopup popup, KeyEvent event) {
        if (event.code() == KeyCode.ESCAPE) {
            dispatch.accept(new CommentsMsg.Closed());
        } else if (event.isChar('J')) {
            scroll.scrollBy(1);
        } else if (event.isChar('K')) {
            scroll.scrollBy(-1);
        } else if (event.code() == KeyCode.PAGE_DOWN) {
            scroll.scrollPages(1);
        } else if (event.code() == KeyCode.PAGE_UP) {
            scroll.scrollPages(-1);
        } else if (event.isChar('j')) {
            highlight(popup, popup.highlightedIndex().getOrElse(0) + 1);
        } else if (event.isChar('k')) {
            highlight(popup, popup.highlightedIndex().getOrElse(0) - 1);
        } else if (event.isChar('o')) {
            dispatch.accept(new CommentsMsg.OpenChosen());
        } else if (event.isChar('c')) {
            dispatch.accept(new CommentMsg.Requested());
        } else {
            Option<Integer> moved = ListKeys.move(
                    event, popup.highlightedIndex().getOrElse(0), popup.all().size(), line -> true);
            moved.forEach(line -> highlight(popup, line));
            return moved.isDefined() ? EventResult.HANDLED : EventResult.UNHANDLED;
        }
        return EventResult.HANDLED;
    }

    /**
     * The wheel moves the highlight; a left click highlights the row clicked on. Every row is one line, and the
     * highlighted one is always drawn, so the row clicked on is found from where that one is.
     */
    private EventResult listMouse(CommentsPopup popup, MouseEvent event) {
        int from = popup.highlightedIndex().getOrElse(0);
        if (event.kind() != MouseEventKind.PRESS || !event.isLeftButton()) {
            return ListKeys.wheel(event, from, popup.all().size(), line -> true, line -> highlight(popup, line));
        }
        return rows.asPartialFunction()
                .lift()
                .apply(from)
                .flatMap(highlighted -> Option.of(highlighted.renderedArea()))
                .map(area -> from + event.y() - area.top())
                .filter(line -> line >= 0 && line < popup.all().size())
                .fold(() -> EventResult.UNHANDLED, line -> {
                    highlight(popup, line);
                    return EventResult.HANDLED;
                });
    }

    private void highlight(CommentsPopup popup, int line) {
        popup.all()
                .asPartialFunction()
                .lift()
                .apply(line)
                .filter(comment -> line != popup.highlightedIndex().getOrElse(-1))
                .forEach(comment -> dispatch.accept(new CommentsMsg.Highlighted(comment.id())));
    }
}
