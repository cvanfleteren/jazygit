package net.vanfleteren.jazygit.feature.branch;

import static net.vanfleteren.jazygit.state.TestModels.loaded;
import static net.vanfleteren.jazygit.state.TestModels.typed;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.feature.branch.checkout.CheckoutCmd;
import net.vanfleteren.jazygit.feature.branch.checkout.CheckoutCmd.Checkout;
import net.vanfleteren.jazygit.feature.branch.checkout.CheckoutCmd.CheckoutRemote;
import net.vanfleteren.jazygit.feature.branch.checkout.CheckoutMsg;
import net.vanfleteren.jazygit.feature.branch.newbranch.NewBranchCmd;
import net.vanfleteren.jazygit.feature.branch.newbranch.NewBranchMsg;
import net.vanfleteren.jazygit.feature.branch.newbranch.NewBranchPopup;
import net.vanfleteren.jazygit.feature.branch.tabs.BranchesTabMsg;
import net.vanfleteren.jazygit.git.model.*;
import net.vanfleteren.jazygit.state.*;
import net.vanfleteren.jazygit.state.Cmd.LoadBranchLog;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update.Next;
import org.junit.jupiter.api.Test;

/**
 * The remote branches: switching to them, checking them out and branching from them.
 */
class RemoteBranchUpdateTest {

    private static final RemoteBranch ORIGIN_FEATURE = new RemoteBranch("origin", new Branch.Name("feature"));
    private static final RemoteBranch ORIGIN_NEW = new RemoteBranch("origin", new Branch.Name("new"));

    /**
     * main and feature locally, where feature follows nothing; origin/feature and origin/new on the remote.
     */
    private static Model withRemotes() {
        return TestModels.remoteBranchesRead(
                        loaded(),
                        List.of(
                                new RemoteRef(ORIGIN_FEATURE, new Sha("ffff"), Instant.EPOCH),
                                new RemoteRef(ORIGIN_NEW, new Sha("eeee"), Instant.EPOCH)))
                .model();
    }

    @Test
    void theTabGoesRoundTheLocalBranchesTheRemoteOnesAndTheWorktrees() {
        Model remote = Update.update(loaded(), new BranchesTabMsg.Switched(1)).model();
        assertEquals(BranchesTab.REMOTE, remote.repoView().branchesTab());

        Model worktrees = Update.update(remote, new BranchesTabMsg.Switched(1)).model();
        assertEquals(BranchesTab.WORKTREES, worktrees.repoView().branchesTab());
        assertEquals(
                BranchesTab.LOCAL,
                Update.update(worktrees, new BranchesTabMsg.Switched(1))
                        .model()
                        .repoView()
                        .branchesTab());
        assertEquals(
                BranchesTab.WORKTREES,
                Update.update(loaded(), new BranchesTabMsg.Switched(-1))
                        .model()
                        .repoView()
                        .branchesTab());
        assertEquals(
                BranchesTab.LOCAL,
                Update.update(remote, new BranchesTabMsg.Switched(-1))
                        .model()
                        .repoView()
                        .branchesTab());
    }

    @Test
    void choosingATabShowsItWhicheverWasShown() {
        Model remote = Update.update(loaded(), new BranchesTabMsg.Chosen(BranchesTab.REMOTE))
                .model();
        assertEquals(BranchesTab.REMOTE, remote.repoView().branchesTab());

        assertEquals(
                BranchesTab.REMOTE,
                Update.update(remote, new BranchesTabMsg.Chosen(BranchesTab.REMOTE))
                        .model()
                        .repoView()
                        .branchesTab());
        assertEquals(
                BranchesTab.LOCAL,
                Update.update(remote, new BranchesTabMsg.Chosen(BranchesTab.LOCAL))
                        .model()
                        .repoView()
                        .branchesTab());
    }

    @Test
    void checkingOutARemoteBranchWithoutALocalOneCreatesOneThatFollowsIt() {
        Next next = Update.update(withRemotes(), new CheckoutMsg.RemoteRequested(ORIGIN_NEW));

        assertEquals(List.of(new CheckoutRemote(new Branch.Name("new"), ORIGIN_NEW)), next.cmds());
        // The remote branch shows as busy, as the local one is not there yet.
        assertEquals(Option.of(CheckoutCmd.CHECKOUT), next.model().jobs().busy().get(new BranchRef.Remote(ORIGIN_NEW)));
        assertEquals(
                List.of(),
                Update.update(next.model(), new CheckoutMsg.RemoteRequested(ORIGIN_NEW))
                        .cmds());
    }

    @Test
    void checkingOutARemoteBranchThatALocalOneFollowsChecksOutThatOne() {
        Model model = TestModels.localBranchesRead(
                        withRemotes(),
                        List.of(
                                new Branch(new Branch.Name("main"), true, new Sha("aaaa")),
                                new Branch(
                                        new Branch.Name("mine"),
                                        false,
                                        new Sha("ffff"),
                                        Instant.EPOCH,
                                        true,
                                        false,
                                        Option.of(new Upstream(ORIGIN_FEATURE, 0, 0)))))
                .model();

        assertEquals(
                List.of(new Checkout(new Branch.Name("mine"))),
                Update.update(model, new CheckoutMsg.RemoteRequested(ORIGIN_FEATURE))
                        .cmds());
    }

