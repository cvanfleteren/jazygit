package net.vanfleteren.jazygit.feature.github.review;

import static net.vanfleteren.jazygit.feature.github.review.ReviewLookup.mapView;
import static net.vanfleteren.jazygit.feature.github.review.ReviewLookup.pullRequestFiles;
import static net.vanfleteren.jazygit.feature.github.review.ReviewLookup.refused;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.diff.CommitFilesCmd;
import net.vanfleteren.jazygit.feature.github.GitHubError.CannotReview.Why;
import net.vanfleteren.jazygit.feature.github.browse.BrowseRemarkCmd;
import net.vanfleteren.jazygit.gh.model.PullRequestLink;
import net.vanfleteren.jazygit.gh.model.ReviewThread;
import net.vanfleteren.jazygit.git.model.PullRequestCommits;
import net.vanfleteren.jazygit.state.*;
import net.vanfleteren.jazygit.state.Update.Next;
import net.vanfleteren.jazygit.state.diff.*;
import net.vanfleteren.jazygit.state.view.*;

/**
 * Looking at the review threads of the diff of a pull request's files, from its cursor: collapsing a thread or a
 * comment, or opening it again, which is kept for the next session (ADR 62, ADR 63), and opening a comment on GitHub
 * (ADR 85). Neither sends anything to GitHub, so neither needs the threads read at the commits of the files, nor a pull
 * request that is not merged.
 */
public final class ThreadUpdate {

    private ThreadUpdate() {}

    public static Next update(Model model, ThreadMsg msg) {
        return switch (msg) {
            case ThreadMsg.ThreadToggled(DiffCursorAt at) -> threadToggled(model, at);
            case ThreadMsg.OpenAsked(DiffCursorAt at) -> openAsked(model, at);
        };
    }

    /**
     * Collapsing the thread or the comment of the cursor {@code at}, or opening it. Collapsing a thread or a comment
     * needs no more than the thread: a merged pull request's too. The first comment of a thread is the thread (ADR 62).
     * Off a thread, Space does nothing: there is nothing to refuse.
     */
    private static Next threadToggled(Model model, DiffCursorAt at) {
        return pullRequestFiles(model)
                .toEither(Why.NOT_READ)
                .flatMap(view -> OnThread.of(view, at.cursor()))
                .flatMap(OnThread::notSending)
                .filterOrElse(on -> !on.comment(at.cursor()).exists(on::deleting), on -> Why.DELETING)
                .fold(
                        why -> Next.of(why == Why.NOT_ON_THREAD ? model : refused(model, why)),
                        on -> toggled(model, on, at.cursor()));
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
     * {@code found} with the thread, or the comment of it the cursor is {@code on}, collapsed or opened, which is kept
     * for the next session (ADR 63).
     */
    private static Next toggled(Model model, OnThread found, Anchor on) {
        CommitFilesView view = found.view();
        Collapsed collapsed = view.collapsed()
                .flipped(
                        on instanceof Anchor.Comment(ReviewThread.Comment.Id id, var _)
                                ? Collapsed.Key.of(id)
                                : Collapsed.Key.of(found.thread().id()),
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
                        OnThread.of(view, on)
                                .toOption()
                                .flatMap(found -> found.comment(on))
                                .map(ReviewThread.Comment::restId)))
                : Option.none();
    }
}
