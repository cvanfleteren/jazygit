package net.vanfleteren.jazygit.kernel.state;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.core.gh.model.GhBranch;
import net.vanfleteren.jazygit.core.gh.model.PrNumber;
import net.vanfleteren.jazygit.core.gh.model.PullRequest;
import net.vanfleteren.jazygit.core.gh.model.PullRequestSummary;
import net.vanfleteren.jazygit.core.git.model.DeleteScope;
import net.vanfleteren.jazygit.core.git.model.PushMode;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.core.repo.RemoteName;
import net.vanfleteren.jazygit.features.branches.branch.delete.DeleteBranchCmd.DeleteBranch;
import net.vanfleteren.jazygit.features.branches.branch.delete.DeleteBranchCmd.DeleteRemoteBranch;
import net.vanfleteren.jazygit.features.branches.branch.pull.PullCmd;
import net.vanfleteren.jazygit.features.branches.branch.push.PushCmd;
import net.vanfleteren.jazygit.features.branches.worktree.add.AddWorktreeCmd;
import net.vanfleteren.jazygit.features.branches.worktree.add.WorktreeSource;
import net.vanfleteren.jazygit.features.changes.fetch.FetchCmd;
import net.vanfleteren.jazygit.features.github.checkout.CheckoutPullRequestCmd;
import net.vanfleteren.jazygit.features.github.checkout.PullRequestWorktree;
import net.vanfleteren.jazygit.features.github.create.CreatePullRequestCmd.PushFirst;
import org.junit.jupiter.api.Test;

/**
 * The commands that write the remote-tracking refs, or {@code FETCH_HEAD}, take their guard; the others do not.
 */
class RemoteRefsGuardTest {

    private static final Option<Cmd.Guard> GUARDED = Option.of(Cmd.Guard.REMOTE_REFS);
    private static final BranchName FEATURE = new BranchName("feature");
    private static final RemoteBranch UPSTREAM = new RemoteBranch("origin", FEATURE);

    @Test
    void fetchingPullingAndPushingTakeIt() {
        assertEquals(GUARDED, new FetchCmd.Fetch(new RemoteName("origin"), List.empty()).guard());
        assertEquals(GUARDED, new PullCmd.Pull(FEATURE).guard());
        assertEquals(GUARDED, new PullCmd.FastForward(FEATURE, new BranchRef.Remote(UPSTREAM)).guard());
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
                new PrNumber(9),
                "Title",
                "bob",
                new GhBranch("patch-1"),
                false,
                true,
                PullRequest.Checks.NONE,
                PullRequest.Review.NONE,
                Instant.EPOCH,
                "https://github.com/owner/repo/pull/9");

        assertEquals(GUARDED, new CheckoutPullRequestCmd(pullRequest).guard());
        assertEquals(GUARDED, new AddWorktreeCmd(new PullRequestWorktree(pullRequest), "../pr").guard());
    }

    @Test
    void listingTheRemotesAndAddingAWorktreeForABranchDoNot() {
        assertEquals(Option.none(), new FetchCmd.ListRemotes().guard());
        assertEquals(Option.none(), new AddWorktreeCmd(new WorktreeSource.Existing(FEATURE), "../feature").guard());
    }
}
