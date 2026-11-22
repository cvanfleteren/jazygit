package net.vanfleteren.jazygit.features.github.remarks;

import io.vavr.collection.HashMap;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Set;
import io.vavr.control.Option;
import java.util.function.Function;
import net.vanfleteren.jazygit.core.gh.model.PrNumber;
import net.vanfleteren.jazygit.core.gh.model.PullRequestDetails;
import net.vanfleteren.jazygit.core.gh.model.Reaction;
import net.vanfleteren.jazygit.core.gh.model.ReviewRemarks;
import net.vanfleteren.jazygit.core.gh.model.ReviewThread;
import net.vanfleteren.jazygit.core.repo.PullRequestCommits;
import net.vanfleteren.jazygit.kernel.state.Loadable;
import net.vanfleteren.jazygit.kernel.state.diff.RemarkThread;
import net.vanfleteren.jazygit.kernel.state.diff.StatedRemarks;
import net.vanfleteren.jazygit.lib.image.Picture;

/**
 * The review threads of the pull request whose files are listed, as GitHub's part keeps them (ADR 55, 171).
 *
 * @param number   the number of the pull request
 * @param remarks  its review threads, as they are read
 * @param threads  those threads as the kernel's, made once when they change, as the kernel asks for them at each frame
 * @param sending  the ids of the review threads a reply or a resolving is sent for, until the threads are read again
 *                 after it was sent, or the send failed (ADR 60)
 * @param deleting the ids of the comments of review threads being deleted, until GitHub took it or refused (ADR 64)
 * @param reacting the ids of the comments of review threads a reaction is sent for, until GitHub took it or refused
 *                 (ADR 86)
 * @param version  how often the review threads were changed here rather than read, as when a comment was deleted: a
 *                 read started before is older than they are, and is read again (ADR 64)
 * @param pictures the images of the review threads that are read to be shown, by where they are: none while one is
 *                 read, or when it could not be (ADR 84)
 */
