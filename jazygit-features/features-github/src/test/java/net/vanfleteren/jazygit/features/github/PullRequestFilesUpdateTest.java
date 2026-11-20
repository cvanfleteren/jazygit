package net.vanfleteren.jazygit.features.github;

import static net.vanfleteren.jazygit.features.github.PullRequestFixtures.*;
import static net.vanfleteren.jazygit.kernel.state.TestModels.*;
import static net.vanfleteren.jazygit.kernel.state.TestModels.lastRead;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.core.gh.StubGitHub;
import net.vanfleteren.jazygit.core.gh.model.GhBranch;
import net.vanfleteren.jazygit.core.gh.model.GhCommit;
import net.vanfleteren.jazygit.core.gh.model.PrNumber;
import net.vanfleteren.jazygit.core.gh.model.PullRequest;
import net.vanfleteren.jazygit.core.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.core.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.core.gh.model.PullRequestDetails;
import net.vanfleteren.jazygit.core.gh.model.PullRequestSummary;
import net.vanfleteren.jazygit.core.git.model.*;
import net.vanfleteren.jazygit.core.repo.*;
import net.vanfleteren.jazygit.features.changes.diff.CommitFilesCmd;
import net.vanfleteren.jazygit.features.changes.diff.CommitFilesMsg;
import net.vanfleteren.jazygit.features.github.browse.BrowsePullRequestCmd;
import net.vanfleteren.jazygit.features.github.create.CreatePullRequestCmd.*;
import net.vanfleteren.jazygit.features.github.show.PullRequestCmd.*;
import net.vanfleteren.jazygit.features.github.show.PullRequestMsg;
import net.vanfleteren.jazygit.features.github.show.PullRequestPopup;
import net.vanfleteren.jazygit.kernel.state.*;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.diff.BaseTip;
import net.vanfleteren.jazygit.kernel.state.diff.Changes;
import net.vanfleteren.jazygit.kernel.state.github.*;
import net.vanfleteren.jazygit.kernel.state.github.prs.PrOverview;
import net.vanfleteren.jazygit.kernel.state.load.*;
import net.vanfleteren.jazygit.kernel.state.operation.*;
import net.vanfleteren.jazygit.kernel.state.popup.*;
import net.vanfleteren.jazygit.kernel.state.preferences.*;
import net.vanfleteren.jazygit.kernel.state.view.*;
import org.junit.jupiter.api.Test;

/**
 * Showing the files of a pull request, or of a branch against its base.
 */
class PullRequestFilesUpdateTest {

    private static final String HEAD_OID = "a".repeat(40);

    private static final String BASE_OID = "b".repeat(40);

    /**
     * The dialog showing pull request 42 as read, of {@code owner/repo} on github.com, at known commits when
     * {@code known}.
     */
    private static Model showingCommits(PullRequestDetails.State state, boolean known) {
        Model shown =
                Update.update(withPr(), new PullRequestMsg.Requested(FEATURE)).model();
        PullRequestDetails read =
                StubGitHub.of().pullRequests().details(new PrNumber(42)).get();
        PullRequestDetails details = read.withRepository("owner/repo")
                .withUrl("https://github.com/owner/repo")
                .withStatus(read.status().withState(state))
                .withBranches(new PullRequestDetails.Branches(
                        new GhBranch("feature"),
                        new GhBranch("main"),
                        GhCommit.parse(known ? HEAD_OID : ""),
                        Option.none(),
                        GhCommit.parse(BASE_OID)));
        return Update.update(shown, new PullRequestMsg.DetailsLoaded(new PrNumber(42), lastRead(shown), details))
                .model();
    }

    @Test
    void theFilesOfAPullRequestOpenInTheFilesPaneInPlaceOfTheDialog() {
        PullRequestCommits commits =
                new PullRequestCommits(42, "owner/repo", "github.com", new Sha(BASE_OID), new Sha(HEAD_OID));
        // A pull request closed without being merged shows its changes as it did while it was open.
        for (PullRequestDetails.State state : List.of(PullRequestDetails.State.OPEN, PullRequestDetails.State.CLOSED)) {
            Model shown = showingCommits(state, true).withScreen(PrOverview.SCREEN);

            Next opened = Update.update(shown, new PullRequestMsg.FilesChosen());

            Changes of = new Changes.OfPullRequest(commits);
            assertEquals(Option.none(), opened.model().popup(PullRequestPopup.class));
            assertEquals(Screen.REPOSITORY, opened.model().screen());
            assertEquals(
                    Option.of(CommitFilesView.of(of, new PaneId("branches"), new PaneId("files"))),
                    opened.model().repoView().files().commitFiles());
            assertEquals(
                    List.of(
                            new LoadCommitFiles(of),
                            new CommitFilesCmd.LoadReviewRemarks(new PrNumber(42), HashSet.empty(), 0),
                            new CommitFilesCmd.LoadCollapsed("owner/repo", new PrNumber(42))),
                    opened.cmds());
            assertTrue(opened.cmds().head().background());
        }
    }

