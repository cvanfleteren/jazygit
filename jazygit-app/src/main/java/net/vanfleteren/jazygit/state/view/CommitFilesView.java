package net.vanfleteren.jazygit.state.view;

import io.vavr.collection.HashMap;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Set;
import io.vavr.control.Option;
import java.util.function.Function;
import lombok.With;
import net.vanfleteren.jazygit.gh.model.PrNumber;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails;
import net.vanfleteren.jazygit.gh.model.Reaction;
import net.vanfleteren.jazygit.gh.model.ReviewRemarks;
import net.vanfleteren.jazygit.gh.model.ReviewThread;
import net.vanfleteren.jazygit.git.model.ChangeType;
import net.vanfleteren.jazygit.git.model.CommitFile;
import net.vanfleteren.jazygit.git.model.DiffText;
import net.vanfleteren.jazygit.git.model.PullRequestCommits;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.image.Picture;
import net.vanfleteren.jazygit.lens.Lens;
import net.vanfleteren.jazygit.state.Loadable;
import net.vanfleteren.jazygit.state.PaneId;
import net.vanfleteren.jazygit.state.diff.Changes;
import net.vanfleteren.jazygit.state.diff.Collapsed;

/**
 * The files a commit, a stash, a pull request or a branch changed, which the files pane lists as a tree in place of
 * the changed files of the working tree while it is open (ADR 44, 55, 61).
 *
 * @param of       the commit, the stash, the pull request or the branch
 * @param pane     the id of the pane it was opened from, which gets the focus back when it is closed
 * @param listedIn the id of the pane that lists the files: when another pane gets the focus, it closes
 * @param files    the files, as they are read
 * @param diff     what changed in the files of the node highlighted in the tree, keyed by them, once one is
 *                 highlighted
 * @param remarks  the review threads of a pull request, as they are read; none for a commit or a stash (ADR 55)
 * @param listed   which of the files are listed: all of them, or only those with review threads (ADR 57)
 * @param collapsed which review threads and comments are collapsed (ADR 62)
 * @param sending  the ids of the review threads a reply or a resolving is sent for, until the threads are read again
 *                 after it was sent, or the send failed (ADR 60)
 * @param deleting the ids of the comments of review threads being deleted, until GitHub took it or refused (ADR 64)
 * @param reacting the ids of the comments of review threads a reaction is sent for, until GitHub took it or refused
 *                 (ADR 86)
 * @param version  how often the review threads were changed here rather than read, as when a comment was deleted: a
 *                 read started before is older than they are, and is read again (ADR 64)
 * @param pictures the images of the review threads that are read to be shown, by where they are: none while one is
 *                 read, or when it could not be (ADR 84)
 * @param filter   the filter of the files: the tree shows only those listed that match it (ADR 105)
 * @param allFiles whether the tree lists all files of the commit the files end at, not only those changed (ADR 128)
 * @param tree     all files of that commit, the changed ones as they changed and the others unchanged, once they were
 *                 asked for: they are read once
 * @param unchanged the node highlighted while all files are listed, when nothing under it changed: the content panel
 *                  shows it in place of a diff (ADR 128)
 */