public record PrRemarks(
        PrNumber number,
        Loadable<ReviewRemarks> remarks,
        Loadable<List<RemarkThread>> threads,
        Set<ReviewThread.Id> sending,
        Set<ReviewThread.Comment.Id> deleting,
        Set<ReviewThread.Comment.Id> reacting,
        int version,
        Map<String, Option<Picture>> pictures) {

    /**
     * The review threads of pull request {@code number}, while they are first read.
     */
    public static PrRemarks reading(PrNumber number) {
        return new PrRemarks(
                number,
                Loadable.loading(),
                Loadable.loading(),
                HashSet.empty(),
                HashSet.empty(),
                HashSet.empty(),
                0,
                HashMap.empty());
    }

    /**
     * These with {@code remarks} read.
     */
    public PrRemarks withRemarks(Loadable<ReviewRemarks> remarks) {
        return new PrRemarks(
                number,
                remarks,
                remarks.map(read -> read.threads().map(FromGitHub::thread)),
                sending,
                deleting,
                reacting,
                version,
                pictures);
    }

    public PrRemarks withSending(Set<ReviewThread.Id> sending) {
        return new PrRemarks(number, remarks, threads, sending, deleting, reacting, version, pictures);
    }

    public PrRemarks withDeleting(Set<ReviewThread.Comment.Id> deleting) {
        return new PrRemarks(number, remarks, threads, sending, deleting, reacting, version, pictures);
    }

    public PrRemarks withReacting(Set<ReviewThread.Comment.Id> reacting) {
        return new PrRemarks(number, remarks, threads, sending, deleting, reacting, version, pictures);
    }

    public PrRemarks withVersion(int version) {
        return new PrRemarks(number, remarks, threads, sending, deleting, reacting, version, pictures);
    }

    public PrRemarks withPictures(Map<String, Option<Picture>> pictures) {
        return new PrRemarks(number, remarks, threads, sending, deleting, reacting, version, pictures);
    }

    /**
     * The review threads read, once they are.
     */
    public List<ReviewThread> read() {
        return remarks.toOption().map(ReviewRemarks::threads).getOrElse(List.empty());
    }

    /**
     * Whether the review threads were read.
     */
    public boolean isRead() {
        return remarks.toOption().isDefined();
    }

    /**
     * What these state for the files of the pull request at {@code commits} (ADR 171).
     */
    public StatedRemarks stated(PullRequestCommits commits) {
        return new StatedRemarks(
                threads,
                atCommits(commits),
                sending.map(FromGitHub::id),
                deleting.map(FromGitHub::id),
                pictures,
                ended().flatMap(FromGitHub::ended));
    }

    /**
     * These without the comment with the id {@code comment}, which GitHub deleted, as it would read them now (ADR 64):
     * the next comment of its thread is then its first, and the thread is gone with its last, unless GitHub has more of
     * it than were read. Changed so, they are of a newer version, which a read started before is older than.
     */
    public PrRemarks withoutComment(ReviewThread.Comment.Id comment) {
        return remarks.toOption()
                .filter(read -> read.threads().exists(thread -> has(thread, comment)))
                .map(read -> withRemarks(new Loadable.Loaded<>(read.withThreads(read.threads()
                                .map(thread -> has(thread, comment) ? without(thread, comment) : thread)
                                .filter(thread -> !thread.comments().isEmpty() || thread.more() > 0))))
                        .withVersion(version + 1))
                .getOrElse(this);
    }

    /**
     * These with the comment with the id {@code comment} saying {@code body}, as GitHub holds it once it is edited
     * (ADR 65). Changed so, they are of a newer version, which a read started before is older than.
     */
    public PrRemarks withEdited(ReviewThread.Comment.Id comment, String body) {
        return withComment(comment, each -> each.withBody(body));
    }

    /**
     * These with {@code reaction} given to the comment with the id {@code comment} by the viewer when {@code given},
     * else taken back, as GitHub holds it once it took that (ADR 86). Changed so, they are of a newer version, which a
     * read started before is older than.
     */
    public PrRemarks withReacted(ReviewThread.Comment.Id comment, Reaction reaction, boolean given) {
        return withComment(comment, each -> each.withReactions(each.reactions().withViewer(reaction, given)));
    }

    /**
     * These with the comment with the id {@code comment} as {@code change} makes it, as GitHub holds it once it took
     * what was sent for it, at a newer version, which a read started before is older than (ADR 64); these as they are
     * when no thread read has it.
     */
    private PrRemarks withComment(
            ReviewThread.Comment.Id comment, Function<ReviewThread.Comment, ReviewThread.Comment> change) {
        return remarks.toOption()
                .filter(read -> read.threads().exists(thread -> has(thread, comment)))
                .map(read -> withRemarks(new Loadable.Loaded<>(read.withThreads(read.threads()
                                .map(thread -> has(thread, comment)
                                        ? thread.withComments(thread.comments()
                                                .map(each -> each.id().equals(comment) ? change.apply(each) : each))
                                        : thread))))
                        .withVersion(version + 1))
                .getOrElse(this);
    }

    private static boolean has(ReviewThread thread, ReviewThread.Comment.Id comment) {
        return thread.comments().exists(each -> each.id().equals(comment));
    }

    private static ReviewThread without(ReviewThread thread, ReviewThread.Comment.Id comment) {
        return thread.withComments(thread.comments().filter(each -> !each.id().equals(comment)));
    }

    /**
     * Whether the review threads were read at {@code commits}, those the files were read at, so that their lines
     * number lines of the diff shown. They are not when the pull request was found merged or closed at newer commits:
     * its files stay at those they were read at (ADR 55).
     */
    public boolean atCommits(PullRequestCommits commits) {
        return remarks.toOption()
                .exists(read ->
                        read.head().contains(commits.head()) && read.base().contains(commits.base()));
    }

    /**
     * Whether the pull request was merged or closed when its review threads were read last, and which: the tree then
     * stays at the commits it was read at (ADR 55).
     */
    public Option<PullRequestDetails.State> ended() {
        return remarks.toOption().map(ReviewRemarks::state).filter(state -> state != PullRequestDetails.State.OPEN);
    }
}
