package net.vanfleteren.jazygit.feature.github.verdict;

import io.vavr.Tuple;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.util.function.Function;
import net.vanfleteren.jazygit.feature.diff.CommitFilesCmd.LoadReviewRemarks;
import net.vanfleteren.jazygit.feature.github.comment.CommentCmd;
import net.vanfleteren.jazygit.feature.github.show.PullRequestPopup;
import net.vanfleteren.jazygit.feature.github.show.PullRequestUpdate;
import net.vanfleteren.jazygit.feature.github.verdict.VerdictCmd.EditReview;
import net.vanfleteren.jazygit.feature.github.verdict.VerdictCmd.SubmitReview;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Reviewing;
import net.vanfleteren.jazygit.gh.model.ReviewRemarks;
import net.vanfleteren.jazygit.gh.model.Verdict;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.AppError;
import net.vanfleteren.jazygit.state.AppError.CannotReview.Why;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;
import net.vanfleteren.jazygit.state.github.Reading;
import net.vanfleteren.jazygit.state.operation.CommandLog;
import net.vanfleteren.jazygit.state.operation.OperationMsg;
import net.vanfleteren.jazygit.state.operation.OperationUpdate;
import net.vanfleteren.jazygit.state.view.CommitFilesView;

/**
 * Approving a pull request, or asking for changes (ADR 100), from its dialog or from the diff of its files. Only an
 * open pull request is reviewed, not by who opened it, nor while they have a pending review; the dialog leaves its
 * choice out when it may not be, but for a pending review, which it does not know of until it is asked. The text is
 * written in a popup of its own, or in the editor, and sent from that popup, which stays open until it is sent, so a
 * review that fails to send is not lost. Once it is sent, the popup it was opened from comes back, the dialog, with
 * its details read again; or, from the diff, its remarks are read again.
 */
public final class VerdictUpdate {

    private VerdictUpdate() {}

    public static Next update(Model model, VerdictMsg msg) {
        return switch (msg) {
            case VerdictMsg.DialogAsked() ->
                model.popup(PullRequestPopup.class)
                        .flatMap(dialog -> dialog.readDetails().map(details -> fromDialog(model, dialog, details)))
                        .getOrElse(() -> Next.of(model));
            case VerdictMsg.DiffAsked() ->
                fromDiff(model).fold(why -> Next.of(refused(model, why)), popup -> Update.ask(model, popup));
            case VerdictMsg.Chosen(Verdict verdict) ->
                Next.of(changed(model, popup -> popup.withVerdict(verdict), true));
            // While it is being sent, the text stays as it is sent.
            case VerdictMsg.Edited(String text) -> Next.of(changed(model, popup -> popup.withText(text), true));
            case VerdictMsg.Confirmed() ->
                editable(model).map(popup -> confirmed(model, popup)).getOrElse(() -> Next.of(model));
            case VerdictMsg.Cancelled() ->
                Next.of(editable(model).map(popup -> back(model, popup)).getOrElse(model));
            case VerdictMsg.EditorRequested() ->
                editable(model)
                        .map(popup -> Next.of(model.clearError(), new EditReview(popup.text())))
                        .getOrElse(() -> Next.of(model));
            // Cancelled meanwhile, the popup stays closed.
            case VerdictMsg.EditorClosed(String text, var commands) ->
                Next.of(changed(
                        CommandLog.append(model, CommentCmd.EDIT_COMMENT, commands),
                        popup -> popup.withText(text),
                        false));
            case VerdictMsg.EditorFailed(Failure failure) ->
                Next.of(CommandLog.append(model, CommentCmd.EDIT_COMMENT, failure.commands())
                        .fail(new AppError.OperationFailed(CommentCmd.EDIT_COMMENT, Option.none(), failure)));
            case VerdictMsg.Sent(SubmitReview cmd, var commands) -> sent(model, cmd, commands);
            // The popup stays open, with the text, to send it again or to copy it.
            case VerdictMsg.SendFailed(SubmitReview cmd, Failure failure) ->
                OperationUpdate.update(model, new OperationMsg.Failed(cmd, failure))
                        .map(failed -> changed(failed, popup -> popup.withSending(false), false));
        };
    }

    /**
     * Whether the pull request {@code dialog} shows may be reviewed by the viewer, as far as its dialog tells before
     * asking: once read, open, and not theirs. A pending review is only told once asked.
     */
    public static boolean reviewable(PullRequestPopup dialog) {
        return dialog.readDetails()
                .exists(details -> mayReview(
                                details.status().state(), details.conversation().reviewing())
                        .isRight());
    }

    /**
     * {@code model} with the review popup {@code was} as the dialog behind it changed it, {@code now}. Once the viewer
     * no longer has a pending review, the error that said they had goes, so the review can be sent.
     */
    public static Model followed(Model model, VerdictPopup was, VerdictPopup now) {
        boolean settled = was.pending()
                && !now.pending()
                && model.outcome().error().contains(new AppError.CannotReview(Why.PENDING_REVIEW));
        return (settled ? model.clearError() : model).openPopup(now);
    }

