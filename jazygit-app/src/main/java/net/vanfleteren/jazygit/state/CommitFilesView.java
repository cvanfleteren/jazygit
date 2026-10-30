package net.vanfleteren.jazygit.state;

import io.vavr.collection.List;
import io.vavr.control.Option;
import lombok.With;
import net.vanfleteren.jazygit.gh.model.ReviewRemarks;
import net.vanfleteren.jazygit.gh.model.ReviewThread;
import net.vanfleteren.jazygit.git.model.CommitFile;
import net.vanfleteren.jazygit.git.model.DiffOptions;
import net.vanfleteren.jazygit.git.model.DiffText;
import net.vanfleteren.jazygit.git.model.PullRequestCommits;
import net.vanfleteren.jazygit.git.model.Sha;

/**
 * The files a commit, a stash or a pull request changed, which the files pane lists as a tree in place of the changed
 * files of the working tree while it is open (ADR 44, 55).
 *
 * @param of       the commit, the stash or the pull request
 * @param pane     the id of the pane it was opened from, which gets the focus back when it is closed
 * @param listedIn the id of the pane that lists the files: when another pane gets the focus, it closes
 * @param files    the files, as they are read
 * @param diff     what changed in the files of the node highlighted in the tree, keyed by them, once one is
 *                 highlighted
 * @param remarks  the review threads of a pull request, as they are read; none for a commit or a stash (ADR 55)
 * @param opened   whether every review thread is shown open, the resolved ones and the hidden comments too
 */
@With
public record CommitFilesView(
        Changes of,
        String pane,
        String listedIn,
        Loadable<List<CommitFile>> files,
        Option<Preview<List<CommitFile>, DiffText>> diff,
        Option<Loadable<ReviewRemarks>> remarks,
        boolean opened) {

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
                false);
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
     * Whose changes the files are.
     */
    public sealed interface Changes {

        Sha sha();

        /**
         * The changes of a commit, of a merge against its first parent.
         */
        record OfCommit(Sha sha) implements Changes {}

        /**
         * The changes of a stash, its untracked files included.
         */
        record OfStash(Sha sha) implements Changes {}

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
