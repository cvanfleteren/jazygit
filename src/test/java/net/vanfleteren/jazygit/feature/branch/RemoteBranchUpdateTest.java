package net.vanfleteren.jazygit.feature.branch;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.branch.BranchCmd.Checkout;
import net.vanfleteren.jazygit.feature.branch.BranchCmd.CheckoutRemote;
import net.vanfleteren.jazygit.feature.branch.BranchCmd.CreateBranch;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.git.model.RemoteRef;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.model.Upstream;
import net.vanfleteren.jazygit.state.BranchesTab;
import net.vanfleteren.jazygit.state.Cmd.LoadBranchLog;
import net.vanfleteren.jazygit.state.LoadMsg;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Operation;
import net.vanfleteren.jazygit.state.SelectionMsg;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static net.vanfleteren.jazygit.state.TestModels.loaded;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The remote branches: switching to them, checking them out and branching from them.
 */
class RemoteBranchUpdateTest {

    private static final RemoteBranch ORIGIN_FEATURE = new RemoteBranch("origin", "feature");
    private static final RemoteBranch ORIGIN_NEW = new RemoteBranch("origin", "new");

    /**
     * main and feature locally, where feature follows nothing; origin/feature and origin/new on the remote.
     */
    private static Model withRemotes() {
        return Update.update(loaded(), new LoadMsg.RemoteBranchesLoaded(List.of(
                new RemoteRef(ORIGIN_FEATURE, new Sha("ffff"), Instant.EPOCH),
                new RemoteRef(ORIGIN_NEW, new Sha("eeee"), Instant.EPOCH)))).model();
    }

    @Test
    void theTabSwitchesBetweenTheLocalAndTheRemoteBranches() {
        Model remote = Update.update(loaded(), new BranchesTabMsg.Switched()).model();
        assertEquals(BranchesTab.REMOTE, remote.branchesTab());

        assertEquals(BranchesTab.LOCAL, Update.update(remote, new BranchesTabMsg.Switched()).model().branchesTab());
    }

    @Test
    void choosingATabShowsItWhicheverWasShown() {
        Model remote = Update.update(loaded(), new BranchesTabMsg.Chosen(BranchesTab.REMOTE)).model();
        assertEquals(BranchesTab.REMOTE, remote.branchesTab());

        assertEquals(BranchesTab.REMOTE,
                Update.update(remote, new BranchesTabMsg.Chosen(BranchesTab.REMOTE)).model().branchesTab());
        assertEquals(BranchesTab.LOCAL,
                Update.update(remote, new BranchesTabMsg.Chosen(BranchesTab.LOCAL)).model().branchesTab());
    }

    @Test
    void checkingOutARemoteBranchWithoutALocalOneCreatesOneThatFollowsIt() {
        Next next = Update.update(withRemotes(), new CheckoutMsg.RemoteRequested(ORIGIN_NEW));

        assertEquals(List.of(new CheckoutRemote("new", ORIGIN_NEW)), next.cmds());
        // The remote branch shows as busy, as the local one is not there yet.
        assertEquals(Option.of(Operation.CHECKOUT), next.model().busy().get("origin/new"));
        assertEquals(List.of(), Update.update(next.model(), new CheckoutMsg.RemoteRequested(ORIGIN_NEW)).cmds());
    }

    @Test
    void checkingOutARemoteBranchThatALocalOneFollowsChecksOutThatOne() {
        Model model = Update.update(withRemotes(), new LoadMsg.BranchesLoaded(List.of(
                new Branch("main", true, new Sha("aaaa")),
                new Branch("mine", false, new Sha("ffff"), Instant.EPOCH, true, false,
                        Option.of(new Upstream(ORIGIN_FEATURE, 0, 0)))))).model();

        assertEquals(List.of(new Checkout("mine")),
                Update.update(model, new CheckoutMsg.RemoteRequested(ORIGIN_FEATURE)).cmds());
    }

