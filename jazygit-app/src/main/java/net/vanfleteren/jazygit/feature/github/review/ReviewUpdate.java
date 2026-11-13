package net.vanfleteren.jazygit.feature.github.review;

import static net.vanfleteren.jazygit.feature.github.review.ReviewLookup.chosen;
import static net.vanfleteren.jazygit.feature.github.review.ReviewLookup.mapPullRequest;
import static net.vanfleteren.jazygit.feature.github.review.ReviewLookup.mapView;
import static net.vanfleteren.jazygit.feature.github.review.ReviewLookup.pullRequestFiles;
import static net.vanfleteren.jazygit.feature.github.review.ReviewLookup.refused;

import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.collection.Set;
import io.vavr.collection.Vector;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.util.function.Function;
import net.vanfleteren.jazygit.feature.diff.CommitFilesCmd.LoadReviewRemarks;
import net.vanfleteren.jazygit.feature.github.GitHubError.CannotReview.Why;
import net.vanfleteren.jazygit.feature.github.review.ReviewCmd.Resolve;
import net.vanfleteren.jazygit.feature.github.review.ReviewCmd.Send;
import net.vanfleteren.jazygit.feature.github.review.ReviewPopup.Target;
import net.vanfleteren.jazygit.feature.github.review.ReviewPopup.Where;
import net.vanfleteren.jazygit.gh.model.NewReviewThread;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails;
import net.vanfleteren.jazygit.gh.model.ReviewThread;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.model.UnifiedDiff.DiffLine;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.*;
import net.vanfleteren.jazygit.state.Update.Next;
import net.vanfleteren.jazygit.state.compose.ComposeUpdate;
import net.vanfleteren.jazygit.state.diff.*;
import net.vanfleteren.jazygit.state.operation.*;
import net.vanfleteren.jazygit.state.view.*;

/**
 * Commenting on the lines of the diff of a pull request's files from its cursor (ADR 60). A thread is started, or
 * replied to, from a popup that stays open until the comment is sent, so one that fails to send is not lost; a thread
 * is resolved at once. Each is sent on its own, and the review threads are read again once it is, the cursor then on
 * the thread started. Nothing is sent for a pull request whose threads were read at other commits than its files, nor
 * for a merged one; nor twice for a thread while something is sent for it. A comment is edited in the same popup as a
 * reply, at any commits, and takes what GitHub holds once it is saved, without reading the threads again (ADR 65).
 * Writing the comment, in that popup or in the editor, is a {@link ComposeUpdate}'s (ADR 115); deleting one a {@link
 * DeleteCommentUpdate}'s, reacting to one a {@link ReactUpdate}'s, and collapsing or opening one a {@link
 * ThreadUpdate}'s.
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
            case ReviewMsg.Refused(Why why) -> Next.of(refused(model, why));
            case ReviewMsg.Sent(Send cmd, var commands) -> sent(model, cmd, commands);
            case ReviewMsg.CommentEdited(Send cmd, String body, var commands) -> edited(model, cmd, body, commands);
            case ReviewMsg.SendFailed(Send cmd, Failure failure) -> sendFailed(model, cmd, failure);
            case ReviewMsg.Resolved(Resolve cmd, var commands) -> resolved(model, cmd, commands);
            case ReviewMsg.ResolveFailed(Resolve cmd, Failure failure) -> resolveFailed(model, cmd, failure);
        };
    }

    /**
     * Resolving the thread of the cursor {@code at}, or unresolving it when it is resolved, asked for.
     */
    private static Next resolveAsked(Model model, DiffCursorAt at) {
        return reviewed(model)
                .flatMap(view -> OnThread.of(view, at.cursor()))
                .flatMap(OnThread::notSending)
                .flatMap(OnThread::firstNotDeleted)
                .flatMap(on -> resolvable(on.thread()).map(thread -> on))
                .fold(why -> Next.of(refused(model, why)), on -> {
                    Resolve resolve = new Resolve(
                            on.number(), on.thread().id(), !on.thread().resolved());
                    return Update.start(sending(model.clearError(), set -> set.add(resolve.thread())), resolve);
                });
    }

    /**
     * {@code cmd} failed to send. The popup stays open, with the comment, to send it again or to copy it.
     */
    private static Next sendFailed(Model model, Send cmd, Failure failure) {
        return OperationUpdate.update(model, new OperationMsg.Failed(cmd, failure))
                .map(failed -> ComposeUpdate.unsent(failed, ReviewPopup.class))
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
     * Sending what {@code popup} holds, asked for. A blank comment is not sent, as GitHub refuses it; a blank edit says
     * that deleting it is what empties it, and an edit that changed nothing closes the popup, sending nothing (ADR 65).
     */
    static Next confirm(Model model, ReviewPopup popup) {
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
                .flatMap(view -> OnThread.of(view, at.cursor()))
                .flatMap(OnThread::notSending)
                .flatMap(OnThread::firstNotDeleted)
                .filterOrElse(on -> on.thread().rights().reply(), on -> Why.NO_REPLY)
                // A thread whose comments read were all deleted has no first comment to reply to until it is read
                // again, which it is (ADR 64).
                .flatMap(on -> on.thread()
                        .comments()
                        .headOption()
                        .toEither(Why.NOT_READ)
                        .map(on::with))
                .map(first -> popup(
                        first.view(),
                        at,
                        new Target.Reply(first.thread().id(), first.comment().restId()),
                        new Where(first.thread().path(), Option.none(), true),
                        first.thread()
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
                .filterOrElse(on -> on.comment().rights().edit(), on -> Why.NO_EDIT)
                .map(on -> {
                    ReviewThread thread = on.thread();
                    ReviewThread.Comment comment = on.comment();
                    List<ReviewThread.Comment> before = thread.comments().takeWhile(each -> !each.equals(comment));
                    // What is written on GitHub's site ends its lines with \r\n, whose \r the text area keeps: an
                    // edit would send it back where it was not typed. Unchanged is told against the same text.
                    String body = comment.body().replace("\r\n", "\n");
                    return popup(
                                    on.view(),
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
                                            .getOrElse(() -> lines(model, on.view(), at, thread)))
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
        return new ReviewPopup(OnThread.number(view), target, where, quoted, "", false, at.area(), at.at());
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

    private static Model sending(Model model, Function<Set<ReviewThread.Id>, Set<ReviewThread.Id>> f) {
        return mapView(model, view -> view.withSending(f.apply(view.sending())));
    }
}
