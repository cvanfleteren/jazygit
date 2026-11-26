package net.vanfleteren.jazygit.features.github.remarks;

import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.collection.Set;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.gh.model.PullRequestDetails;
import net.vanfleteren.jazygit.core.gh.model.ReviewRemarks;
import net.vanfleteren.jazygit.core.gh.model.ReviewThread;
import net.vanfleteren.jazygit.core.repo.PrNumber;
import net.vanfleteren.jazygit.core.repo.PullRequestCommits;
import net.vanfleteren.jazygit.features.changes.diff.CommitFilesMsg;
import net.vanfleteren.jazygit.features.changes.diff.CommitFilesUpdate;
import net.vanfleteren.jazygit.kernel.state.Cmd;
import net.vanfleteren.jazygit.kernel.state.Loadable;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.diff.Changes;

/**
 * Reading the review threads of the pull request whose files are listed (ADR 55): once its files are opened, again
 * when asked, and after what was sent for them; with the images they show (ADR 84), and those collapsed in an earlier
 * session (ADR 63). The kernel is told nothing: it asks GitHub's part for them (ADR 171).
 */
public final class RemarksUpdate {

    // The most images of the review threads of a pull request that are read to be shown (ADR 84).
    private static final int MAX_PICTURES = 20;

    private RemarksUpdate() {}

    public static Next update(Model model, RemarksMsg msg) {
        return switch (msg) {
            case RemarksMsg.Loaded(PrNumber number, var remarks, var settled, int version) ->
                remarksRead(model, number, remarks, settled, version);
            // The review threads read before stay shown; what was sent is done with all the same. A failed read
            // older than they are changes nothing they show either (ADR 64).
            case RemarksMsg.Failed(PrNumber number, var failure, var settled, int version) ->
                Next.of(RemarksState.mapPullRequest(
                        model,
                        number,
                        read -> read.withRemarks(read.isRead() ? read.remarks() : new Loadable.Failed<>(failure))
                                .withSending(read.sending().removeAll(settled))));
            // Those flipped before the threads are read are as they start until then (ADR 63).
            case RemarksMsg.CollapsedLoaded(PrNumber number, var kept) ->
                RemarksState.files(model)
                        .filter(files -> files.number().equals(number))
                        .map(files -> Next.of(RemarksState.mapView(
                                model,
                                view -> view.withCollapsed(
                                        view.collapsed().with(kept).readAgain(files.remarkThreads())))))
                        .getOrElse(() -> Next.of(model));
            case RemarksMsg.CollapsedKept() -> Next.of(model);
            case RemarksMsg.PictureLoaded(PrNumber number, String url, var picture) ->
                Next.of(RemarksState.mapPullRequest(
                        model, number, read -> read.withPictures(read.pictures().put(url, picture))));
            case RemarksMsg.Refreshed() ->
                Next.of(model)
                        .and(RemarksState.files(model)
                                .map(files -> (Cmd) new RemarksCmd.LoadReviewRemarks(
                                        files.number(), HashSet.empty(), files.version()))
                                .toList());
        };
    }

    /**
     * What GitHub reads once the files of {@code changes} were opened: the review threads of a pull request, and those
     * of its threads collapsed in an earlier session (ADR 55, 63); nothing for other changes.
     */
    public static Next opened(Model model, Changes changes) {
        return changes instanceof Changes.OfPullRequest(PullRequestCommits commits, var _)
                ? Next.of(model.withPart(
                                RemarksState.PART, new RemarksState(Option.of(PrRemarks.reading(commits.number())))))
                        .and(List.of(
                                new RemarksCmd.LoadReviewRemarks(commits.number(), HashSet.empty(), 0),
                                new RemarksCmd.LoadCollapsed(commits.repository(), commits.number())))
                : Next.of(model.withPart(RemarksState.PART, RemarksState.NONE));
    }

    /**
     * {@code remarks} read for pull request {@code number}, while its files are listed, after what was sent for the
     * threads {@code settled}. When its head or its base moved on since its files were read, they are read again, at
     * the commits they are at now: the threads are of those. Read at an older {@code version} than they are at now, as
     * before a comment was deleted, they are dropped, and read again for the threads {@code settled} (ADR 64).
     */
    private static Next remarksRead(
            Model model, PrNumber number, ReviewRemarks remarks, Set<ReviewThread.Id> settled, int version) {
        return RemarksState.files(model)
                .filter(files -> files.number().equals(number))
                .map(files -> {
                    if (version < files.version()) {
                        return Next.of(model, new RemarksCmd.LoadReviewRemarks(number, settled, files.version()));
                    }
                    // What was sent for a thread shows in what is read after it; a read started before it was
                    // sent may not show it, so the thread still takes no key until then (ADR 60). A thread resolved
                    // or unresolved is then as such a one starts (ADR 62).
                    PrRemarks read = files.read()
                            .withRemarks(new Loadable.Loaded<>(remarks))
                            .withSending(files.sending().removeAll(settled));
                    Model kept = RemarksState.mapView(
                            RemarksState.mapPullRequest(model, number, ignored -> read),
                            view -> view.withCollapsed(view.collapsed()
                                    .readAgain(read.threads().toOption().getOrElse(List.empty()))));
                    // The same pull request, read again at the commits it moved on to: what the kernel keeps of its
                    // files stays (ADR 105, 128).
                    Next moved = moved(files, remarks)
                            .map(commits -> CommitFilesUpdate.update(
                                    kept, new CommitFilesMsg.Moved(files.of(), files.at(commits))))
                            .getOrElse(() -> Next.of(kept));
                    return moved.then(now -> pictures(now, read));
                })
                .getOrElse(() -> Next.of(model));
    }

    /**
     * Reads the images of the review threads {@code read} that are not read or being read yet, up to
     * {@link #MAX_PICTURES} of them, to show them (ADR 84).
     */
    private static Next pictures(Model model, PrRemarks read) {
        // Lazily, so that the comments are parsed only until there are enough images.
        List<String> urls = read.read()
                .iterator()
                .flatMap(ReviewThread::comments)
                .flatMap(comment -> GitHubImages.in(comment.body()))
                .distinct()
                .filter(url -> !read.pictures().containsKey(url))
                .take(Math.max(0, MAX_PICTURES - read.pictures().size()))
                .toList();
        return Next.of(RemarksState.mapPullRequest(
                        model,
                        read.number(),
                        open -> open.withPictures(
                                urls.foldLeft(open.pictures(), (pictures, url) -> pictures.put(url, Option.none())))))
                .and(urls.map(url -> new RemarksCmd.LoadPicture(read.number(), url)));
    }

    /**
     * The commits of the pull request {@code files} are of, as {@code remarks} were read at, when they are others than
     * its files were read at, while it is open: a merged or closed one stays at the commits it was read at.
     */
    private static Option<PullRequestCommits> moved(PrFiles files, ReviewRemarks remarks) {
        PullRequestCommits commits = files.commits();
        return remarks.state() == PullRequestDetails.State.OPEN
                ? remarks.base()
                        .flatMap(base -> remarks.head()
                                .map(head -> new PullRequestCommits(
                                        commits.number(), commits.repository(), commits.host(), base, head)))
                        .filter(read -> !read.equals(commits))
                : Option.none();
    }
}
