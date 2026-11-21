package net.vanfleteren.jazygit.features.github.show;

import static dev.tamboui.toolkit.Toolkit.row;
import static dev.tamboui.toolkit.Toolkit.text;

import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.StyledElement;
import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.core.gh.model.MergeMethod;
import net.vanfleteren.jazygit.core.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.core.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.core.gh.model.PullRequestDetails;
import net.vanfleteren.jazygit.core.gh.model.PullRequestDetails.AutoMerge;
import net.vanfleteren.jazygit.core.gh.model.PullRequestDetails.Check;
import net.vanfleteren.jazygit.core.gh.model.PullRequestDetails.ReviewThreads;
import net.vanfleteren.jazygit.core.gh.model.PullRequestStack;
import net.vanfleteren.jazygit.features.github.checks.ChecksMsg;
import net.vanfleteren.jazygit.features.github.checks.ChecksUpdate;
import net.vanfleteren.jazygit.features.github.comment.CommentMsg;
import net.vanfleteren.jazygit.features.github.comments.CommentsMsg;
import net.vanfleteren.jazygit.features.github.edit.EditPullRequestMsg;
import net.vanfleteren.jazygit.features.github.state.CheckTimes;
import net.vanfleteren.jazygit.features.github.state.Reading;
import net.vanfleteren.jazygit.features.github.verdict.VerdictMsg;
import net.vanfleteren.jazygit.features.github.verdict.VerdictUpdate;
import net.vanfleteren.jazygit.features.github.widgets.CheckRows;
import net.vanfleteren.jazygit.features.github.widgets.PullRequestSymbols;
import net.vanfleteren.jazygit.features.github.widgets.PullRequestSymbols.Symbol;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.popup.PopupMsg;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;
import net.vanfleteren.jazygit.kernel.ui.text.ErrorText;
import net.vanfleteren.jazygit.kernel.ui.text.ShownTime;
import net.vanfleteren.jazygit.kernel.ui.text.TextWidth;
import net.vanfleteren.jazygit.kernel.ui.text.TimeText;
import net.vanfleteren.jazygit.kernel.ui.theme.Theme;
import net.vanfleteren.jazygit.kernel.ui.widgets.ChoiceDialog.Choice;
import net.vanfleteren.jazygit.kernel.ui.widgets.ChoiceList;
import net.vanfleteren.jazygit.kernel.ui.widgets.DialogFrame;
import net.vanfleteren.jazygit.kernel.ui.widgets.KeysFirst;
import net.vanfleteren.jazygit.kernel.ui.widgets.PopupView;
import net.vanfleteren.jazygit.kernel.ui.widgets.WithDetails;

