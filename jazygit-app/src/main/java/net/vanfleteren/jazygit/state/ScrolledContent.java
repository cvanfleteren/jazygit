package net.vanfleteren.jazygit.state;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.gh.model.PullRequestPreview;
import net.vanfleteren.jazygit.gh.model.PullRequestSummary;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.git.model.CommitFile;
import net.vanfleteren.jazygit.git.model.DiffText;
import net.vanfleteren.jazygit.git.model.Sha;

/**
 * What an area of the content panel shows, which where it is {@link Scrolled scrolled} to holds for.
 */
public sealed interface ScrolledContent {

    /**
     * Whether {@code other} shows the changes this shows, read again, as after {@code R} or Ctrl+W: the cursor of a
     * rich diff then stays on its stop (ADR 60).
     */
    default boolean sameChanges(ScrolledContent other) {
        return switch (this) {
            case CommitChanges(Commit commit, var ignored) ->
                other instanceof CommitChanges(Commit same, var also)
                        && same.sha().equals(commit.sha());
            case StashChanges(Sha sha, var ignored) ->
                other instanceof StashChanges(Sha same, var also) && same.equals(sha);
            case FileChanges(Sha sha, List<CommitFile> files, var ignored) ->
                other instanceof FileChanges(Sha same, List<CommitFile> those, var also)
                        && same.equals(sha)
                        && those.map(CommitFile::path).equals(files.map(CommitFile::path));
            default -> equals(other);
        };
    }

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
     * What the commit or the stash {@code sha} changed in {@code files}.
     */
    record FileChanges(Sha sha, List<CommitFile> files, DiffText diff) implements ScrolledContent {}

    /**
     * A pull request, and what it says and changes.
     */
    record PullRequest(PullRequestSummary pullRequest, PullRequestPreview preview) implements ScrolledContent {}
}