    @Test
    void theFilesOfAPullRequestThatIsMergedOrAtCommitsNotKnownAreNotListed() {
        for (Model shown : List.of(
                showingCommits(PullRequestDetails.State.MERGED, true),
                showingCommits(PullRequestDetails.State.OPEN, false))) {
            Next chosen = Update.update(shown, new PullRequestMsg.FilesChosen());

            assertEquals(shown, chosen.model());
            assertEquals(List.empty(), chosen.cmds());
        }
    }

    /**
     * The details of pull request 42 as read, of {@code owner/repo} on github.com, at known commits.
     */
    private static PullRequestDetails readAtCommits(PullRequestDetails.State state) {
        PullRequestDetails read =
                StubGitHub.of().pullRequests().details(new PrNumber(42)).get();
        return read.withRepository("owner/repo")
                .withUrl("https://github.com/owner/repo")
                .withStatus(read.status().withState(state))
                .withBranches(new PullRequestDetails.Branches(
                        new GhBranch("feature"),
                        new GhBranch("main"),
                        GhCommit.parse(HEAD_OID),
                        Option.none(),
                        GhCommit.parse(BASE_OID)));
    }

    @Test
    void shiftFOnABranchWhosePullRequestIsListedWithoutItsCommitsShowsItsFilesOnceItIsRead() {
        Next requested = Update.update(withPr(), new PullRequestMsg.FilesRequested(FEATURE));

        // Its dialog shows it being read meanwhile.
        PullRequestPopup loading =
                requested.model().popup(PullRequestPopup.class).get();
        assertTrue(loading.filesWanted());
        assertEquals(
                List.of(new LoadPullRequestDetails(new PrNumber(42), lastRead(requested.model()))), requested.cmds());

        Next opened = Update.update(
                requested.model(),
                new PullRequestMsg.DetailsLoaded(
                        new PrNumber(42), lastRead(requested.model()), readAtCommits(PullRequestDetails.State.OPEN)));

        Changes of = new Changes.OfPullRequest(
                new PullRequestCommits(42, "owner/repo", "github.com", new Sha(BASE_OID), new Sha(HEAD_OID)));
        assertEquals(Option.none(), opened.model().popup(PullRequestPopup.class));
        assertEquals(Screen.REPOSITORY, opened.model().screen());
        assertEquals(
                Option.of(CommitFilesView.of(of, new PaneId("branches"), new PaneId("files"))),
                opened.model().repoView().files().commitFiles());
        assertTrue(opened.cmds()
                .containsAll(List.of(
                        new LoadCommitFiles(of),
                        new CommitFilesCmd.LoadReviewRemarks(new PrNumber(42), HashSet.empty(), 0))));
    }

    @Test
    void aPullRequestWhoseFilesCannotBeShownStaysShownInItsDialog() {
        Model requested = Update.update(withPr(), new PullRequestMsg.FilesRequested(FEATURE))
                .model();
        Model merged = Update.update(
                        requested,
                        new PullRequestMsg.DetailsLoaded(
                                new PrNumber(42), lastRead(requested), readAtCommits(PullRequestDetails.State.MERGED)))
                .model();
        Model failed = Update.update(
                        requested,
                        new PullRequestMsg.DetailsFailed(new PrNumber(42), lastRead(requested), refused("gh: offline")))
                .model();

        for (Model shown : List.of(merged, failed)) {
            PullRequestPopup popup = shown.popup(PullRequestPopup.class).get();
            assertTrue(popup.details().isDefined());
            // A read later, like the one R asks for, does not go on to the files.
            assertFalse(popup.filesWanted());
        }
    }

