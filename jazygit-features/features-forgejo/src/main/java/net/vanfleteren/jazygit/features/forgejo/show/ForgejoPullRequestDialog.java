package net.vanfleteren.jazygit.features.forgejo.show;

import static dev.tamboui.toolkit.Toolkit.text;
import static net.vanfleteren.jazygit.kernel.ui.widgets.LabelledLines.INDENT;

import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.StyledElement;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.core.forgejo.CommitStatus;
import net.vanfleteren.jazygit.core.forgejo.ForgejoMergeStyle;
import net.vanfleteren.jazygit.core.forgejo.ForgejoPullRequestDetails;
import net.vanfleteren.jazygit.core.forgejo.ForgejoReview.Verdict;
import net.vanfleteren.jazygit.core.forgejo.ForgejoScheduledMerge;
import net.vanfleteren.jazygit.features.forgejo.checks.ChecksMsg;
import net.vanfleteren.jazygit.features.forgejo.checks.ChecksUpdate;
import net.vanfleteren.jazygit.features.forgejo.pulls.ForgejoPullRequests;
import net.vanfleteren.jazygit.features.forgejo.pulls.PullRequestMsg;
import net.vanfleteren.jazygit.features.forgejo.pulls.PullRequestUpdate;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.popup.PopupMsg;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;
import net.vanfleteren.jazygit.kernel.ui.text.ErrorText;
import net.vanfleteren.jazygit.kernel.ui.text.ShownTime;
import net.vanfleteren.jazygit.kernel.ui.text.TimeText;
import net.vanfleteren.jazygit.kernel.ui.theme.Theme;
import net.vanfleteren.jazygit.kernel.ui.widgets.CheckSymbols;
import net.vanfleteren.jazygit.kernel.ui.widgets.ChoiceDialog.Choice;
import net.vanfleteren.jazygit.kernel.ui.widgets.ChoiceList;
import net.vanfleteren.jazygit.kernel.ui.widgets.DialogFrame;
import net.vanfleteren.jazygit.kernel.ui.widgets.KeysFirst;
import net.vanfleteren.jazygit.kernel.ui.widgets.LabelledLines;
import net.vanfleteren.jazygit.kernel.ui.widgets.LabelledLines.Line;
import net.vanfleteren.jazygit.kernel.ui.widgets.PopupView;
import net.vanfleteren.jazygit.kernel.ui.widgets.ReviewSymbols;
import net.vanfleteren.jazygit.kernel.ui.widgets.WithDetails;

/**
 * Popup showing the pull request on Forgejo that merges a branch (ADR 176), as GitHub's shows its own (ADR 175):
 * its title, state, branches, review and checks, as labelled rows, and under them what can be done with it: open it
 * in the browser, merge it in one of Forgejo's five styles (ADR 180), or read it again. While it is read again, the
 * details stay shown, with a spinner in the title; when that fails, the title says they are stale.
 */
public final class ForgejoPullRequestDialog implements PopupView {

    public static final String ID = "forgejo-pull-request";

    private static final int WIDTH = 80;
    // A check listed after the indent takes the rest of the line.
    private static final int CHECK_WIDTH = DialogFrame.contentWidth(WIDTH) - INDENT.length();
    // The failed and running checks listed one by one; the rest are counted.
    private static final int LISTED_CHECKS = 7;
    // A longer title is cut.
    private static final int TITLE_LINES = 3;
    /*
     * The most lines the details take: the title and a blank line; state, author, branch, created, updated, changes
     * and mergeable, and a blank line; review, reviewers, comments and labels, and a blank line; the checks counted,
     * listed, and how many more there are. The dialog is always this high, so it keeps its size when the details
     * arrive; and three lines more, those GitHub's has for the stack, the threads and the bar of the checks, so that
     * the dialogs of both forges are as high (ADR 175). A merge scheduled for when the checks succeed takes the line of
     * the stack, as GitHub's auto-merge does (ADR 181).
     */
    private static final int CONTENT_HEIGHT = TITLE_LINES + 1 + 7 + 1 + 4 + 1 + 1 + LISTED_CHECKS + 1 + 3;

