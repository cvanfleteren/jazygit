package net.vanfleteren.jazygit.features.github.comments;

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
import io.vavr.collection.Iterator;
import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.core.gh.model.PullRequestComment;
import net.vanfleteren.jazygit.features.github.comment.CommentMsg;
import net.vanfleteren.jazygit.features.github.comment.CommentPopup;
import net.vanfleteren.jazygit.features.github.show.PullRequestPopup;
import net.vanfleteren.jazygit.features.github.widgets.PullRequestSymbols;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;
import net.vanfleteren.jazygit.kernel.ui.markdown.MarkdownAutolinks;
import net.vanfleteren.jazygit.kernel.ui.markdown.MarkdownImages;
import net.vanfleteren.jazygit.kernel.ui.markdown.MarkdownScroll;
import net.vanfleteren.jazygit.kernel.ui.text.ErrorText;
import net.vanfleteren.jazygit.kernel.ui.text.ShownTime;
import net.vanfleteren.jazygit.kernel.ui.text.TextWidth;
import net.vanfleteren.jazygit.kernel.ui.text.TimeText;
import net.vanfleteren.jazygit.kernel.ui.theme.Theme;
import net.vanfleteren.jazygit.kernel.ui.widgets.DialogFrame;
import net.vanfleteren.jazygit.kernel.ui.widgets.LargeDialog;
import net.vanfleteren.jazygit.kernel.ui.widgets.ListKeys;
import net.vanfleteren.jazygit.kernel.ui.widgets.PopupView;
import net.vanfleteren.jazygit.lib.image.Picture;

