package net.vanfleteren.jazygit.state;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.feature.branch.delete.DeleteBranchCmd.DeleteBranch;
import net.vanfleteren.jazygit.feature.branch.delete.DeleteBranchCmd.DeleteRemoteBranch;
import net.vanfleteren.jazygit.feature.branch.pull.PullCmd;
import net.vanfleteren.jazygit.feature.branch.push.PushCmd;
import net.vanfleteren.jazygit.feature.fetch.FetchCmd;
import net.vanfleteren.jazygit.feature.github.checkout.CheckoutPullRequestCmd;
import net.vanfleteren.jazygit.feature.github.create.CreatePullRequestCmd.PushFirst;
import net.vanfleteren.jazygit.feature.worktree.add.AddWorktreeCmd;
import net.vanfleteren.jazygit.feature.worktree.add.WorktreeSource;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequestSummary;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.DeleteScope;
import net.vanfleteren.jazygit.git.model.PushMode;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import org.junit.jupiter.api.Test;

/**
 * The commands that write the remote-tracking refs, or {@code FETCH_HEAD}, take their guard; the others do not.
 */
class RemoteRefsGuardTest {

    private static final Option<Cmd.Guard> GUARDED = Option.of(Cmd.Guard.REMOTE_REFS);
    private static final Branch.Name FEATURE = new Branch.Name("feature");
    private static final RemoteBranch UPSTREAM = new RemoteBranch("origin", FEATURE);

    @Test
    void fetchingPullingAndPushingTakeIt() {
        assertEquals(GUARDED, new FetchCmd.Fetch("origin").guard());
        assertEquals(GUARDED, new PullCmd.Pull(FEATURE).guard());
        assertEquals(GUARDED, new PullCmd.FastForward(FEATURE, UPSTREAM).guard());
        assertEquals(GUARDED, new PushCmd.Push(FEATURE, UPSTREAM, PushMode.FAST_FORWARD).guard());
        assertEquals(GUARDED, new PushCmd.PushSettingUpstream(FEATURE, UPSTREAM).guard());
        assertEquals(GUARDED, new PushFirst(new PushCmd.PushSettingUpstream(FEATURE, UPSTREAM)).guard());
    }

    @Test
    void deletingTakesItOnlyOnTheRemote() {
        assertEquals(Option.none(), new DeleteBranch(FEATURE, UPSTREAM, DeleteScope.LOCAL).guard());
        assertEquals(GUARDED, new DeleteBranch(FEATURE, UPSTREAM, DeleteScope.REMOTE).guard());
        assertEquals(GUARDED, new DeleteBranch(FEATURE, UPSTREAM, DeleteScope.BOTH).guard());
        assertEquals(GUARDED, new DeleteRemoteBranch(UPSTREAM).guard());
    }

    @Test
    void checkingOutAPullRequestTakesIt() {
        PullRequestSummary pullRequest = new PullRequestSummary(
                "owner/repo",
                9,
                "Title",
                "bob",
                "patch-1",
                false,
                true,
                PullRequest.Checks.NONE,
                PullRequest.Review.NONE,
                Instant.EPOCH,
                "https://github.com/owner/repo/pull/9");

        assertEquals(GUARDED, new CheckoutPullRequestCmd(pullRequest).guard());
        assertEquals(GUARDED, new AddWorktreeCmd(new WorktreeSource.PullRequest(pullRequest), "../pr").guard());
    }

    @Test
    void listingTheRemotesAndAddingAWorktreeForABranchDoNot() {
        assertEquals(Option.none(), new FetchCmd.ListRemotes().guard());
        assertEquals(Option.none(), new AddWorktreeCmd(new WorktreeSource.Existing(FEATURE), "../feature").guard());
    }
}
