package net.vanfleteren.jazygit.git.model;

import io.vavr.collection.List;
import io.vavr.control.Option;

/**
 * The state of the working tree and HEAD, as reported by {@code git status}.
 *
 * @param files the changed and untracked files, sorted by path
 */
public record RepoStatus(Head head, List<FileEntry> files) {

    /**
     * What HEAD points to.
     */
    public sealed interface Head {

        /**
         * A checked out branch.
         *
         * @param tip the commit it points to; none in a repository without commits
         */
        record OnBranch(Branch.Name branch, Option<Sha> tip) implements Head {
        }

        /**
         * A commit checked out without a branch.
         */
        record Detached(Sha at) implements Head {
        }

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
    }

    /**
     * The commit HEAD points to; none in a repository without commits.
     */
    public Option<Sha> headCommit() {
        return head.commit();
    }
}
