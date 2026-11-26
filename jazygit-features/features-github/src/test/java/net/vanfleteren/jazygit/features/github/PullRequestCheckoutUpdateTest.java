package net.vanfleteren.jazygit.features.github;

import static net.vanfleteren.jazygit.features.github.PullRequestFixtures.*;
import static net.vanfleteren.jazygit.kernel.state.TestModels.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.core.gh.model.PullRequestSummary;
import net.vanfleteren.jazygit.core.git.model.*;
import net.vanfleteren.jazygit.core.repo.*;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.ForgeBranch;
import net.vanfleteren.jazygit.core.repo.RepoSlug;
import net.vanfleteren.jazygit.features.branches.branch.checkout.CheckoutMsg;
import net.vanfleteren.jazygit.features.branches.branch.checkout.CheckoutWherePopup;
import net.vanfleteren.jazygit.features.branches.branch.checkout.SwitchWorktreePopup;
import net.vanfleteren.jazygit.features.branches.repos.RecentReposCmd;
import net.vanfleteren.jazygit.features.branches.worktree.add.AddWorktreeCmd;
import net.vanfleteren.jazygit.features.branches.worktree.add.LocationAsked;
import net.vanfleteren.jazygit.features.branches.worktree.add.NewWorktreePopup;
import net.vanfleteren.jazygit.features.github.checkout.CheckoutPullRequestCmd;
import net.vanfleteren.jazygit.features.github.checkout.CheckoutPullRequestMsg;
import net.vanfleteren.jazygit.features.github.checkout.PullRequestCheckout;
import net.vanfleteren.jazygit.features.github.checkout.PullRequestWorktree;
import net.vanfleteren.jazygit.features.github.create.GitHubCreatePullRequestCmd.*;
import net.vanfleteren.jazygit.features.github.show.PullRequestCmd.*;
import net.vanfleteren.jazygit.features.github.state.*;
import net.vanfleteren.jazygit.features.github.state.GitHubState;
import net.vanfleteren.jazygit.kernel.settings.SettingsStore;
import net.vanfleteren.jazygit.kernel.state.*;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.load.*;
import net.vanfleteren.jazygit.kernel.state.operation.*;
import net.vanfleteren.jazygit.kernel.state.popup.*;
import net.vanfleteren.jazygit.kernel.state.preferences.*;
import net.vanfleteren.jazygit.kernel.state.view.*;
import org.junit.jupiter.api.Test;

/**
 * Checking out a pull request, here, in another worktree or in a new one.
 */
class PullRequestCheckoutUpdateTest {

    private static Model ofOwnerRepo(RepoStatus status) {
        return Update.update(loaded(), new LoadMsg.StatusLoaded(status))
                .model()
                .mapPart(GitHubState.PART, gitHub -> gitHub.withRepository(Option.of(new RepoSlug("owner/repo"))));
    }

    @Test
    void aPullRequestOfTheRepositoryIsCheckedOutWhenOnlyUntrackedFilesAreAround() {
        PullRequestSummary pr = summary(new RepoSlug("owner/repo"));

        Next checkingOut = Update.update(ofOwnerRepo(DIRTY), new CheckoutPullRequestMsg.Requested(pr));

        assertEquals(List.of(new CheckoutPullRequestCmd(pr)), checkingOut.cmds());
        assertEquals(Option.none(), checkingOut.model().outcome().error());
    }

    @Test
    void forAPullRequestWhoseBranchAnotherWorktreeHasSwitchingToThatWorktreeIsOffered() {
        BranchName patch = new BranchName("patch-1");
        Model elsewhere = localBranchesRead(
                        ofOwnerRepo(CLEAN),
                        List.of(new Branch(
                                patch,
                                false,
                                new Sha("bbbb"),
                                Instant.EPOCH,
                                Option.none(),
                                false,
                                false,
                                new Tracking.None(),
                                Option.of(java.nio.file.Path.of("/repos/linked")))))
                .model();
        PullRequestSummary fork = summary(new RepoSlug("owner/repo"));
        PullRequestSummary own = new PullRequestSummary(
                fork.repository(),
                fork.number(),
                fork.title(),
                fork.author(),
                new ForgeBranch("patch-1"),
                false,
                false,
                fork.checks(),
                fork.review(),
                fork.updatedAt(),
                fork.url());

        Next offered = Update.update(elsewhere, new CheckoutPullRequestMsg.Requested(own));

        assertEquals(List.empty(), offered.cmds());
        assertEquals(
                Option.of(new SwitchWorktreePopup(patch, "/repos/linked")),
                offered.model().popup());
        // The branch of one from a fork has another name here, so it is checked out as ever.
        assertEquals(
                List.of(new CheckoutPullRequestCmd(fork)),
                Update.update(elsewhere, new CheckoutPullRequestMsg.Requested(fork))
                        .cmds());
    }

