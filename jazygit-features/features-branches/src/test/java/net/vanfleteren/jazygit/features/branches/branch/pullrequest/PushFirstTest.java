package net.vanfleteren.jazygit.features.branches.branch.pullrequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.Git;
import net.vanfleteren.jazygit.core.git.StubGit;
import net.vanfleteren.jazygit.core.git.branches.StubRemotes;
import net.vanfleteren.jazygit.core.git.model.PushMode;
import net.vanfleteren.jazygit.core.git.process.GitError;
import net.vanfleteren.jazygit.core.git.process.GitFailure;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.features.branches.branch.push.PushCmd;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import org.junit.jupiter.api.Test;

/**
 * The push before a pull request is created, which is the push it holds.
 */
class PushFirstTest {

    private static final BranchName FEATURE = new BranchName("feature");
    private static final PushCmd.Push PUSH =
            new PushCmd.Push(FEATURE, new RemoteBranch("origin", FEATURE), PushMode.FAST_FORWARD);
    private static final GitFailure REJECTED =
            new GitFailure(new GitError.CommandFailed(Option.none(), 1, "rejected"), List.empty());

    @Test
    void itPushesAsItsPushDoes() {
        NewPullRequestCmd.PushFirst first = new NewPullRequestCmd.PushFirst(PUSH, (model, branch) -> Next.of(model));
        Git git = StubGit.of().withRemotes(new StubRemotes() {
            @Override
            public Either<GitFailure, List<ExecutedCommand>> push(
                    BranchName branch, RemoteBranch upstream, PushMode mode) {
                return Either.left(REJECTED);
            }
        });

        assertEquals(FEATURE, first.branch());
        assertEquals(PUSH.operation(), first.operation());
        assertEquals(PUSH.guard(), first.guard());
        assertEquals(PUSH.changesForge(), first.changesForge());
        assertEquals(Either.left(REJECTED), first.perform(git));
    }
}
