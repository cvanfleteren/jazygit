package net.vanfleteren.jazygit.core.git.history;

import static net.vanfleteren.jazygit.core.git.StubGit.ran;

import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.core.git.model.InProgressAction;
import net.vanfleteren.jazygit.core.git.model.MergeMode;
import net.vanfleteren.jazygit.core.git.process.GitFailure;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.core.repo.Commit;
import net.vanfleteren.jazygit.core.repo.InProgress;
import net.vanfleteren.jazygit.core.repo.Revision;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.core.repo.StackLayer;

/**
 * {@link Integrate} for tests: merging, cherry-picking, reverting and rebasing succeed without running anything.
 * Fakes override what they need.
 */
public interface StubIntegrate extends Integrate {

    @Override
    default Either<GitFailure, List<ExecutedCommand>> merge(BranchRef branch, MergeMode mode) {
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
    default Either<GitFailure, List<ExecutedCommand>> rebase(BranchRef onto) {
        return ran();
    }

    @Override
    default Either<GitFailure, List<ExecutedCommand>> rebaseStack(Revision base, List<StackLayer.Commits> layers) {
        return ran();
    }
}
