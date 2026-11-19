package net.vanfleteren.jazygit.core.git.files;

import static net.vanfleteren.jazygit.core.git.StubGit.ran;

import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.core.git.model.DiscardPlan;
import net.vanfleteren.jazygit.core.git.process.GitFailure;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;

/**
 * {@link Index} for tests: changing it succeeds without running anything. Fakes override what they need.
 */
public interface StubIndex extends Index {

    @Override
    default Either<GitFailure, List<ExecutedCommand>> stage(List<String> paths) {
        return ran();
    }

    @Override
    default Either<GitFailure, List<ExecutedCommand>> unstageNew(List<String> paths) {
        return ran();
    }

    @Override
    default Either<GitFailure, List<ExecutedCommand>> discard(DiscardPlan plan) {
        return ran();
    }

    @Override
    default Either<GitFailure, List<ExecutedCommand>> unstage(List<String> paths) {
        return ran();
    }
}