    /**
     * The popup asking for a review from the dialog, which tells why it cannot be sent while the viewer has a pending
     * review: the dialog shows no error of its own.
     */
    private static Next fromDialog(Model model, PullRequestPopup dialog, PullRequestDetails details) {
        Reviewing reviewing = details.conversation().reviewing();
        return mayReview(details.status().state(), reviewing).fold(why -> Next.of(model), may -> {
            Next asked = Update.ask(model, VerdictPopup.of(dialog, reviewing));
            return reviewing.pending() ? Next.of(refused(asked.model(), Why.PENDING_REVIEW)) : asked;
        });
    }

    /**
     * The popup asking for a review of the pull request whose files the diff shows, once its remarks are read, which
     * tell how it may be reviewed.
     */
    private static Either<Why, VerdictPopup> fromDiff(Model model) {
        return model.repoView()
                .commitFiles()
                .filter(view -> view.pullRequest().isDefined())
                .flatMap(view ->
                        view.remarks().flatMap(remarks -> remarks.toOption()).map(remarks -> Tuple.of(view, remarks)))
                .toEither(Why.NOT_READ)
                .flatMap(found -> fromRemarks(found._1(), found._2()));
    }

    private static Either<Why, VerdictPopup> fromRemarks(CommitFilesView view, ReviewRemarks remarks) {
        Reviewing reviewing = remarks.reviewing();
        return mayReview(remarks.state(), reviewing)
                .filterOrElse(may -> !reviewing.pending(), may -> Why.PENDING_REVIEW)
                .map(may -> VerdictPopup.ofDiff(view.pullRequest().get(), reviewing, remarks.draft()));
    }

    /**
     * Whether a pull request in {@code state} may be reviewed by the viewer, as {@code reviewing} tells: a pending
     * review is told apart.
     */
    private static Either<Why, Reviewing> mayReview(PullRequestDetails.State state, Reviewing reviewing) {
        if (reviewing.pullRequest().isEmpty()) {
            return Either.left(Why.NOT_READ);
        }
        if (state != PullRequestDetails.State.OPEN) {
            return Either.left(state == PullRequestDetails.State.MERGED ? Why.MERGED : Why.NOT_OPEN);
        }
        return reviewing.authored() ? Either.left(Why.OWN_PULL_REQUEST) : Either.right(reviewing);
    }

    /**
     * Sending what {@code popup} holds: not while the viewer has a pending review, nor a request for changes without a
     * reason, which GitHub refuses. An approval may have no text. Text is sent as typed.
     */
    private static Next confirmed(Model model, VerdictPopup popup) {
        if (popup.pending()) {
            return Next.of(refused(model, Why.PENDING_REVIEW));
        }
        if (popup.verdict().needsText() && popup.text().isBlank()) {
            return Next.of(refused(model, Why.NO_REASON));
        }
        return Update.start(
                model.clearError().openPopup(popup.withSending(true)),
                new SubmitReview(popup.number(), popup.pullRequest(), popup.verdict(), popup.text()));
    }

    /**
     * {@code cmd} sent: the popup closes. Back on the dialog, its details are read again, so its review and reviewers
     * are current, quietly (ADR 34); from the diff, its remarks are read again, as {@code R} reads them.
     */
    private static Next sent(Model model, SubmitReview cmd, List<ExecutedCommand> commands) {
        Next done = OperationUpdate.succeeded(model, cmd, commands)
                .map(succeeded -> succeeded.mapGitHub(gitHub -> gitHub.withChanged(cmd.number())));
        return done.model()
                .popup(VerdictPopup.class)
                .filter(popup -> popup.sending() && popup.number() == cmd.number())
                .map(popup -> done.then(changed -> popup.from()
                        .map(from -> PullRequestUpdate.read(changed, cmd.number())
                                .apply((given, load) -> Next.of(
                                        given.openPopup(from.withPullRequestPopup(from.pullRequestPopup()
                                                .readingAgain(Reading.BY_ITSELF, model.now(), load.read()))),
                                        load)))
                        .getOrElse(() -> Next.of(
                                changed.withoutPopup(VerdictPopup.class),
                                LoadReviewRemarks.after(changed, cmd.number(), HashSet.empty())))))
                .getOrElse(done);
    }

    /**
     * {@code model} back where {@code popup} was opened from: the dialog, or the diff.
     */
    private static Model back(Model model, VerdictPopup popup) {
        return popup.from()
                .map(from -> model.clearError().openPopup(from))
                .getOrElse(() -> model.clearError().withoutPopup(VerdictPopup.class));
    }

    /**
     * The popup, if it is open and not being sent.
     */
    private static Option<VerdictPopup> editable(Model model) {
        return model.popup(VerdictPopup.class).filter(popup -> !popup.sending());
    }

    /**
     * {@code model} with the popup as {@code change} makes it, if it is open, and only while it is not being sent when
     * {@code onlyEditable}.
     */
    private static Model changed(Model model, Function<VerdictPopup, VerdictPopup> change, boolean onlyEditable) {
        return model.popup(VerdictPopup.class)
                .filter(popup -> !onlyEditable || !popup.sending())
                .map(popup -> model.openPopup(change.apply(popup)))
                .getOrElse(model);
    }

    private static Model refused(Model model, Why why) {
        return model.fail(new AppError.CannotReview(why));
    }
}
