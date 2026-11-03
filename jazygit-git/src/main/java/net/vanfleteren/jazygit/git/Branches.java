package net.vanfleteren.jazygit.git;

import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.model.AllBranches;
import net.vanfleteren.jazygit.git.model.BaseDistance;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.process.ExecutedCommand;

/**
 * Listing, renaming and deleting branches, as git branch does, and the branch each was made from (ADR 69).
 */
public interface Branches {

    /**
     * The local and the remote-tracking branches, read at the same moment.
     */
    Either<ProcessError, AllBranches> all();

    /**
     * Deletes the local branch {@code name} when it is merged; git refuses one that is not, which is a
     * {@link GitError.NotFullyMerged}.
     */
    Either<GitFailure<GitError>, List<ExecutedCommand>> deleteLocal(BranchName name);

    /**
     * Deletes the local branch {@code name}, merged or not.
     */
    Either<GitFailure<ProcessError>, List<ExecutedCommand>> forceDeleteLocal(BranchName name);

    /**
     * How many commits of the local branch {@code name} no other branch, remote-tracking branch or tag has: the ones
     * deleting it loses.
     */
    Either<ProcessError, Integer> countOnlyOn(BranchName name);

    /**
     * Renames the local branch {@code name} to {@code newName}; it keeps following the same upstream. Fails when a
     * branch is called {@code newName} already.
     */
    Either<GitFailure<ProcessError>, List<ExecutedCommand>> rename(BranchName name, BranchName newName);

    /**
     * The branch {@code branch} was made from, if that was recorded: as {@code gh} does, in
     * {@code branch.<name>.gh-merge-base}.
     */
    Either<ProcessError, Option<BranchName>> parent(BranchName branch);

    /**
     * The branch each branch was made from, of those that have one recorded, as {@link #parent} reads it.
     */
    Either<ProcessError, Map<BranchName, BranchName>> parents();

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