    @Test
    void shiftFOnABranchWhosePullRequestIsListedWithItsCommitsShowsItsFilesAtOnce() {
        PullRequest listed = new PullRequest(
                new PrNumber(42),
                new GhBranch("feature"),
                false,
                Checks.PASSING,
                Review.APPROVED,
                Option.none(),
                Option.of(new PullRequest.Commits(
                        "owner/repo",
                        "https://github.com/owner/repo/pull/42",
                        new GhCommit(BASE_OID),
                        new GhCommit(HEAD_OID))));
        Model shown = Update.update(model(), new PullRequestsMsg.Loaded(List.of(listed)))
                .model();

        Next opened = Update.update(shown, new PullRequestMsg.FilesRequested(FEATURE));

        Changes of = new Changes.OfPullRequest(
                new PullRequestCommits(42, "owner/repo", "github.com", new Sha(BASE_OID), new Sha(HEAD_OID)));
        assertEquals(Option.none(), opened.model().popup(PullRequestPopup.class));
        assertEquals(
                Option.of(CommitFilesView.of(of, new PaneId("branches"), new PaneId("files"))),
                opened.model().repoView().files().commitFiles());
        assertEquals(
                List.of(
                        new LoadCommitFiles(of),
                        new CommitFilesCmd.LoadReviewRemarks(new PrNumber(42), HashSet.empty(), 0),
                        new CommitFilesCmd.LoadCollapsed("owner/repo", new PrNumber(42))),
                opened.cmds());
    }

    @Test
    void shiftFOnABranchWithoutAPullRequestShowsItsFilesAgainstItsBase() {
        Changes of = new Changes.OfBranch(
                UNPUSHED.name(), new BaseTip(new BranchRef.Local(MAIN.name()), MAIN.tip()), UNPUSHED.tip());
        // Also when it is not known whether it has one: the pull requests are not read yet, or reading them failed.
        for (Model shown : List.of(
                withPr(),
                localBranchesRead(loaded(), List.of(UNPUSHED, MAIN)).model(),
                Update.update(model(), new PullRequestsMsg.LoadFailed(refused("gh: offline")))
                        .model())) {
            Next opened = Update.update(shown, new PullRequestMsg.FilesRequested(UNPUSHED.name()));

            assertEquals(
                    Option.of(CommitFilesView.of(of, new PaneId("branches"), new PaneId("files"))),
                    opened.model().repoView().files().commitFiles());
            assertEquals(List.of(new LoadCommitFiles(of)), opened.cmds());
            assertFalse(opened.cmds().head().background());
        }
    }

    @Test
    void shiftFOnTheMainBranchListsAllFilesOfItsTipAsCommitted() {
        Next opened = Update.update(withPr(), new PullRequestMsg.FilesRequested(MAIN.name()));

        Changes of = new Changes.OfTip(MAIN.name(), MAIN.tip());
        assertEquals(Option.none(), opened.model().outcome().error());
        assertEquals(
                Option.of(CommitFilesView.of(of, new PaneId("branches"), new PaneId("files"))
                        .withAllFiles(true)
                        .withTree(Option.of(Loadable.loading()))),
                opened.model().repoView().files().commitFiles());
        assertEquals(List.of(new LoadCommitFiles(of), new LoadCommitTree(of)), opened.cmds());
    }

    @Test
    void shiftFOnTheMainBranchWithAPullRequestShowsTheFilesOfThatAsOnAnyBranch() {
        PullRequest fromMain =
                new PullRequest(new PrNumber(7), new GhBranch("main"), false, Checks.PASSING, Review.APPROVED);
        Model shown = Update.update(model(), new PullRequestsMsg.Loaded(List.of(fromMain)))
                .model();

        Next requested = Update.update(shown, new PullRequestMsg.FilesRequested(MAIN.name()));

        // Its dialog reads its commits first, as for a pull request of any branch listed without them.
        assertTrue(requested.model().popup(PullRequestPopup.class).get().filesWanted());
        assertEquals(
                List.of(new LoadPullRequestDetails(new PrNumber(7), lastRead(requested.model()))), requested.cmds());
        assertEquals(Option.none(), requested.model().repoView().files().commitFiles());
    }

    @Test
    void theFilesOfABranchShownTakeTheErrorBeforeAway() {
        Model failed = withPr().fail(new BaseBranchError.None(UNPUSHED.name()));

        Next opened = Update.update(failed, new PullRequestMsg.FilesRequested(UNPUSHED.name()));

        assertEquals(Option.none(), opened.model().outcome().error());
        assertTrue(opened.model().repoView().files().commitFiles().isDefined());
    }

