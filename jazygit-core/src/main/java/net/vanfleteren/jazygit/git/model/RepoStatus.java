package net.vanfleteren.jazygit.git.model;

import io.vavr.collection.List;
import io.vavr.control.Option;

/**
 * The state of the working tree and HEAD, as reported by {@code git status}.
 *
 * @param files      the changed and untracked files, sorted by path
 * @param inProgress the operation the repository is in the middle of, if any
 */
public record RepoStatus(Head head, List<FileEntry> files, Option<InProgress> inProgress) {

    /**
     * The status of a repository that is in the middle of nothing.
     */
    public RepoStatus(Head head, List<FileEntry> files) {
        this(head, files, Option.none());
    }

    public RepoStatus withInProgress(Option<InProgress> inProgress) {
        return new RepoStatus(head, files, inProgress);
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

    /**
     * What HEAD points to.
     */
    public sealed interface Head {

        static Head onBranch(Branch.Name branch, Sha tip) {
            return new OnBranch(branch, Option.of(tip));
        }

        /**
         * The commit HEAD points to; none in a repository without commits.
         */
        default Option<Sha> commit() {
            return switch (this) {
                case OnBranch(Branch.Name branch, Option<Sha> tip) -> tip;
                case Detached(Sha at) -> Option.of(at);
            };
        }

        /**
         * The checked out branch; none for a detached HEAD.
         */
        default Option<Branch.Name> branchName() {
            return this instanceof OnBranch(Branch.Name branch, Option<Sha> tip) ? Option.of(branch) : Option.none();
        }

        /**
         * A checked out branch.
         *
         * @param tip the commit it points to; none in a repository without commits
         */
        record OnBranch(Branch.Name branch, Option<Sha> tip) implements Head {}

        /**
         * A commit checked out without a branch.
         */
        record Detached(Sha at) implements Head {}
    }
}
