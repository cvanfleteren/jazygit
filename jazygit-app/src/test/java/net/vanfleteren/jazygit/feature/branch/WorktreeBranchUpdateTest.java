package net.vanfleteren.jazygit.feature.branch;

import static net.vanfleteren.jazygit.state.TestModels.errorText;
import static net.vanfleteren.jazygit.state.TestModels.loaded;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.nio.file.Path;
import java.time.Instant;
import net.vanfleteren.jazygit.feature.branch.checkout.CheckoutMsg;
import net.vanfleteren.jazygit.feature.branch.checkout.SwitchWorktreePopup;
import net.vanfleteren.jazygit.feature.branch.delete.DeleteBranchCmd;
import net.vanfleteren.jazygit.feature.branch.delete.DeleteBranchMsg;
import net.vanfleteren.jazygit.feature.branch.delete.DeleteBranchPopup;
import net.vanfleteren.jazygit.feature.branch.merge.MergeMsg;
import net.vanfleteren.jazygit.feature.branch.merge.MergePopup;
import net.vanfleteren.jazygit.feature.branch.pull.PullMsg;
import net.vanfleteren.jazygit.feature.branch.rename.RenameBranchMsg;
import net.vanfleteren.jazygit.feature.branch.rename.RenameBranchPopup;
import net.vanfleteren.jazygit.feature.repos.RecentReposCmd;
import net.vanfleteren.jazygit.git.model.*;
import net.vanfleteren.jazygit.state.BranchesTab;
import net.vanfleteren.jazygit.state.Landing;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;
import org.junit.jupiter.api.Test;

/**
 * A branch that is checked out in another worktree, which git keeps from being checked out, deleted or
 * fast-forwarded here: that is said up front, instead of running a command git refuses, and switching to that
 * worktree is offered instead of the checkout.
 */
class WorktreeBranchUpdateTest {

    private static final Path LINKED = Path.of("/repos/linked");
    private static final BranchName FEATURE = new BranchName("feature");
    private static final BranchName LOCAL = new BranchName("local");

    /**
     * {@code feature}, which is on its remote, and {@code local}, which is not, are both checked out in another
     * worktree.
     */
    private static Model elsewhere() {
        RemoteBranch upstream = new RemoteBranch("origin", FEATURE);
        return TestModels.localBranchesRead(
                        loaded(),
                        List.of(
                                Fixtures.branch(new BranchName("main"), true, new Sha("aaaa")),
                                new Branch(
                                        FEATURE,
                                        false,
                                        new Sha("ffff"),
                                        Instant.EPOCH,
                                        true,
                                        false,
                                        new Tracking.Tracks(new Upstream(new BranchRef.Remote(upstream), 0, 1)),
                                        Option.of(LINKED)),
                                new Branch(
                                        LOCAL,
                                        false,
                                        new Sha("1111"),
                                        Instant.EPOCH,
                                        false,
                                        false,
                                        new Tracking.None(),
                                        Option.of(LINKED))))
                .model();
    }

    @Test
    void insteadOfCheckingItOutSwitchingToItsWorktreeIsOffered() {
        Next asked = Update.update(elsewhere(), new CheckoutMsg.Requested(FEATURE));

        assertEquals(List.of(), asked.cmds());
        assertEquals(
                Option.of(new SwitchWorktreePopup(FEATURE, "/repos/linked")),
                asked.model().popup());

        Model focused = asked.model().mapRepoView(view -> view.withFocusedPane(Option.of("branches")));
        Next confirmed = Update.update(focused, new CheckoutMsg.SwitchWorktreeConfirmed());
        // It is shown as a checkout here shows it: on the local branches, the focus staying where it was.
        assertEquals(
                List.of(new RecentReposCmd.SwitchTo(
                        "/repos/linked", new Landing(BranchesTab.LOCAL, Option.of("branches")))),
                confirmed.cmds());
        assertEquals(Option.none(), confirmed.model().popup());
        assertEquals(
                Option.none(),
                Update.update(asked.model(), new Msg.PopupDismissed(SwitchWorktreePopup.class))
                        .model()
                        .popup());
        // Without the popup open, confirming does nothing.
        assertEquals(
                List.of(),
                Update.update(elsewhere(), new CheckoutMsg.SwitchWorktreeConfirmed())
                        .cmds());
    }

    @Test
    void itIsNotFastForwarded() {
        Next next = Update.update(elsewhere(), new PullMsg.Requested(FEATURE));

        assertEquals(List.of(), next.cmds());
        assertEquals(
                Option.of("Fast-forwarding branch feature failed: it is checked out in the worktree at /repos/linked"),
                errorText(next.model()));
    }

    @Test
    void itCanOnlyBeDeletedOnItsRemote() {
        Model open = Update.update(elsewhere(), new DeleteBranchMsg.Requested(FEATURE))
                .model();

        assertEquals(Option.of(new DeleteBranchPopup(FEATURE)), open.popup());
        for (DeleteScope scope : List.of(DeleteScope.LOCAL, DeleteScope.BOTH)) {
            assertEquals(
                    List.of(),
                    Update.update(open, new DeleteBranchMsg.Chosen(scope)).cmds());
        }
        assertEquals(
                List.of(new DeleteBranchCmd.DeleteBranch(
                        FEATURE, new RemoteBranch("origin", FEATURE), DeleteScope.REMOTE)),
                Update.update(open, new DeleteBranchMsg.Chosen(DeleteScope.REMOTE))
                        .cmds());
    }

    @Test
    void withoutARemoteBranchItIsNotDeletedAndTheErrorSaysWhere() {
        Next next = Update.update(elsewhere(), new DeleteBranchMsg.Requested(LOCAL));

        assertEquals(
                Option.of("Deleting branch local failed: it is checked out in the worktree at /repos/linked"),
                errorText(next.model()));
        assertEquals(Option.none(), next.model().popup(DeleteBranchPopup.class));
    }

    @Test
    void itCanStillBeMergedRebasedOntoAndRenamed() {
        Model model = elsewhere();

        assertEquals(
                Option.of(new MergePopup(new BranchName("main"), FEATURE)),
                Update.update(model, new MergeMsg.Requested(FEATURE)).model().popup());
        assertEquals(
                Option.of(new RenameBranchPopup(FEATURE)),
                Update.update(model, new RenameBranchMsg.Requested(FEATURE))
                        .model()
                        .popup());
    }
}
