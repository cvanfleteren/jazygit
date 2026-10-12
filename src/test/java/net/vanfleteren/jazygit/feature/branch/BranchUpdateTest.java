package net.vanfleteren.jazygit.feature.branch;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.branch.BranchCmd.Checkout;
import net.vanfleteren.jazygit.feature.branch.BranchCmd.CheckoutWithStash;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.ChangeType;
import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.git.model.RepoStatus;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.state.Cmd.LoadBranches;
import net.vanfleteren.jazygit.state.Cmd.LoadStatus;
import net.vanfleteren.jazygit.state.LoadMsg;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update.Next;
import net.vanfleteren.jazygit.state.Update;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static net.vanfleteren.jazygit.state.TestModels.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The branch transitions: checkout, creating and deleting branches.
 */
class BranchUpdateTest {

    @Test
    void checkoutRequestOfAnotherBranchChecksItOut() {
        Model failedBefore = Update.update(loaded(), new CheckoutMsg.Failed("feature", "boom")).model();

        Next next = Update.update(failedBefore, new CheckoutMsg.Requested("feature"));

        assertEquals(List.of(new Checkout("feature")), next.cmds());
        assertEquals(Option.none(), next.model().error());
    }

    @Test
    void checkoutRefusedForLocalChangesAsksToStashThem() {
        Next next = Update.update(loaded(), new CheckoutMsg.NeedsStash("feature"));

        assertEquals(List.of(), next.cmds());
        assertEquals(Option.of(new StashCheckoutPopup("feature")), next.model().popup(StashCheckoutPopup.class));
    }

    @Test
    void checkoutRequestWithChangedFilesStillTriesTheCheckoutFirst() {
        Model dirty = Update.update(loaded(), new LoadMsg.StatusLoaded(new RepoStatus("main", "aaaa",
                List.of(new FileEntry("a.txt", ChangeType.MODIFIED))))).model();

        assertEquals(List.of(new Checkout("feature")),
                Update.update(dirty, new CheckoutMsg.Requested("feature")).cmds());
    }

    @Test
    void confirmingTheStashChecksOutThroughAStashAndClosesThePopup() {
        Model asked = Update.update(loaded(), new CheckoutMsg.NeedsStash("feature")).model();

        Next next = Update.update(asked, new CheckoutMsg.StashConfirmed());

        assertEquals(List.of(new CheckoutWithStash("feature")), next.cmds());
        assertEquals(Option.none(), next.model().popup());
    }

    @Test
    void cancellingTheStashClosesThePopupWithoutCheckingOut() {
        Model asked = Update.update(loaded(), new CheckoutMsg.NeedsStash("feature")).model();

        Next next = Update.update(asked, new CheckoutMsg.StashCancelled());

        assertEquals(List.of(), next.cmds());
        assertEquals(Option.none(), next.model().popup());
    }

    @Test
    void confirmingWithoutAnOpenPopupDoesNothing() {
        assertEquals(List.of(), Update.update(loaded(), new CheckoutMsg.StashConfirmed()).cmds());
    }

    @Test
    void checkoutRequestIsIgnoredForTheCurrentOrAnUnknownBranch() {
        Model model = loaded();

        assertEquals(List.of(), Update.update(model, new CheckoutMsg.Requested("main")).cmds());
        assertEquals(List.of(), Update.update(model, new CheckoutMsg.Requested("gone")).cmds());
        assertEquals(List.of(), Update.update(Update.init("repo").model(), new CheckoutMsg.Requested("feature")).cmds());
    }

    @Test
    void pushRequestOfAKnownBranchPushesIt() {
        Model failedBefore = Update.update(loaded(), new PushMsg.Failed("feature", "boom")).model();

        Next next = Update.update(failedBefore, new PushMsg.Requested("feature"));

        assertEquals(List.of(new BranchCmd.Push("feature", false)), next.cmds());
        assertEquals(Option.none(), next.model().error());
        assertEquals(io.vavr.collection.HashSet.of("feature"), next.model().pushing());
        // Not pushed again while it is being pushed.
        assertEquals(List.of(), Update.update(next.model(), new PushMsg.Requested("feature")).cmds());
        assertEquals(List.of(), Update.update(loaded(), new PushMsg.Requested("gone")).cmds());
    }

    @Test
    void pushRequestOfADivergedBranchAsksForConfirmationInsteadOfPushing() {
        Model diverged = Update.update(loaded(), new LoadMsg.BranchesLoaded(List.of(
                new Branch("main", true, "aaaa"),
                new Branch("feature", false, "ffff", Instant.EPOCH, true, false, 1, 2)))).model();

        Next asked = Update.update(diverged, new PushMsg.Requested("feature"));

        assertEquals(List.of(), asked.cmds());
        assertEquals(Option.of(new ForcePushPopup("feature")), asked.model().popup());
        assertEquals(io.vavr.collection.HashSet.empty(), asked.model().pushing());

        Next confirmed = Update.update(asked.model(), new PushMsg.ForceConfirmed());
        assertEquals(List.of(new BranchCmd.Push("feature", true)), confirmed.cmds());
        assertEquals(Option.none(), confirmed.model().popup());
        assertEquals(io.vavr.collection.HashSet.of("feature"), confirmed.model().pushing());

        Next cancelled = Update.update(asked.model(), new PushMsg.ForceCancelled());
        assertEquals(List.of(), cancelled.cmds());
        assertEquals(Option.none(), cancelled.model().popup());
    }