@With
public record CommitFilesView(
        Changes of,
        PaneId pane,
        PaneId listedIn,
        Loadable<List<CommitFile>> files,
        Option<Preview<List<CommitFile>, DiffText>> diff,
        Option<Loadable<ReviewRemarks>> remarks,
        Listed listed,
        Collapsed collapsed,
        Set<ReviewThread.Id> sending,
        Set<ReviewThread.Comment.Id> deleting,
        Set<ReviewThread.Comment.Id> reacting,
        int version,
        Map<String, Option<Picture>> pictures,
        Option<FileFilter> filter,
        boolean allFiles,
        Option<Loadable<List<CommitFile>>> tree,
        Option<UnchangedNode> unchanged) {

    /**
     * The view of the files of {@code of}, opened from the pane {@code pane} and listed in {@code listedIn}, while
     * they are read, and the review threads of a pull request.
     */
    public static CommitFilesView of(Changes of, PaneId pane, PaneId listedIn) {
        return new CommitFilesView(
                of,
                pane,
                listedIn,
                Loadable.loading(),
                Option.none(),
                Option.when(of instanceof Changes.OfPullRequest, Loadable::loading),
                Listed.ALL,
                Collapsed.NONE,
                HashSet.empty(),
                HashSet.empty(),
                HashSet.empty(),
                0,
                HashMap.empty(),
                Option.none(),
                false,
                Option.none(),
                Option.none());
    }

    /**
     * The files the tree shows: all files of the commit while they are listed, read or not, else the changed ones
     * (ADR 128).
     */
    public Loadable<List<CommitFile>> shownFiles() {
        return allFiles ? tree.getOrElse(Loadable::loading) : files;
    }

    /**
     * These listing all files of the commit when {@code all}, else the changed ones; the node without changes shown
     * goes with all files. Whether all files are still to be read is for the caller to say, by {@link #tree}.
     */
    public CommitFilesView listingAll(boolean all) {
        return withAllFiles(all).withUnchanged(all ? unchanged : Option.none());
    }

    /**
     * {@code changed}, the files the changes end at changed, with the others of {@code paths}, all files of the commit
     * they end at, as unchanged: those changed keep how, also those that are not in it, as a file the commit deleted;
     * sorted by path (ADR 128).
     */
    public static List<CommitFile> merged(List<CommitFile> changed, List<String> paths) {
        io.vavr.collection.Set<String> touched = changed.map(CommitFile::path).toSet();
        return changed.appendAll(paths.filter(path -> !touched.contains(path))
                        .map(path -> new CommitFile(path, ChangeType.UNCHANGED)))
                .sortBy(CommitFile::path);
    }

    /**
     * The files the tree lists, of those read: all of them, or those with the review threads {@link #listed} keeps,
     * once those are read too (ADR 57).
     */
    public Loadable<List<CommitFile>> listedFiles() {
        Loadable<List<CommitFile>> shown = shownFiles();
        if (listed == Listed.ALL || !(shown instanceof Loadable.Loaded<List<CommitFile>>(List<CommitFile> read))) {
            return shown;
        }
        return remarks.<Loadable<List<ReviewThread>>>map(loaded -> loaded.map(ReviewRemarks::threads))
                .getOrElse(() -> new Loadable.Loaded<>(List.empty()))
                .map(threads -> read.filter(file -> listed.keeps(file, threads)));
    }

    /**
     * The number of the pull request the files are of, if they are.
     */
    public Option<PrNumber> pullRequest() {
        return of instanceof Changes.OfPullRequest(PullRequestCommits commits)
                ? Option.of(new PrNumber(commits.number()))
                : Option.none();
    }

    /**
     * The review threads read, once they are; none for a commit or a stash.
     */
    public List<ReviewThread> threads() {
        return remarks.flatMap(Loadable::toOption).map(ReviewRemarks::threads).getOrElse(List.empty());
    }

    /**
     * These without the comment with the id {@code comment}, which GitHub deleted, as it would read them now (ADR 64):
     * the next comment of its thread is then its first, and the thread is gone with its last, unless GitHub has more of
     * it than were read. Changed so, they are of a newer version, which a read started before is older than.
     */
    public CommitFilesView withoutComment(ReviewThread.Comment.Id comment) {
        return remarks.flatMap(Loadable::toOption)
                .filter(read -> read.threads().exists(thread -> has(thread, comment)))
                .map(read -> {
                    List<ReviewThread> threads = read.threads()
                            .map(thread -> has(thread, comment) ? without(thread, comment) : thread)
                            .filter(thread -> !thread.comments().isEmpty() || thread.more() > 0);
                    return withRemarks(Option.of(new Loadable.Loaded<>(read.withThreads(threads))))
                            .withCollapsed(collapsed.readAgain(threads))
                            .withVersion(version + 1);
                })
                .getOrElse(this);
    }

    /**
     * These with the comment with the id {@code comment} saying {@code body}, as GitHub holds it once it is edited
     * (ADR 65). Changed so, they are of a newer version, which a read started before is older than.
     */
    public CommitFilesView withEdited(ReviewThread.Comment.Id comment, String body) {
        return withComment(comment, each -> each.withBody(body));
    }

    /**
     * These with {@code reaction} given to the comment with the id {@code comment} by the viewer when {@code given},
     * else taken back, as GitHub holds it once it took that (ADR 86). Changed so, they are of a newer version, which a
     * read started before is older than.
     */
    public CommitFilesView withReacted(ReviewThread.Comment.Id comment, Reaction reaction, boolean given) {
        return withComment(comment, each -> each.withReactions(each.reactions().withViewer(reaction, given)));
    }

    /**
     * These with the comment with the id {@code comment} as {@code change} makes it, as GitHub holds it once it took
     * what was sent for it, at a newer version, which a read started before is older than (ADR 64); these as they are
     * when no thread read has it.
     */
    private CommitFilesView withComment(
            ReviewThread.Comment.Id comment, Function<ReviewThread.Comment, ReviewThread.Comment> change) {
        return remarks.flatMap(Loadable::toOption)
                .filter(read -> read.threads().exists(thread -> has(thread, comment)))
                .map(read -> {
                    List<ReviewThread> threads = read.threads()
                            .map(thread -> has(thread, comment)
                                    ? thread.withComments(thread.comments()
                                            .map(each -> each.id().equals(comment) ? change.apply(each) : each))
                                    : thread);
                    return withRemarks(Option.of(new Loadable.Loaded<>(read.withThreads(threads))))
                            .withVersion(version + 1);
                })
                .getOrElse(this);
    }

    private static boolean has(ReviewThread thread, ReviewThread.Comment.Id comment) {
        return thread.comments().exists(each -> each.id().equals(comment));
    }

    private static ReviewThread without(ReviewThread thread, ReviewThread.Comment.Id comment) {
        return thread.withComments(thread.comments().filter(each -> !each.id().equals(comment)));
    }

    /**
     * Whether its review threads were read at the commits its files were read at, so that their lines number lines of
     * the diff it shows. They are not when it is of a pull request found merged or closed at newer commits: its files
     * stay at those they were read at (ADR 55).
     */
    public boolean remarksAtItsCommits() {
        return of instanceof Changes.OfPullRequest(PullRequestCommits commits)
                && remarks.flatMap(Loadable::toOption)
                        .exists(read -> read.head()
                                        .flatMap(head -> Sha.parse(head.oid()))
                                        .contains(commits.head())
                                && read.base()
                                        .flatMap(base -> Sha.parse(base.oid()))
                                        .contains(commits.base()));
    }

    /**
     * Whether the pull request the files are of was merged or closed when its review threads were read last, and
     * which: the tree then stays at the commits it was read at (ADR 55).
     */
    public Option<PullRequestDetails.State> ended() {
        return remarks.flatMap(Loadable::toOption)
                .map(ReviewRemarks::state)
                .filter(state -> state != PullRequestDetails.State.OPEN);
    }

    /**
     * The view of the files of {@code of}, while it is the one open.
     */
    public static Lens<RepoView, CommitFilesView> open(Changes of) {
        return Lens.where(
                RepoView::commitFiles,
                RepoView::withCommitFiles,
                view -> view.of().equals(of));
    }

    /**
     * Which of the files the tree lists (ADR 57).
     */
    public enum Listed {
        /**
         * Every file.
         */
        ALL,
        /**
         * The files with a review thread that is not resolved.
         */
        UNRESOLVED,
        /**
         * The files with a review thread, resolved or not.
         */
        REMARKED;

        /**
         * What is listed after this, as {@code c} cycles through them.
         */
        public Listed next() {
            return values()[(ordinal() + 1) % values().length];
        }

        /**
         * Whether {@code file} is listed, with {@code threads} the review threads of the pull request.
         */
        public boolean keeps(CommitFile file, List<ReviewThread> threads) {
            return this == ALL
                    || threads.exists(thread ->
                            (this == REMARKED || !thread.resolved()) && thread.isOn(file.path(), file.origin()));
        }
    }
}
