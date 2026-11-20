package net.vanfleteren.jazygit.core.git.branches;

import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.model.BaseCandidates;
import net.vanfleteren.jazygit.core.git.model.BaseDistance;
import net.vanfleteren.jazygit.core.git.process.GitError;
import net.vanfleteren.jazygit.core.git.process.GitFailure;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.core.repo.AllBranches;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.core.repo.CaughtUpBranch;
import net.vanfleteren.jazygit.core.repo.Sha;

/**
 * Listing, renaming and deleting branches, as git branch does, and the branch each was made from (ADR 69).
 */
public interface Branches {

    /**
     * The local and the remote-tracking branches, read at the same moment.
     */
    Either<GitError, AllBranches> all();

    /**
     * Deletes the local branch {@code name} when it is merged; git refuses one that is not, which is a
     * {@link GitError.NotFullyMerged}.
     */
    Either<GitFailure, List<ExecutedCommand>> deleteLocal(BranchName name);

    /**
     * Deletes the local branch {@code name}, merged or not.
     */
    Either<GitFailure, List<ExecutedCommand>> forceDeleteLocal(BranchName name);

    /**
     * How many commits of the local branch {@code name} no other branch, remote-tracking branch or tag has: the ones
     * deleting it loses.
     */
    Either<GitError, Integer> countOnlyOn(BranchName name);

    /**
     * Renames the local branch {@code name} to {@code newName}; it keeps following the same upstream. Fails when a
     * branch is called {@code newName} already.
     */
    Either<GitFailure, List<ExecutedCommand>> rename(BranchName name, BranchName newName);

    /**
     * Has each of {@code branches} at its branch on the remote, as that was last fetched (ADR 99): makes the ones
     * to make, following it, with their parent recorded, and fast-forwards the others. Either all of them are moved or
     * none: git refuses all of them when one is no fast-forward any more, or is checked out in a worktree. Following and
     * recording the parent come after, and are not undone when they fail.
     */
    Either<GitFailure, List<ExecutedCommand>> catchUp(List<CaughtUpBranch> branches);

    /**
     * The branch {@code branch} was made from, if that was recorded: as {@code gh} does, in
     * {@code branch.<name>.gh-merge-base}.
     */
    Either<GitError, Option<BranchName>> parent(BranchName branch);

    /**
     * The branch each branch was made from, of those that have one recorded, as {@link #parent} reads it.
     */
    Either<GitError, Map<BranchName, BranchName>> parents();

    /**
     * Records {@code parent} as the branch {@code branch} is built on, in {@code branch.<name>.gh-merge-base}, or
     * removes what is recorded for none (ADR 143).
     */
    Either<GitFailure, List<ExecutedCommand>> setParent(BranchName branch, Option<BranchName> parent);

    /**
     * The branches {@code branch}, at {@code tip}, could be built on, with how far each is from it, in one
     * {@code for-each-ref}, and the branch its reflog says it was made from (ADR 143).
     */
    Either<GitError, BaseCandidates> baseCandidates(BranchName branch, Sha tip);

    /**
     * The local branches that have {@code commit}, in one {@code for-each-ref --contains}: those at it, and those
     * built on it (ADR 148).
     */
    Either<GitError, List<BranchName>> containing(Sha commit);

    /**
     * How far each local branch is from its base, by its name, for those that have a base (ADR 46): the branch it was
     * made from, as {@code parents} has it, else {@code main}; of each, the one on {@code origin} when it is there,
     * else the local one; never the branch itself. The branches and their bases are those of {@code tips}, at the
     * commits it has them at (ADR 49). The branches of a base whose count cannot be read are left out.
     *
     * @param tips the commit of each local branch and of each branch on {@code origin}, as the branches read last
     *             have them
     */
    Map<BranchName, BaseDistance> baseDistance(
            Map<BranchRef, Sha> tips, Map<BranchName, BranchName> parents, Option<BranchName> main);
}
