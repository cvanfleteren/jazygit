package net.vanfleteren.jazygit.git.model;

import io.vavr.collection.List;
import io.vavr.control.Option;
import lombok.With;

/**
 * The state of the working tree and HEAD, as reported by {@code git status}.
 *
 * @param files      the changed and untracked files, sorted by path
 * @param inProgress the operation the repository is in the middle of, if any
 */
@With
public record RepoStatus(Head head, List<FileEntry> files, Option<InProgress> inProgress) {

    /**
     * The status of a repository that is in the middle of nothing.
     */
    public RepoStatus(Head head, List<FileEntry> files) {
        this(head, files, Option.none());
    }

    /**
     * How many files have conflicts still to resolve.
     */
    public int conflicts() {
        return files.count(file -> file.conflict().isDefined());
    }

    /**
     * The commit HEAD points to; none in a repository without commits.
     */
    public Option<Sha> headCommit() {
        return head.commit();
    }
}
