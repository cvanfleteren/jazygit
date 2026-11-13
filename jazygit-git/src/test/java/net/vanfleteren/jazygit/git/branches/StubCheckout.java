package net.vanfleteren.jazygit.git.branches;

import static net.vanfleteren.jazygit.git.StubGit.ran;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.git.model.Revision;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.process.GitFailure;
import net.vanfleteren.jazygit.process.ExecutedCommand;

/**
 * {@link Checkout} for tests: checking out succeeds without running anything, and no revision names a commit. Fakes
 * override what they need.
 */
public interface StubCheckout extends Checkout {

    @Override
    default Either<GitFailure, List<ExecutedCommand>> branch(BranchName branch) {
        return ran();
    }

    @Override
    default Either<GitFailure, List<ExecutedCommand>> branchWithStash(BranchName branch) {
        return ran();
    }

    @Override
    default Either<GitFailure, List<ExecutedCommand>> newBranch(
            BranchName name, Revision startPoint, Option<BranchName> parent) {
        return ran();
    }

    @Override
    default Either<GitFailure, List<ExecutedCommand>> newTrackingBranch(BranchName name, RemoteBranch upstream) {
        return ran();
    }

    @Override
    default Either<GitFailure, Option<Sha>> resolve(String text) {
        return Either.right(Option.none());
    }
}