    @Test
    void aPullRequestIsNotCheckedOutOverChanges() {
        RepoStatus changed = new RepoStatus(
                Head.onBranch(new BranchName("main"), new Sha("aaaa")),
                List.of(Fixtures.fileEntry("a.txt", ChangeType.MODIFIED)));

        Next refused = Update.update(
                ofOwnerRepo(changed), new CheckoutPullRequestMsg.Requested(summary(new RepoSlug("owner/repo"))));

        assertEquals(List.empty(), refused.cmds());
        assertEquals(
                Option.of(new GitHubError.ChangesBeforeCheckout("owner/repo#9")),
                refused.model().outcome().error());
    }

    @Test
    void inALinkedWorktreeAPullRequestIsCheckedOutWhereTheUserSays() {
        RepoStatus changed = new RepoStatus(
                Head.onBranch(new BranchName("main"), new Sha("aaaa")),
                List.of(Fixtures.fileEntry("a.txt", ChangeType.MODIFIED)));
        Worktree main =
                new Worktree(java.nio.file.Path.of("/repos/app"), Option.none(), true, false, Option.none(), false);
        Worktree linked = new Worktree(
                java.nio.file.Path.of("/repos/app-agent"), Option.none(), false, true, Option.none(), false);
        Model inLinked = Update.update(ofOwnerRepo(changed), new LoadMsg.WorktreesLoaded(List.of(main, linked)))
                .model();
        PullRequestSummary pr = summary(new RepoSlug("owner/repo"));
        PullRequestCheckout request = new PullRequestCheckout(pr);

        // The changes here only stand in the way of checking it out here.
        Model asked = Update.update(inLinked, new CheckoutPullRequestMsg.Requested(pr))
                .model();
        assertEquals(Option.of(new CheckoutWherePopup(request, linked, Option.of(main), Option.none())), asked.popup());

        Next here = Update.update(asked, new CheckoutMsg.HereChosen());
        assertEquals(List.empty(), here.cmds());
        assertEquals(
                Option.of(new GitHubError.ChangesBeforeCheckout("owner/repo#9")),
                here.model().outcome().error());

        assertEquals(
                List.of(new RecentReposCmd.SwitchTo(
                        "/repos/app",
                        new Landing(BranchesTab.LOCAL, asked.repoView().focusedPane())
                                .followedBy(new CheckoutMsg.Placed(request)))),
                Update.update(asked, new CheckoutMsg.MainChosen()).cmds());
        assertEquals(
                Option.of(new NewWorktreePopup(new PullRequestWorktree(pr), "/repos/app-pr-9", true)),
                LocationAsked.located(Update.update(asked, new CheckoutMsg.NewWorktreeChosen()))
                        .model()
                        .popup());
        // There, which repository it is of is not asked again: the pull requests may not be read yet.
        assertEquals(
                List.of(new CheckoutPullRequestCmd(pr)),
                Update.update(
                                Update.update(loaded(), new LoadMsg.StatusLoaded(CLEAN))
                                        .model(),
                                new CheckoutMsg.Placed(request))
                        .cmds());
    }

    /**
     * {@code current} checked out in the linked worktree {@code /repos/app-agent}, which is shown; {@code patch-1} in
     * the main worktree when {@code patchInMain}, else nowhere.
     */
    private static Model linkedWith(BranchName current, boolean patchInMain) {
        java.nio.file.Path mainPath = java.nio.file.Path.of("/repos/app");
        Model branches = localBranchesRead(
                        ofOwnerRepo(CLEAN),
                        List.of(
                                Fixtures.branch(current, true, new Sha("1111")),
                                new Branch(
                                        new BranchName("patch-1"),
                                        false,
                                        new Sha("bbbb"),
                                        Instant.EPOCH,
                                        Option.none(),
                                        false,
                                        false,
                                        new Tracking.None(),
                                        patchInMain ? Option.of(mainPath) : Option.none())))
                .model();
        return Update.update(
                        branches,
                        new LoadMsg.WorktreesLoaded(List.of(
                                new Worktree(mainPath, Option.none(), true, false, Option.none(), false),
                                new Worktree(
                                        java.nio.file.Path.of("/repos/app-agent"),
                                        Option.of(Head.onBranch(current, new Sha("1111"))),
                                        false,
                                        true,
                                        Option.none(),
                                        false))))
                .model();
    }

