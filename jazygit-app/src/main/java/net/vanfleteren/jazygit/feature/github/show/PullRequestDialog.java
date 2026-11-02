package net.vanfleteren.jazygit.feature.github.show;

import static dev.tamboui.toolkit.Toolkit.row;
import static dev.tamboui.toolkit.Toolkit.text;

import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.StyledElement;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.feature.github.comment.CommentMsg;
import net.vanfleteren.jazygit.feature.github.comments.CommentsMsg;
import net.vanfleteren.jazygit.feature.github.edit.EditPullRequestMsg;
import net.vanfleteren.jazygit.gh.model.MergeMethod;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Check;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.ReviewThreads;
import net.vanfleteren.jazygit.gh.model.PullRequestStack;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.CheckTimes;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Reading;
import net.vanfleteren.jazygit.ui.CheckRows;
import net.vanfleteren.jazygit.ui.ErrorText;
import net.vanfleteren.jazygit.ui.PullRequestSymbols;
import net.vanfleteren.jazygit.ui.PullRequestSymbols.Symbol;
import net.vanfleteren.jazygit.ui.TextWidth;
import net.vanfleteren.jazygit.ui.Theme;
import net.vanfleteren.jazygit.ui.TimeText;
import net.vanfleteren.jazygit.ui.widgets.ChoiceDialog.Choice;
import net.vanfleteren.jazygit.ui.widgets.ChoiceList;
import net.vanfleteren.jazygit.ui.widgets.DialogFrame;
import net.vanfleteren.jazygit.ui.widgets.PopupView;

/**
 * Popup showing the pull request of a branch: its title, state, branches, review and checks, as labelled
 * rows, and under them, in two columns (ADR 66), what can be done with it: open it in the browser, comment on it, edit it, read its comments, mark it
 * ready for review or turn it into a draft, merge it or close it, or read it again. While it is read again, the
 * details stay shown, with a spinner in the title; when that fails, the title says they are stale. Whether it is
 * open lives in the {@link Model}.
 */
public final class PullRequestDialog implements PopupView {

    public static final String ID = "pull-request";

    private static final int WIDTH = 80;
    // The labels right-aligned in a column of this many characters, then two spaces and the value.
    private static final int LABEL_COLUMN = 11;
    private static final String INDENT = " ".repeat(LABEL_COLUMN + 2);
    // A check listed after the indent takes the rest of the line, how it is doing lined up on the right.
    private static final int CHECK_WIDTH = DialogFrame.contentWidth(WIDTH) - INDENT.length();
    // The failed and running checks listed one by one; the rest are counted.
    private static final int LISTED_CHECKS = 8;
    // A longer title is cut.
    private static final int TITLE_LINES = 3;
    /*
     * The most lines the details take: the title and a blank line; state, author, branch, stack, created, pushed,
     * changes and mergeable, and a blank line; review, reviewers, comments, threads and labels, and a blank line; the
     * checks counted, listed, and how many more there are. The dialog is always this high, so it keeps its size
     * when the details arrive.
     */
    private static final int CONTENT_HEIGHT = TITLE_LINES + 1 + 8 + 1 + 5 + 1 + 1 + LISTED_CHECKS + 1;

    private final Clock clock;
    private final ChoiceList<PullRequestPopup> actions;

    /**
     * @param dispatch receives the messages for the user's actions, on the render thread
     */
    public PullRequestDialog(Consumer<Msg> dispatch) {
        this(dispatch, Clock.systemDefaultZone());
    }

    /**
     * @param clock tells on which day the pull request was opened, and animates the spinner; how long ago is told by
     *              the time of the model
     */
    PullRequestDialog(Consumer<Msg> dispatch, Clock clock) {
        this.clock = clock;
        this.actions = ChoiceList.inTwoColumns(
                ID,
                List.of(
                        new Choice<>('o', "dialog.pullRequest.open", PullRequestMsg.OpenConfirmed::new),
                        new Choice<>(
                                'c',
                                "dialog.pullRequest.comment",
                                CommentMsg.Requested::new,
                                (model, popup) -> popup.detailsRead()),
                        new Choice<>(
                                'e',
                                "dialog.pullRequest.edit",
                                EditPullRequestMsg.Requested::new,
                                (model, popup) -> popup.detailsRead()),
                        new Choice<>(
                                'v',
                                "dialog.pullRequest.viewComments",
                                CommentsMsg.Requested::new,
                                (model, popup) -> popup.detailsRead()),
                        new Choice<>(
                                'F',
                                "dialog.pullRequest.files",
                                PullRequestMsg.FilesChosen::new,
                                (model, popup) -> popup.commits().isDefined()),
                        // Before merging, which a draft has to wait for.
                        new Choice<>(
                                'd',
                                popup -> Messages.get(
                                        popup.draft().getOrElse(false)
                                                ? "dialog.pullRequest.ready"
                                                : "dialog.pullRequest.toDraft"),
                                PullRequestMsg.DraftChosen::new,
                                PullRequestUpdate::draftChangeable),
                        merge('m', "dialog.pullRequest.merge", MergeMethod.MERGE),
                        merge('s', "dialog.pullRequest.squash", MergeMethod.SQUASH),
                        merge('r', "dialog.pullRequest.rebase", MergeMethod.REBASE),
                        new Choice<>(
                                'x',
                                "dialog.pullRequest.close",
                                PullRequestMsg.CloseChosen::new,
                                (model, popup) -> popup.open()),
                        new Choice<>(
                                'R',
                                "dialog.pullRequest.refresh",
                                PullRequestMsg.RefreshChosen::new,
                                PullRequestUpdate::refreshChoosable)),
                () -> new Msg.PopupDismissed(PullRequestPopup.class),
                dispatch,
                DialogFrame.contentWidth(WIDTH));
    }

