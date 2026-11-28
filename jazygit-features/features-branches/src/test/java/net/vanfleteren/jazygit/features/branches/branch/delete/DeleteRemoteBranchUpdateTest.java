package net.vanfleteren.jazygit.features.branches.branch.delete;

import static net.vanfleteren.jazygit.kernel.state.TestModels.loaded;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.core.git.model.*;
import net.vanfleteren.jazygit.core.repo.*;
import net.vanfleteren.jazygit.features.branches.branch.BranchError;
import net.vanfleteren.jazygit.features.branches.branch.delete.DeleteBranchCmd.DeleteRemoteBranch;
import net.vanfleteren.jazygit.kernel.state.*;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.popup.*;
import org.junit.jupiter.api.Test;

/**
 * Deleting remote branches on their remote.
 */
class DeleteRemoteBranchUpdateTest {

    private static final RemoteBranch ORIGIN_MAIN = new RemoteBranch("origin", new BranchName("main"));
    private static final RemoteBranch ORIGIN_FEATURE = new RemoteBranch("origin", new BranchName("feature"));
    private static final RemoteBranch UPSTREAM_MAIN = new RemoteBranch("upstream", new BranchName("main"));

    /**
     * main locally, the default branch; origin/main, origin/feature and upstream/main on the remotes.
     */
    private static Model model() {
        Model branches = TestModels.localBranchesRead(
                        loaded(),
                        List.of(Fixtures.branch(
                                new BranchName("main"), true, new Sha("aaaa"), Instant.EPOCH, true, true)))
                .model();
        return TestModels.remoteBranchesRead(
                        branches,
                        List.of(
                                new RemoteRef(ORIGIN_MAIN, new Sha("aaaa"), Instant.EPOCH),
                                new RemoteRef(ORIGIN_FEATURE, new Sha("ffff"), Instant.EPOCH),
                                new RemoteRef(UPSTREAM_MAIN, new Sha("bbbb"), Instant.EPOCH)))
                .model();
    }

    @Test
    void deletingARemoteBranchAsksToConfirmFirst() {
        Next asked = Update.update(model(), new DeleteBranchMsg.RemoteRequested(ORIGIN_FEATURE));

        assertEquals(
                Option.of(new DeleteRemoteBranchPopup(ORIGIN_FEATURE)),
                asked.model().popup());
        assertEquals(List.empty(), asked.cmds());
    }

    @Test
    void confirmingDeletesItAndKeepsItBusy() {
        Model asked = Update.update(model(), new DeleteBranchMsg.RemoteRequested(ORIGIN_FEATURE))
                .model();

        Next confirmed = TestModels.stepped(asked, new DeleteBranchMsg.RemoteConfirmed());

        assertEquals(List.of(new DeleteRemoteBranch(ORIGIN_FEATURE)), confirmed.cmds());
        assertEquals(Option.none(), confirmed.model().popup());
        assertEquals(
                Option.of(DeleteBranchCmd.DELETE_BRANCH),
                confirmed.model().jobs().busy().get(new BranchRef.Remote(ORIGIN_FEATURE)));
        assertEquals(
                Option.none(),
                Update.update(confirmed.model(), new DeleteBranchMsg.RemoteRequested(ORIGIN_FEATURE))
                        .model()
                        .popup());
    }

    @Test
    void cancellingDeletesNothing() {
        Model asked = Update.update(model(), new DeleteBranchMsg.RemoteRequested(ORIGIN_FEATURE))
                .model();

        Next cancelled = Update.update(asked, new PopupMsg.Dismissed(DeleteRemoteBranchPopup.class));

        assertEquals(Option.none(), cancelled.model().popup());
        assertEquals(List.empty(), cancelled.cmds());
    }

    @Test
    void theDefaultBranchOfOriginIsNotDeleted() {
        Next asked = Update.update(model(), new DeleteBranchMsg.RemoteRequested(ORIGIN_MAIN));

        assertEquals(List.empty(), asked.cmds());
        assertEquals(
                Option.of(new BranchError.DefaultBranch(new BranchRef.Remote(ORIGIN_MAIN))),
                asked.model().outcome().error());
    }

    @Test
    void aBranchOfAnotherRemoteWithTheDefaultNameCanBeDeleted() {
        assertEquals(
                Option.of(new DeleteRemoteBranchPopup(UPSTREAM_MAIN)),
                Update.update(model(), new DeleteBranchMsg.RemoteRequested(UPSTREAM_MAIN))
                        .model()
                        .popup());
    }
    /**
     * {@link #model()} of a fork: the pull requests, and so the base, are on {@code upstream}.
     */
    private static Model fork() {
        Branch main = Fixtures.branch(new BranchName("main"), true, new Sha("bbbb"), Instant.EPOCH, true, true);
        return Update.update(
                        loaded(),
                        TestModels.branchesLoaded(new AllBranches(
                                List.of(main),
                                List.of(
                                        new RemoteRef(ORIGIN_MAIN, new Sha("aaaa"), Instant.EPOCH),
                                        new RemoteRef(UPSTREAM_MAIN, new Sha("bbbb"), Instant.EPOCH)),
                                RemoteSetup.of(new RemoteName("upstream")))))
                .model();
    }

    @Test
    void inAForkTheDefaultBranchOfTheBaseRemoteIsNotDeleted() {
        Next asked = Update.update(fork(), new DeleteBranchMsg.RemoteRequested(UPSTREAM_MAIN));

        assertEquals(List.empty(), asked.cmds());
        assertEquals(
                Option.of(new BranchError.DefaultBranch(new BranchRef.Remote(UPSTREAM_MAIN))),
                asked.model().outcome().error());
    }

    @Test
    void inAForkTheBranchOfTheForkWithTheDefaultNameCanBeDeleted() {
        assertEquals(
                Option.of(new DeleteRemoteBranchPopup(ORIGIN_MAIN)),
                Update.update(fork(), new DeleteBranchMsg.RemoteRequested(ORIGIN_MAIN))
                        .model()
                        .popup());
    }
}