    @Test
    void inALinkedWorktreeAPullRequestWhoseBranchTheMainWorktreeHasOffersToSwitchThere() {
        Next offered = Update.update(
                linkedWith(new BranchName("agent"), true), new CheckoutPullRequestMsg.Requested(own("patch-1")));

        assertEquals(List.empty(), offered.cmds());
        assertEquals(
                Option.of(new SwitchWorktreePopup(new BranchName("patch-1"), "/repos/app")),
                offered.model().popup());
    }

    @Test
    void inALinkedWorktreeThatHasItsBranchAPullRequestIsCheckedOutThereWithoutAsking() {
        Next next = Update.update(
                linkedWith(new BranchName("patch-1"), false), new CheckoutPullRequestMsg.Requested(own("patch-1")));

        // It changes no branch, and no other worktree could check it out.
        assertEquals(List.of(new CheckoutPullRequestCmd(own("patch-1"))), next.cmds());
        assertEquals(Option.none(), next.model().popup());
    }

    @Test
    void inTheMainWorktreeSwitchedToAPullRequestWaitsForTheStatusBeforeCheckingOutOverChanges() {
        PullRequestSummary pr = summary(new RepoSlug("owner/repo"));
        Landing landing = new Landing(BranchesTab.LOCAL, Option.none())
                .followedBy(new CheckoutMsg.Placed(new PullRequestCheckout(pr)));
        Model model = TestModels.started(TestModels.init("repo", SettingsStore.Loaded.DEFAULT, Option.none(), landing))
                .model();
        Next branches = TestModels.done(
                model,
                LoadBranches.LISTED,
                TestModels.branchesLoaded(new AllBranches(TestModels.BRANCHES, List.empty())));
        Next worktrees = TestModels.done(
                TestModels.started(branches).model(),
                new LoadWorktrees(),
                new LoadMsg.WorktreesLoaded(List.of(new Worktree(
                        java.nio.file.Path.of("/repos/app"), Option.none(), true, true, Option.none(), false))));

        Next status = TestModels.done(
                TestModels.started(worktrees).model(),
                new LoadStatus(),
                new LoadMsg.StatusLoaded(new RepoStatus(
                        Head.onBranch(new BranchName("main"), new Sha("aaaa")),
                        List.of(Fixtures.fileEntry("a.txt", ChangeType.MODIFIED)))));

        assertTrue(branches.cmds()
                .appendAll(worktrees.cmds())
                .appendAll(status.cmds())
                .filter(CheckoutPullRequestCmd.class::isInstance)
                .isEmpty());
        assertEquals(
                Option.of(new GitHubError.ChangesBeforeCheckout("owner/repo#9")),
                status.model().outcome().error());
    }

    @Test
    void aPullRequestOfAnotherRepositoryIsNotCheckedOut() {
        Next refused = Update.update(
                ofOwnerRepo(CLEAN), new CheckoutPullRequestMsg.Requested(summary(new RepoSlug("other/repo"))));

        assertEquals(List.empty(), refused.cmds());
        assertEquals(
                Option.of(new GitHubError.OtherRepository("other/repo#9")),
                refused.model().outcome().error());
    }

    @Test
    void aPullRequestIsNotCheckedOutWhileItIsNotKnownWhichRepositoryThisIs() {
        Model reading = Update.update(loaded(), new LoadMsg.StatusLoaded(CLEAN)).model();

        Next refused =
                Update.update(reading, new CheckoutPullRequestMsg.Requested(summary(new RepoSlug("owner/repo"))));

        assertEquals(List.empty(), refused.cmds());
        assertEquals(
                Option.of(new PullRequestsError.NotRead()),
                refused.model().outcome().error());
    }

    private static PullRequestSummary own(String headBranch) {
        PullRequestSummary fork = summary(new RepoSlug("owner/repo"));
        return new PullRequestSummary(
                fork.repository(),
                fork.number(),
                fork.title(),
                fork.author(),
                new ForgeBranch(headBranch),
                false,
                false,
                fork.checks(),
                fork.review(),
                fork.updatedAt(),
                fork.url());
    }

    /**
     * The repository with {@code status}, its main worktree at {@code /repos/app}.
     */
    private static Model withMainWorktree(RepoStatus status) {
        return Update.update(
                        ofOwnerRepo(status),
                        new LoadMsg.WorktreesLoaded(List.of(new Worktree(
                                java.nio.file.Path.of("/repos/app"),
                                Option.of(new Head.OnBranch(new BranchName("main"), Option.of(new Sha("aaaa")))),
                                true,
                                true,
                                Option.none(),
                                false))))
                .model();
    }

