package net.vanfleteren.jazygit.feature.github.review;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.Tuple3;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.collection.Set;
import io.vavr.collection.Vector;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.util.function.Function;
import net.vanfleteren.jazygit.feature.diff.CommitFilesCmd;
import net.vanfleteren.jazygit.feature.diff.CommitFilesCmd.LoadReviewRemarks;
import net.vanfleteren.jazygit.feature.github.browse.BrowseRemarkCmd;
import net.vanfleteren.jazygit.feature.github.review.ReviewCmd.DeleteComment;
import net.vanfleteren.jazygit.feature.github.review.ReviewCmd.EditComment;
import net.vanfleteren.jazygit.feature.github.review.ReviewCmd.React;
import net.vanfleteren.jazygit.feature.github.review.ReviewCmd.Resolve;
import net.vanfleteren.jazygit.feature.github.review.ReviewCmd.Send;
import net.vanfleteren.jazygit.feature.github.review.ReviewPopup.Target;
import net.vanfleteren.jazygit.feature.github.review.ReviewPopup.Where;
import net.vanfleteren.jazygit.gh.model.NewReviewThread;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails;
import net.vanfleteren.jazygit.gh.model.PullRequestLink;
import net.vanfleteren.jazygit.gh.model.Reaction;
import net.vanfleteren.jazygit.gh.model.ReviewThread;
import net.vanfleteren.jazygit.git.model.PullRequestCommits;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.model.UnifiedDiff.DiffLine;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.*;
import net.vanfleteren.jazygit.state.AppError.CannotReview.Why;
import net.vanfleteren.jazygit.state.Update.Next;
import net.vanfleteren.jazygit.state.diff.*;
import net.vanfleteren.jazygit.state.operation.*;
import net.vanfleteren.jazygit.state.view.*;

/**
 * Commenting on the lines of the diff of a pull request's files from its cursor (ADR 60). A thread is started, or
 * replied to, from a popup that stays open until the comment is sent, so one that fails to send is not lost; a thread
 * is resolved at once. Each is sent on its own, and the review threads are read again once it is, the cursor then on
 * the thread started. Nothing is sent for a pull request whose threads were read at other commits than its files, nor
 * for a merged one; nor twice for a thread while something is sent for it. A comment is deleted after asking, at any
 * commits, and taken out of the threads once GitHub took it, without reading them again (ADR 64); one is edited in the
 * same popup as a reply, at any commits too, and takes what GitHub holds once it is saved, without reading again either
 * (ADR 65).
 */
public final class ReviewUpdate {

    // How many lines of the last comment of a thread the popup of a reply shows.
    private static final int QUOTED = 6;

    private ReviewUpdate() {}

    public static Next update(Model model, ReviewMsg msg) {
        return switch (msg) {
            case ReviewMsg.StartAsked(DiffCursorAt at) -> asked(model, start(model, at));
            case ReviewMsg.ReplyAsked(DiffCursorAt at) -> asked(model, reply(model, at));
            case ReviewMsg.EditAsked(DiffCursorAt at) -> asked(model, edit(model, at));
            case ReviewMsg.ResolveAsked(DiffCursorAt at) -> resolveAsked(model, at);
            case ReviewMsg.ThreadToggled(DiffCursorAt at) -> threadToggled(model, at);
            case ReviewMsg.OpenAsked(DiffCursorAt at) -> openAsked(model, at);
            case ReviewMsg.Refused(Why why) -> Next.of(refused(model, why));
            case ReviewMsg.Edited(String text) -> typed(model, text);
            case ReviewMsg.Confirmed() -> confirmed(model);
            case ReviewMsg.Cancelled() -> cancelled(model);
            case ReviewMsg.EditorRequested() -> editorRequested(model);
            case ReviewMsg.EditorClosed(String text, var commands) -> editorClosed(model, text, commands);
            case ReviewMsg.EditorFailed(Failure failure) -> editorFailed(model, failure);
            case ReviewMsg.Sent(Send cmd, var commands) -> sent(model, cmd, commands);
            case ReviewMsg.CommentEdited(Send cmd, String body, var commands) -> edited(model, cmd, body, commands);
            case ReviewMsg.SendFailed(Send cmd, Failure failure) -> sendFailed(model, cmd, failure);
            case ReviewMsg.Resolved(Resolve cmd, var commands) -> resolved(model, cmd, commands);
            case ReviewMsg.ResolveFailed(Resolve cmd, Failure failure) -> resolveFailed(model, cmd, failure);
            case ReviewMsg.DeleteAsked(DiffCursorAt at) -> deleteAsked(model, at);
            case ReviewMsg.DeleteConfirmed() -> deleteConfirmed(model);
            case ReviewMsg.CommentDeleted(DeleteComment cmd, var commands) -> commentDeleted(model, cmd, commands);
            case ReviewMsg.DeleteFailed(DeleteComment cmd, Failure failure) -> deleteFailed(model, cmd, failure);
            case ReviewMsg.ReactAsked(DiffCursorAt at) -> reactAsked(model, at);
            case ReviewMsg.ReactionChosen(Reaction reaction) -> reactionChosen(model, reaction);
            case ReviewMsg.Reacted(React cmd, var commands) -> reacted(model, cmd, commands);
            case ReviewMsg.ReactFailed(React cmd, Failure failure) -> reactFailed(model, cmd, failure);
        };
    }

