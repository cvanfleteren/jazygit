package net.vanfleteren.jazygit.gh;

import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.gh.model.*;

/**
 * {@link Checks} through gh, of the repository gh finds from the git remotes of its working tree (ADR 114).
 */
final class CliChecks implements Checks {

    private final GhProcess process;

    CliChecks(GhProcess process) {
        this.process = process;
    }

    @Override
    public Either<GhError, BranchChecks> ofBranches() {
        return process.readJson(BranchesQuery.COMMAND, BranchesQuery.DECODER);
    }

    @Override
    public Either<GhError, BranchCheckRuns> runs(List<String> branches) {
        return process.readJson(BranchCheckRunsQuery.command(branches), BranchCheckRunsQuery.decoder(branches.size()));
    }

    @Override
    public Either<GhError, RecentChecks> recent() {
        return process.readJson(RecentChecksQuery.COMMAND, RecentChecksQuery.DECODER);
    }

    @Override
    public Either<GhError, List<GhBranch>> protectedBranches() {
        return process.readJson(ProtectedBranchesQuery.COMMAND, ProtectedBranchesQuery.DECODER);
    }
}
