package net.vanfleteren.jazygit.features.branches.branch.pull;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.core.git.Git;
import net.vanfleteren.jazygit.core.git.StubGit;
import net.vanfleteren.jazygit.core.git.branches.StubRemotes;
import net.vanfleteren.jazygit.core.git.model.PullMode;
import net.vanfleteren.jazygit.core.git.process.GitError;
import net.vanfleteren.jazygit.core.git.process.GitFailure;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.operation.OperationMsg;
import org.junit.jupiter.api.Test;

/**
 * A pull that git refuses because the branch has diverged asks how to pull instead of failing.
 */
class PullCmdTest {

    private static final BranchName MAIN = new BranchName("main");
    private static final GitError DIVERGED =
            new GitError.CommandFailed("fatal: Need to specify how to reconcile divergent branches.", 128);

    private static Git pullFailingWith(GitError error) {
        return StubGit.of().withRemotes(new StubRemotes() {
            @Override
            public Either<GitFailure, List<ExecutedCommand>> pull(PullMode mode) {
                return Either.left(new GitFailure(error, List.empty()));
            }
        });
    }

    @Test
    void aPullThatGitRefusesAsTheBranchDivergedAsksHowToPull() {
        PullCmd.Pull pull = new PullCmd.Pull(MAIN);

        assertEquals(new PullMsg.NeedsStrategy(pull, List.empty()), pull.run(new Env(pullFailingWith(DIVERGED))));
    }

    @Test
    void aPullByTheChosenWayThatFailsFailsWhateverItSays() {
        Msg msg = new PullCmd.Pull(MAIN, PullMode.REBASE).run(new Env(pullFailingWith(DIVERGED)));

        assertInstanceOf(OperationMsg.Failed.class, msg);
    }

    @Test
    void aPullThatStopsOnConflictsIsStoppedNotAsked() {
        Msg msg = new PullCmd.Pull(MAIN)
                .run(new Env(pullFailingWith(new GitError.StoppedOnConflicts(
                        io.vavr.control.Option.of(new net.vanfleteren.jazygit.core.repo.InProgress.Merging(
                                new net.vanfleteren.jazygit.core.repo.Sha("a"))),
                        1))));

        assertInstanceOf(OperationMsg.Stopped.class, msg);
    }

    @Test
    void anyOtherFailureOfAPullFailsIt() {
        Msg msg = new PullCmd.Pull(MAIN)
                .run(new Env(pullFailingWith(new GitError.CommandFailed("fatal: no such remote", 128))));

        assertInstanceOf(OperationMsg.Failed.class, msg);
    }
}