    /**
     * Resolving the thread of the cursor {@code at}, or unresolving it when it is resolved, asked for.
     */
    private static Next resolveAsked(Model model, DiffCursorAt at) {
        return reviewed(model)
                .flatMap(view -> thread(view, at.cursor()))
                .flatMap(ReviewUpdate::notSending)
                .flatMap(ReviewUpdate::firstNotDeleted)
                .flatMap(found -> resolvable(found._2()).map(thread -> found))
                .fold(why -> Next.of(refused(model, why)), found -> {
                    Resolve resolve = new Resolve(
                            number(found._1()), found._2().id(), !found._2().resolved());
                    return Update.start(sending(model.clearError(), set -> set.add(resolve.thread())), resolve);
                });
    }

    /**
     * Collapsing the thread or the comment of the cursor {@code at}, or opening it. Collapsing a thread or a comment
     * needs no more than the thread: a merged pull request's too. The first comment of a thread is the thread (ADR 62).
     * Off a thread, Space does nothing: there is nothing to refuse.
     */
    private static Next threadToggled(Model model, DiffCursorAt at) {
        return pullRequestFiles(model)
                .toEither(Why.NOT_READ)
                .flatMap(view -> thread(view, at.cursor()))
                .flatMap(ReviewUpdate::notSending)
                .filterOrElse(
                        found -> !comment(found._2(), at.cursor())
                                .exists(comment -> found._1().deleting().contains(comment.id())),
                        found -> Why.DELETING)
                .fold(
                        why -> Next.of(why == Why.NOT_ON_THREAD ? model : refused(model, why)),
                        found -> toggled(model, found, at.cursor()));
    }

    /**
     * The comment of the cursor {@code at} opened on GitHub. Opening only reads: it is never refused, whatever the pull
     * request or its threads are at (ADR 85).
     */
    private static Next openAsked(Model model, DiffCursorAt at) {
        return pullRequestFiles(model)
                .flatMap(view -> link(view, at.cursor()))
                .map(link -> Update.start(model.clearError(), new BrowseRemarkCmd(link)))
                .getOrElse(() -> Next.of(model));
    }

    /**
     * {@code text} typed in the popup. While it is being sent, the comment stays as it is sent.
     */
    private static Next typed(Model model, String text) {
        return Next.of(changed(model, popup -> popup.withText(text), true));
    }

    /**
     * Sending what the popup holds, asked for, unless it is being sent already.
     */
    private static Next confirmed(Model model) {
        return editable(model).map(popup -> confirm(model, popup)).getOrElse(() -> Next.of(model));
    }

    /**
     * The popup closed, unless it is being sent.
     */
    private static Next cancelled(Model model) {
        return Next.of(editable(model)
                .map(popup -> model.withoutPopup(ReviewPopup.class))
                .getOrElse(model));
    }

    /**
     * The comment of the popup opened in an editor, unless it is being sent.
     */
    private static Next editorRequested(Model model) {
        return editable(model)
                .map(popup -> Next.of(model.clearError(), new EditComment(popup.text())))
                .getOrElse(() -> Next.of(model));
    }

    /**
     * The editor closed on {@code text}, which the popup then holds. Cancelled meanwhile, the popup stays closed.
     */
    private static Next editorClosed(Model model, String text, List<ExecutedCommand> commands) {
        return Next.of(changed(
                CommandLog.append(model, ReviewCmd.EDIT_COMMENT, commands), popup -> popup.withText(text), false));
    }

    /**
     * The editor failed: the failure says why.
     */
    private static Next editorFailed(Model model, Failure failure) {
        return Next.of(CommandLog.append(model, ReviewCmd.EDIT_COMMENT, failure.commands())
                .fail(new AppError.OperationFailed(ReviewCmd.EDIT_COMMENT, Option.none(), failure)));
    }

