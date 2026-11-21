package net.vanfleteren.jazygit.features.branches.branch.push;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.core.git.Git;
import net.vanfleteren.jazygit.core.git.StubGit;
import net.vanfleteren.jazygit.core.git.branches.StubRemotes;
import net.vanfleteren.jazygit.core.git.model.PushMode;
import net.vanfleteren.jazygit.core.git.process.GitError;
import net.vanfleteren.jazygit.core.git.process.GitFailure;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.operation.OperationMsg;
import org.junit.jupiter.api.Test;

class PushCmdTest {

    private static final RemoteBranch DEVELOP = new RemoteBranch("origin", new BranchName("develop"));
    private static final PushCmd.Push PUSH =
            new PushCmd.Push(new BranchName("feature"), DEVELOP, PushMode.FAST_FORWARD);

    @Test
    void aPushThatPushDefaultRefusesSaysSoRatherThanFailing() {
        Msg msg = PUSH.run(new Env(pushFailingWith(new GitError.PushRefused("simple", DEVELOP))));

        assertEquals(new PushMsg.Refused(PUSH, new SetUpstreamPopup.Refusal("simple", DEVELOP), List.empty()), msg);
    }

    @Test
    void anyOtherFailureOfAPushFailsIt() {
        Msg msg = PUSH.run(
                new Env(pushFailingWith(new GitError.CommandFailed(io.vavr.control.Option.none(), 1, "rejected"))));

        assertInstanceOf(OperationMsg.Failed.class, msg);
    }

    private static Git pushFailingWith(GitError error) {
        return StubGit.of().withRemotes(new StubRemotes() {
            @Override
            public Either<GitFailure, List<ExecutedCommand>> push(
                    BranchName branch, RemoteBranch upstream, PushMode mode) {
                return Either.left(new GitFailure(error, List.empty()));
            }
        });
    }
}