    private final Clock clock;
    private final ChoiceList<ForgejoPullRequestPopup> actions;

    /**
     * @param dispatch receives the messages for the user's actions, on the render thread
     */
    public ForgejoPullRequestDialog(Consumer<Msg> dispatch) {
        this(dispatch, Clock.systemDefaultZone());
    }

    /**
     * @param clock tells on which day the pull request was opened, and animates the spinner; how long ago is told by
     *              the time of the model
     */
    ForgejoPullRequestDialog(Consumer<Msg> dispatch, Clock clock) {
        this.clock = clock;
        this.actions = ChoiceList.inTwoColumns(
                ID,
                List.of(
                        new Choice<>('o', "forgejo.dialog.pullRequest.open", PullRequestMsg.OpenChosen::new),
                        merge('m', ForgejoMergeStyle.MERGE),
                        merge('s', ForgejoMergeStyle.SQUASH),
                        merge('r', ForgejoMergeStyle.REBASE),
                        merge('M', ForgejoMergeStyle.REBASE_MERGE),
                        merge('f', ForgejoMergeStyle.FAST_FORWARD_ONLY),
                        // On the key GitHub's lists its checks on (ADR 195).
                        new Choice<ForgejoPullRequestPopup>(
                                        'C',
                                        "forgejo.dialog.pullRequest.viewChecks",
                                        ChecksMsg.PullRequestChecksRequested::new,
                                        (model, popup) -> ChecksUpdate.ofPullRequest(popup))
                                .withDetails("forgejo.dialog.pullRequest.viewChecks.details"),
                        new Choice<ForgejoPullRequestPopup>(
                                        'R',
                                        "forgejo.dialog.pullRequest.refresh",
                                        PullRequestMsg.RefreshChosen::new,
                                        (model, popup) -> PullRequestUpdate.refreshable(popup))
                                .withDetails("forgejo.dialog.pullRequest.refresh.details")),
                () -> new PopupMsg.Dismissed(ForgejoPullRequestPopup.class),
                dispatch,
                DialogFrame.contentWidth(WIDTH));
    }

    /**
     * Merging it as {@code style} says (ADR 180), on the keys GitHub's has for the styles both have: now, when its
     * checks succeed, or cancelling the merge scheduled (ADR 181), as its label says. When it does nothing, its details
     * say why.
     */
    private static Choice<ForgejoPullRequestPopup> merge(char key, ForgejoMergeStyle style) {
        String labelKey = "forgejo.dialog.pullRequest.merge." + named(style);
        return new Choice<ForgejoPullRequestPopup>(
                        key,
                        popup -> popup.shown()
                                .map(shown -> switch (MergeKey.of(shown, style)) {
                                    case MergeKey.WhenChecksSucceed() -> Messages.get(labelKey + ".whenChecks");
                                    case MergeKey.Cancel(var _) ->
                                        Messages.get("forgejo.dialog.pullRequest.merge.cancel");
                                    case MergeKey.Now(), MergeKey.Disabled(var _) -> Messages.get(labelKey);
                                })
                                .getOrElse(() -> Messages.get(labelKey)),
                        () -> new PullRequestMsg.MergeChosen(style),
                        (model, popup) -> MergeUpdate.choosable(popup, style))
                .withDetails(popup -> popup.shown()
                        .map(shown -> switch (MergeKey.of(shown, style)) {
                            case MergeKey.Now() -> Messages.get(labelKey + ".details");
                            case MergeKey.WhenChecksSucceed() ->
                                Messages.get("forgejo.dialog.pullRequest.merge.whenChecks.details");
                            case MergeKey.Cancel(var _) ->
                                Messages.get("forgejo.dialog.pullRequest.merge.cancel.details");
                            case MergeKey.Disabled(var why) -> why(why);
                        })
                        .getOrElse(() -> Messages.get(labelKey + ".details")));
    }

