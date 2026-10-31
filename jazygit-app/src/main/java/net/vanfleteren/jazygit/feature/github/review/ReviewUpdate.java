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
import net.vanfleteren.jazygit.feature.github.review.ReviewCmd.DeleteComment;
import net.vanfleteren.jazygit.feature.github.review.ReviewCmd.EditComment;
import net.vanfleteren.jazygit.feature.github.review.ReviewCmd.Resolve;
import net.vanfleteren.jazygit.feature.github.review.ReviewCmd.Send;
import net.vanfleteren.jazygit.feature.github.review.ReviewPopup.Target;
import net.vanfleteren.jazygit.feature.github.review.ReviewPopup.Where;
import net.vanfleteren.jazygit.gh.model.NewReviewThread;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails;
import net.vanfleteren.jazygit.gh.model.ReviewThread;
import net.vanfleteren.jazygit.git.model.DiffText;
import net.vanfleteren.jazygit.git.model.PullRequestCommits;
import net.vanfleteren.jazygit.git.model.UnifiedDiff.DiffLine;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.*;
import net.vanfleteren.jazygit.state.AppError.CannotReview.Why;
import net.vanfleteren.jazygit.state.CommitFilesView.Changes;
import net.vanfleteren.jazygit.state.Update.Next;

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
            case ReviewMsg.ResolveAsked(DiffCursorAt at) ->
                reviewed(model)
                        .flatMap(view -> thread(view, at.cursor()))
                        .flatMap(ReviewUpdate::notSending)
                        .flatMap(ReviewUpdate::firstNotDeleted)
                        .flatMap(found -> resolvable(found._2()).map(thread -> found))
                        .fold(why -> Next.of(refused(model, why)), found -> {
                            Resolve resolve = new Resolve(
                                    number(found._1()),
                                    found._2().id(),
                                    !found._2().resolved());
                            return Update.start(sending(model.clearError(), set -> set.add(resolve.thread())), resolve);
                        });
            // Collapsing a thread or a comment needs no more than the thread: a merged pull request's too. The first
            // comment of a thread is the thread (ADR 62). Off a thread, Space does nothing: there is nothing to refuse.
            case ReviewMsg.ThreadToggled(DiffCursorAt at) ->
                pullRequestFiles(model)
                        .toEither(Why.NOT_READ)
                        .flatMap(view -> thread(view, at.cursor()))
                        .flatMap(ReviewUpdate::notSending)
                        .flatMap(found -> comment(found._2(), at.cursor())
                                .filter(comment -> found._1().deleting().contains(comment.id()))
                                .fold(
                                        () -> Either.<Why, Tuple2<CommitFilesView, ReviewThread>>right(found),
                                        comment -> Either.left(Why.DELETING)))
                        .fold(
                                why -> Next.of(why == Why.NOT_ON_THREAD ? model : refused(model, why)),
                                found -> toggled(model, found, at.cursor()));
            case ReviewMsg.Refused(Why why) -> Next.of(refused(model, why));
            // While it is being sent, the comment stays as it is sent.
            case ReviewMsg.Edited(String text) -> Next.of(changed(model, popup -> popup.withText(text), true));
            case ReviewMsg.Confirmed() ->
                editable(model).map(popup -> confirmed(model, popup)).getOrElse(() -> Next.of(model));
            case ReviewMsg.Cancelled() ->
                Next.of(editable(model)
                        .map(popup -> model.withoutPopup(ReviewPopup.class))
                        .getOrElse(model));
            case ReviewMsg.EditorRequested() ->
                editable(model)
                        .map(popup -> Next.of(model.clearError(), new EditComment(popup.text())))
                        .getOrElse(() -> Next.of(model));
            // Cancelled meanwhile, the popup stays closed.
            case ReviewMsg.EditorClosed(String text, var commands) ->
                Next.of(changed(
                        CommandLog.append(model, ReviewCmd.EDIT_COMMENT, commands),
                        popup -> popup.withText(text),
                        false));
            case ReviewMsg.EditorFailed(Failure failure) ->
                Next.of(CommandLog.append(model, ReviewCmd.EDIT_COMMENT, failure.commands())
                        .fail(new AppError.OperationFailed(ReviewCmd.EDIT_COMMENT, Option.none(), failure)));
            case ReviewMsg.Sent(Send cmd, var commands) -> sent(model, cmd, commands);
            case ReviewMsg.CommentEdited(Send cmd, String body, var commands) -> edited(model, cmd, body, commands);
            // The popup stays open, with the comment, to send it again or to copy it.
            case ReviewMsg.SendFailed(Send cmd, Failure failure) -> {
                Next failed = OperationUpdate.update(model, new OperationMsg.Failed(cmd, failure));
                Model back = changed(failed.model(), popup -> popup.withSending(false), false);
                yield new Next(
                        cmd.target() instanceof Target.Reply(String thread, var comment)
                                ? sending(back, set -> set.remove(thread))
                                : back,
                        failed.cmds());
            }
            // The thread says it is being sent until the threads are read again after it, which shows it resolved.
            case ReviewMsg.Resolved(Resolve cmd, var commands) -> {
                Next done = OperationUpdate.succeeded(model, cmd, commands);
                yield new Next(
                        done.model(),
                        done.cmds().append(remarksLoad(done.model(), cmd.number(), HashSet.of(cmd.thread()))));
            }
            case ReviewMsg.ResolveFailed(Resolve cmd, Failure failure) -> {
                Next failed = OperationUpdate.update(model, new OperationMsg.Failed(cmd, failure));
                yield new Next(sending(failed.model(), set -> set.remove(cmd.thread())), failed.cmds());
            }
            case ReviewMsg.DeleteAsked(DiffCursorAt at) ->
                deletable(model, at.cursor())
                        .fold(
                                why -> Next.of(refused(model, why)),
                                found -> Update.ask(
                                        model,
                                        new DeleteCommentPopup(
                                                number(found._1()), found._2().id(), found._3(), model.now())));
            case ReviewMsg.DeleteConfirmed() ->
                model.popup(DeleteCommentPopup.class)
                        .map(popup -> deleteConfirmed(model, popup))
                        .getOrElse(() -> Next.of(model));
            // Not read again: what GitHub did is known (ADR 64). The cursor on it goes to its thread, or its line.
            // Unless it was the last comment read of a thread GitHub has more of: which of those is its first now, that
            // a reply goes to, is not known until the threads are read again.
            case ReviewMsg.CommentDeleted(DeleteComment cmd, var commands) -> {
                Next done = OperationUpdate.succeeded(model, cmd, commands);
                Model deleted = mapPullRequest(
                        done.model(),
                        cmd.number(),
                        view -> view.withoutComment(cmd.comment())
                                .withDeleting(view.deleting().remove(cmd.comment())));
                boolean unread = pullRequestFiles(deleted)
                        .filter(view -> view.pullRequest().contains(cmd.number()))
                        .exists(view -> view.threads()
                                .exists(thread -> thread.id().equals(cmd.thread())
                                        && thread.comments().isEmpty()));
                yield new Next(
                        deleted,
                        unread ? done.cmds().append(remarksLoad(deleted, cmd.number(), HashSet.empty())) : done.cmds());
            }
            case ReviewMsg.DeleteFailed(DeleteComment cmd, Failure failure) -> {
                Next failed = OperationUpdate.update(model, new OperationMsg.Failed(cmd, failure));
                yield new Next(
                        mapPullRequest(
                                failed.model(),
                                cmd.number(),
                                view -> view.withDeleting(view.deleting().remove(cmd.comment()))),
                        failed.cmds());
            }
        };
    }

    /**
     * Sending what {@code popup} holds, asked for. A blank comment is not sent, as GitHub refuses it; a blank edit says
     * that deleting it is what empties it, and an edit that changed nothing closes the popup, sending nothing (ADR 65).
     */
    private static Next confirmed(Model model, ReviewPopup popup) {
        if (popup.target() instanceof Target.Edit(var thread, var comment, String body)) {
            if (popup.text().equals(body)) {
                return Next.of(model.clearError().withoutPopup(ReviewPopup.class));
            }
            if (popup.text().isBlank()) {
                return Next.of(refused(model, Why.EMPTY));
            }
        } else if (popup.text().isBlank()) {
            return Next.of(model);
        }
        // An edit does not mark its thread as being sent, as a reply does: the popup takes every key while it is
        // saved, so nothing else is sent for the thread meanwhile (ADR 65).
        Model sending = model.clearError().openPopup(popup.withSending(true));
        return Update.start(
                popup.target() instanceof Target.Reply(String thread, var comment)
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
        return reviewed(model).flatMap(view -> {
            String commit = view.of().sha().value();
            if (at.mark().isEmpty() && at.cursor() instanceof Anchor.File(String path)) {
                return Either.right(popup(
                        view,
                        at,
                        new Target.Start(commit, path, Option.none()),
                        new Where(path, Option.none(), false),
                        List.empty()));
            }
            Anchor from = at.mark().getOrElse(at.cursor());
            if (!(from instanceof Anchor.OnLine) || !(at.cursor() instanceof Anchor.OnLine)) {
                return Either.left(Why.NOT_ON_LINE);
            }
            DiffStops stops = stops(view);
            int one = stops.indexOf(from);
            int other = stops.indexOf(at.cursor());
            if (one < 0
                    || other < 0
                    || !stops.stops()
                            .get(one)
                            .hunk()
                            .equals(stops.stops().get(other).hunk())) {
                return Either.left(Why.NOT_ONE_HUNK);
            }
            Vector<DiffStops.Stop> range = stops.stops()
                    .slice(Math.min(one, other), Math.max(one, other) + 1)
                    .filter(stop -> stop.line().isDefined());
            Anchor.OnLine first = (Anchor.OnLine) range.head().anchor();
            Anchor.OnLine last = (Anchor.OnLine) range.last().anchor();
            return Either.right(popup(
                    view,
                    at,
                    new Target.Start(
                            commit,
                            first.path(),
                            Option.of(new NewReviewThread.Lines(
                                    first.side(), first.number(), last.side(), last.number()))),
                    new Where(first.path(), Option.of(new ReviewPopup.Lines(first.number(), last.number())), false),
                    range.flatMap(stop -> stop.line().map(ReviewUpdate::quoted)).toList()));
        });
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
                                            .getOrElse(() -> lines(found._1(), thread)))
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
     * The lines of the diff {@code thread} is on, as the popup that starts one shows them; none when the diff does not
     * show them, as for an outdated thread, or one on a whole file.
     */
    private static List<String> lines(CommitFilesView view, ReviewThread thread) {
        return thread.line()
                .map(line -> {
                    DiffStops stops = stops(view);
                    int last = stops.indexOf(new Anchor.OnLine(thread.path(), thread.side(), line));
                    int first = stops.indexOf(new Anchor.OnLine(
                            thread.path(),
                            thread.startSide(),
                            thread.startLine().getOrElse(line)));
                    return first < 0 || last < 0
                            ? List.<String>empty()
                            : stops.stops()
                                    .slice(Math.min(first, last), Math.max(first, last) + 1)
                                    .flatMap(stop -> stop.line().map(ReviewUpdate::quoted))
                                    .toList();
                })
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
        return thread.resolved()
                ? thread.rights().unresolve() ? Either.right(thread) : Either.left(Why.NO_UNRESOLVE)
                : thread.rights().resolve() ? Either.right(thread) : Either.left(Why.NO_RESOLVE);
    }

    /**
     * {@code cmd} sent: the popup closes, the threads are read again, and the cursor goes to the last line of the
     * thread started, if one was, which the thread is drawn right after, its range dropped. GitHub does not say which
     * thread a comment started.
     */
    private static Next sent(Model model, Send cmd, List<ExecutedCommand> commands) {
        Next done = OperationUpdate.succeeded(model, cmd, commands);
        Option<ReviewPopup> popup =
                done.model().popup(ReviewPopup.class).filter(open -> open.sending() && open.number() == cmd.number());
        Model closed =
                popup.map(open -> done.model().withoutPopup(ReviewPopup.class)).getOrElse(done.model());
        Model moved = popup.flatMap(open -> lastLine(open.target())
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
        Set<String> settled =
                cmd.target() instanceof Target.Reply(String thread, var comment) ? HashSet.of(thread) : HashSet.empty();
        return new Next(moved, done.cmds().append(remarksLoad(moved, cmd.number(), settled)));
    }

    /**
     * The edit {@code cmd} saved, as GitHub holds it, {@code body}: the popup closes, and the comment says it, at a
     * newer version, without the threads read again (ADR 65). The cursor stays on it.
     */
    private static Next edited(Model model, Send cmd, String body, List<ExecutedCommand> commands) {
        Next done = OperationUpdate.succeeded(model, cmd, commands);
        Model closed = done.model()
                .popup(ReviewPopup.class)
                .filter(open -> open.sending() && open.number() == cmd.number())
                .map(open -> done.model().withoutPopup(ReviewPopup.class))
                .getOrElse(done.model());
        return new Next(
                cmd.target() instanceof Target.Edit(var thread, String comment, var original)
                        ? mapPullRequest(closed, cmd.number(), view -> view.withEdited(comment, body))
                        : closed,
                done.cmds());
    }

    /**
     * The read of the review threads of pull request {@code number} after what was sent for the threads
     * {@code settled}, at the version they are at (ADR 64).
     */
    private static LoadReviewRemarks remarksLoad(Model model, int number, Set<String> settled) {
        return new LoadReviewRemarks(
                number,
                settled,
                pullRequestFiles(model)
                        .filter(view -> view.pullRequest().contains(number))
                        .map(CommitFilesView::version)
                        .getOrElse(0));
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
     * Deleting the comment {@code popup} asks about, confirmed: asked again as it is now, as the comment may have gone,
     * or something be sent for its thread, meanwhile. It says it is being deleted until GitHub answers.
     */
    private static Next deleteConfirmed(Model model, DeleteCommentPopup popup) {
        Anchor on = new Anchor.Comment(popup.comment().id(), new Anchor.Thread(popup.thread(), Option.none()));
        return deletable(model, on)
                .filterOrElse(found -> found._1().pullRequest().contains(popup.number()), found -> Why.NOT_READ)
                .fold(why -> Next.of(refused(model.withoutPopup(DeleteCommentPopup.class), why)), found -> {
                    Next started = Update.confirm(
                            model,
                            DeleteCommentPopup.class,
                            open -> Option.of(new DeleteComment(
                                    open.number(), open.thread(), open.comment().id())));
                    return new Next(
                            mapPullRequest(
                                    started.model(),
                                    popup.number(),
                                    view -> view.withDeleting(
                                            view.deleting().add(found._3().id()))),
                            started.cmds());
                });
    }

    /**
     * The comment of {@code thread} the cursor {@code on} is on: the one it names, or its first, which its stop is.
     */
    private static Option<ReviewThread.Comment> comment(ReviewThread thread, Anchor on) {
        return on instanceof Anchor.Comment(String id, var _)
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
     * The stops of the diff the files of the pull request {@code view} shows, as the content panel draws it.
     */
    private static DiffStops stops(CommitFilesView view) {
        return view.diff()
                .flatMap(preview -> preview.shown().value().toOption())
                .map(DiffText::parsed)
                .map(parsed ->
                        DiffStops.of(parsed.diff(), view.threads(), view.remarksAtItsCommits(), view.collapsed()))
                .getOrElse(() -> new DiffStops(Vector.empty()));
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
                        on instanceof Anchor.Comment(String id, var _)
                                ? id
                                : found._2().id(),
                        view.threads());
        return new Next(
                mapView(model, files -> files.withCollapsed(collapsed)),
                view.of() instanceof Changes.OfPullRequest(PullRequestCommits commits)
                        ? List.of(new CommitFilesCmd.SaveCollapsed(
                                commits.repository(), commits.number(), collapsed.toggled(), model.now()))
                        : List.empty());
    }

    private static Option<CommitFilesView> pullRequestFiles(Model model) {
        return model.repoView().commitFiles().filter(view -> view.pullRequest().isDefined());
    }

    /**
     * The thread the cursor {@code on} is on, or on a comment of, with the files it is of.
     */
    private static Either<Why, Tuple2<CommitFilesView, ReviewThread>> thread(CommitFilesView view, Anchor on) {
        Option<String> id = switch (on) {
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

    private static Model sending(Model model, Function<Set<String>, Set<String>> f) {
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