    /**
     * {@code model} once the details of pull request 42 come back from read {@code read}, open at known commits.
     */
    private static Next readAt(Model model, long read) {
        return Update.update(
                model,
                new PullRequestMsg.DetailsLoaded(new PrNumber(42), read, readAtCommits(PullRequestDetails.State.OPEN)));
    }

    @Test
    void theFilesAreNotShownWhenTheDialogWasClosedBeforeItWasRead() {
        Model requested = Update.update(withPr(), new PullRequestMsg.FilesRequested(FEATURE))
                .model();
        Model closed = Update.update(requested, new PopupMsg.Dismissed(PullRequestPopup.class))
                .model();

        Next read = readAt(closed, lastRead(requested));

        assertEquals(Option.none(), read.model().repoView().files().commitFiles());
        assertEquals(Option.none(), read.model().popup(PullRequestPopup.class));
        assertFalse(read.cmds().exists(cmd -> cmd instanceof LoadCommitFiles));
    }

    @Test
    void theFilesAreNotShownWhenTheDialogOfAnotherPullRequestWasOpenedMeanwhile() {
        Model both = Update.update(
                        model(),
                        new PullRequestsMsg.Loaded(List.of(
                                FEATURE_PR,
                                new PullRequest(
                                        new PrNumber(43), new GhBranch("ahead"), false, Checks.PASSING, Review.NONE))))
                .model();
        Model requested =
                Update.update(both, new PullRequestMsg.FilesRequested(FEATURE)).model();
        // Its files are asked for too, and wait for its own details.
        Model other = Update.update(requested, new PullRequestMsg.FilesRequested(AHEAD.name()))
                .model();

        Next read = readAt(other, lastRead(requested));

        assertEquals(Option.none(), read.model().repoView().files().commitFiles());
        PullRequestPopup shown = read.model().popup(PullRequestPopup.class).get();
        assertEquals(new PrNumber(43), shown.number());
        assertTrue(shown.filesWanted());
    }

    @Test
    void theFilesAreShownOnlyOnceTheReadTheDialogWaitsForIsBack() {
        Model first = Update.update(withPr(), new PullRequestMsg.FilesRequested(FEATURE))
                .model();
        // Closed and asked for again: a newer read of the same pull request is waited for.
        Model again = Update.update(
                        Update.update(first, new PopupMsg.Dismissed(PullRequestPopup.class))
                                .model(),
                        new PullRequestMsg.FilesRequested(FEATURE))
                .model();

        Next older = readAt(again, lastRead(first));

        assertEquals(Option.none(), older.model().repoView().files().commitFiles());
        assertTrue(older.model().popup(PullRequestPopup.class).get().filesWanted());

        Next newer = readAt(older.model(), lastRead(again));

        assertTrue(newer.model().repoView().files().commitFiles().isDefined());
        assertEquals(Option.none(), newer.model().popup(PullRequestPopup.class));
    }

    @Test
    void onceItsDetailsWereReadThePullRequestOfABranchShowsItsFilesAtOnce() {
        Model requested = Update.update(withPr(), new PullRequestMsg.FilesRequested(FEATURE))
                .model();
        Model shown = readAt(requested, lastRead(requested)).model();
        Model closed = Update.update(shown, new CommitFilesMsg.Closed()).model();

        Next again = Update.update(closed, new PullRequestMsg.FilesRequested(FEATURE));

        assertEquals(Option.none(), again.model().popup(PullRequestPopup.class));
        assertEquals(
                Option.of(CommitFilesView.of(
                        new Changes.OfPullRequest(new PullRequestCommits(
                                42, "owner/repo", "github.com", new Sha(BASE_OID), new Sha(HEAD_OID))),
                        new PaneId("branches"),
                        new PaneId("files"))),
                again.model().repoView().files().commitFiles());
        assertFalse(again.cmds().exists(cmd -> cmd instanceof LoadPullRequestDetails));
    }

    @Test
    void aBranchWithoutABaseHasNoFilesToShow() {
        Model alone = Update.update(
                        localBranchesRead(loaded(), List.of(UNPUSHED)).model(),
                        new PullRequestsMsg.Loaded(List.empty()))
                .model();

        Next opened = Update.update(alone, new PullRequestMsg.FilesRequested(UNPUSHED.name()));

        assertEquals(
                Option.of(new BaseBranchError.None(UNPUSHED.name())),
                opened.model().outcome().error());
        assertEquals(List.empty(), opened.cmds());
    }