    private static String named(ForgejoMergeStyle style) {
        return switch (style) {
            case MERGE -> "merge";
            case SQUASH -> "squash";
            case REBASE -> "rebase";
            case REBASE_MERGE -> "rebaseMerge";
            case FAST_FORWARD_ONLY -> "fastForward";
        };
    }

    private static String why(MergeKey.Why why) {
        return switch (why) {
            case MergeKey.Why.NotOpen() -> Messages.get("forgejo.dialog.pullRequest.merge.notOpen");
            case MergeKey.Why.Draft() -> Messages.get("forgejo.dialog.pullRequest.merge.draft");
            case MergeKey.Why.Conflicts() -> Messages.get("forgejo.dialog.pullRequest.merge.conflicts");
            case MergeKey.Why.NotAllowed(var style) ->
                Messages.get(
                        "forgejo.dialog.pullRequest.merge.notAllowed",
                        Messages.get("forgejo.dialog.pullRequest.merge." + named(style)));
        };
    }

    @Override
    public List<String> ids() {
        return List.of(ID);
    }

    /**
     * Whether the dialog shows a spinner in {@code model}: while its pull request is read the first time, or again as
     * the user asked.
     */
    public static boolean showsSpinner(Model model) {
        return model.popup(ForgejoPullRequestPopup.class).exists(ForgejoPullRequestPopup::reading);
    }

    /**
     * The times the dialog shows (ADR 124): how long ago the pull request was opened and last changed, and its merge
     * scheduled (ADR 181).
     */
    public static List<ShownTime> shownTimes(Model model) {
        return model.popup(ForgejoPullRequestPopup.class)
                .flatMap(ForgejoPullRequestPopup::shown)
                .toList()
                .map(shown -> ShownTime.reading(now -> TimeText.ago(
                                Duration.between(shown.pullRequest().activity().createdAt(), now))
                        + " "
                        + TimeText.ago(
                                Duration.between(shown.pullRequest().activity().updatedAt(), now))
                        + shown.scheduled()
                                .map(merge -> " " + TimeText.ago(Duration.between(merge.at(), now)))
                                .getOrElse("")));
    }