    /**
     * {@code cmd} failed to send. The popup stays open, with the comment, to send it again or to copy it.
     */
    private static Next sendFailed(Model model, Send cmd, Failure failure) {
        return OperationUpdate.update(model, new OperationMsg.Failed(cmd, failure))
                .map(failed -> changed(failed, popup -> popup.withSending(false), false))
                .map(back -> cmd.target() instanceof Target.Reply(ReviewThread.Id thread, var comment)
                        ? sending(back, set -> set.remove(thread))
                        : back);
    }

    /**
     * {@code cmd} resolved its thread. The thread says it is being sent until the threads are read again after it,
     * which shows it resolved.
     */
    private static Next resolved(Model model, Resolve cmd, List<ExecutedCommand> commands) {
        return OperationUpdate.succeeded(model, cmd, commands)
                .then(done -> Next.of(done, LoadReviewRemarks.after(done, cmd.number(), HashSet.of(cmd.thread()))));
    }

    /**
     * {@code cmd} failed to resolve its thread, which is no longer being sent.
     */
    private static Next resolveFailed(Model model, Resolve cmd, Failure failure) {
        return OperationUpdate.update(model, new OperationMsg.Failed(cmd, failure))
                .map(failed -> sending(failed, set -> set.remove(cmd.thread())));
    }

    /**
     * Deleting the comment of the cursor {@code at} asked for: asked about first.
     */
    private static Next deleteAsked(Model model, DiffCursorAt at) {
        return deletable(model, at.cursor())
                .fold(
                        why -> Next.of(refused(model, why)),
                        found -> Update.ask(
                                model,
                                new DeleteCommentPopup(
                                        number(found._1()), found._2().id(), found._3(), model.now())));
    }

    /**
     * Deleting the comment the popup asks about, confirmed, if it is open.
     */
    private static Next deleteConfirmed(Model model) {
        return model.popup(DeleteCommentPopup.class)
                .map(popup -> confirmDelete(model, popup))
                .getOrElse(() -> Next.of(model));
    }

    /**
     * {@code cmd} deleted its comment. Not read again: what GitHub did is known (ADR 64). The cursor on it goes to its
     * thread, or its line. Unless it was the last comment read of a thread GitHub has more of: which of those is its
     * first now, that a reply goes to, is not known until the threads are read again.
     */
    private static Next commentDeleted(Model model, DeleteComment cmd, List<ExecutedCommand> commands) {
        return OperationUpdate.succeeded(model, cmd, commands)
                .map(done -> mapPullRequest(
                        done,
                        cmd.number(),
                        view -> view.withDeleting(view.deleting().remove(cmd.comment()))))
                .then(done -> gone(done, cmd.number(), cmd.thread(), cmd.comment()));
    }

    /**
     * {@code cmd} failed to delete its comment, which is no longer being deleted.
     */
    private static Next deleteFailed(Model model, DeleteComment cmd, Failure failure) {
        return OperationUpdate.update(model, new OperationMsg.Failed(cmd, failure))
                .map(failed -> mapPullRequest(
                        failed,
                        cmd.number(),
                        view -> view.withDeleting(view.deleting().remove(cmd.comment()))));
    }

    /**
     * Reacting to the comment of the cursor {@code at} asked for: the reaction is chosen first. Reacting does not
     * number a line: it is done at whatever commits, on a merged pull request too (ADR 86).
     */
    private static Next reactAsked(Model model, DiffCursorAt at) {
        return reactable(model, at.cursor())
                .fold(
                        why -> Next.of(refused(model, why)),
                        found -> Update.ask(
                                model,
                                new ReactionPopup(
                                        number(found._1()),
                                        found._2().id(),
                                        found._3().id(),
                                        found._3().reactions())));
    }

    /**
     * {@code reaction} chosen in the popup, if it is open.
     */
    private static Next reactionChosen(Model model, Reaction reaction) {
        return model.popup(ReactionPopup.class)
                .map(popup -> confirmReaction(model, popup, reaction))
                .getOrElse(() -> Next.of(model));
    }

    /**
     * {@code cmd} reacted to its comment. Not read again: what GitHub did is known, as for an edit (ADR 65).
     */
    private static Next reacted(Model model, React cmd, List<ExecutedCommand> commands) {
        return OperationUpdate.succeeded(model, cmd, commands)
                .map(done -> mapPullRequest(
                        done,
                        cmd.number(),
                        view -> view.withReacted(cmd.comment(), cmd.reaction(), cmd.given())
                                .withReacting(view.reacting().remove(cmd.comment()))));
    }

    /**
     * {@code cmd} failed to react to its comment. A comment GitHub no longer has was deleted, as by someone else: it
     * leaves the threads, as one deleted here does (ADR 64), and the failure says so.
     */
    private static Next reactFailed(Model model, React cmd, Failure failure) {
        Next failed = OperationUpdate.update(model, new OperationMsg.Failed(cmd, failure))
                .map(done -> mapPullRequest(
                        done,
                        cmd.number(),
                        view -> view.withReacting(view.reacting().remove(cmd.comment()))));
        return failure.gone() ? failed.then(done -> gone(done, cmd.number(), cmd.thread(), cmd.comment())) : failed;
    }