    @Test
    void shiftFOnThePullRequestsScreenShowsTheFilesOfAPullRequestAtItsCommits() {
        PullRequestSummary listed = new PullRequestSummary(
                "owner/repo",
                new PrNumber(9),
                "From a fork",
                "bob",
                new GhBranch("patch-1"),
                false,
                true,
                Checks.NONE,
                Review.NONE,
                Instant.EPOCH,
                "https://github.com/owner/repo/pull/9",
                Option.none(),
                Option.of(new GhCommit(BASE_OID)),
                Option.of(new GhCommit(HEAD_OID)));
        Model ofOwnerRepo = loaded().withScreen(PrOverview.SCREEN)
                .withPrOverview(net.vanfleteren.jazygit.kernel.state.github.prs.PrOverview.INITIAL.withRepositoryName(
                        Option.of("owner/repo")));

        Next opened = Update.update(ofOwnerRepo, new PullRequestMsg.SummaryFilesRequested(listed));

        Changes of = new Changes.OfPullRequest(
                new PullRequestCommits(9, "owner/repo", "github.com", new Sha(BASE_OID), new Sha(HEAD_OID)));
        assertEquals(Option.none(), opened.model().popup());
        assertEquals(Screen.REPOSITORY, opened.model().screen());
        assertEquals(
                Option.of(CommitFilesView.of(of, new PaneId("branches"), new PaneId("files"))),
                opened.model().repoView().files().commitFiles());
        assertTrue(opened.cmds().contains(new LoadCommitFiles(of)));
    }

    @Test
    void shiftFOnThePullRequestsScreenShowsTheFilesOfAPullRequestListedWithoutItsCommitsOnceItIsRead() {
        Model ofOwnerRepo = loaded().withScreen(PrOverview.SCREEN)
                .withPrOverview(net.vanfleteren.jazygit.kernel.state.github.prs.PrOverview.INITIAL.withRepositoryName(
                        Option.of("owner/repo")));

        Next requested =
                Update.update(ofOwnerRepo, new PullRequestMsg.SummaryFilesRequested(summary("owner/repo", 42)));

        // Its dialog shows it being read meanwhile, over the pull requests screen.
        PullRequestPopup loading =
                requested.model().popup(PullRequestPopup.class).get();
        assertTrue(loading.filesWanted());
        assertEquals(PrOverview.SCREEN, requested.model().screen());
        assertEquals(
                List.of(new LoadPullRequestDetails(new PrNumber(42), lastRead(requested.model()))), requested.cmds());

        Next opened = Update.update(
                requested.model(),
                new PullRequestMsg.DetailsLoaded(
                        new PrNumber(42), lastRead(requested.model()), readAtCommits(PullRequestDetails.State.OPEN)));

        Changes of = new Changes.OfPullRequest(
                new PullRequestCommits(42, "owner/repo", "github.com", new Sha(BASE_OID), new Sha(HEAD_OID)));
        assertEquals(Option.none(), opened.model().popup(PullRequestPopup.class));
        assertEquals(Screen.REPOSITORY, opened.model().screen());
        assertEquals(
                Option.of(CommitFilesView.of(of, new PaneId("branches"), new PaneId("files"))),
                opened.model().repoView().files().commitFiles());
        assertTrue(opened.cmds().contains(new LoadCommitFiles(of)));
    }

    @Test
    void shiftFOnAPullRequestOfAnotherRepositoryOpensItInTheBrowser() {
        PullRequestSummary elsewhere = summary("other/repo");
        Model ofOwnerRepo = loaded().withPrOverview(
                        net.vanfleteren.jazygit.kernel.state.github.prs.PrOverview.INITIAL.withRepositoryName(
                                Option.of("owner/repo")));

        Next opened = Update.update(ofOwnerRepo, new PullRequestMsg.SummaryFilesRequested(elsewhere));

        assertEquals(Option.none(), opened.model().popup());
        assertEquals(Option.none(), opened.model().repoView().files().commitFiles());
        assertEquals(List.of(new BrowsePullRequestCmd(elsewhere)), opened.cmds());
    }
}