    @Test
    void aPullRequestIsCheckedOutInANewWorktreeNamedAfterItsBranchOrForAForkItsNumberChangesOrNot() {
        PullRequestSummary fork = summary(new RepoSlug("owner/repo"));

        Next asked = LocationAsked.located(
                Update.update(withMainWorktree(DIRTY), new CheckoutPullRequestMsg.WorktreeRequested(fork)));

        assertEquals(List.empty(), asked.cmds());
        assertEquals(
                Option.of(new NewWorktreePopup(new PullRequestWorktree(fork), "/repos/app-pr-9")),
                asked.model().popup());
        assertEquals(
                List.of(new AddWorktreeCmd(new PullRequestWorktree(fork), "/repos/app-pr-9")),
                Update.update(asked.model(), new PopupMsg.Confirmed(NewWorktreePopup.class))
                        .cmds());

        RepoStatus changed = new RepoStatus(
                Head.onBranch(new BranchName("main"), new Sha("aaaa")),
                List.of(Fixtures.fileEntry("a.txt", ChangeType.MODIFIED)));
        PullRequestSummary own = own("feature/login");
        assertEquals(
                Option.of(new NewWorktreePopup(new PullRequestWorktree(own), "/repos/app-feature-login")),
                LocationAsked.located(Update.update(
                                withMainWorktree(changed), new CheckoutPullRequestMsg.WorktreeRequested(own)))
                        .model()
                        .popup());
    }

    @Test
    void aPullRequestWhoseBranchIsCheckedOutHereGetsNoWorktreeAndOneOfAnotherRepositoryNeither() {
        Model onPatch = localBranchesRead(
                        withMainWorktree(CLEAN),
                        List.of(Fixtures.branch(new BranchName("patch-1"), true, new Sha("bbbb"))))
                .model();

        Next here = LocationAsked.located(
                Update.update(onPatch, new CheckoutPullRequestMsg.WorktreeRequested(own("patch-1"))));
        assertEquals(Option.none(), here.model().popup(NewWorktreePopup.class));
        assertEquals(
                Option.of(new GitHubError.PullRequestCheckedOutHere("owner/repo#9")),
                here.model().outcome().error());

        Next other = LocationAsked.located(Update.update(
                withMainWorktree(CLEAN),
                new CheckoutPullRequestMsg.WorktreeRequested(summary(new RepoSlug("other/repo")))));
        assertEquals(Option.none(), other.model().popup(NewWorktreePopup.class));
        assertEquals(
                Option.of(new GitHubError.OtherRepository("other/repo#9")),
                other.model().outcome().error());
    }

    @Test
    void forAPullRequestWhoseBranchAnotherWorktreeHasSwitchingToItIsOfferedInsteadOfANewOne() {
        BranchName patch = new BranchName("patch-1");
        Model elsewhere = localBranchesRead(
                        withMainWorktree(CLEAN),
                        List.of(new Branch(
                                patch,
                                false,
                                new Sha("bbbb"),
                                Instant.EPOCH,
                                Option.none(),
                                false,
                                false,
                                new Tracking.None(),
                                Option.of(java.nio.file.Path.of("/repos/linked")))))
                .model();

        assertEquals(
                Option.of(new SwitchWorktreePopup(patch, "/repos/linked")),
                LocationAsked.located(
                                Update.update(elsewhere, new CheckoutPullRequestMsg.WorktreeRequested(own("patch-1"))))
                        .model()
                        .popup());
    }

    @Test
    void whileTheWorktreeOfAPullRequestIsBeingAddedItsBranchIsBusyAndAnotherIsNotAsked() {
        PullRequestSummary own = own("feature/login");
        Model asked = LocationAsked.located(
                        Update.update(withMainWorktree(CLEAN), new CheckoutPullRequestMsg.WorktreeRequested(own)))
                .model();

        Next adding = TestModels.stepped(asked, new PopupMsg.Confirmed(NewWorktreePopup.class));
        BranchRef login = new BranchRef.Local(new BranchName("feature/login"));
        assertEquals(
                Option.of(AddWorktreeCmd.ADD_WORKTREE),
                adding.model().jobs().busy().get(login));

        Next again =
                LocationAsked.located(Update.update(adding.model(), new CheckoutPullRequestMsg.WorktreeRequested(own)));
        assertEquals(Option.none(), again.model().popup(NewWorktreePopup.class));
        assertEquals(List.empty(), again.cmds());

        // The branch gh gives one from a fork is not known up front, so nothing is busy for it.
        assertEquals(
                Option.none(),
                new AddWorktreeCmd(new PullRequestWorktree(summary(new RepoSlug("owner/repo"))), "/repos/app-pr-9")
                        .busyBranch());
    }
}