    /**
     * Sending what {@code popup} holds, asked for. A blank comment is not sent, as GitHub refuses it; a blank edit says
     * that deleting it is what empties it, and an edit that changed nothing closes the popup, sending nothing (ADR 65).
     */
    private static Next confirm(Model model, ReviewPopup popup) {
        return switch (popup.target()) {
            case Target.Edit(var _, var _, String body)
            when popup.text().equals(body) -> Next.of(model.clearError().withoutPopup(ReviewPopup.class));
            case Target.Edit _ when popup.text().isBlank() -> Next.of(refused(model, Why.EMPTY));
            case Target.Edit _ -> send(model, popup);
            case Target _ when popup.text().isBlank() -> Next.of(model);
            case Target _ -> send(model, popup);
        };
    }

    /**
     * Sending what {@code popup} holds, which is worth sending.
     */
    private static Next send(Model model, ReviewPopup popup) {
        // An edit does not mark its thread as being sent, as a reply does: the popup takes every key while it is
        // saved, so nothing else is sent for the thread meanwhile (ADR 65).
        Model sending = model.clearError().openPopup(popup.withSending(true));
        return Update.start(
                popup.target() instanceof Target.Reply(ReviewThread.Id thread, var comment)
                        ? sending(sending, set -> set.add(thread))
                        : sending,
                new Send(popup.number(), popup.target(), popup.text()));
    }

    private static Next asked(Model model, Either<Why, ReviewPopup> popup) {
        return popup.fold(why -> Next.of(refused(model, why)), open -> Update.ask(model, open));
    }

    /**
     * The popup that starts a thread on the line or the range of the cursor {@code at}, or on the bar of a file: the
     * first line of a range is the one the diff shows first, and both are in one hunk.
     */
    private static Either<Why, ReviewPopup> start(Model model, DiffCursorAt at) {
        return reviewed(model).flatMap(view -> startIn(model, view, at));
    }

    private static Either<Why, ReviewPopup> startIn(Model model, CommitFilesView view, DiffCursorAt at) {
        Sha commit = view.of().sha();
        if (model.repoView()
                .version(at.area(), at.at().content())
                .flatMap(Version::showing)
                .isDefined()) {
            return Either.left(Why.IN_VERSION);
        }
        if (at.mark().isEmpty() && at.cursor() instanceof Anchor.File(String path)) {
            return Either.right(popup(
                    view,
                    at,
                    new Target.Start(commit, path, Option.none()),
                    new Where(path, Option.none(), false),
                    List.empty()));
        }
        if (!(at.mark().getOrElse(at.cursor()) instanceof Anchor.OnLine from)
                || !(at.cursor() instanceof Anchor.OnLine cursor)) {
            return Either.left(Why.NOT_ON_LINE);
        }
        return diffLines(model, view, at)
                .between(from, cursor)
                .filter(range -> range.head().hunk().equals(range.last().hunk()))
                .toEither(Why.NOT_ONE_HUNK)
                .map(range -> startOn(view, at, commit, range));
    }

    /**
     * The popup that starts a thread at {@code commit} on {@code range}, lines of one hunk.
     */
    private static ReviewPopup startOn(
            CommitFilesView view, DiffCursorAt at, Sha commit, Vector<DiffLines.Line> range) {
        Anchor.OnLine first = range.head().anchor();
        Anchor.OnLine last = range.last().anchor();
        Option<NewReviewThread.Lines> lines =
                Option.of(new NewReviewThread.Lines(first.side(), first.number(), last.side(), last.number()));
        return popup(
                view,
                at,
                new Target.Start(commit, first.path(), lines),
                new Where(first.path(), Option.of(new ReviewPopup.Lines(first.number(), last.number())), false),
                quoted(range));
    }

    /**
     * The popup that replies to the thread of the cursor {@code at}.
     */
    private static Either<Why, ReviewPopup> reply(Model model, DiffCursorAt at) {
        return reviewed(model)
                .flatMap(view -> thread(view, at.cursor()))
                .flatMap(ReviewUpdate::notSending)
                .flatMap(ReviewUpdate::firstNotDeleted)
                .filterOrElse(found -> found._2().rights().reply(), found -> Why.NO_REPLY)
                // A thread whose comments read were all deleted has no first comment to reply to until it is read
                // again, which it is (ADR 64).
                .flatMap(found -> found._2()
                        .comments()
                        .headOption()
                        .toEither(Why.NOT_READ)
                        .map(first -> Tuple.of(found._1(), found._2(), first)))
                .map(found -> popup(
                        found._1(),
                        at,
                        new Target.Reply(found._2().id(), found._3().restId()),
                        new Where(found._2().path(), Option.none(), true),
                        found._2()
                                .comments()
                                .lastOption()
                                .map(ReviewUpdate::excerpt)
                                .getOrElse(List.empty())));
    }