    /**
     * Merging as {@code method} says; only for a pull request that can be merged, as far as is known. One with others
     * below it in its stack says how many it merges, as it merges those too.
     */
    private static Choice<PullRequestPopup> merge(char key, String labelKey, MergeMethod method) {
        return new Choice<>(
                key,
                popup -> popup.stack()
                        .filter(stack -> stack.below() > 0)
                        .map(stack -> Messages.get(labelKey + ".stack", String.valueOf(stack.below() + 1)))
                        .getOrElse(() -> Messages.get(labelKey)),
                () -> new PullRequestMsg.MergeChosen(method),
                PullRequestUpdate::mergeable);
    }

    /**
     * Where the pull request sits in its stack, and how many below it merging it merges too.
     */
    private static Line stack(PullRequestStack stack) {
        String position = Messages.get(
                "dialog.pullRequest.stack.position",
                String.valueOf(stack.number()),
                String.valueOf(stack.position()),
                String.valueOf(stack.size()));
        return stack.below() > 0
                ? Line.labelled(
                        "dialog.pullRequest.stack",
                        text(position + " · "),
                        text(Messages.get("dialog.pullRequest.stack.below", stack.below()))
                                .addClass(Theme.Classes.CHECKS_RUNNING))
                : Line.labelled("dialog.pullRequest.stack", text(position));
    }

    private static List<Line> failed(Failure failure) {
        return List.of(Line.wrapped(
                Messages.get("dialog.pullRequest.failed", ErrorText.of(failure)), Theme.Classes.ERROR, CONTENT_HEIGHT));
    }

    private static StyledElement<?> label(String labelKey) {
        return text(TextWidth.alignRight(Messages.get(labelKey), LABEL_COLUMN) + "  ")
                .addClass(Theme.Classes.DIM);
    }

    private static String state(PullRequestDetails pr) {
        String state = Messages.get(
                switch (pr.status().state()) {
                    case OPEN -> "dialog.pullRequest.state.open";
                    case CLOSED -> "dialog.pullRequest.state.closed";
                    case MERGED -> "dialog.pullRequest.state.merged";
                });
        return pr.status().draft() ? state + " · " + Messages.get("dialog.pullRequest.draft") : state;
    }

    // Mergeable only says it merges without conflicts; GitHub warns about a base that moved on apart from that.
    private static Option<StyledElement<?>> behindBase(PullRequestDetails pr) {
        return pr.branches()
                .behindBase()
                .filter(commits -> commits > 0)
                .map(commits -> text(" · " + Messages.get("dialog.pullRequest.branch.behind", String.valueOf(commits)))
                        .addClass(Theme.Classes.BEHIND));
    }

    private static StyledElement<?> mergeable(PullRequestDetails.Mergeable mergeable) {
        return switch (mergeable) {
            case MERGEABLE -> text(Messages.get("dialog.pullRequest.mergeable.yes"));
            case CONFLICTING ->
                text(Messages.get("dialog.pullRequest.mergeable.conflicting")).addClass(Theme.Classes.CHECKS_FAILED);
            case UNKNOWN ->
                text(Messages.get("dialog.pullRequest.mergeable.unknown")).addClass(Theme.Classes.DIM);
        };
    }

    private static List<StyledElement<?>> review(Review review) {
        String text = Messages.get(
                switch (review) {
                    case NONE -> "dialog.pullRequest.review.none";
                    case REQUIRED -> "dialog.pullRequest.review.required";
                    case APPROVED -> "dialog.pullRequest.review.approved";
                    case CHANGES_REQUESTED -> "dialog.pullRequest.review.changes";
                });
        return PullRequestSymbols.review(review)
                .map(symbol -> List.of(symbol(symbol), text(" " + text)))
                .getOrElse(() -> List.of(text(text).addClass(Theme.Classes.DIM)));
    }