    @Override
    public Option<Element> render(Model model) {
        return model.popup(ForgejoPullRequestPopup.class).map(popup -> {
            List<Line> lines = popup.details()
                    .map(read -> read.fold(
                            ForgejoPullRequestDialog::failed,
                            shown -> details(
                                    shown,
                                    model.part(ForgejoPullRequests.PART)
                                            .style(shown.pullRequest().number()),
                                    model.now())))
                    .getOrElse(() -> loading(popup));
            Element dialog = DialogFrame.withHint(
                            title(popup),
                            WIDTH,
                            "dialog.hint.selectCancel",
                            LabelledLines.filled(lines, CONTENT_HEIGHT)
                                    .append(Line.blank().element())
                                    .append(actions.render(model, popup)))
                    // The lines, a blank line, the actions, the blank line and the hint, plus the border.
                    .length(CONTENT_HEIGHT + 1 + actions.height(model, popup) + 2 + 2);
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
    private String title(ForgejoPullRequestPopup popup) {
        String title = Messages.get(
                "forgejo.dialog.pullRequest.title",
                String.valueOf(popup.pullRequest().number().value()));
        return popup.asked()
                ? title + " " + TimeText.spinnerFrame(clock.instant())
                : popup.refreshFailed()
                        .map(failure -> title + " · " + Messages.get("forgejo.dialog.pullRequest.stale"))
                        .getOrElse(title);
    }

    private List<Line> loading(ForgejoPullRequestPopup popup) {
        return List.of(
                Line.labelled(
                        "forgejo.dialog.pullRequest.branch",
                        text(popup.pullRequest().head().name())),
                Line.blank(),
                Line.pieces(List.of(
                        text(Messages.get("forgejo.dialog.pullRequest.loading") + " ")
                                .addClass(Theme.Classes.DIM),
                        text(TimeText.spinnerFrame(clock.instant())).addClass(Theme.Classes.SPINNER))));
    }

    private static List<Line> failed(Failure failure) {
        return List.of(Line.wrapped(
                Messages.get("forgejo.dialog.pullRequest.failed", ErrorText.of(failure)),
                Theme.Classes.ERROR,
                WIDTH,
                CONTENT_HEIGHT));
    }

    /**
     * Its merge scheduled for when its checks succeed (ADR 181): how, when jazygit scheduled it, as Forgejo does not
     * tell; by whom and how long ago, as its timeline does.
     */
    private static Line scheduled(ForgejoScheduledMerge merge, Option<ForgejoMergeStyle> style, Instant now) {
        return Line.labelled(
                "forgejo.dialog.pullRequest.autoMerge",
                text(List.of(style.map(how -> Messages.get("forgejo.dialog.pullRequest.autoMerge." + named(how)))
                                .getOrElse(() -> Messages.get("forgejo.dialog.pullRequest.autoMerge.scheduled")))
                        .appendAll(Option.when(!merge.by().isEmpty(), () -> "@" + merge.by()))
                        .append(TimeText.ago(Duration.between(merge.at(), now)))
                        .mkString(" · ")));
    }

    private List<Line> details(ShownPullRequest shown, Option<ForgejoMergeStyle> style, Instant now) {
        ForgejoPullRequestDetails pr = shown.pullRequest();
        return List.of(
                        Line.wrapped(pr.title(), Theme.Classes.BOLD, WIDTH, TITLE_LINES),
                        Line.blank(),
                        Line.labelled("forgejo.dialog.pullRequest.state", text(state(pr))))
                .appendAll(Option.when(
                        !pr.author().isEmpty(),
                        () -> Line.labelled("forgejo.dialog.pullRequest.author", text("@" + pr.author()))))
                .append(Line.labelled(
                        "forgejo.dialog.pullRequest.branch",
                        text(pr.branches().head().name() + " → "
                                + pr.branches().base().name())))
                // In the line GitHub's has for the stack, as GitHub's shows its auto-merge (ADR 112).
                .appendAll(shown.scheduled().map(merge -> scheduled(merge, style, now)))
                .appendAll(List.of(
                        Line.labelled(
                                "forgejo.dialog.pullRequest.created",
                                text(when(pr.activity().createdAt(), now))),
                        Line.labelled(
                                "forgejo.dialog.pullRequest.updated",
                                text(when(pr.activity().updatedAt(), now))),
                        Line.labelled(
                                "forgejo.dialog.pullRequest.changes",
                                text("+" + pr.size().additions()).addClass(Theme.Classes.DIFF_ADDED),
                                text(" −" + pr.size().deletions()).addClass(Theme.Classes.DIFF_REMOVED),
                                text(" "
                                        + Messages.get(
                                                "forgejo.dialog.pullRequest.changes.files",
                                                pr.size().changedFiles())))))
                .appendAll(mergeable(pr))
                .appendAll(List.of(
                        Line.blank(), Line.labelled("forgejo.dialog.pullRequest.review", review(shown.review()))))
                .appendAll(Option.when(
                        !pr.conversation().reviewers().isEmpty(),
                        () -> Line.labelled("forgejo.dialog.pullRequest.reviewers", reviewers(pr))))
                .append(Line.labelled(
                        "forgejo.dialog.pullRequest.comments",
                        text(String.valueOf(pr.conversation().comments()))))
                .appendAll(Option.when(
                        !pr.conversation().labels().isEmpty(),
                        () -> Line.labelled(
                                "forgejo.dialog.pullRequest.labels",
                                text(pr.conversation().labels().mkString(", ")))))
                .append(Line.blank())
                .appendAll(checks(shown.checks()));
    }

    private static String state(ForgejoPullRequestDetails pr) {
        String state = Messages.get(
                switch (pr.status().state()) {
                    case OPEN -> "forgejo.dialog.pullRequest.state.open";
                    case CLOSED -> "forgejo.dialog.pullRequest.state.closed";
                    case MERGED -> "forgejo.dialog.pullRequest.state.merged";
                });
        return pr.status().draft() ? state + " · " + Messages.get("forgejo.dialog.pullRequest.draft") : state;
    }

    /**
     * Whether it merges, for an open pull request that is no draft: Forgejo says it does not for a draft, while it
     * checks for conflicts, and for one merged or closed, so that "no" is all it tells, not a conflict.
     */
    private static Option<Line> mergeable(ForgejoPullRequestDetails pr) {
        return Option.when(
                pr.status().state() == ForgejoPullRequestDetails.State.OPEN
                        && !pr.status().draft(),
                () -> Line.labelled(
                        "forgejo.dialog.pullRequest.mergeable",
                        text(Messages.get(
                                pr.status().mergeable()
                                        ? "forgejo.dialog.pullRequest.mergeable.yes"
                                        : "forgejo.dialog.pullRequest.mergeable.no"))));
    }

    private static List<StyledElement<?>> review(Option<Verdict> review) {
        return review.map(verdict -> switch (verdict) {
                    case CHANGES_REQUESTED ->
                        List.<StyledElement<?>>of(
                                ReviewSymbols.CHANGES.element(),
                                text(" " + Messages.get("forgejo.dialog.pullRequest.review.changes")));
                    default ->
                        List.<StyledElement<?>>of(
                                ReviewSymbols.APPROVED.element(),
                                text(" " + Messages.get("forgejo.dialog.pullRequest.review.approved")));
                })
                .getOrElse(() -> List.of(text(Messages.get("forgejo.dialog.pullRequest.review.none"))
                        .addClass(Theme.Classes.DIM)));
    }

    /**
     * Each reviewer asked, after the symbol of a review still to do; two spaces in between.
     */
    private static List<StyledElement<?>> reviewers(ForgejoPullRequestDetails pr) {
        return pr.conversation()
                .reviewers()
                .map(login ->
                        List.<StyledElement<?>>of(ReviewSymbols.TO_REVIEW.element(), text(" "), text("@" + login)))
                .zipWithIndex()
                .flatMap(indexed -> indexed._2 > 0 ? indexed._1.prepend(text("  ")) : indexed._1);
    }

    /**
     * How many builds failed, are running and passed; then the failed ones by name, and the running ones.
     */
    private static List<Line> checks(CommitStatus status) {
        List<CommitStatus.Status> all = status.statuses();
        if (all.isEmpty()) {
            return List.of(Line.labelled(
                    "forgejo.dialog.pullRequest.checks",
                    text(Messages.get("forgejo.dialog.pullRequest.checks.none")).addClass(Theme.Classes.DIM)));
        }
        List<CommitStatus.Status> notPassed = all.filter(check -> check.state() == CommitStatus.State.FAILED)
                .appendAll(all.filter(check -> check.state() == CommitStatus.State.RUNNING));
        return List.of(Line.labelled(
                        "forgejo.dialog.pullRequest.checks",
                        CheckSymbols.counts(
                                all.count(check -> check.state() == CommitStatus.State.FAILED),
                                all.count(check -> check.state() == CommitStatus.State.RUNNING),
                                all.count(check -> check.state() == CommitStatus.State.PASSED))))
                .appendAll(notPassed
                        .take(LISTED_CHECKS)
                        .map(check -> Line.indented(CheckSymbols.named(
                                check.state() == CommitStatus.State.FAILED ? CheckSymbols.FAILED : CheckSymbols.RUNNING,
                                check.context(),
                                CHECK_WIDTH))))
                .appendAll(Option.when(
                        notPassed.size() > LISTED_CHECKS,
                        () -> Line.of(text(INDENT
                                        + Messages.get(
                                                "forgejo.dialog.pullRequest.checks.more",
                                                String.valueOf(notPassed.size() - LISTED_CHECKS)))
                                .addClass(Theme.Classes.DIM))));
    }

    private String when(Instant at, Instant now) {
        return Messages.get(
                "forgejo.dialog.pullRequest.when",
                LocalDate.ofInstant(at, clock.getZone()).toString(),
                TimeText.ago(Duration.between(at, now)));
    }
}
