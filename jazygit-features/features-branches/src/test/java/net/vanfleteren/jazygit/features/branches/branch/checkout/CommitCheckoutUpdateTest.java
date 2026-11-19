package net.vanfleteren.jazygit.features.branches.branch.checkout;

import static net.vanfleteren.jazygit.kernel.state.TestModels.loaded;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.nio.file.Path;
import net.vanfleteren.jazygit.core.git.model.BranchName;
import net.vanfleteren.jazygit.core.git.model.Head;
import net.vanfleteren.jazygit.core.git.model.RepoStatus;
import net.vanfleteren.jazygit.core.git.model.Revision;
import net.vanfleteren.jazygit.core.git.model.Sha;
import net.vanfleteren.jazygit.core.git.model.Worktree;
import net.vanfleteren.jazygit.features.branches.branch.checkout.CommitCheckoutCmd.CheckoutCommit;
import net.vanfleteren.jazygit.features.branches.branch.checkout.CommitCheckoutCmd.CheckoutCommitWithStash;
import net.vanfleteren.jazygit.features.branches.repos.RecentReposCmd;
import net.vanfleteren.jazygit.features.branches.worktree.add.LocationAsked;
import net.vanfleteren.jazygit.features.branches.worktree.add.NewWorktreePopup;
import net.vanfleteren.jazygit.features.branches.worktree.add.WorktreeSource;
import net.vanfleteren.jazygit.kernel.state.Landing;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Update;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.load.LoadMsg;
import net.vanfleteren.jazygit.kernel.state.popup.PopupMsg;
import net.vanfleteren.jazygit.kernel.state.view.BranchesTab;
import org.junit.jupiter.api.Test;

/**
 * Checking out a commit, with HEAD detached at it (ADR 137).
 */
class CommitCheckoutUpdateTest {

    private static final Sha HEAD_COMMIT = new Sha("aaaa");
    private static final Sha OLDER = new Sha("bbbb");

    /**
     * The loaded model, on {@code main} at {@link #HEAD_COMMIT}, in the main worktree.
     */
    private static Model onMain() {
        return at(Head.onBranch(new BranchName("main"), HEAD_COMMIT));
    }

    private static Model at(Head head) {
        return Update.update(loaded(), new LoadMsg.StatusLoaded(new RepoStatus(head, List.empty())))
                .model();
    }

    @Test
    void aCommitIsCheckedOutDetached() {
        assertEquals(
                List.of(new CheckoutCommit(OLDER)),
                Update.update(onMain(), new CheckoutMsg.CommitRequested(OLDER)).cmds());
    }

    @Test
    void changesInTheWayAreOfferedToBeTakenAlongThroughAStash() {
        Model asked = Update.update(onMain(), new CheckoutMsg.NeedsStash(new CheckoutCommit(OLDER), List.empty()))
                .model();
        assertTrue(asked.popup(StashCheckoutPopup.class).isDefined());

        Next confirmed = Update.update(asked, new PopupMsg.Confirmed(StashCheckoutPopup.class));

        assertEquals(List.of(new CheckoutCommitWithStash(OLDER)), confirmed.cmds());
        assertEquals(Option.none(), confirmed.model().popup());
    }

    @Test
    void inALinkedWorktreeItAsksWhereAndANewWorktreeStartsDetachedAtTheCommit() {
        Worktree main = new Worktree(
                Path.of("/repos/app"),
                Option.of(new Head.OnBranch(new BranchName("main"), Option.of(new Sha("1111")))),
                true,
                false,
                Option.none(),
                false);
        Worktree linked = new Worktree(
                Path.of("/repos/app-agent"),
                Option.of(new Head.OnBranch(new BranchName("main"), Option.of(HEAD_COMMIT))),
                false,
                true,
                Option.none(),
                false);
        Model inLinked = Update.update(onMain(), new LoadMsg.WorktreesLoaded(List.of(main, linked)))
                .model();

        Next asked = Update.update(inLinked, new CheckoutMsg.CommitRequested(OLDER));

        assertEquals(List.of(), asked.cmds());
        assertEquals(
                Option.of(new CheckoutRequest.Commit(OLDER)),
                asked.model().popup(CheckoutWherePopup.class).map(CheckoutWherePopup::request));
        assertEquals(
                Option.of(new NewWorktreePopup(
                        new WorktreeSource.Detached(new Revision.Commit(OLDER), OLDER.shortSha()),
                        "/repos/app-" + OLDER.shortSha(),
                        true)),
                LocationAsked.located(Update.update(asked.model(), new CheckoutMsg.NewWorktreeChosen()))
                        .model()
                        .popup());

        // Here, it is checked out as it is without asking.
        assertEquals(
                List.of(new CheckoutCommit(OLDER)),
                Update.update(asked.model(), new CheckoutMsg.HereChosen()).cmds());

        // In the main worktree, which lands as after checking out a branch there, and checks the commit out once shown.
        assertEquals(
                List.of(new RecentReposCmd.SwitchTo(
                        "/repos/app",
                        new Landing(BranchesTab.LOCAL, asked.model().repoView().focusedPane())
                                .followedBy(new CheckoutMsg.CommitRequested(OLDER)))),
                Update.update(asked.model(), new CheckoutMsg.MainChosen()).cmds());
    }

    @Test
    void checkedOutHereTheBranchesStayOnTheTabTheyWereOn() {
        Model remoteTab = onMain().mapRepoView(
                        repoView -> repoView.mapBranches(branches -> branches.withTab(BranchesTab.REMOTE)));

        Model done = Update.update(remoteTab, new CheckoutCommit(OLDER).succeeded(List.empty()))
                .model();

        assertEquals(remoteTab.repoView().branches(), done.repoView().branches());
    }

    @Test
    void theCommitHeadIsAtIsNotCheckedOutAgain() {
        assertEquals(
                List.of(),
                Update.update(onMain(), new CheckoutMsg.CommitRequested(HEAD_COMMIT))
                        .cmds());
        assertEquals(
                List.of(),
                Update.update(at(new Head.Detached(HEAD_COMMIT)), new CheckoutMsg.CommitRequested(HEAD_COMMIT))
                        .cmds());
    }
}
