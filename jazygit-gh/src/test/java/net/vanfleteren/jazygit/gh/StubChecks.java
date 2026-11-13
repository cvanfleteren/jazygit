package net.vanfleteren.jazygit.gh;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.gh.model.*;

/**
 * {@link Checks} for tests: a repository {@code owner/repo} without branches, checks or protected branches. Fakes
 * override what they need.
 */
public interface StubChecks extends Checks {

    /**
     * A repository {@code owner/repo} without branches.
     */
    @Override
    default Either<GhError, BranchChecks> ofBranches() {
        return Either.right(new BranchChecks("owner/repo", HashMap.empty()));
    }

    /**
     * The repository {@code owner/repo} without checks on any branch.
     */
    @Override
    default Either<GhError, BranchCheckRuns> runs(List<String> branches) {
        return Either.right(new BranchCheckRuns("owner/repo", HashMap.empty()));
    }

    /**
     * The repository {@code owner/repo} without checks that ran.
     */
    @Override
    default Either<GhError, RecentChecks> recent() {
        return Either.right(new RecentChecks("owner/repo", List.empty()));
    }

    /**
     * A repository that protects no branch.
     */
    @Override
    default Either<GhError, List<GhBranch>> protectedBranches() {
        return Either.right(List.empty());
    }
}