    @Test
    void confirmingWithoutAPromptDoesNothing() {
        assertEquals(List.of(), Update.update(loaded(), new PushMsg.ForceConfirmed()).cmds());
    }

    @Test
    void failedPushIsStoredAndASuccessfulOneReloadsTheBranches() {
        Model failed = Update.update(loaded(), new PushMsg.Failed("feature", "boom")).model();
        assertEquals(Option.of("Pushing branch feature failed: boom"), failed.error());

        Model pushing = Update.update(loaded(), new PushMsg.Requested("feature")).model();
        assertEquals(io.vavr.collection.HashSet.empty(), Update.update(pushing, new PushMsg.Failed("feature", "boom")).model().pushing());
        assertEquals(io.vavr.collection.HashSet.empty(), Update.update(pushing, new PushMsg.Done("feature", List.of())).model().pushing());

        Next done = Update.update(withError(loaded(), "boom"), new PushMsg.Done("feature", List.of()));
        assertEquals(Option.none(), done.model().error());
        assertEquals(true, done.cmds().contains(new LoadBranches()));
    }

    @Test
    void newBranchRequestOpensTheDialogAndCancelClosesIt() {
        Model open = Update.update(loaded(), new NewBranchMsg.Requested("feature")).model();
        assertEquals(Option.of(new NewBranchPopup("feature")), open.popup());

        Next cancelled = Update.update(open, new NewBranchMsg.Cancelled());
        assertEquals(Option.none(), cancelled.model().popup());
        assertEquals(List.of(), cancelled.cmds());
    }

    @Test
    void newBranchRequestedAtACommitOpensTheDialogWithItsSha() {
        net.vanfleteren.jazygit.git.model.Commit commit = new net.vanfleteren.jazygit.git.model.Commit(new Sha("abc1234567"), "Ada",
                "a@e.com", java.time.Instant.EPOCH, "msg", "");
        Model model = Update.update(loaded(), new net.vanfleteren.jazygit.state.LoadMsg.CommitsLoaded(List.of(commit))).model();

        assertEquals(Option.of(new NewBranchPopup("abc1234567", "abc1234")),
                Update.update(model, new NewBranchMsg.RequestedAtCommit(0)).model().popup());
        assertEquals(Option.none(), Update.update(model, new NewBranchMsg.RequestedAtCommit(5)).model().popup());
    }

    @Test
    void confirmedNewBranchIsCreatedAtTheBase() {
        Model open = Update.update(loaded(), new NewBranchMsg.Requested("feature")).model();

        Next next = Update.update(open, new NewBranchMsg.Confirmed(" topic "));

        assertEquals(List.of(new BranchCmd.CreateBranch("topic", "feature")), next.cmds());
        assertEquals(Option.none(), next.model().popup());
    }

    @Test
    void blankNewBranchNameKeepsTheDialogOpen() {
        Model open = Update.update(loaded(), new NewBranchMsg.Requested("feature")).model();

        Next next = Update.update(open, new NewBranchMsg.Confirmed("  "));

        assertEquals(List.of(), next.cmds());
        assertEquals(Option.of(new NewBranchPopup("feature")), next.model().popup());
    }

    @Test
    void failedBranchCreationIsStored() {
        Model model = Update.update(loaded(), new NewBranchMsg.Failed("topic", "boom")).model();

        assertEquals(Option.of("Creating branch topic failed: boom"), model.error());
    }

    @Test
    void deleteRequestOpensThePopupOnlyForAnotherKnownBranch() {
        Model model = loaded();

        assertEquals(Option.of(new DeleteBranchPopup("feature")),
                Update.update(model, new DeleteBranchMsg.Requested("feature")).model().popup());
        assertEquals(Option.none(), Update.update(model, new DeleteBranchMsg.Requested("main")).model().popup());
        assertEquals(Option.none(), Update.update(model, new DeleteBranchMsg.Requested("gone")).model().popup());
    }

    @Test
    void remoteScopesAreRefusedForALocalOnlyBranch() {
        Model open = Update.update(loaded(), new DeleteBranchMsg.Requested("feature")).model();

        for (BranchCmd.DeleteBranch.DeleteScope scope : List.of(BranchCmd.DeleteBranch.DeleteScope.REMOTE, BranchCmd.DeleteBranch.DeleteScope.BOTH)) {
            Next next = Update.update(open, new DeleteBranchMsg.Chosen(scope));
            assertEquals(List.of(), next.cmds());
            assertEquals(Option.of(new DeleteBranchPopup("feature")), next.model().popup());
        }
        assertEquals(List.of(new BranchCmd.DeleteBranch("feature", BranchCmd.DeleteBranch.DeleteScope.LOCAL)),
                Update.update(open, new DeleteBranchMsg.Chosen(BranchCmd.DeleteBranch.DeleteScope.LOCAL)).cmds());
    }

