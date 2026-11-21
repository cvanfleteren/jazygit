package net.vanfleteren.jazygit.features.github.review;

import io.vavr.collection.HashSet;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.util.function.Function;
import net.vanfleteren.jazygit.core.gh.model.PrNumber;
import net.vanfleteren.jazygit.core.gh.model.ReviewThread;
import net.vanfleteren.jazygit.features.github.GitHubError;
import net.vanfleteren.jazygit.features.github.GitHubError.CannotReview.Why;
import net.vanfleteren.jazygit.features.github.remarks.FromGitHub;
import net.vanfleteren.jazygit.features.github.remarks.PrFiles;
import net.vanfleteren.jazygit.features.github.remarks.PrRemarks;
import net.vanfleteren.jazygit.features.github.remarks.RemarksCmd.LoadReviewRemarks;
import net.vanfleteren.jazygit.features.github.remarks.RemarksState;
import net.vanfleteren.jazygit.kernel.state.*;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.diff.*;
import net.vanfleteren.jazygit.kernel.state.view.*;

/**
 * What the updates of the review threads of a pull request's files share: finding the comment the cursor is on, and
 * changing those files while they are listed.
 */
final class ReviewLookup {

    private ReviewLookup() {}

    /**
     * The comment the cursor {@code on} is on, of the files listed, with its thread and those files, if it may be
     * deleted or edited now, as the viewer's rights allow (ADR 64, ADR 65): the comment of the cursor, or the first of
     * a thread, which its stop is; on a collapsed thread only when it has no other, as which is meant is not shown. Not
     * one being deleted, nor of a thread something is sent for, as a reply to its first comment, whatever the commits
     * it was read at.
     */
    static Either<Why, OnComment> chosen(Model model, Anchor on) {
        return pullRequestFiles(model)
                .filter(view ->
                        view.remarks().exists(remarks -> remarks.toOption().isDefined()))
                .toEither(Why.NOT_READ)
                .flatMap(view -> OnThread.of(view, on))
                .flatMap(OnThread::notSending)
                .filterOrElse(
                        found -> on instanceof Anchor.Remark
                                || !found.view().collapsed().thread(FromGitHub.thread(found.thread()))
                                || found.thread().comments().size()
                                                + found.thread().more()
                                        == 1,
                        found -> Why.COLLAPSED)
                .flatMap(found -> found.comment(on).toEither(Why.NOT_ON_THREAD).map(found::with))
                .filterOrElse(found -> !found.deleting(), found -> Why.DELETING);
    }

    /**
     * {@code model} once the comment {@code comment} of the thread {@code thread} of pull request {@code number} is
     * gone from GitHub: taken out of the threads without reading them again (ADR 64). Unless it was the last comment
     * read of a thread GitHub has more of: which of those is its first now, that a reply goes to, is not known until
     * the threads are read again.
     */
    static Next gone(Model model, PrNumber number, ReviewThread.Id thread, ReviewThread.Comment.Id comment) {
        Model deleted = mapPullRequest(model, number, view -> view.withoutComment(comment));
        Option<PrFiles> files =
                pullRequestFiles(deleted).filter(view -> view.number().equals(number));
        Model collapsed = files.map(view -> RemarksState.mapView(
                        deleted, open -> open.withCollapsed(open.collapsed().readAgain(view.remarkThreads()))))
                .getOrElse(deleted);
        boolean unread = files.exists(view -> view.threads()
                .exists(each -> each.id().equals(thread) && each.comments().isEmpty()));
        return unread
                ? Next.of(collapsed, LoadReviewRemarks.after(collapsed, number, HashSet.empty()))
                : Next.of(collapsed);
    }

    static Option<PrFiles> pullRequestFiles(Model model) {
        return RemarksState.files(model);
    }

    static Model refused(Model model, Why why) {
        return model.fail(new GitHubError.CannotReview(why));
    }

    static Model mapView(Model model, Function<CommitFilesView, CommitFilesView> f) {
        return RemarksState.mapView(model, f);
    }

    /**
     * {@code model} with the review threads of pull request {@code number} as {@code f} makes them, while they are
     * kept.
     */
    static Model mapPullRequest(Model model, PrNumber number, Function<PrRemarks, PrRemarks> f) {
        return RemarksState.mapPullRequest(model, number, f);
    }
}
