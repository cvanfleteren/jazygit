package net.vanfleteren.jazygit.feature.branch.push;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.gh.StubGitHub;
import net.vanfleteren.jazygit.git.GitError;
import net.vanfleteren.jazygit.git.GitFailure;
import net.vanfleteren.jazygit.git.StubGitProvider;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.PushMode;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.OperationMsg;
import org.junit.jupiter.api.Test;

class PushCmdTest {

    private static final RemoteBranch DEVELOP = new RemoteBranch("origin", new BranchName("develop"));
    private static final PushCmd.Push PUSH =
            new PushCmd.Push(new BranchName("feature"), DEVELOP, PushMode.FAST_FORWARD);

    @Test
    void aPushThatPushDefaultRefusesSaysSoRatherThanFailing() {
        Msg msg = PUSH.run(new Env(pushFailingWith(new GitError.PushRefused("simple", DEVELOP)), StubGitHub.empty()));

        assertEquals(new PushMsg.Refused(PUSH, new SetUpstreamPopup.Refusal("simple", DEVELOP), List.empty()), msg);
    }

    @Test
    void anyOtherFailureOfAPushFailsIt() {
        Msg msg = PUSH.run(new Env(
                pushFailingWith(new GitError.CommandFailed(io.vavr.control.Option.none(), 1, "rejected")),
                StubGitHub.empty()));

        assertInstanceOf(OperationMsg.Failed.class, msg);
    }

    private static StubGitProvider pushFailingWith(GitError error) {
        return new StubGitProvider() {
            @Override
            public Either<GitFailure<GitError>, List<ExecutedCommand>> push(
                    BranchName branch, RemoteBranch upstream, PushMode mode) {
                return Either.left(new GitFailure<>(error, List.empty()));
            }
        };
    }
}