    @Test
    void remoteScopesAreRefusedForTheDefaultBranch() {
        Model model = Update.update(loaded(), new LoadMsg.BranchesLoaded(List.of(new Branch("main", true, "aaaa"),
                new Branch("trunk", false, "tttt", java.time.Instant.EPOCH, true, true)))).model();
        Model open = Update.update(model, new DeleteBranchMsg.Requested("trunk")).model();

        assertEquals(List.of(), Update.update(open, new DeleteBranchMsg.Chosen(BranchCmd.DeleteBranch.DeleteScope.REMOTE)).cmds());
        assertEquals(List.of(), Update.update(open, new DeleteBranchMsg.Chosen(BranchCmd.DeleteBranch.DeleteScope.BOTH)).cmds());
        assertEquals(List.of(new BranchCmd.DeleteBranch("trunk", BranchCmd.DeleteBranch.DeleteScope.LOCAL)),
                Update.update(open, new DeleteBranchMsg.Chosen(BranchCmd.DeleteBranch.DeleteScope.LOCAL)).cmds());
    }

    @Test
    void choosingAScopeDeletesTheBranchAndClosesThePopup() {
        Model withRemote = Update.update(loaded(), new LoadMsg.BranchesLoaded(
                List.of(new Branch("main", true, "aaaa"), new Branch("feature", false, "ffff", java.time.Instant.EPOCH, true)))).model();
        Model open = Update.update(withRemote, new DeleteBranchMsg.Requested("feature")).model();

        Next next = Update.update(open, new DeleteBranchMsg.Chosen(BranchCmd.DeleteBranch.DeleteScope.REMOTE));

        assertEquals(List.of(new BranchCmd.DeleteBranch("feature", BranchCmd.DeleteBranch.DeleteScope.REMOTE)), next.cmds());
        assertEquals(Option.none(), next.model().popup());
    }

    @Test
    void cancellingClosesThePopupWithoutDeleting() {
        Model open = Update.update(loaded(), new DeleteBranchMsg.Requested("feature")).model();

        Next next = Update.update(open, new DeleteBranchMsg.Cancelled());

        assertEquals(List.of(), next.cmds());
        assertEquals(Option.none(), next.model().popup());
    }

    @Test
    void failedDeletionIsStored() {
        Model model = Update.update(loaded(), new DeleteBranchMsg.Failed("feature", "boom")).model();

        assertEquals(Option.of("Deleting branch feature failed: boom"), model.error());
    }

    @Test
    void checkedOutReloadsStatusAndBranches() {
        Next next = Update.update(loaded(), new CheckoutMsg.Done("feature", List.of("git checkout feature")));

        assertEquals(List.of(new LoadStatus(), new LoadBranches(), new net.vanfleteren.jazygit.state.Cmd.LoadStashes()), next.cmds());
        assertEquals(Option.none(), next.model().error());
    }

    @Test
    void failedCheckoutIsStoredWithoutFurtherCommands() {
        Next next = Update.update(loaded(), new CheckoutMsg.Failed("feature", "local changes would be overwritten"));

        assertEquals(Option.of("Checkout of feature failed: local changes would be overwritten"),
                next.model().error());
        assertEquals(List.of(), next.cmds());
    }

    @Test
    void rebaseRequestOfAnotherBranchOpensTheChoiceForTheCheckedOutOne() {
        Model model = Update.update(loaded(), new LoadMsg.BranchesLoaded(List.of(
                new Branch("main", true, "1"), new Branch("feature", false, "2")))).model();

        Next next = Update.update(model, new RebaseMsg.Requested("feature"));

        assertEquals(Option.of(new RebasePopup("main", "feature")), next.model().popup(RebasePopup.class));
        assertEquals(List.of(), next.cmds());
    }

    @Test
    void theCheckedOutBranchAndUnknownBranchesCannotBeRebasedOnto() {
        Model model = Update.update(loaded(), new LoadMsg.BranchesLoaded(List.of(
                new Branch("main", true, "1"), new Branch("feature", false, "2")))).model();

        assertEquals(Option.none(), Update.update(model, new RebaseMsg.Requested("main")).model().popup(RebasePopup.class));
        assertEquals(Option.none(), Update.update(model, new RebaseMsg.Requested("nope")).model().popup(RebasePopup.class));
    }

    @Test
    void choosingARebaseClosesThePopupAndRebases() {
        Model open = Update.update(loaded(), new LoadMsg.BranchesLoaded(List.of(
                new Branch("main", true, "1"), new Branch("feature", false, "2")))).model();
        open = Update.update(open, new RebaseMsg.Requested("feature")).model();

        Next next = Update.update(open, new RebaseMsg.Chosen());

        assertEquals(List.of(new BranchCmd.Rebase("feature")), next.cmds());
        assertEquals(Option.none(), next.model().popup(RebasePopup.class));
    }

    @Test
    void aFailedRebaseShowsTheError() {
        Next next = Update.update(loaded(), new RebaseMsg.Failed("feature", "conflict"));

        assertEquals(Option.of("Rebasing onto feature failed: conflict"), next.model().error());
    }
}
