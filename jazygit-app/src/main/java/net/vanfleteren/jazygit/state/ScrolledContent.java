package net.vanfleteren.jazygit.state;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.gh.model.PullRequestPreview;
import net.vanfleteren.jazygit.gh.model.PullRequestSummary;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.git.model.DiffText;
import net.vanfleteren.jazygit.git.model.Sha;

/**
 * What an area of the content panel shows, which where it is {@link Scrolled scrolled} to holds for.
 */
public sealed interface ScrolledContent {

    /**
     * A diff of the working tree: the staged or the unstaged changes.
     */
    record Diff(DiffText diff) implements ScrolledContent {}

    /**
     * The log of a branch.
     */
    record BranchLog(List<Commit> commits) implements ScrolledContent {}

    /**
     * A commit and what it changed, in the commits pane or in a divergence.
     */
    record CommitChanges(Commit commit, DiffText changes) implements ScrolledContent {}

    /**
     * What the stash {@code sha} stashed. Named by its sha, as its {@code stash@{n}} changes as stashes are added and
     * dropped.
     */
    record StashChanges(Sha sha, DiffText changes) implements ScrolledContent {}

    /**
     * A pull request, and what it says and changes.
     */
    record PullRequest(PullRequestSummary pullRequest, PullRequestPreview preview) implements ScrolledContent {}
}