    /**
     * The popup that edits the comment of the cursor {@code at}, opened on its body (ADR 65): under the lines of its
     * thread, as starting it showed them, or under the comment before it.
     */
    private static Either<Why, ReviewPopup> edit(Model model, DiffCursorAt at) {
        return chosen(model, at.cursor())
                .filterOrElse(found -> found._3().rights().edit(), found -> Why.NO_EDIT)
                .map(found -> {
                    ReviewThread thread = found._2();
                    ReviewThread.Comment comment = found._3();
                    List<ReviewThread.Comment> before = thread.comments().takeWhile(each -> !each.equals(comment));
                    // What is written on GitHub's site ends its lines with \r\n, whose \r the text area keeps: an
                    // edit would send it back where it was not typed. Unchanged is told against the same text.
                    String body = comment.body().replace("\r\n", "\n");
                    return popup(
                                    found._1(),
                                    at,
                                    new Target.Edit(thread.id(), comment.id(), body),
                                    new Where(
                                            thread.path(),
                                            thread.line()
                                                    .map(line -> new ReviewPopup.Lines(
                                                            thread.startLine().getOrElse(line), line)),
                                            false),
                                    before.lastOption()
                                            .map(ReviewUpdate::excerpt)
                                            .getOrElse(() -> lines(model, found._1(), at, thread)))
                            .withText(body);
                });
    }

    /**
     * {@code comment} as the popup shows it above another: who wrote it, and its first lines.
     */
    private static List<String> excerpt(ReviewThread.Comment comment) {
        return List.ofAll(comment.body().lines()).take(QUOTED).prepend(comment.author() + ":");
    }

    /**
     * The lines of the diff {@code thread} is on, as the popup that starts one shows them, with the lines expanded in
     * the area of the cursor {@code at} (ADR 77); none when the diff does not show them, as for an outdated thread, or
     * one on a whole file.
     */
    private static List<String> lines(Model model, CommitFilesView view, DiffCursorAt at, ReviewThread thread) {
        return thread.line()
                .flatMap(line -> diffLines(model, view, at)
                        .between(
                                new Anchor.OnLine(
                                        thread.path(),
                                        thread.startSide(),
                                        thread.startLine().getOrElse(line)),
                                new Anchor.OnLine(thread.path(), thread.side(), line)))
                .map(ReviewUpdate::quoted)
                .getOrElse(List.empty());
    }

    private static ReviewPopup popup(
            CommitFilesView view, DiffCursorAt at, Target target, Where where, List<String> quoted) {
        return new ReviewPopup(number(view), target, where, quoted, "", false, at.area(), at.at());
    }

    /**
     * {@code thread} if the viewer may resolve it, or unresolve it when it is resolved.
     */
    private static Either<Why, ReviewThread> resolvable(ReviewThread thread) {
        boolean may = thread.resolved()
                ? thread.rights().unresolve()
                : thread.rights().resolve();
        return may ? Either.right(thread) : Either.left(thread.resolved() ? Why.NO_UNRESOLVE : Why.NO_RESOLVE);
    }

    /**
     * {@code cmd} sent: the popup closes, the threads are read again, and the cursor goes to the last line of the
     * thread started, if one was, which the thread is drawn right after, its range dropped. GitHub does not say which
     * thread a comment started.
     */
    private static Next sent(Model model, Send cmd, List<ExecutedCommand> commands) {
        Set<ReviewThread.Id> settled = cmd.target() instanceof Target.Reply(ReviewThread.Id thread, var comment)
                ? HashSet.of(thread)
                : HashSet.empty();
        return OperationUpdate.succeeded(model, cmd, commands)
                .map(done -> closedOnSent(done, cmd))
                .then(moved -> Next.of(moved, LoadReviewRemarks.after(moved, cmd.number(), settled)));
    }

    /**
     * {@code model} with the popup that sent {@code cmd} closed, and the cursor on the last line of the thread it
     * started, if it did.
     */
    private static Model closedOnSent(Model model, Send cmd) {
        Option<ReviewPopup> popup =
                model.popup(ReviewPopup.class).filter(open -> open.sending() && open.number() == cmd.number());
        Model closed = popup.map(open -> model.withoutPopup(ReviewPopup.class)).getOrElse(model);
        return popup.flatMap(open -> lastLine(open.target())
                        .map(line -> closed.withScrolled(closed.scrolled()
                                .put(
                                        open.area(),
                                        new Scrolled(
                                                open.at().content(),
                                                open.at().row(),
                                                open.at().column(),
                                                Option.of(line),
                                                Option.none())))))
                .getOrElse(closed);
    }