    @Test
    void aLocalBranchOfTheSameNameThatFollowsSomethingElseMakesItAskForAnotherName() {
        Model asked = Update.update(withRemotes(), new CheckoutMsg.RemoteRequested(ORIGIN_FEATURE)).model();

        assertEquals(Option.of(new NewBranchPopup("refs/remotes/origin/feature", "origin/feature", "feature",
                Option.of(ORIGIN_FEATURE))), asked.popup(NewBranchPopup.class));
        assertEquals(List.of(new CheckoutRemote("feature-2", ORIGIN_FEATURE)),
                Update.update(asked, new NewBranchMsg.Confirmed("feature-2")).cmds());
    }

    @Test
    void anUnknownRemoteBranchIsNotCheckedOut() {
        assertEquals(List.of(), Update.update(withRemotes(),
                new CheckoutMsg.RemoteRequested(new RemoteBranch("origin", "nowhere"))).cmds());
    }

    @Test
    void aNewBranchCanStartAtARemoteBranch() {
        Model asked = Update.update(withRemotes(), new NewBranchMsg.RequestedAtRemote(ORIGIN_NEW)).model();

        assertEquals(Option.of(new NewBranchPopup("refs/remotes/origin/new", "origin/new")),
                asked.popup(NewBranchPopup.class));
        assertEquals(List.of(new CreateBranch("topic", "refs/remotes/origin/new")),
                Update.update(asked, new NewBranchMsg.Confirmed("topic")).cmds());
    }

    @Test
    void aCheckoutOrANewBranchFromTheRemoteBranchesShowsTheLocalOnesOnceItSucceeded() {
        Model onRemotes = Update.update(withRemotes(), new BranchesTabMsg.Switched()).model();

        List.<BranchCmd>of(new CheckoutRemote("new", ORIGIN_NEW), new Checkout("feature"),
                        new CreateBranch("topic", "refs/remotes/origin/new"))
                .forEach(cmd -> assertEquals(BranchesTab.LOCAL,
                        Update.update(onRemotes, cmd.succeeded(List.empty())).model().branchesTab(), cmd.toString()));
    }

    @Test
    void aSuccessfulCheckoutOfARemoteBranchReleasesIt() {
        Model started = Update.update(withRemotes(), new CheckoutMsg.RemoteRequested(ORIGIN_NEW)).model();

        Model done = Update.update(started, new CheckoutRemote("new", ORIGIN_NEW).succeeded(List.empty())).model();

        assertEquals(Option.none(), done.busy().get("origin/new"));
    }

    @Test
    void aFailedCheckoutFromTheRemoteBranchesStaysOnThem() {
        Model started = Update.update(Update.update(withRemotes(), new BranchesTabMsg.Switched()).model(),
                new CheckoutMsg.RemoteRequested(ORIGIN_NEW)).model();

        Model failed = Update.update(started, new CheckoutRemote("new", ORIGIN_NEW).failed(
                net.vanfleteren.jazygit.state.TestModels.refused("boom"))).model();

        assertEquals(BranchesTab.REMOTE, failed.branchesTab());
    }

    @Test
    void theLogOfAHighlightedRemoteBranchIsReadAgainWhenItMoves() {
        BranchRef remote = new BranchRef.Remote(ORIGIN_NEW);
        Model model = Update.update(withRemotes(), new SelectionMsg.BranchSelected(remote)).model();

        Next moved = Update.update(model, new LoadMsg.RemoteBranchesLoaded(List.of(
                new RemoteRef(ORIGIN_NEW, new Sha("9999"), Instant.EPOCH))));
        Next unmoved = Update.update(model, new LoadMsg.RemoteBranchesLoaded(List.of(
                new RemoteRef(ORIGIN_NEW, new Sha("eeee"), Instant.EPOCH))));

        assertEquals(List.of(new LoadBranchLog(remote)), moved.cmds());
        assertEquals(List.of(), unmoved.cmds());
    }
}
