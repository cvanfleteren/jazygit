package net.vanfleteren.jazygit.git;

import static net.vanfleteren.jazygit.git.StubGit.ran;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.nio.file.Path;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.Head;
import net.vanfleteren.jazygit.git.model.NewBranchStart;
import net.vanfleteren.jazygit.git.model.Revision;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.model.Worktree;
import net.vanfleteren.jazygit.process.ExecutedCommand;

/**
 * {@link Worktrees} for tests: only the main worktree, at {@link #shown()}, without changes, and changing them
 * succeeds without running anything. Fakes override what they need.
 */
public interface StubWorktrees extends Worktrees {

    /**
     * Where the main worktree is, which is the one shown.
     */
    default Path shown() {
        return Path.of("/repos/repo");
    }

    /**
     * Only the main worktree, which is the one shown.
     */
    @Override
    default Either<ProcessError, List<Worktree>> list() {
        return Either.right(List.of(new Worktree(
                shown(),
                Option.of(new Head.OnBranch(new BranchName("main"), Option.of(new Sha("aaaa")))),
                true,
                true,
                Option.none(),
                false)));
    }

    @Override
    default Either<ProcessError, Boolean> hasChanges(Path location) {
        return Either.right(false);
    }

    @Override
    default Either<GitFailure<ProcessError>, List<ExecutedCommand>> add(Path location, BranchName branch) {
        return ran();
    }

    @Override
    default Either<GitFailure<ProcessError>, List<ExecutedCommand>> addDetached(Path location, Revision startPoint) {
        return ran();
    }

    @Override
    default Either<GitFailure<ProcessError>, List<ExecutedCommand>> addWithNewBranch(
            Path location, BranchName name, NewBranchStart start) {
        return ran();
    }

    @Override
    default Either<GitFailure<GitError>, List<ExecutedCommand>> remove(Path location, boolean force) {
        return ran();
    }

    @Override
    default Either<GitFailure<ProcessError>, List<ExecutedCommand>> removeAnyway(Path location) {
        return ran();
    }

    @Override
    default Either<GitFailure<ProcessError>, List<ExecutedCommand>> prune() {
        return ran();
    }

    @Override
    default Either<GitFailure<ProcessError>, List<ExecutedCommand>> lock(Path location, boolean lock) {
        return ran();
    }

    @Override
    default Either<GitFailure<ProcessError>, List<ExecutedCommand>> lock(Path location, String reason) {
        return ran();
    }
}
