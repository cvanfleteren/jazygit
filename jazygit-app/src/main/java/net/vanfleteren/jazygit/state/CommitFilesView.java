package net.vanfleteren.jazygit.state;

import io.vavr.collection.List;
import io.vavr.control.Option;
import lombok.With;
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
 */
@With
public record CommitFilesView(
        Changes of,
        String pane,
        String listedIn,
        Loadable<List<CommitFile>> files,
        Option<Preview<List<CommitFile>, DiffText>> diff) {

    /**
     * The view of the files of {@code of}, opened from the pane {@code pane} and listed in {@code listedIn}, while
     * they are read.
     */
    public static CommitFilesView of(Changes of, String pane, String listedIn) {
        return new CommitFilesView(of, pane, listedIn, Loadable.loading(), Option.none());
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
