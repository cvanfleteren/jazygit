package net.vanfleteren.jazygit.feature.branch;

import static net.vanfleteren.jazygit.state.TestModels.loaded;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.feature.branch.BranchCmd.DeleteRemoteBranch;
import net.vanfleteren.jazygit.git.model.*;
import net.vanfleteren.jazygit.state.*;
import net.vanfleteren.jazygit.state.Update.Next;
import org.junit.jupiter.api.Test;

/**
 * Deleting remote branches on their remote.
 */
class DeleteRemoteBranchUpdateTest {

    private static final RemoteBranch ORIGIN_MAIN = new RemoteBranch("origin", new Branch.Name("main"));
    private static final RemoteBranch ORIGIN_FEATURE = new RemoteBranch("origin", new Branch.Name("feature"));
    private static final RemoteBranch UPSTREAM_MAIN = new RemoteBranch("upstream", new Branch.Name("main"));

    /**
     * main locally, the default branch; origin/main, origin/feature and upstream/main on the remotes.
     */
    private static Model model() {
        Model branches = Update.update(
                        loaded(),
                        new LoadMsg.BranchesLoaded(List.of(
                                new Branch(new Branch.Name("main"), true, new Sha("aaaa"), Instant.EPOCH, true, true))))
                .model();
        return Update.update(
                        branches,
                        new LoadMsg.RemoteBranchesLoaded(List.of(
                                new RemoteRef(ORIGIN_MAIN, new Sha("aaaa"), Instant.EPOCH),
                                new RemoteRef(ORIGIN_FEATURE, new Sha("ffff"), Instant.EPOCH),
                                new RemoteRef(UPSTREAM_MAIN, new Sha("bbbb"), Instant.EPOCH))))
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

        Next confirmed = Update.update(asked, new DeleteBranchMsg.RemoteConfirmed());

        assertEquals(List.of(new DeleteRemoteBranch(ORIGIN_FEATURE)), confirmed.cmds());
        assertEquals(Option.none(), confirmed.model().popup());
        assertEquals(
                Option.of(Operation.DELETE_BRANCH),
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

        Next cancelled = Update.update(asked, new DeleteBranchMsg.RemoteCancelled());

        assertEquals(Option.none(), cancelled.model().popup());
        assertEquals(List.empty(), cancelled.cmds());
    }

    @Test
    void theDefaultBranchOfOriginIsNotDeleted() {
        Next asked = Update.update(model(), new DeleteBranchMsg.RemoteRequested(ORIGIN_MAIN));

        assertEquals(List.empty(), asked.cmds());
        assertEquals(
                Option.of(new AppError.DefaultBranch("origin/main")),
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
}