/**
 * Popup showing the conversation on a pull request, as big as the screen allows: a list of its description, comments
 * and reviews on the left, and the highlighted one in full on the right, as Markdown. On a narrow screen the list is
 * above it instead. j/k and the arrows move through the list, J/K and Page Up/Down scroll the comment, c comments,
 * o opens the pull request in the browser, a click on a link opens the link, and Escape goes back to the pull request
 * dialog. The comments and which one is highlighted live in the {@link Model}, as a {@link CommentsPopup}; how far the
 * comment is scrolled, and where its links were drawn, live here.
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
    // A symbol takes one column, and a space after it.
    private static final int SYMBOL_WIDTH = 2;
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
     * The times the dialog shows (ADR 124): how long ago each comment was written.
     */
    public static List<ShownTime> shownTimes(Model model) {
        return model.popup(CommentsPopup.class)
                .flatMap(popup -> popup.comments().flatMap(Either::toOption))
                .toList()
                .flatMap(comments -> comments)
                .map(comment -> ShownTime.reading(now -> TimeText.age(Duration.between(comment.at(), now))));
    }

    /**
     * The pictures the dialog may draw in {@code model} (ADR 191): those of the comments, also while a comment is
     * written over them.
     */
    public static List<Picture> pictures(Model model) {
        return model.popup(CommentsPopup.class)
                .orElse(() -> model.popup(CommentPopup.class)
                        .map(CommentPopup::from)
                        .filter(CommentsPopup.class::isInstance)
                        .map(CommentsPopup.class::cast))
                .toList()
                .flatMap(popup -> popup.shownPictures().values());
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
        int contentWidth = DialogFrame.contentWidth(width);
        int contentHeight = Math.max(0, height - FRAME_HEIGHT);
        boolean narrow = width + 2 * MARGIN_X < NARROW;
        List<PullRequestComment> all = popup.all();
        int listWidth = narrow ? contentWidth : Math.min(LIST_MAX_WIDTH, contentWidth * 2 / 5);
        int highlightedIndex = popup.highlightedIndex().getOrElse(0);
        rows = all.map(comment -> line(comment, now, listWidth - PANE_BORDER));
        list.elements(rows.toJavaArray(StyledElement[]::new))
                .selected(highlightedIndex)
                .onMouseEvent(event -> listMouse(popup, event));
        StyledElement<?> listPane = panel(list);
        StyledElement<?> comment = comment(popup).onMouseEvent(this::commentMouse);
        Element body = narrow
                ? column(
                                listPane.length(Math.min(Math.max(1, all.size()), contentHeight / 3) + PANE_BORDER),
                                comment.fill())
                        .fill()
                : row(listPane.length(listWidth), comment.fill()).fill();
        return DialogFrame.withHint(
                        Messages.get(
                                "github.dialog.comments.title",
                                String.valueOf(popup.number().value())),
                        width,
                        "github.dialog.comments.hint",
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
                                        Messages.get("github.dialog.comments.failed", ErrorText.of(failure)),
                                        Integer.MAX_VALUE,
                                        Integer.MAX_VALUE)
                                .addClass(Theme.Classes.ERROR),
                        comments -> highlighted
                                .<Element>map(comment -> scroll.render(
                                        MarkdownAutolinks.linked(body(comment), repository(popup)),
                                        popup.shownPictures()))
                                .getOrElse(() -> text(Messages.get("github.dialog.comments.none"))
                                        .addClass(Theme.Classes.DIM))))
                .getOrElse(() -> row(
                        text(Messages.get("github.dialog.comments.loading") + " ")
                                .addClass(Theme.Classes.DIM),
                        text(TimeText.spinnerFrame(clock.instant())).addClass(Theme.Classes.SPINNER)));
        Panel pane = panel(content);
        return highlighted.map(comment -> pane.title(title(comment))).getOrElse(pane);
    }

    /**
     * The repository of the pull request, which a reference to an issue or pull request without one is to (ADR 98):
     * as its details were read, else as it was listed.
     */
    private static Option<MarkdownAutolinks.Repository> repository(CommentsPopup popup) {
        PullRequestPopup shown = popup.shown();
        return shown.readDetails()
                .flatMap(read -> MarkdownAutolinks.Repository.of(read.host(), read.repository()))
                .orElse(() -> shown.pullRequest()
                        .commits()
                        .flatMap(commits -> MarkdownAutolinks.Repository.of(commits.host(), commits.repository())));
    }

    private static String body(PullRequestComment comment) {
        return comment.body().isBlank() ? Messages.get("github.dialog.comments.noText") : comment.body();
    }

    /**
     * Who wrote {@code comment} and when, how the review went, and whether it was edited.
     */
    private String title(PullRequestComment comment) {
        String written = WRITTEN.format(LocalDateTime.ofInstant(comment.at(), clock.getZone()));
        return " "
                + List.of(author(comment), written)
                        .appendAll(state(comment.kind()))
                        .appendAll(Option.when(comment.edited(), () -> Messages.get("github.dialog.comments.edited")))
                        .mkString(" · ")
                + " ";
    }

    private static String author(PullRequestComment comment) {
        return "@" + (comment.author().isEmpty() ? Messages.get("github.dialog.comments.ghost") : comment.author());
    }

    private static Option<String> state(PullRequestComment.Kind kind) {
        return switch (kind) {
            case DESCRIPTION -> Option.of(Messages.get("github.dialog.comments.description"));
            case COMMENT -> Option.none();
            case APPROVED -> Option.of(Messages.get("github.dialog.comments.approved"));
            case CHANGES_REQUESTED -> Option.of(Messages.get("github.dialog.comments.changesRequested"));
            case COMMENTED -> Option.of(Messages.get("github.dialog.comments.reviewed"));
            case DISMISSED -> Option.of(Messages.get("github.dialog.comments.dismissed"));
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
                                    ? Messages.get("github.dialog.comments.hidden")
                                    : Messages.get("github.dialog.comments.hiddenBecause", reason.toLowerCase()),
                            width))
                    .addClass(Theme.Classes.DIM);
        }
        if (comment.kind() == PullRequestComment.Kind.DESCRIPTION) {
            return text(TextWidth.cut(Messages.get("github.dialog.comments.description"), width))
                    .addClass(Theme.Classes.DIM);
        }
        return text(TextWidth.cut(firstLine(comment.body()), width));
    }

    /**
     * The first line of {@code markdown} with text, without what marks it as a heading, quote or list item: a marker
     * followed by a space, so {@code **Note**} and {@code +1} keep theirs.
     */
    static String firstLine(String markdown) {
        return Iterator.of(MarkdownImages.asText(MarkdownScroll.withoutHtmlComments(markdown))
                        .split("\\R"))
                .map(line -> line.replaceFirst("^\\s*(?:(?:#{1,6}|>|[*+-])\\s+)*", "")
                        .strip())
                .find(line -> !line.isEmpty())
                .getOrElse("");
    }

    /**
     * A key sends a message, scrolls the comment, or moves the highlight, tried in that order: Page Up/Down scroll the
     * comment before they could move the highlight.
     */
    private EventResult keys(CommentsPopup popup, KeyEvent event) {
        Option<Msg> msg = message(event);
        msg.forEach(dispatch);
        return msg.isDefined() || scrolled(event) || moved(popup, event) ? EventResult.HANDLED : EventResult.UNHANDLED;
    }

    /**
     * What {@code event} asks of the app: Escape goes back, o opens the pull request in the browser, c comments.
     */
    private static Option<Msg> message(KeyEvent event) {
        if (event.isCancel()) {
            return Option.of(new CommentsMsg.Closed());
        }
        if (event.isChar('o')) {
            return Option.of(new CommentsMsg.OpenChosen());
        }
        return Option.when(event.isChar('c'), CommentMsg.Requested::new);
    }

    /**
     * Scrolls the comment a line on J/K, a page on Page Up/Down; whether {@code event} was one of those.
     */
    private boolean scrolled(KeyEvent event) {
        if (event.isChar('J') || event.isChar('K')) {
            scroll.scrollBy(event.isChar('J') ? 1 : -1);
            return true;
        }
        if (event.code() == KeyCode.PAGE_DOWN || event.code() == KeyCode.PAGE_UP) {
            scroll.scrollPages(event.code() == KeyCode.PAGE_DOWN ? 1 : -1);
            return true;
        }
        return false;
    }

    /**
     * Moves the highlight on j/k, and on the keys that move it in any list; whether {@code event} was one of those.
     */
    private boolean moved(CommentsPopup popup, KeyEvent event) {
        int from = popup.highlightedIndex().getOrElse(0);
        Option<Integer> to = event.isChar('j')
                ? Option.of(from + 1)
                : event.isChar('k')
                        ? Option.of(from - 1)
                        : ListKeys.move(event, from, popup.all().size(), line -> true);
        to.forEach(line -> highlight(popup, line));
        return to.isDefined();
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

    /**
     * The wheel scrolls the comment; a left click on a link opens it (ADR 96).
     */
    private EventResult commentMouse(MouseEvent event) {
        if (event.kind() != MouseEventKind.PRESS || !event.isLeftButton()) {
            return scroll.wheel(event);
        }
        return scroll.linkAt(event.x(), event.y()).fold(() -> EventResult.UNHANDLED, url -> {
            dispatch.accept(new CommentsMsg.LinkChosen(url));
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
