package net.vanfleteren.jazygit.feature.github.review;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.collection.Set;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.util.function.Function;
import net.vanfleteren.jazygit.feature.diff.CommitFilesCmd.LoadReviewRemarks;
import net.vanfleteren.jazygit.feature.github.review.ReviewCmd.EditComment;
import net.vanfleteren.jazygit.feature.github.review.ReviewCmd.Resolve;
import net.vanfleteren.jazygit.feature.github.review.ReviewCmd.Send;
import net.vanfleteren.jazygit.feature.github.review.ReviewPopup.Target;
import net.vanfleteren.jazygit.feature.github.review.ReviewPopup.Where;
import net.vanfleteren.jazygit.gh.model.NewReviewThread;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails;
import net.vanfleteren.jazygit.gh.model.ReviewThread;
import net.vanfleteren.jazygit.git.model.DiffText;
import net.vanfleteren.jazygit.git.model.UnifiedDiff.DiffLine;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.*;
import net.vanfleteren.jazygit.state.AppError.CannotReview.Why;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * Commenting on the lines of the diff of a pull request's files from its cursor (ADR 60). A thread is started, or
 * replied to, from a popup that stays open until the comment is sent, so one that fails to send is not lost; a thread
 * is resolved at once. Each is sent on its own, and the review threads are read again once it is, the cursor then on
 * the thread started. Nothing is sent for a pull request whose threads were read at other commits than its files, nor
 * for a merged one; nor twice for a thread while something is sent for it.
 */
public final class ReviewUpdate {

    // How many lines of the last comment of a thread the popup of a reply shows.
    private static final int QUOTED = 6;

    private ReviewUpdate() {}

    public static Next update(Model model, ReviewMsg msg) {
        return switch (msg) {
            case ReviewMsg.StartAsked(DiffCursorAt at) -> asked(model, start(model, at));
            case ReviewMsg.ReplyAsked(DiffCursorAt at) -> asked(model, reply(model, at));
            case ReviewMsg.ResolveAsked(DiffCursorAt at) ->
                reviewed(model)
                        .flatMap(view -> thread(view, at.cursor()))
                        .flatMap(ReviewUpdate::notSending)
                        .flatMap(found -> resolvable(found._2()).map(thread -> found))
                        .fold(why -> Next.of(refused(model, why)), found -> {
                            Resolve resolve = new Resolve(
                                    number(found._1()),
                                    found._2().id(),
                                    !found._2().resolved());
                            return Update.start(sending(model.clearError(), set -> set.add(resolve.thread())), resolve);
                        });
            // Collapsing a thread or a comment needs no more than the thread: a merged pull request's too. The first
            // comment of a thread is the thread (ADR 62).
            case ReviewMsg.ThreadToggled(DiffCursorAt at) ->
                pullRequestFiles(model)
                        .toEither(Why.NOT_READ)
                        .flatMap(view -> thread(view, at.cursor()))
                        .flatMap(ReviewUpdate::notSending)
                        .fold(
                                why -> Next.of(refused(model, why)),
                                found -> Next.of(mapView(
                                        model,
                                        view -> view.withCollapsed(view.collapsed()
                                                .flipped(
                                                        at.cursor() instanceof Anchor.Comment(String id, var _)
                                                                ? id
                                                                : found._2().id())))));
            case ReviewMsg.Refused(Why why) -> Next.of(refused(model, why));
            // While it is being sent, the comment stays as it is sent.
            case ReviewMsg.Edited(String text) -> Next.of(changed(model, popup -> popup.withText(text), true));
            // A blank comment is not sent, as GitHub refuses it.
            case ReviewMsg.Confirmed() ->
                editable(model)
                        .filter(popup -> !popup.text().isBlank())
                        .map(popup -> {
                            Model sending = model.clearError().openPopup(popup.withSending(true));
                            return Update.start(
                                    popup.target() instanceof Target.Reply(String thread, var comment)
                                            ? sending(sending, set -> set.add(thread))
                                            : sending,
                                    new Send(popup.number(), popup.target(), popup.text()));
                        })
                        .getOrElse(() -> Next.of(model));
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
                        done.cmds().append(new LoadReviewRemarks(cmd.number(), HashSet.of(cmd.thread()))));
            }
            case ReviewMsg.ResolveFailed(Resolve cmd, Failure failure) -> {
                Next failed = OperationUpdate.update(model, new OperationMsg.Failed(cmd, failure));
                yield new Next(sending(failed.model(), set -> set.remove(cmd.thread())), failed.cmds());
            }
        };
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
            DiffStops stops = stops(view);
            List<Anchor> anchors = stops.anchors();
            Anchor from = at.mark().getOrElse(at.cursor());
            if (!(from instanceof Anchor.OnLine) || !(at.cursor() instanceof Anchor.OnLine)) {
                return Either.left(Why.NOT_ON_LINE);
            }
            int one = anchors.indexOf(from);
            int other = anchors.indexOf(at.cursor());
            if (one < 0
                    || other < 0
                    || !stops.stops()
                            .get(one)
                            .hunk()
                            .equals(stops.stops().get(other).hunk())) {
                return Either.left(Why.NOT_ONE_HUNK);
            }
            List<DiffStops.Stop> range = stops.stops()
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
                    range.flatMap(stop -> stop.line().map(ReviewUpdate::quoted))));
        });
    }

    /**
     * The popup that replies to the thread of the cursor {@code at}.
     */
    private static Either<Why, ReviewPopup> reply(Model model, DiffCursorAt at) {
        return reviewed(model)
                .flatMap(view -> thread(view, at.cursor()))
                .flatMap(ReviewUpdate::notSending)
                .filterOrElse(found -> found._2().rights().reply(), found -> Why.NO_REPLY)
                .map(found -> popup(
                        found._1(),
                        at,
                        new Target.Reply(
                                found._2().id(), found._2().comments().head().restId()),
                        new Where(found._2().path(), Option.none(), true),
                        found._2()
                                .comments()
                                .lastOption()
                                .map(comment -> List.ofAll(comment.body().lines())
                                        .take(QUOTED)
                                        .prepend(comment.author() + ":"))
                                .getOrElse(List.empty())));
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
        return new Next(moved, done.cmds().append(new LoadReviewRemarks(cmd.number(), settled)));
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
                .getOrElse(() -> new DiffStops(List.empty()));
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