    /**
     * The edit {@code cmd} saved, as GitHub holds it, {@code body}: the popup closes, and the comment says it, at a
     * newer version, without the threads read again (ADR 65). The cursor stays on it.
     */
    private static Next edited(Model model, Send cmd, String body, List<ExecutedCommand> commands) {
        return OperationUpdate.succeeded(model, cmd, commands)
                .map(done -> done.popup(ReviewPopup.class)
                        .filter(open -> open.sending() && open.number() == cmd.number())
                        .map(open -> done.withoutPopup(ReviewPopup.class))
                        .getOrElse(done))
                .map(closed ->
                        cmd.target() instanceof Target.Edit(var thread, ReviewThread.Comment.Id comment, var original)
                                ? mapPullRequest(closed, cmd.number(), view -> view.withEdited(comment, body))
                                : closed);
    }

    /**
     * The comment the cursor {@code on} is on, of the files listed, with its thread and those files, if the viewer may
     * delete it now (ADR 64).
     */
    private static Either<Why, Tuple3<CommitFilesView, ReviewThread, ReviewThread.Comment>> deletable(
            Model model, Anchor on) {
        return chosen(model, on).filterOrElse(found -> found._3().rights().delete(), found -> Why.NO_DELETE);
    }

    /**
     * The comment the cursor {@code on} is on, of the files listed, with its thread and those files, if it may be
     * deleted or edited now, as the viewer's rights allow (ADR 64, ADR 65): the comment of the cursor, or the first of
     * a thread, which its stop is; on a collapsed thread only when it has no other, as which is meant is not shown. Not
     * one being deleted, nor of a thread something is sent for, as a reply to its first comment, whatever the commits
     * it was read at.
     */
    private static Either<Why, Tuple3<CommitFilesView, ReviewThread, ReviewThread.Comment>> chosen(
            Model model, Anchor on) {
        return pullRequestFiles(model)
                .filter(view ->
                        view.remarks().exists(remarks -> remarks.toOption().isDefined()))
                .toEither(Why.NOT_READ)
                .flatMap(view -> thread(view, on))
                .flatMap(ReviewUpdate::notSending)
                .filterOrElse(
                        found -> on instanceof Anchor.Comment
                                || !found._1().collapsed().thread(found._2())
                                || found._2().comments().size() + found._2().more() == 1,
                        found -> Why.COLLAPSED)
                .flatMap(found -> comment(found._2(), on)
                        .toEither(Why.NOT_ON_THREAD)
                        .map(comment -> Tuple.of(found._1(), found._2(), comment)))
                .filterOrElse(
                        found -> !found._1().deleting().contains(found._3().id()), found -> Why.DELETING);
    }

    /**
     * {@code model} once the comment {@code comment} of the thread {@code thread} of pull request {@code number} is
     * gone from GitHub: taken out of the threads without reading them again (ADR 64). Unless it was the last comment
     * read of a thread GitHub has more of: which of those is its first now, that a reply goes to, is not known until
     * the threads are read again.
     */
    private static Next gone(Model model, int number, ReviewThread.Id thread, ReviewThread.Comment.Id comment) {
        Model deleted = mapPullRequest(model, number, view -> view.withoutComment(comment));
        boolean unread = pullRequestFiles(deleted)
                .filter(view -> view.pullRequest().contains(number))
                .exists(view -> view.threads()
                        .exists(each ->
                                each.id().equals(thread) && each.comments().isEmpty()));
        return unread ? Next.of(deleted, LoadReviewRemarks.after(deleted, number, HashSet.empty())) : Next.of(deleted);
    }

    /**
     * The comment the cursor {@code on} is on, with its thread and the files listed, if the viewer may react to it now
     * (ADR 86): chosen as for an edit or a delete, and not while a reaction to it is sent, which a second one, chosen
     * from what was read before, could undo.
     */
    private static Either<Why, Tuple3<CommitFilesView, ReviewThread, ReviewThread.Comment>> reactable(
            Model model, Anchor on) {
        return chosen(model, on)
                .filterOrElse(found -> found._3().rights().react(), found -> Why.NO_REACT)
                .filterOrElse(
                        found -> !found._1().reacting().contains(found._3().id()), found -> Why.REACTING);
    }