    @Test
    void aLocalBranchOfTheSameNameThatFollowsSomethingElseMakesItAskForAnotherName() {
        Model asked = Update.update(withRemotes(), new CheckoutMsg.RemoteRequested(ORIGIN_FEATURE))
                .model();

        assertEquals(
                Option.of(new NewBranchPopup(
                        "refs/remotes/origin/feature",
                        "origin/feature",
                        "feature",
                        Option.of(ORIGIN_FEATURE),
                        Option.none())),
                asked.popup(NewBranchPopup.class));
        assertEquals(
                List.of(new CheckoutRemote(new Branch.Name("feature-2"), ORIGIN_FEATURE)),
                Update.update(typed(asked, "feature-2"), new NewBranchMsg.Confirmed())
                        .cmds());
    }

    @Test
    void anUnknownRemoteBranchIsNotCheckedOut() {
        assertEquals(
                List.of(),
                Update.update(
                                withRemotes(),
                                new CheckoutMsg.RemoteRequested(new RemoteBranch("origin", new Branch.Name("nowhere"))))
                        .cmds());
    }

    @Test
    void aNewBranchCanStartAtARemoteBranch() {
        Model asked = Update.update(withRemotes(), new NewBranchMsg.RequestedAtRemote(ORIGIN_NEW))
                .model();

        assertEquals(
                Option.of(
                        new NewBranchPopup("refs/remotes/origin/new", "origin/new", Option.of(new Branch.Name("new")))),
                asked.popup(NewBranchPopup.class));
        assertEquals(
                List.of(new NewBranchCmd(
                        new Branch.Name("topic"), "refs/remotes/origin/new", Option.of(new Branch.Name("new")))),
                Update.update(typed(asked, "topic"), new NewBranchMsg.Confirmed())
                        .cmds());
    }

    @Test
    void aCheckoutOrANewBranchFromTheRemoteBranchesShowsTheLocalOnesOnceItSucceeded() {
        Model onRemotes =
                Update.update(withRemotes(), new BranchesTabMsg.Switched(1)).model();

        List.<BranchCmd>of(
                        new CheckoutRemote(new Branch.Name("new"), ORIGIN_NEW),
                        new Checkout(new Branch.Name("feature")),
                        new NewBranchCmd(
                                new Branch.Name("topic"), "refs/remotes/origin/new", Option.of(new Branch.Name("new"))))
                .forEach(cmd -> assertEquals(
                        BranchesTab.LOCAL,
                        Update.update(onRemotes, cmd.succeeded(List.empty()))
                                .model()
                                .repoView()
                                .branchesTab(),
                        cmd.toString()));
    }

    @Test
    void aSuccessfulCheckoutOfARemoteBranchReleasesIt() {
        Model started = Update.update(withRemotes(), new CheckoutMsg.RemoteRequested(ORIGIN_NEW))
                .model();

        Model done = Update.update(
                        started, new CheckoutRemote(new Branch.Name("new"), ORIGIN_NEW).succeeded(List.empty()))
                .model();

        assertEquals(Option.none(), done.jobs().busy().get(new BranchRef.Remote(ORIGIN_NEW)));
    }

    @Test
    void aFailedCheckoutFromTheRemoteBranchesStaysOnThem() {
        Model started = Update.update(
                        Update.update(withRemotes(), new BranchesTabMsg.Switched(1))
                                .model(),
                        new CheckoutMsg.RemoteRequested(ORIGIN_NEW))
                .model();

        Model failed = Update.update(
                        started,
                        new CheckoutRemote(new Branch.Name("new"), ORIGIN_NEW)
                                .failed(net.vanfleteren.jazygit.state.TestModels.refused("boom")))
                .model();

        assertEquals(BranchesTab.REMOTE, failed.repoView().branchesTab());
    }

    @Test
    void theLogOfAHighlightedRemoteBranchIsReadAgainWhenItMoves() {
        BranchRef remote = new BranchRef.Remote(ORIGIN_NEW);
        Model model = Update.update(withRemotes(), new SelectionMsg.BranchSelected(remote))
                .model();

        Next moved = TestModels.remoteBranchesRead(
                model, List.of(new RemoteRef(ORIGIN_NEW, new Sha("9999"), Instant.EPOCH)));
        Next unmoved = TestModels.remoteBranchesRead(
                model, List.of(new RemoteRef(ORIGIN_NEW, new Sha("eeee"), Instant.EPOCH)));

        assertEquals(List.of(new LoadBranchLog(remote)), moved.cmds());
        assertEquals(List.of(), unmoved.cmds());
    }
}
