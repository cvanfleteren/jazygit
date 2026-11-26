package net.vanfleteren.jazygit.features.branches.branch;

import static net.vanfleteren.jazygit.kernel.state.TestModels.loaded;
import static net.vanfleteren.jazygit.kernel.state.TestModels.typed;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.core.git.model.*;
import net.vanfleteren.jazygit.core.repo.*;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.features.branches.branch.checkout.CheckoutCmd;
import net.vanfleteren.jazygit.features.branches.branch.checkout.CheckoutCmd.Checkout;
import net.vanfleteren.jazygit.features.branches.branch.checkout.CheckoutCmd.CheckoutRemote;
import net.vanfleteren.jazygit.features.branches.branch.checkout.CheckoutMsg;
import net.vanfleteren.jazygit.features.branches.branch.newbranch.NameAsked;
import net.vanfleteren.jazygit.features.branches.branch.newbranch.NewBranchCmd;
import net.vanfleteren.jazygit.features.branches.branch.newbranch.NewBranchMsg;
import net.vanfleteren.jazygit.features.branches.branch.newbranch.NewBranchPopup;
import net.vanfleteren.jazygit.kernel.state.*;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.load.SelectionMsg;
import net.vanfleteren.jazygit.kernel.state.view.*;
import net.vanfleteren.jazygit.kernel.state.view.ViewMsg;
import org.junit.jupiter.api.Test;

/**
 * The remote branches: switching to them, checking them out and branching from them.
 */
class RemoteBranchUpdateTest {

    private static final RemoteBranch ORIGIN_FEATURE = new RemoteBranch("origin", new BranchName("feature"));
    private static final RemoteBranch ORIGIN_NEW = new RemoteBranch("origin", new BranchName("new"));

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
    void checkingOutARemoteBranchWithoutALocalOneCreatesOneThatFollowsIt() {
        Next next = TestModels.stepped(withRemotes(), new CheckoutMsg.RemoteRequested(ORIGIN_NEW));

        assertEquals(List.of(new CheckoutRemote(new BranchName("new"), ORIGIN_NEW)), next.cmds());
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
                                Fixtures.branch(new BranchName("main"), true, new Sha("aaaa")),
                                // Another remote branch is followed by a branch listed first.
                                Fixtures.branch(
                                        new BranchName("other"),
                                        false,
                                        new Sha("eeee"),
                                        Instant.EPOCH,
                                        true,
                                        false,
                                        Option.of(new Upstream(new BranchRef.Remote(ORIGIN_NEW), 0, 0))),
                                Fixtures.branch(
                                        new BranchName("mine"),
                                        false,
                                        new Sha("ffff"),
                                        Instant.EPOCH,
                                        true,
                                        false,
                                        Option.of(new Upstream(new BranchRef.Remote(ORIGIN_FEATURE), 0, 0)))))
                .model();

        assertEquals(
                List.of(new Checkout(new BranchName("mine"))),
                Update.update(model, new CheckoutMsg.RemoteRequested(ORIGIN_FEATURE))
                        .cmds());
    }

    @Test
    void aLocalBranchOfTheSameNameThatFollowsSomethingElseMakesItAskForAnotherName() {
        Model asked = Update.update(withRemotes(), new CheckoutMsg.RemoteRequested(ORIGIN_FEATURE))
                .model();

        assertEquals(
                Option.of(new NewBranchPopup(
                        ORIGIN_FEATURE.tracking(), "feature", Option.of(ORIGIN_FEATURE), Option.none())),
                asked.popup(NewBranchPopup.class));
        assertEquals(
                List.of(new CheckoutRemote(new BranchName("feature-2"), ORIGIN_FEATURE)),
                Update.update(typed(asked, "feature-2"), new NewBranchMsg.Confirmed())
                        .cmds());
    }

    @Test
    void anUnknownRemoteBranchIsNotCheckedOut() {
        assertEquals(
                List.of(),
                Update.update(
                                withRemotes(),
                                new CheckoutMsg.RemoteRequested(new RemoteBranch("origin", new BranchName("nowhere"))))
                        .cmds());
    }

    @Test
    void aNewBranchCanStartAtARemoteBranch() {
        Model asked = NameAsked.asked(withRemotes(), new NewBranchMsg.RequestedAtRemote(ORIGIN_NEW));

        assertEquals(
                Option.of(new NewBranchPopup(ORIGIN_NEW.tracking(), Option.of(new BranchName("new")))),
                asked.popup(NewBranchPopup.class));
        assertEquals(
                List.of(new NewBranchCmd(
                        new BranchName("topic"), ORIGIN_NEW.tracking(), Option.of(new BranchName("new")))),
                Update.update(typed(asked, "topic"), new NewBranchMsg.Confirmed())
                        .cmds());
    }

    @Test
    void aCheckoutOrANewBranchFromTheRemoteBranchesShowsTheLocalOnesOnceItSucceeded() {
        Model onRemotes = Update.update(withRemotes(), new ViewMsg.TabChosen(PaneId.BRANCHES, BranchesTab.REMOTE.id()))
                .model();

        List.<BranchCmd>of(
                        new CheckoutRemote(new BranchName("new"), ORIGIN_NEW),
                        new Checkout(new BranchName("feature")),
                        new NewBranchCmd(
                                new BranchName("topic"), ORIGIN_NEW.tracking(), Option.of(new BranchName("new"))))
                .forEach(cmd -> assertEquals(
                        Option.of(BranchesTab.LOCAL),
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
                        started, new CheckoutRemote(new BranchName("new"), ORIGIN_NEW).succeeded(List.empty()))
                .model();

        assertEquals(Option.none(), done.jobs().busy().get(new BranchRef.Remote(ORIGIN_NEW)));
    }

    @Test
    void aFailedCheckoutFromTheRemoteBranchesStaysOnThem() {
        Model started = Update.update(
                        Update.update(withRemotes(), new ViewMsg.TabChosen(PaneId.BRANCHES, BranchesTab.REMOTE.id()))
                                .model(),
                        new CheckoutMsg.RemoteRequested(ORIGIN_NEW))
                .model();

        Model failed = Update.update(
                        started,
                        new CheckoutRemote(new BranchName("new"), ORIGIN_NEW)
                                .failed(net.vanfleteren.jazygit.kernel.state.TestModels.refused("boom")))
                .model();

        assertEquals(Option.of(BranchesTab.REMOTE), failed.repoView().branchesTab());
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

        assertEquals(List.of(TestModels.loadBranchLog(moved.model(), remote)), moved.cmds());
        assertEquals(List.of(), unmoved.cmds());
    }
}