    /**
     * {@code reaction} chosen in {@code popup}: asked again as the comment is now, as the threads may have been read
     * again, the comment gone, or something be sent for it meanwhile. Whether it is given or taken back is told from
     * the reactions it has now, not those the popup opened with.
     */
    private static Next confirmReaction(Model model, ReactionPopup popup, Reaction reaction) {
        Anchor on = new Anchor.Comment(popup.comment(), new Anchor.Thread(popup.thread(), Option.none()));
        return reactable(model, on)
                .filterOrElse(found -> found._1().pullRequest().contains(popup.number()), found -> Why.NOT_READ)
                .fold(why -> Next.of(refused(model.withoutPopup(ReactionPopup.class), why)), found -> {
                    React react = new React(
                            popup.number(),
                            popup.thread(),
                            popup.comment(),
                            reaction,
                            !found._3().reactions().viewerGave(reaction));
                    return Update.confirm(model, ReactionPopup.class, open -> Option.of(react))
                            .map(started -> mapPullRequest(
                                    started,
                                    popup.number(),
                                    view -> view.withReacting(view.reacting().add(popup.comment()))));
                });
    }

    /**
     * Deleting the comment {@code popup} asks about, confirmed: asked again as it is now, as the comment may have gone,
     * or something be sent for its thread, meanwhile. It says it is being deleted until GitHub answers.
     */
    private static Next confirmDelete(Model model, DeleteCommentPopup popup) {
        Anchor on = new Anchor.Comment(popup.comment().id(), new Anchor.Thread(popup.thread(), Option.none()));
        return deletable(model, on)
                .filterOrElse(found -> found._1().pullRequest().contains(popup.number()), found -> Why.NOT_READ)
                .fold(
                        why -> Next.of(refused(model.withoutPopup(DeleteCommentPopup.class), why)),
                        found -> Update.confirm(
                                        model,
                                        DeleteCommentPopup.class,
                                        open -> Option.of(new DeleteComment(
                                                open.number(),
                                                open.thread(),
                                                open.comment().id())))
                                .map(started -> mapPullRequest(
                                        started,
                                        popup.number(),
                                        view -> view.withDeleting(
                                                view.deleting().add(found._3().id())))));
    }

    /**
     * The comment of {@code thread} the cursor {@code on} is on: the one it names, or its first, which its stop is.
     */
    private static Option<ReviewThread.Comment> comment(ReviewThread thread, Anchor on) {
        return on instanceof Anchor.Comment(ReviewThread.Comment.Id id, var _)
                ? thread.comments().find(comment -> comment.id().equals(id))
                : thread.comments().headOption();
    }

    /**
     * {@code found}, unless the first comment of its thread is being deleted, which a reply goes to (ADR 64).
     */
    private static Either<Why, Tuple2<CommitFilesView, ReviewThread>> firstNotDeleted(
            Tuple2<CommitFilesView, ReviewThread> found) {
        return found._2()
                        .comments()
                        .headOption()
                        .exists(first -> found._1().deleting().contains(first.id()))
                ? Either.left(Why.DELETING)
                : Either.right(found);
    }

    /**
     * The last line a thread to start is on.
     */
    private static Option<Anchor.OnLine> lastLine(Target target) {
        return target instanceof Target.Start(var ignored, String path, Option<NewReviewThread.Lines> lines)
                ? lines.map(on -> new Anchor.OnLine(path, on.side(), on.line()))
                : Option.none();
    }

    /**
     * A line of the diff as the popup shows it: with its marker.
     */
    private static String quoted(DiffLine line) {
        return switch (line.kind()) {
            case ADDED -> "+" + line.text();
            case REMOVED -> "-" + line.text();
            case CONTEXT -> " " + line.text();
            case NOTE -> line.text();
        };
    }

    /**
     * The lines of {@code range}, as the popup that starts a thread quotes them.
     */
    private static List<String> quoted(Vector<DiffLines.Line> range) {
        return range.map(line -> quoted(line.line())).toList();
    }

    /**
     * The lines the cursor {@code at} stops on in the diff the files of the pull request {@code view} shows, as the
     * content panel draws it: with the lines expanded in its area (ADR 77), so that a range stays in a hunk as shown.
     */
    private static DiffLines diffLines(Model model, CommitFilesView view, DiffCursorAt at) {
        return model.diffLines(
                at, view.diff().flatMap(preview -> preview.shown().value().toOption()));
    }

    /**
     * The files of the pull request listed, if they can be commented on: its threads read at the commits of its files,
     * so their lines number those the diff shows, and not merged.
     */
    private static Either<Why, CommitFilesView> reviewed(Model model) {
        return pullRequestFiles(model)
                .filter(view ->
                        view.remarks().exists(remarks -> remarks.toOption().isDefined()))
                .toEither(Why.NOT_READ)
                .filterOrElse(view -> !view.ended().contains(PullRequestDetails.State.MERGED), view -> Why.MERGED)
                .filterOrElse(CommitFilesView::remarksAtItsCommits, view -> Why.MOVED_ON);
    }