    /**
     * Each reviewer, preceded by the symbol of their review like the review line; two spaces in between.
     */
    private static List<StyledElement<?>> reviewers(PullRequestDetails pr) {
        return pr.conversation()
                .reviewers()
                .map(reviewer -> List.<StyledElement<?>>of(
                        symbol(PullRequestSymbols.reviewer(reviewer.state())), text(" "), text("@" + reviewer.login())))
                .zipWithIndex()
                .flatMap(indexed -> indexed._2 > 0 ? indexed._1.prepend(text("  ")) : indexed._1);
    }

    /**
     * How many checks failed, are running and passed, and when they are expected to be done; then the failed ones
     * by name, and the ones not done with how long they run and are expected to take still: the running ones before
     * the queued ones, each the soonest done first (ADR 32), lined up on the right as in the checks popup.
     */
    private static List<Line> checks(List<Check> checks, Option<CheckTimes> times, Instant now) {
        if (checks.isEmpty()) {
            return List.of(Line.labelled(
                    "dialog.pullRequest.checks",
                    text(Messages.get("dialog.pullRequest.checks.none")).addClass(Theme.Classes.DIM)));
        }
        List<Check> notPassed = checks.filter(check -> check.state() != Checks.PASSING);
        List<Check> listed = CheckRows.order(notPassed, checks, times).take(LISTED_CHECKS);
        int doingWidth = CheckRows.doingWidth(listed, checks, times, now, false);
        return List.of(Line.pieces(List.<StyledElement<?>>of(label("dialog.pullRequest.checks"))
                        .appendAll(CheckRows.counts(checks, times, now))))
                .appendAll(listed.map(check -> Line.pieces(List.<StyledElement<?>>of(text(INDENT))
                        .appendAll(CheckRows.filling(check, checks, times, now, CHECK_WIDTH, doingWidth, false)))))
                .appendAll(Option.when(
                        notPassed.size() > LISTED_CHECKS,
                        () -> Line.of(text(INDENT
                                        + Messages.get(
                                                "dialog.pullRequest.checks.more",
                                                String.valueOf(notPassed.size() - LISTED_CHECKS)))
                                .addClass(Theme.Classes.DIM))));
    }

    private static StyledElement<?> symbol(Symbol symbol) {
        return text(symbol.text()).addClass(symbol.cssClass());
    }

    @Override
    public List<String> ids() {
        return List.of(ID);
    }

    /**
     * Whether the dialog shows a spinner in {@code model}: while the details of the pull request are read the first
     * time, or again as the user asked.
     */
    public static boolean showsSpinner(Model model) {
        return model.popup(PullRequestPopup.class)
                .exists(popup -> popup.details().isEmpty() || popup.reading() == Reading.ASKED);
    }

    @Override
    public Option<Element> render(Model model) {
        return model.popup(PullRequestPopup.class).map(popup -> {
            List<Line> lines = popup.details()
                    .map(read -> read.fold(
                            PullRequestDialog::failed,
                            pr -> details(
                                    pr,
                                    model.now(),
                                    model.gitHub()
                                            .checkTimes()
                                            .filter(times -> times.repository().equals(pr.repository())))))
                    .getOrElse(() -> loading(popup));
            int blanks = CONTENT_HEIGHT - lines.map(Line::height).sum().intValue();
            return DialogFrame.withHint(
                            title(popup),
                            WIDTH,
                            "dialog.hint.selectCancel",
                            lines.appendAll(List.fill(Math.max(0, blanks), Line::blank))
                                    .append(Line.blank())
                                    .map(Line::element)
                                    .append(actions.render(model, popup)))
                    // The lines, a blank line, the actions, the blank line and the hint, plus the border.
                    .length(CONTENT_HEIGHT + 1 + actions.height(model, popup) + 2 + 2);
        });
    }

    /**
     * The number of the pull request; then a spinner while it is read again as the user asked (ADR 34), or a mark
     * when a read failed.
     */
    private String title(PullRequestPopup popup) {
        String title = Messages.get(
                "dialog.pullRequest.title", String.valueOf(popup.pullRequest().number()));
        return popup.reading() == Reading.ASKED
                ? title + " " + TimeText.spinnerFrame(clock.instant())
                : popup.refreshFailed()
                        .map(failure -> title + " · " + Messages.get("dialog.pullRequest.stale"))
                        .getOrElse(title);
    }