/**
 * Popup showing the pull request of a branch: its title, state, branches, review and checks, as labelled
 * rows, and under them, in two columns (ADR 66), what can be done with it: open it in the browser, comment on it, edit it, read its comments or see its checks, mark it
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
    private static final int LISTED_CHECKS = 7;
    // How wide the bar of each check is, after its name, while one is not done: half that of the checks popup.
    private static final int CHECK_BAR = 12;
    // A longer title is cut.
    private static final int TITLE_LINES = 3;
    /*
     * The most lines the details take: the title and a blank line; state, author, branch, stack, created, pushed,
     * changes and mergeable, and a blank line; review, reviewers, comments, threads and labels, and a blank line; the
     * checks counted, how far they are, listed, and how many more there are. The dialog is always this high, so it
     * keeps its size when the details arrive.
     */
    private static final int CONTENT_HEIGHT = TITLE_LINES + 1 + 8 + 1 + 5 + 1 + 1 + 1 + LISTED_CHECKS + 1;

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
                        new Choice<>('o', "github.dialog.pullRequest.open", PullRequestMsg.OpenConfirmed::new),
                        new Choice<>(
                                'c',
                                "github.dialog.pullRequest.comment",
                                CommentMsg.Requested::new,
                                (model, popup) -> popup.detailsRead()),
                        // Not on the viewer's own, nor on one that is not open (ADR 100).
                        new Choice<PullRequestPopup>(
                                        'a',
                                        "github.dialog.pullRequest.approve",
                                        VerdictMsg.DialogAsked::new,
                                        (model, popup) -> VerdictUpdate.reviewable(popup))
                                .withDetails("github.dialog.pullRequest.approve.details"),
                        new Choice<PullRequestPopup>(
                                        'e',
                                        "github.dialog.pullRequest.edit",
                                        EditPullRequestMsg.Requested::new,
                                        (model, popup) -> popup.detailsRead())
                                .withDetails("github.dialog.pullRequest.edit.details"),
                        new Choice<PullRequestPopup>(
                                        'v',
                                        "github.dialog.pullRequest.viewComments",
                                        CommentsMsg.Requested::new,
                                        (model, popup) -> popup.detailsRead())
                                .withDetails("github.dialog.pullRequest.viewComments.details"),
                        new Choice<PullRequestPopup>(
                                        'C',
                                        "github.dialog.pullRequest.viewChecks",
                                        ChecksMsg.PullRequestChecksRequested::new,
                                        (model, popup) -> ChecksUpdate.ofPullRequest(model, popup)
                                                .isDefined())
                                .withDetails("github.dialog.pullRequest.viewChecks.details"),
                        new Choice<PullRequestPopup>(
                                        'F',
                                        "github.dialog.pullRequest.files",
                                        PullRequestMsg.FilesChosen::new,
                                        (model, popup) -> popup.commits().isDefined())
                                .withDetails("github.dialog.pullRequest.files.details"),
                        // Before merging, which a draft has to wait for.
                        new Choice<PullRequestPopup>(
                                        'd',
                                        popup -> Messages.get(
                                                popup.draft().getOrElse(false)
                                                        ? "github.dialog.pullRequest.ready"
                                                        : "github.dialog.pullRequest.toDraft"),
                                        PullRequestMsg.DraftChosen::new,
                                        PullRequestUpdate::draftChangeable)
                                .withDetails("github.dialog.pullRequest.draft.details"),
                        merge('m', "github.dialog.pullRequest.merge", MergeMethod.MERGE),
                        merge('s', "github.dialog.pullRequest.squash", MergeMethod.SQUASH),
                        merge('r', "github.dialog.pullRequest.rebase", MergeMethod.REBASE),
                        new Choice<PullRequestPopup>(
                                        'x',
                                        "github.dialog.pullRequest.close",
                                        PullRequestMsg.CloseChosen::new,
                                        (model, popup) -> popup.open())
                                .withDetails("github.dialog.pullRequest.close.details"),
                        new Choice<PullRequestPopup>(
                                        'R',
                                        "github.dialog.pullRequest.refresh",
                                        PullRequestMsg.RefreshChosen::new,
                                        PullRequestUpdate::refreshChoosable)
                                .withDetails("github.dialog.pullRequest.refresh.details")),
                () -> new PopupMsg.Dismissed(PullRequestPopup.class),
                dispatch,
                DialogFrame.contentWidth(WIDTH));
    }

    /**
     * Merging as {@code method} says; merging now or when ready only for a pull request that can be merged, as far as
     * is known, while cancelling auto-merge only needs it open. One with others below it in its stack says how many it
     * merges, as it merges those too. What it does depends on what GitHub says
     * of the pull request (ADR 112): merging it now, when ready, or cancelling its auto-merge; when it does nothing,
     * its details say why.
     */
    private static Choice<PullRequestPopup> merge(char key, String labelKey, MergeMethod method) {
        return new Choice<PullRequestPopup>(
                        key,
                        popup -> popup.mergeKey(method)
                                .map(mergeKey -> switch (mergeKey) {
                                    case MergeKey.WhenReady() -> Messages.get(labelKey + ".whenReady");
                                    case MergeKey.Cancel() ->
                                        Messages.get("github.dialog.pullRequest.autoMerge.cancel");
                                    case MergeKey.Now(), MergeKey.Disabled(var _) -> mergeNow(popup, labelKey);
                                })
                                .getOrElse(() -> mergeNow(popup, labelKey)),
                        () -> new PullRequestMsg.MergeChosen(method),
                        (model, popup) -> PullRequestUpdate.mergeChoosable(model, popup, method))
                .withDetails(popup -> popup.mergeKey(method)
                        .map(mergeKey -> switch (mergeKey) {
                            case MergeKey.WhenReady() -> Messages.get("github.dialog.pullRequest.whenReady.details");
                            case MergeKey.Cancel() ->
                                Messages.get("github.dialog.pullRequest.autoMerge.cancel.details");
                            case MergeKey.Disabled(var why) -> why(why);
                            case MergeKey.Now() -> mergeNowDetails(popup, labelKey);
                        })
                        .getOrElse(() -> mergeNowDetails(popup, labelKey)));
    }

    private static String mergeNow(PullRequestPopup popup, String labelKey) {
        return popup.stack()
                .filter(stack -> stack.below() > 0)
                .map(stack -> Messages.get(labelKey + ".stack", String.valueOf(stack.below() + 1)))
                .getOrElse(() -> Messages.get(labelKey));
    }

    /**
     * What merging now does: one with others below it in its stack merges those too, and a stacked one is merged now
     * also while it is blocked, as GitHub has no auto-merge for stacked pull requests (ADR 112).
     */
    private static String mergeNowDetails(PullRequestPopup popup, String labelKey) {
        return List.of(Messages.get(labelKey + ".details"))
                .appendAll(Option.when(
                        popup.stack().exists(stack -> stack.below() > 0),
                        () -> Messages.get("github.dialog.pullRequest.merge.stack.details")))
                .appendAll(Option.when(
                        popup.stack().isDefined(),
                        () -> Messages.get("github.dialog.pullRequest.merge.stack.noAutoMerge")))
                .mkString(" ");
    }

    /**
     * Why a merge key does nothing (ADR 112).
     */
    static String why(MergeKey.Why why) {
        return switch (why) {
            case MergeKey.Why.AutoMergeOn(var on) ->
                on.enabledBy().isEmpty()
                        ? Messages.get("github.dialog.pullRequest.autoMerge.on", method(on.method()))
                        : Messages.get("github.dialog.pullRequest.autoMerge.onBy", method(on.method()), on.enabledBy());
            case MergeKey.Why.NotAllowed() -> Messages.get("github.dialog.pullRequest.autoMerge.notAllowed");
            case MergeKey.Why.Blocked() -> Messages.get("github.dialog.pullRequest.autoMerge.blocked");
            case MergeKey.Why.Behind() -> Messages.get("github.dialog.pullRequest.autoMerge.behind");
        };
    }

    private static String method(MergeMethod method) {
        return Messages.get(
                switch (method) {
                    case MERGE -> "github.dialog.pullRequest.autoMerge.method.merge";
                    case SQUASH -> "github.dialog.pullRequest.autoMerge.method.squash";
                    case REBASE -> "github.dialog.pullRequest.autoMerge.method.rebase";
                });
    }

    /**
     * Its auto-merge, while it is on (ADR 112): how, by whom and how long ago it was turned on, as far as GitHub says.
     */
    private static Line autoMerge(AutoMerge on, Instant now) {
        return Line.labelled(
                "github.dialog.pullRequest.autoMerge",
                text(List.of(method(on.method()))
                                .appendAll(Option.when(
                                        !on.enabledBy().isEmpty(),
                                        () -> Messages.get("github.dialog.pullRequest.autoMerge.by", on.enabledBy())))
                                .appendAll(on.enabledAt().map(at -> TimeText.ago(Duration.between(at, now))))
                                .mkString(" · "))
                        .addClass(Theme.Classes.CHECKS_RUNNING));
    }

    /**
     * Where the pull request sits in its stack, and how many below it merging it merges too.
     */
    private static Line stack(PullRequestStack stack) {
        String position = Messages.get(
                "github.dialog.pullRequest.stack.position",
                String.valueOf(stack.number()),
                String.valueOf(stack.position()),
                String.valueOf(stack.size()));
        return stack.below() > 0
                ? Line.labelled(
                        "github.dialog.pullRequest.stack",
                        text(position + " · "),
                        text(Messages.get("github.dialog.pullRequest.stack.below", stack.below()))
                                .addClass(Theme.Classes.CHECKS_RUNNING))
                : Line.labelled("github.dialog.pullRequest.stack", text(position));
    }

    private static List<Line> failed(Failure failure) {
        return List.of(Line.wrapped(
                Messages.get("github.dialog.pullRequest.failed", ErrorText.of(failure)),
                Theme.Classes.ERROR,
                CONTENT_HEIGHT));
    }

    private static StyledElement<?> label(String labelKey) {
        return text(TextWidth.alignRight(Messages.get(labelKey), LABEL_COLUMN) + "  ")
                .addClass(Theme.Classes.DIM);
    }

    private static String state(PullRequestDetails pr) {
        String state = Messages.get(
                switch (pr.status().state()) {
                    case OPEN -> "github.dialog.pullRequest.state.open";
                    case CLOSED -> "github.dialog.pullRequest.state.closed";
                    case MERGED -> "github.dialog.pullRequest.state.merged";
                });
        return pr.status().draft() ? state + " · " + Messages.get("github.dialog.pullRequest.draft") : state;
    }

    // Mergeable only says it merges without conflicts; GitHub warns about a base that moved on apart from that.
    private static Option<StyledElement<?>> behindBase(PullRequestDetails pr) {
        return pr.branches()
                .behindBase()
                .filter(commits -> commits > 0)
                .map(commits -> text(" · "
                                + Messages.get("github.dialog.pullRequest.branch.behind", String.valueOf(commits)))
                        .addClass(Theme.Classes.BEHIND));
    }

    private static StyledElement<?> mergeable(PullRequestDetails.Mergeable mergeable) {
        return switch (mergeable) {
            case MERGEABLE -> text(Messages.get("github.dialog.pullRequest.mergeable.yes"));
            case CONFLICTING ->
                text(Messages.get("github.dialog.pullRequest.mergeable.conflicting"))
                        .addClass(Theme.Classes.CHECKS_FAILED);
            case UNKNOWN ->
                text(Messages.get("github.dialog.pullRequest.mergeable.unknown"))
                        .addClass(Theme.Classes.DIM);
        };
    }

    private static List<StyledElement<?>> review(Review review) {
        String text = Messages.get(
                switch (review) {
                    case NONE -> "github.dialog.pullRequest.review.none";
                    case REQUIRED -> "github.dialog.pullRequest.review.required";
                    case APPROVED -> "github.dialog.pullRequest.review.approved";
                    case CHANGES_REQUESTED -> "github.dialog.pullRequest.review.changes";
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
     * How many checks failed, are running and passed, and when they are expected to be done; a bar of how far they
     * are together, {@code progress}, as in the checks popup; then the failed ones by name, and the ones not done: the
     * running ones before the queued ones, each the soonest done first (ADR 32). While one is not done, each has a bar
     * of how far it is after its name, and how long it runs of how long it is expected to take, or how long it took,
     * lined up on the right as in the checks popup.
     */
    private static List<Line> checks(List<Check> checks, Option<CheckTimes> times, double progress, Instant now) {
        if (checks.isEmpty()) {
            return List.of(Line.labelled(
                    "github.dialog.pullRequest.checks",
                    text(Messages.get("github.dialog.pullRequest.checks.none")).addClass(Theme.Classes.DIM)));
        }
        List<Check> notPassed = checks.filter(check -> check.state() != Checks.PASSING);
        List<Check> listed = CheckRows.order(notPassed, checks, times).take(LISTED_CHECKS);
        boolean running = listed.exists(check -> !check.done());
        int doingWidth = running
                ? CheckRows.barredDoingWidth(listed, checks, times, now)
                : CheckRows.doingWidth(listed, checks, times, now, false);
        return List.of(
                        Line.pieces(List.<StyledElement<?>>of(label("github.dialog.pullRequest.checks"))
                                .appendAll(CheckRows.counts(checks, times, now))),
                        Line.pieces(List.<StyledElement<?>>of(text(INDENT))
                                .appendAll(CheckRows.gauge(checks, times, progress, now, CHECK_WIDTH))))
                .appendAll(listed.map(check -> Line.pieces(List.<StyledElement<?>>of(text(INDENT))
                        .appendAll(
                                running
                                        ? CheckRows.barred(
                                                check, checks, times, now, CHECK_WIDTH, CHECK_BAR, doingWidth)
                                        : CheckRows.filling(
                                                check, checks, times, now, CHECK_WIDTH, doingWidth, false)))))
                .appendAll(Option.when(
                        notPassed.size() > LISTED_CHECKS,
                        () -> Line.of(text(INDENT
                                        + Messages.get(
                                                "github.dialog.pullRequest.checks.more",
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
     * The times the dialog shows (ADR 124): how long ago the pull request was opened and pushed to, and its auto-merge
     * turned on (ADR 112), and every second
     * while one of its checks is not done.
     */
    public static List<ShownTime> shownTimes(Model model) {
        return model.popup(PullRequestPopup.class)
                .flatMap(popup -> popup.details().flatMap(Either::toOption))
                .toList()
                .flatMap(pr -> List.of(ShownTime.reading(now -> TimeText.ago(
                                        Duration.between(pr.activity().createdAt(), now))
                                + " "
                                + TimeText.ago(Duration.between(pr.activity().pushedAt(), now))
                                + pr.merging()
                                        .autoMerge()
                                        .flatMap(AutoMerge::enabledAt)
                                        .map(at -> " " + TimeText.ago(Duration.between(at, now)))
                                        .getOrElse("")))
                        .appendAll(Option.when(
                                pr.checks().exists(check -> !check.done()),
                                () -> ShownTime.every(Duration.ofSeconds(1)))));
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
                            pr -> details(pr, model.now(), PullRequestPopup.times(model, pr), popup.progress(model))))
                    .getOrElse(() -> loading(popup));
            int blanks = CONTENT_HEIGHT - lines.map(Line::height).sum().intValue();
            Element dialog = DialogFrame.withHint(
                            title(popup),
                            WIDTH,
                            "dialog.hint.selectCancel",
                            lines.appendAll(List.fill(Math.max(0, blanks), Line::blank))
                                    .append(Line.blank())
                                    .map(Line::element)
                                    .append(actions.render(model, popup)))
                    // The lines, a blank line, the actions, the blank line and the hint, plus the border.
                    .length(CONTENT_HEIGHT + 1 + actions.height(model, popup) + 2 + 2);
            // The tallest popup: what the actions do shows only when there is room for it under the whole popup.
            // On a screen shorter than the popup the actions are cut off, and so not drawn; the popup as a whole
            // takes their keys, so they and Escape still work.
            return new KeysFirst(
                    WithDetails.uncut(
                            dialog,
                            actions.highlightedDetails(model, popup, Option.none()),
                            actions.details(model, popup)),
                    ID,
                    event -> actions.handleKey(model, popup, event));
        });
    }

    /**
     * The number of the pull request; then a spinner while it is read again as the user asked (ADR 34), or a mark
     * when a read failed.
     */
    private String title(PullRequestPopup popup) {
        String title = Messages.get(
                "github.dialog.pullRequest.title",
                String.valueOf(popup.pullRequest().number().value()));
        return popup.reading() == Reading.ASKED
                ? title + " " + TimeText.spinnerFrame(clock.instant())
                : popup.refreshFailed()
                        .map(failure -> title + " · " + Messages.get("github.dialog.pullRequest.stale"))
                        .getOrElse(title);
    }

    private List<Line> loading(PullRequestPopup popup) {
        return List.of(
                Line.labelled(
                        "github.dialog.pullRequest.branch",
                        text(popup.pullRequest().headBranch().name())),
                Line.blank(),
                Line.pieces(List.of(
                        text(Messages.get("github.dialog.pullRequest.loading") + " ")
                                .addClass(Theme.Classes.DIM),
                        text(TimeText.spinnerFrame(clock.instant())).addClass(Theme.Classes.SPINNER))));
    }

    private List<Line> details(PullRequestDetails pr, Instant now, Option<CheckTimes> times, double progress) {
        return List.of(
                        Line.wrapped(pr.title(), Theme.Classes.BOLD, TITLE_LINES),
                        Line.blank(),
                        Line.labelled("github.dialog.pullRequest.state", text(state(pr))))
                .appendAll(Option.when(
                        !pr.author().isEmpty(),
                        () -> Line.labelled("github.dialog.pullRequest.author", text("@" + pr.author()))))
                .append(Line.pieces(List.<StyledElement<?>>of(
                                label("github.dialog.pullRequest.branch"),
                                text(pr.branches().head().name() + " → "
                                        + pr.branches().base().name()))
                        .appendAll(behindBase(pr))))
                .appendAll(pr.stack().map(PullRequestDialog::stack))
                // Next to the stack, which GitHub has no auto-merge for: both only show when it was turned on
                // elsewhere.
                .appendAll(pr.merging().autoMerge().map(on -> autoMerge(on, now)))
                .appendAll(List.of(
                        Line.labelled(
                                "github.dialog.pullRequest.created",
                                text(when(pr.activity().createdAt(), now))),
                        Line.labelled(
                                "github.dialog.pullRequest.pushed",
                                text(when(pr.activity().pushedAt(), now))),
                        Line.labelled(
                                "github.dialog.pullRequest.changes",
                                text("+" + pr.size().additions()).addClass(Theme.Classes.DIFF_ADDED),
                                text(" −" + pr.size().deletions()).addClass(Theme.Classes.DIFF_REMOVED),
                                text(" "
                                        + Messages.get(
                                                "github.dialog.pullRequest.changes.files",
                                                pr.size().changedFiles(),
                                                pr.size().commits()))),
                        Line.labelled(
                                "github.dialog.pullRequest.mergeable",
                                mergeable(pr.status().mergeable())),
                        Line.blank(),
                        Line.pieces(List.<StyledElement<?>>of(label("github.dialog.pullRequest.review"))
                                .appendAll(review(pr.conversation().review())))))
                .appendAll(Option.when(
                        !pr.conversation().reviewers().isEmpty(),
                        () -> Line.pieces(List.<StyledElement<?>>of(label("github.dialog.pullRequest.reviewers"))
                                .appendAll(reviewers(pr)))))
                .append(Line.labelled(
                        "github.dialog.pullRequest.comments",
                        text(String.valueOf(pr.conversation().comments()))))
                .append(Line.labelled(
                        "github.dialog.pullRequest.threads",
                        text(threads(pr.conversation().reviewThreads()))))
                .appendAll(Option.when(
                        !pr.conversation().labels().isEmpty(),
                        () -> Line.labelled(
                                "github.dialog.pullRequest.labels",
                                text(pr.conversation().labels().mkString(", ")))))
                .append(Line.blank())
                .appendAll(checks(pr.checks(), times, progress, now));
    }

    private static String threads(ReviewThreads threads) {
        if (threads.threads() == 0) {
            return "0";
        }
        if (threads.unresolved() == 0) {
            // Unless every thread was read, one that was not may still be unresolved.
            return Messages.get(
                    threads.complete()
                            ? "github.dialog.pullRequest.threads.resolved"
                            : "github.dialog.pullRequest.threads.partial",
                    threads.threads(),
                    threads.comments());
        }
        return Messages.get(
                "github.dialog.pullRequest.threads.unresolved",
                threads.threads(),
                threads.comments(),
                threads.unresolved());
    }

    private String when(Instant at, Instant now) {
        return Messages.get(
                "github.dialog.pullRequest.when",
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
