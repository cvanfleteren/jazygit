package net.vanfleteren.jazygit.git;

import static net.vanfleteren.jazygit.git.StubGit.ran;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.model.AllBranches;
import net.vanfleteren.jazygit.git.model.BaseDistance;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.RemoteRef;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.process.ExecutedCommand;

/**
 * {@link Branches} for tests: no branches, none with a parent, and changing them succeeds without running anything.
 * Fakes override what they need.
 */
public interface StubBranches extends Branches {

    /**
     * The local branches {@link #all()} reads, for a stub that only has those.
     */
    default Either<ProcessError, List<Branch>> local() {
        return Either.right(List.empty());
    }

    /**
     * The remote-tracking branches {@link #all()} reads, for a stub that only has those.
     */
    default Either<ProcessError, List<RemoteRef>> remote() {
        return Either.right(List.empty());
    }

    @Override
    default Either<ProcessError, AllBranches> all() {
        return local().flatMap(local -> remote().map(remote -> new AllBranches(local, remote)));
    }

    @Override
    default Either<GitFailure<GitError>, List<ExecutedCommand>> deleteLocal(BranchName name) {
        return ran();
    }

    @Override
    default Either<GitFailure<ProcessError>, List<ExecutedCommand>> forceDeleteLocal(BranchName name) {
        return ran();
    }

    @Override
    default Either<ProcessError, Integer> countOnlyOn(BranchName name) {
        return Either.right(0);
    }

    @Override
    default Either<GitFailure<ProcessError>, List<ExecutedCommand>> rename(BranchName name, BranchName newName) {
        return ran();
    }

    @Override
    default Either<ProcessError, Option<BranchName>> parent(BranchName branch) {
        return Either.right(Option.none());
    }

    @Override
    default Either<ProcessError, Map<BranchName, BranchName>> parents() {
        return Either.right(HashMap.empty());
    }

    @Override
    default Map<BranchName, BaseDistance> baseDistance(
            Map<BranchRef, Sha> tips, Map<BranchName, BranchName> parents, Option<BranchName> main) {
        return HashMap.empty();
    }
}
