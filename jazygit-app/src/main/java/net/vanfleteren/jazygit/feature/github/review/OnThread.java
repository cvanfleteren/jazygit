package net.vanfleteren.jazygit.feature.github.review;

import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.github.GitHubError.CannotReview.Why;
import net.vanfleteren.jazygit.gh.model.ReviewThread;
import net.vanfleteren.jazygit.state.diff.Anchor;
import net.vanfleteren.jazygit.state.view.CommitFilesView;

/**
 * A review thread the cursor is on, or on a comment of, with the files of the pull request it is of.
 *
 * @param view   the files of the pull request, as they are listed
 * @param thread the thread
 */
record OnThread(CommitFilesView view, ReviewThread thread) {

    /**
     * The thread the cursor {@code on} is on, or on a comment of, of the files {@code view} lists.
     */
    static Either<Why, OnThread> of(CommitFilesView view, Anchor on) {
        Option<ReviewThread.Id> id = switch (on) {
            case Anchor.Thread thread -> Option.of(thread.id());
            case Anchor.Comment(var _, Anchor.Thread thread) -> Option.of(thread.id());
            default -> Option.none();
        };
        return id.flatMap(found -> view.threads().find(thread -> thread.id().equals(found)))
                .toEither(Why.NOT_ON_THREAD)
                .map(thread -> new OnThread(view, thread));
    }

    /**
     * The number of the pull request.
     */
    int number() {
        return view.pullRequest().getOrElse(0);
    }

    /**
     * This, unless something is sent for its thread: a second one would act on what was read before.
     */
    Either<Why, OnThread> notSending() {
        return view.sending().contains(thread.id()) ? Either.left(Why.SENDING) : Either.right(this);
    }

    /**
     * This, unless the first comment of its thread is being deleted, which a reply goes to (ADR 64).
     */
    Either<Why, OnThread> firstNotDeleted() {
        return thread.comments().headOption().exists(this::deleting) ? Either.left(Why.DELETING) : Either.right(this);
    }

    /**
     * The comment of the thread the cursor {@code on} is on: the one it names, or its first, which its stop is.
     */
    Option<ReviewThread.Comment> comment(Anchor on) {
        return on instanceof Anchor.Comment(ReviewThread.Comment.Id id, var _)
                ? thread.comments().find(comment -> comment.id().equals(id))
                : thread.comments().headOption();
    }

    /**
     * {@code comment} of this thread.
     */
    OnComment with(ReviewThread.Comment comment) {
        return new OnComment(view, thread, comment);
    }

    /**
     * Whether {@code comment} is being deleted.
     */
    boolean deleting(ReviewThread.Comment comment) {
        return view.deleting().contains(comment.id());
    }
}
