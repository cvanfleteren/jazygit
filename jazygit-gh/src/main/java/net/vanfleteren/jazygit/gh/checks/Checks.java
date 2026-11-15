package net.vanfleteren.jazygit.gh.checks;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.model.*;
import net.vanfleteren.jazygit.gh.process.GhError;

/**
 * How the CI checks are doing on the branches of the repository, how long they take, and which branches are
 * protected (ADR 114).
 */
public interface Checks {

    /**
     * How the CI checks are doing on the tip of each branch of the repository.
     */
    Either<GhError, BranchChecks> ofBranches();

    /**
     * The checks on the tips of {@code branches} of the repository, one by one and with their timing.
     */
    Either<GhError, BranchCheckRuns> runs(List<String> branches);

    /**
     * The checks that ran lately on the repository, to learn how long they take from: on the pull requests merged
     * last and on the last commits of its default branch.
     */
    Either<GhError, RecentChecks> recent();

    /**
     * The builds on the commits of {@code branch} of the repository, some commits at a time, the newest first: from
     * {@code after}, where the commits read before end, or from its tip without (ADR 131). None when the branch is not
     * on GitHub.
     */
    Either<GhError, Option<BuildsPage>> builds(String branch, Option<String> after);

    /**
     * The names of the protected branches of the repository (ADR 72).
     */
    Either<GhError, List<GhBranch>> protectedBranches();
}
