package net.vanfleteren.jazygit.state;

import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.collection.Set;
import io.vavr.control.Option;
import lombok.With;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails;
import net.vanfleteren.jazygit.gh.model.ReviewRemarks;
import net.vanfleteren.jazygit.gh.model.ReviewThread;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.CommitFile;
import net.vanfleteren.jazygit.git.model.DiffOptions;
import net.vanfleteren.jazygit.git.model.DiffText;
import net.vanfleteren.jazygit.git.model.PullRequestCommits;
import net.vanfleteren.jazygit.git.model.Sha;

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
 * @param opened   whether every review thread is shown open, the resolved ones and the hidden comments too
 * @param listed   which of the files are listed: all of them, or only those with review threads (ADR 57)
 * @param toggled  the ids of the review threads opened or closed against {@code opened}, one by one (ADR 60)
 * @param sending  the ids of the review threads a reply or a resolving is sent for, until the threads are read again
 *                 after it was sent, or the send failed (ADR 60)
 */
@With
public record CommitFilesView(
        Changes of,
        String pane,
        String listedIn,
        Loadable<List<CommitFile>> files,
        Option<Preview<List<CommitFile>, DiffText>> diff,
        Option<Loadable<ReviewRemarks>> remarks,
        boolean opened,
        Listed listed,
        Set<String> toggled,
        Set<String> sending) {

    /**
     * The view of the files of {@code of}, opened from the pane {@code pane} and listed in {@code listedIn}, while
     * they are read, and the review threads of a pull request.
     */
    public static CommitFilesView of(Changes of, String pane, String listedIn) {
        return new CommitFilesView(
                of,
                pane,
                listedIn,
                Loadable.loading(),
                Option.none(),
                Option.when(of instanceof Changes.OfPullRequest, Loadable::loading),
                false,
                Listed.ALL,
                HashSet.empty(),
                HashSet.empty());
    }

    /**
     * The files the tree lists, of those read: all of them, or those with the review threads {@link #listed} keeps,
     * once those are read too (ADR 57).
     */
    public Loadable<List<CommitFile>> listedFiles() {
        if (listed == Listed.ALL || !(files instanceof Loadable.Loaded<List<CommitFile>>(List<CommitFile> read))) {
            return files;
        }
        return remarks.<Loadable<List<ReviewThread>>>map(loaded -> loaded.map(ReviewRemarks::threads))
                .getOrElse(() -> new Loadable.Loaded<>(List.empty()))
                .map(threads -> read.filter(file -> listed.keeps(file, threads)));
    }

    /**
     * The number of the pull request the files are of, if they are.
     */
    public Option<Integer> pullRequest() {
        return of instanceof Changes.OfPullRequest(PullRequestCommits commits)
                ? Option.of(commits.number())
                : Option.none();
    }

    /**
     * The review threads read, once they are; none for a commit or a stash.
     */
    public List<ReviewThread> threads() {
        return remarks.flatMap(Loadable::toOption).map(ReviewRemarks::threads).getOrElse(List.empty());
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
     * The load of the files.
     */
    public Cmd.LoadCommitFiles load() {
        return new Cmd.LoadCommitFiles(of);
    }

    /**
     * The load of the diff of {@code files}, read with {@code options}.
     */
    public Cmd.LoadCommitFileDiff diffLoad(List<CommitFile> files, DiffOptions options) {
        return new Cmd.LoadCommitFileDiff(of, files, options);
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

    /**
     * Whose changes the files are.
     */
    public sealed interface Changes {

        Sha sha();

        /**
         * Whether {@code other} are the changes of the same commit, stash or pull request: a pull request stays the
         * same when its head or its base moved on, as after a force-push (ADR 60).
         */
        default boolean sameAs(Changes other) {
            return this instanceof OfPullRequest(PullRequestCommits commits)
                    ? other instanceof OfPullRequest(PullRequestCommits those)
                            && those.number() == commits.number()
                            && those.repository().equals(commits.repository())
                            && those.host().equals(commits.host())
                    : equals(other);
        }

        /**
         * The changes of a commit, of a merge against its first parent.
         */
        record OfCommit(Sha sha) implements Changes {}

        /**
         * The changes of a stash, its untracked files included.
         */
        record OfStash(Sha sha) implements Changes {}

        /**
         * The changes of a branch against its base, as a pull request of it would show them: its tip against the merge
         * base of that and the tip of its base (ADR 61).
         *
         * @param branch the branch
         * @param base   the base of ADR 20 it is compared with, and the commit that was at
         * @param sha    the commit the branch was at
         */
        record OfBranch(BranchName branch, Cmd.LoadAhead.Base base, Sha sha) implements Changes {}

        /**
         * The changes of a pull request on GitHub, as GitHub shows them: its head against the merge base of its head
         * and its base (ADR 55).
         */
        record OfPullRequest(PullRequestCommits commits) implements Changes {

            /**
             * Its head commit.
             */
            @Override
            public Sha sha() {
                return commits.head();
            }
        }
    }
}
