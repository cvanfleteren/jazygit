package net.vanfleteren.jazygit.git;

import static net.vanfleteren.jazygit.git.StubGit.ran;

import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.git.model.InProgress;
import net.vanfleteren.jazygit.git.model.InProgressAction;
import net.vanfleteren.jazygit.git.model.MergeMode;
import net.vanfleteren.jazygit.git.model.Revision;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.model.StackLayer;
import net.vanfleteren.jazygit.process.ExecutedCommand;

/**
 * {@link Integrate} for tests: merging, cherry-picking, reverting and rebasing succeed without running anything.
 * Fakes override what they need.
 */
public interface StubIntegrate extends Integrate {

    @Override
    default Either<GitFailure, List<ExecutedCommand>> merge(BranchName branch, MergeMode mode) {
        return ran();
    }

    @Override
    default Either<GitFailure, List<ExecutedCommand>> cherryPick(Sha sha) {
        return ran();
    }

    @Override
    default Either<GitFailure, List<ExecutedCommand>> revert(Commit commit) {
        return ran();
    }

    @Override
    default Either<GitFailure, List<ExecutedCommand>> continueOrAbort(InProgress inProgress, InProgressAction action) {
        return ran();
    }

    @Override
    default Either<GitFailure, List<ExecutedCommand>> rebase(BranchName onto) {
        return ran();
    }

    @Override
    default Either<GitFailure, List<ExecutedCommand>> rebaseStack(Revision base, List<StackLayer.Commits> layers) {
        return ran();
    }
}
