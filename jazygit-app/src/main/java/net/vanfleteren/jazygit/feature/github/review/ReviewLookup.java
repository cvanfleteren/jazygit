package net.vanfleteren.jazygit.feature.github.review;

import io.vavr.collection.HashSet;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.util.function.Function;
import net.vanfleteren.jazygit.feature.diff.CommitFilesCmd.LoadReviewRemarks;
import net.vanfleteren.jazygit.feature.github.GitHubError;
import net.vanfleteren.jazygit.feature.github.GitHubError.CannotReview.Why;
import net.vanfleteren.jazygit.gh.model.PrNumber;
import net.vanfleteren.jazygit.gh.model.ReviewThread;
import net.vanfleteren.jazygit.state.*;
import net.vanfleteren.jazygit.state.Update.Next;
import net.vanfleteren.jazygit.state.diff.*;
import net.vanfleteren.jazygit.state.view.*;

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
                        found -> on instanceof Anchor.Comment
                                || !found.view().collapsed().thread(found.thread())
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
        boolean unread = pullRequestFiles(deleted)
                .filter(view -> view.pullRequest().contains(number))
                .exists(view -> view.threads()
                        .exists(each ->
                                each.id().equals(thread) && each.comments().isEmpty()));
        return unread ? Next.of(deleted, LoadReviewRemarks.after(deleted, number, HashSet.empty())) : Next.of(deleted);
    }

    static Option<CommitFilesView> pullRequestFiles(Model model) {
        return model.repoView().commitFiles().filter(view -> view.pullRequest().isDefined());
    }

    static Model refused(Model model, Why why) {
        return model.fail(new GitHubError.CannotReview(why));
    }

    static Model mapView(Model model, Function<CommitFilesView, CommitFilesView> f) {
        return model.mapRepoView(repoView -> repoView.withCommitFiles(
                repoView.commitFiles().map(view -> view.pullRequest().isDefined() ? f.apply(view) : view)));
    }

    /**
     * {@code model} with the files of pull request {@code number} as {@code f} makes them, while they are listed.
     */
    static Model mapPullRequest(Model model, PrNumber number, Function<CommitFilesView, CommitFilesView> f) {
        return mapView(model, view -> view.pullRequest().contains(number) ? f.apply(view) : view);
    }
}