    /**
     * {@code found} with the thread, or the comment of it the cursor is {@code on}, collapsed or opened, which is kept
     * for the next session (ADR 63).
     */
    private static Next toggled(Model model, Tuple2<CommitFilesView, ReviewThread> found, Anchor on) {
        CommitFilesView view = found._1();
        Collapsed collapsed = view.collapsed()
                .flipped(
                        on instanceof Anchor.Comment(ReviewThread.Comment.Id id, var _)
                                ? Collapsed.Key.of(id)
                                : Collapsed.Key.of(found._2().id()),
                        view.threads());
        return new Next(
                mapView(model, files -> files.withCollapsed(collapsed)),
                view.of() instanceof Changes.OfPullRequest(PullRequestCommits commits)
                        ? List.of(new CommitFilesCmd.SaveCollapsed(
                                commits.repository(), commits.number(), collapsed.toggled(), model.now()))
                        : List.empty());
    }

    /**
     * Where the pull request {@code view} lists the files of is on GitHub, on the comment the cursor {@code on} is on,
     * if any: the reply it names, or the first of its thread (ADR 85).
     */
    private static Option<PullRequestLink> link(CommitFilesView view, Anchor on) {
        return view.of() instanceof Changes.OfPullRequest(PullRequestCommits commits)
                ? Option.of(new PullRequestLink(
                        commits.host(),
                        commits.repository(),
                        commits.number(),
                        thread(view, on)
                                .toOption()
                                .flatMap(found -> comment(found._2(), on))
                                .map(ReviewThread.Comment::restId)))
                : Option.none();
    }

    private static Option<CommitFilesView> pullRequestFiles(Model model) {
        return model.repoView().commitFiles().filter(view -> view.pullRequest().isDefined());
    }

    /**
     * The thread the cursor {@code on} is on, or on a comment of, with the files it is of.
     */
    private static Either<Why, Tuple2<CommitFilesView, ReviewThread>> thread(CommitFilesView view, Anchor on) {
        Option<ReviewThread.Id> id = switch (on) {
            case Anchor.Thread thread -> Option.of(thread.id());
            case Anchor.Comment(var _, Anchor.Thread thread) -> Option.of(thread.id());
            default -> Option.none();
        };
        return id.flatMap(found -> view.threads().find(thread -> thread.id().equals(found)))
                .toEither(Why.NOT_ON_THREAD)
                .map(thread -> Tuple.of(view, thread));
    }

    /**
     * {@code found}, unless something is sent for its thread: a second one would act on what was read before.
     */
    private static Either<Why, Tuple2<CommitFilesView, ReviewThread>> notSending(
            Tuple2<CommitFilesView, ReviewThread> found) {
        return found._1().sending().contains(found._2().id()) ? Either.left(Why.SENDING) : Either.right(found);
    }

    private static int number(CommitFilesView view) {
        return view.pullRequest().getOrElse(0);
    }

    private static Model refused(Model model, Why why) {
        return model.fail(new AppError.CannotReview(why));
    }

    private static Model mapView(Model model, Function<CommitFilesView, CommitFilesView> f) {
        return model.mapRepoView(repoView -> repoView.withCommitFiles(
                repoView.commitFiles().map(view -> view.pullRequest().isDefined() ? f.apply(view) : view)));
    }

    /**
     * {@code model} with the files of pull request {@code number} as {@code f} makes them, while they are listed.
     */
    private static Model mapPullRequest(Model model, int number, Function<CommitFilesView, CommitFilesView> f) {
        return mapView(model, view -> view.pullRequest().contains(number) ? f.apply(view) : view);
    }

    private static Model sending(Model model, Function<Set<ReviewThread.Id>, Set<ReviewThread.Id>> f) {
        return mapView(model, view -> view.withSending(f.apply(view.sending())));
    }

    /**
     * The review popup, if it is open and not being sent.
     */
    private static Option<ReviewPopup> editable(Model model) {
        return model.popup(ReviewPopup.class).filter(popup -> !popup.sending());
    }

    /**
     * {@code model} with the review popup as {@code change} makes it, if it is open, and not being sent when
     * {@code editable}.
     */
    private static Model changed(Model model, Function<ReviewPopup, ReviewPopup> change, boolean editable) {
        return model.popup(ReviewPopup.class)
                .filter(popup -> !editable || !popup.sending())
                .map(popup -> model.openPopup(change.apply(popup)))
                .getOrElse(model);
    }
}