    private List<Line> loading(PullRequestPopup popup) {
        return List.of(
                Line.labelled(
                        "dialog.pullRequest.branch", text(popup.pullRequest().headBranch())),
                Line.blank(),
                Line.pieces(List.of(
                        text(Messages.get("dialog.pullRequest.loading") + " ").addClass(Theme.Classes.DIM),
                        text(TimeText.spinnerFrame(clock.instant())).addClass(Theme.Classes.SPINNER))));
    }

    private List<Line> details(PullRequestDetails pr, Instant now, Option<CheckTimes> times) {
        return List.of(
                        Line.wrapped(pr.title(), Theme.Classes.BOLD, TITLE_LINES),
                        Line.blank(),
                        Line.labelled("dialog.pullRequest.state", text(state(pr))))
                .appendAll(Option.when(
                        !pr.author().isEmpty(),
                        () -> Line.labelled("dialog.pullRequest.author", text("@" + pr.author()))))
                .append(Line.pieces(List.<StyledElement<?>>of(
                                label("dialog.pullRequest.branch"),
                                text(pr.branches().head() + " → "
                                        + pr.branches().base()))
                        .appendAll(behindBase(pr))))
                .appendAll(pr.stack().map(PullRequestDialog::stack))
                .appendAll(List.of(
                        Line.labelled(
                                "dialog.pullRequest.created",
                                text(when(pr.activity().createdAt(), now))),
                        Line.labelled(
                                "dialog.pullRequest.pushed",
                                text(when(pr.activity().pushedAt(), now))),
                        Line.labelled(
                                "dialog.pullRequest.changes",
                                text("+" + pr.size().additions()).addClass(Theme.Classes.DIFF_ADDED),
                                text(" −" + pr.size().deletions()).addClass(Theme.Classes.DIFF_REMOVED),
                                text(" "
                                        + Messages.get(
                                                "dialog.pullRequest.changes.files",
                                                pr.size().changedFiles(),
                                                pr.size().commits()))),
                        Line.labelled(
                                "dialog.pullRequest.mergeable",
                                mergeable(pr.status().mergeable())),
                        Line.blank(),
                        Line.pieces(List.<StyledElement<?>>of(label("dialog.pullRequest.review"))
                                .appendAll(review(pr.conversation().review())))))
                .appendAll(Option.when(
                        !pr.conversation().reviewers().isEmpty(),
                        () -> Line.pieces(List.<StyledElement<?>>of(label("dialog.pullRequest.reviewers"))
                                .appendAll(reviewers(pr)))))
                .append(Line.labelled(
                        "dialog.pullRequest.comments",
                        text(String.valueOf(pr.conversation().comments()))))
                .append(Line.labelled(
                        "dialog.pullRequest.threads",
                        text(threads(pr.conversation().reviewThreads()))))
                .appendAll(Option.when(
                        !pr.conversation().labels().isEmpty(),
                        () -> Line.labelled(
                                "dialog.pullRequest.labels",
                                text(pr.conversation().labels().mkString(", ")))))
                .append(Line.blank())
                .appendAll(checks(pr.checks(), times, now));
    }

    private static String threads(ReviewThreads threads) {
        if (threads.threads() == 0) {
            return "0";
        }
        if (threads.unresolved() == 0) {
            // Unless every thread was read, one that was not may still be unresolved.
            return Messages.get(
                    threads.complete() ? "dialog.pullRequest.threads.resolved" : "dialog.pullRequest.threads.partial",
                    threads.threads(),
                    threads.comments());
        }
        return Messages.get(
                "dialog.pullRequest.threads.unresolved", threads.threads(), threads.comments(), threads.unresolved());
    }

    private String when(Instant at, Instant now) {
        return Messages.get(
                "dialog.pullRequest.when",
                LocalDate.ofInstant(at, clock.getZone()).toString(),
                TimeText.ago(Duration.between(at, now)));
    }

    /**
     * A part of the dialog, and how many lines it takes.
     */
    private record Line(Element element, int height) {

        static Line of(StyledElement<?> element) {
            return new Line(element, 1);
        }

        static Line blank() {
            return of(text(""));
        }

        /**
         * {@code text}, wrapped at the width of the dialog, and cut after {@code maxLines}.
         */
        static Line wrapped(String text, String cssClass, int maxLines) {
            return new Line(
                    DialogFrame.wrapped(text, WIDTH, maxLines).addClass(cssClass),
                    DialogFrame.wrappedHeight(text, WIDTH, maxLines));
        }

        /**
         * A row with {@code label} in the label column and {@code value} next to it.
         */
        static Line labelled(String labelKey, StyledElement<?>... value) {
            return pieces(List.<StyledElement<?>>of(label(labelKey)).appendAll(List.of(value)));
        }

        static Line pieces(List<StyledElement<?>> pieces) {
            return of(pieces.size() == 1 ? pieces.head() : row(pieces.toJavaArray(StyledElement[]::new)));
        }
    }
}
