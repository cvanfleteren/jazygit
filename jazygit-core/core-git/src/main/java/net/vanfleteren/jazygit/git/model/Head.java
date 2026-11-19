package net.vanfleteren.jazygit.git.model;

import io.vavr.control.Option;

/**
 * What the HEAD of a working tree points to.
 */
public sealed interface Head {

    static Head onBranch(BranchName branch, Sha tip) {
        return new OnBranch(branch, Option.of(tip));
    }

    /**
     * The commit HEAD points to; none in a repository without commits.
     */
    default Option<Sha> commit() {
        return switch (this) {
            case OnBranch(BranchName branch, Option<Sha> tip) -> tip;
            case Detached(Sha at) -> Option.of(at);
        };
    }

    /**
     * The checked out branch; none for a detached HEAD.
     */
    default Option<BranchName> branchName() {
        return this instanceof OnBranch(BranchName branch, Option<Sha> tip) ? Option.of(branch) : Option.none();
    }

    /**
     * A checked out branch.
     *
     * @param tip the commit it points to; none in a repository without commits
     */
    record OnBranch(BranchName branch, Option<Sha> tip) implements Head {}

    /**
     * A commit checked out without a branch.
     */
    record Detached(Sha at) implements Head {}
}
