package net.vanfleteren.jazygit.features.github.remarks;

import io.vavr.collection.List;
import io.vavr.collection.Set;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.gh.model.PullRequestDetails;
import net.vanfleteren.jazygit.core.gh.model.ReviewRemarks;
import net.vanfleteren.jazygit.core.gh.model.ReviewThread;
import net.vanfleteren.jazygit.core.git.model.CommitFile;
import net.vanfleteren.jazygit.core.git.model.DiffText;
import net.vanfleteren.jazygit.core.repo.PrNumber;
import net.vanfleteren.jazygit.core.repo.PullRequestCommits;
import net.vanfleteren.jazygit.kernel.state.Loadable;
import net.vanfleteren.jazygit.kernel.state.diff.Changes;
import net.vanfleteren.jazygit.kernel.state.diff.Collapsed;
import net.vanfleteren.jazygit.kernel.state.diff.RemarkThread;
import net.vanfleteren.jazygit.kernel.state.view.CommitFilesView;
import net.vanfleteren.jazygit.kernel.state.view.Preview;

/**
 * The files of a pull request, as the kernel lists them, with the review threads GitHub's part keeps of it (ADR 171).
 *
 * @param view    the files, as they are listed
 * @param commits the commits of the pull request the files are of
 * @param read    its review threads, as GitHub's part keeps them
 */
public record PrFiles(CommitFilesView view, PullRequestCommits commits, PrRemarks read) {

    /**
     * What the files are of.
     */
    public Changes of() {
        return view.of();
    }

    /**
     * The same pull request at the commits {@code moved} it moved on to, of the same local branch (ADR 192).
     */
    public Changes.OfPullRequest at(PullRequestCommits moved) {
        return view.of() instanceof Changes.OfPullRequest pullRequest
                ? pullRequest.at(moved)
                : new Changes.OfPullRequest(moved);
    }

    /**
     * What changed in the files of the node highlighted, once one is.
     */
    public Option<Preview<List<CommitFile>, DiffText>> diff() {
        return view.diff();
    }

    /**
     * The number of the pull request.
     */
    public PrNumber number() {
        return read.number();
    }

    /**
     * The number of the pull request, as the files of one have it.
     */
    public Option<PrNumber> pullRequest() {
        return Option.of(read.number());
    }

    /**
     * Its review threads, as they are read.
     */
    public Option<Loadable<ReviewRemarks>> remarks() {
        return Option.of(read.remarks());
    }

    /**
     * Its review threads read, once they are.
     */
    public List<ReviewThread> threads() {
        return read.read();
    }

    /**
     * Its review threads read, as the kernel's, which it collapses by (ADR 62, 171).
     */
    public List<RemarkThread> remarkThreads() {
        return read.threads().toOption().getOrElse(List.empty());
    }

    public Set<ReviewThread.Id> sending() {
        return read.sending();
    }

    public Set<ReviewThread.Comment.Id> deleting() {
        return read.deleting();
    }

    public Set<ReviewThread.Comment.Id> reacting() {
        return read.reacting();
    }

    public int version() {
        return read.version();
    }

    /**
     * Which threads and comments are collapsed, which the kernel keeps with the files (ADR 62).
     */
    public Collapsed collapsed() {
        return view.collapsed();
    }

    /**
     * Whether its review threads were read at the commits its files were read at (ADR 55).
     */
    public boolean remarksAtItsCommits() {
        return read.atCommits(commits);
    }

    /**
     * Whether the pull request was merged or closed when its review threads were read last, and which (ADR 55).
     */
    public Option<PullRequestDetails.State> ended() {
        return read.ended();
    }
}
