package net.vanfleteren.jazygit.core.git.branches;

import static net.vanfleteren.jazygit.core.git.StubGit.ran;

import io.vavr.collection.HashMap;
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
import net.vanfleteren.jazygit.core.repo.Branch;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.core.repo.CaughtUpBranch;
import net.vanfleteren.jazygit.core.repo.RemoteRef;
import net.vanfleteren.jazygit.core.repo.Sha;

/**
 * {@link Branches} for tests: no branches, none with a parent, and changing them succeeds without running anything.
 * Fakes override what they need.
 */
public interface StubBranches extends Branches {

    /**
     * The local branches {@link #all()} reads, for a stub that only has those.
     */
    default Either<GitError, List<Branch>> local() {
        return Either.right(List.empty());
    }

    /**
     * The remote-tracking branches {@link #all()} reads, for a stub that only has those.
     */
    default Either<GitError, List<RemoteRef>> remote() {
        return Either.right(List.empty());
    }

    @Override
    default Either<GitError, AllBranches> all() {
        return local().flatMap(local -> remote().map(remote -> new AllBranches(local, remote)));
    }

    @Override
    default Either<GitFailure, List<ExecutedCommand>> deleteLocal(BranchName name) {
        return ran();
    }

    @Override
    default Either<GitFailure, List<ExecutedCommand>> forceDeleteLocal(BranchName name) {
        return ran();
    }

    @Override
    default Either<GitError, Integer> countOnlyOn(BranchName name) {
        return Either.right(0);
    }

    @Override
    default Either<GitFailure, List<ExecutedCommand>> rename(BranchName name, BranchName newName) {
        return ran();
    }

    @Override
    default Either<GitError, Option<BranchName>> parent(BranchName branch) {
        return Either.right(Option.none());
    }

    @Override
    default Either<GitError, Map<BranchName, BranchName>> parents() {
        return Either.right(HashMap.empty());
    }

    @Override
    default Either<GitFailure, List<ExecutedCommand>> setParent(BranchName branch, Option<BranchName> parent) {
        return ran();
    }

    @Override
    default Either<GitError, BaseCandidates> baseCandidates(BranchName branch, Sha tip) {
        return Either.right(new BaseCandidates(List.empty(), Option.none()));
    }

    @Override
    default Either<GitError, List<BranchName>> containing(Sha commit) {
        return Either.right(List.empty());
    }

    @Override
    default Map<BranchName, BaseDistance> baseDistance(
            Map<BranchRef, Sha> tips, Map<BranchName, BranchName> parents, Option<BranchName> main) {
        return HashMap.empty();
    }

    @Override
    default Either<GitFailure, List<ExecutedCommand>> catchUp(List<CaughtUpBranch> branches) {
        return ran();
    }
}
