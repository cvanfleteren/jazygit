package net.vanfleteren.jazygit.core.git.model;

import io.vavr.collection.List;
import io.vavr.control.Option;
import lombok.With;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.Head;
import net.vanfleteren.jazygit.core.repo.InProgress;
import net.vanfleteren.jazygit.core.repo.Midway;
import net.vanfleteren.jazygit.core.repo.Plan;
import net.vanfleteren.jazygit.core.repo.RepoPath;
import net.vanfleteren.jazygit.core.repo.Sha;

/**
 * The state of the working tree and HEAD, as reported by {@code git status}.
 *
 * @param files      the changed and untracked files, sorted by path
 * @param inProgress the operation the repository is in the middle of, if any
 * @param resolved   the files whose conflicts were resolved since it stopped, which git keeps what they were in
 *                   conflict with for (ADR 198); none when it is in the middle of nothing
 * @param plan       the steps of what it is in the middle of, as git keeps them (ADR 199); none for a merge, for a
 *                   rebase of the apply backend, and when it is in the middle of nothing
 */
@With
public record RepoStatus(
        Head head, List<FileEntry> files, Option<InProgress> inProgress, List<RepoPath> resolved, Option<Plan> plan) {

    /**
     * The status of a repository in the middle of {@code inProgress}, if anything, whose steps are not known.
     */
    public RepoStatus(Head head, List<FileEntry> files, Option<InProgress> inProgress, List<RepoPath> resolved) {
        this(head, files, inProgress, resolved, Option.none());
    }

    /**
     * The status of a repository in the middle of {@code inProgress}, if anything, with no conflict resolved yet.
     */
    public RepoStatus(Head head, List<FileEntry> files, Option<InProgress> inProgress) {
        this(head, files, inProgress, List.empty());
    }

    /**
     * The status of a repository that is in the middle of nothing.
     */
    public RepoStatus(Head head, List<FileEntry> files) {
        this(head, files, Option.none(), List.empty());
    }

    /**
     * How many files have conflicts still to resolve.
     */
    public int conflicts() {
        return files.count(file -> file.conflict().isDefined());
    }

    /**
     * What the repository is in the middle of, if anything, with how many files still have conflicts (ADR 197).
     */
    public Option<Midway> midway() {
        return inProgress.map(operation -> new Midway(operation, conflicts()));
    }

    /**
     * The branch a rebase works on while it detaches HEAD, if one does: the branch worked on here meanwhile, which
     * the app shows as checked out (ADR 197). None for a rebase of a detached HEAD.
     */
    public Option<BranchName> rebasedBranch() {
        return head instanceof Head.Detached
                ? inProgress.flatMap(operation ->
                        operation instanceof InProgress.Rebasing rebasing ? rebasing.branch() : Option.none())
                : Option.none();
    }

    /**
     * The commit HEAD points to; none in a repository without commits.
     */
    public Option<Sha> headCommit() {
        return head.commit();
    }
}
