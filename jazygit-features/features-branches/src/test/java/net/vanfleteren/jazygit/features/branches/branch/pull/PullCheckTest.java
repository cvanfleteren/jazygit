package net.vanfleteren.jazygit.features.branches.branch.pull;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import io.vavr.control.Either;
import net.vanfleteren.jazygit.core.git.Git;
import net.vanfleteren.jazygit.core.git.StubGit;
import net.vanfleteren.jazygit.core.git.branches.StubRemotes;
import net.vanfleteren.jazygit.core.git.process.GitError;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.kernel.state.Env;
import org.junit.jupiter.api.Test;

/**
 * Asking whether git knows how to pull a diverged branch.
 */
class PullCheckTest {

    private static final BranchName MAIN = new BranchName("main");
    private static final PullCheck CHECK = new PullCheck(MAIN);

    private static Git answering(Either<GitError, Boolean> answer) {
        return StubGit.of().withRemotes(new StubRemotes() {
            @Override
            public Either<GitError, Boolean> pullConfigured(BranchName branch) {
                return answer;
            }
        });
    }

    @Test
    void tellsWhetherPullRebaseOrPullFfIsSet() {
        assertEquals(new PullMsg.Checked(MAIN, true), CHECK.run(new Env(answering(Either.right(true)))));
        assertEquals(new PullMsg.Checked(MAIN, false), CHECK.run(new Env(answering(Either.right(false)))));
    }

    @Test
    void aFailingGitIsReportedForTheBranch() {
        PullMsg.CheckFailed failed = assertInstanceOf(
                PullMsg.CheckFailed.class,
                CHECK.run(new Env(answering(Either.left(new GitError.CommandFailed("boom", 1))))));

        assertEquals(MAIN, failed.branch());
    }
}
