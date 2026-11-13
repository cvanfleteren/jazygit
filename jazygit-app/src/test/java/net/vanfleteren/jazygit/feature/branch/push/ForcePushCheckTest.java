package net.vanfleteren.jazygit.feature.branch.push;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import io.vavr.control.Either;
import net.vanfleteren.jazygit.gh.StubGitHub;
import net.vanfleteren.jazygit.git.Git;
import net.vanfleteren.jazygit.git.GitError;
import net.vanfleteren.jazygit.git.StubGit;
import net.vanfleteren.jazygit.git.StubHistory;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.git.model.Revision;
import net.vanfleteren.jazygit.state.Env;
import org.junit.jupiter.api.Test;

/**
 * Asking git whether a branch ever had what is on its upstream, before its force push is offered (ADR 116).
 */
class ForcePushCheckTest {

    private static final BranchName FEATURE = new BranchName("feature");
    private static final RemoteBranch ORIGIN_FEATURE = new RemoteBranch("origin", FEATURE);
    private static final ForcePushCheck CHECK = new ForcePushCheck(new ForcePushPopup(FEATURE, 0, 1), ORIGIN_FEATURE);

    private static Git history(Either<GitError, Boolean> answer) {
        return StubGit.of().withHistory(new StubHistory() {
            @Override
            public Either<GitError, Boolean> everHad(BranchName branch, Revision revision) {
                return branch.equals(FEATURE) && revision.equals(ORIGIN_FEATURE.tracking())
                        ? answer
                        : Either.left(new GitError.CommandFailed("asked about another branch", 1));
            }
        });
    }

    @Test
    void asksAboutTheRemoteTrackingBranchOfTheUpstream() {
        assertEquals(
                new PushMsg.ForceChecked(CHECK.popup(), true),
                CHECK.run(new Env(history(Either.right(true)), StubGitHub.of())));
        assertEquals(
                new PushMsg.ForceChecked(CHECK.popup(), false),
                CHECK.run(new Env(history(Either.right(false)), StubGitHub.of())));
    }

    @Test
    void aFailingGitIsReportedForTheBranch() {
        PushMsg.ForceCheckFailed failed = assertInstanceOf(
                PushMsg.ForceCheckFailed.class,
                CHECK.run(new Env(history(Either.left(new GitError.CommandFailed("boom", 1))), StubGitHub.of())));

        assertEquals(FEATURE, failed.branch());
    }
}
