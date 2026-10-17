package net.vanfleteren.jazygit.feature.github;

import static net.vanfleteren.jazygit.state.TestModels.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.feature.fetch.FetchCmd;
import net.vanfleteren.jazygit.feature.github.PullRequestCmd.*;
import net.vanfleteren.jazygit.gh.GhError;
import net.vanfleteren.jazygit.gh.GhFailure;
import net.vanfleteren.jazygit.gh.StubGitHub;
import net.vanfleteren.jazygit.gh.model.MergeMethod;
import net.vanfleteren.jazygit.gh.model.NewPullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails;
import net.vanfleteren.jazygit.git.StubGitProvider;
import net.vanfleteren.jazygit.git.model.*;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.*;
import net.vanfleteren.jazygit.state.Update.Next;
import org.junit.jupiter.api.Test;

class PullRequestUpdateTest {

    private static final Branch MAIN = new Branch(
            new Branch.Name("main"),
            true,
            new Sha("1"),
            Instant.EPOCH,
            true,
            true,
            Option.of(new Upstream(new RemoteBranch("origin", new Branch.Name("main")), 0, 0)));
    private static final Branch RELEASE = new Branch(
            new Branch.Name("release"),
            false,
            new Sha("2"),
            Instant.EPOCH,
            true,
            false,
            Option.of(new Upstream(new RemoteBranch("origin", new Branch.Name("release")), 0, 0)));
    private static final Branch PUSHED = new Branch(
            new Branch.Name("feature"),
            false,
            new Sha("3"),
            Instant.EPOCH,
            true,
            false,
            Option.of(new Upstream(new RemoteBranch("origin", new Branch.Name("feature")), 0, 0)));
    private static final Branch UNPUSHED = new Branch(new Branch.Name("local"), false, new Sha("4"));
    private static final Branch AHEAD = new Branch(
            new Branch.Name("ahead"),
            false,
            new Sha("5"),
            Instant.EPOCH,
            true,
            false,
            Option.of(new Upstream(new RemoteBranch("origin", new Branch.Name("ahead")), 2, 0)));
    private static final Branch.Name FEATURE = PUSHED.name();
    private static final PullRequest FEATURE_PR =
            new PullRequest(42, "feature", false, Checks.PASSING, Review.APPROVED);

    private static Model withPr() {
        return Update.update(model(), new PullRequestsMsg.Loaded(List.of(FEATURE_PR)))
                .model();
    }

    private static Env env() {
        return new Env(new StubGitProvider() {}, StubGitHub.empty());
    }

    /**
     * The branches, and no pull request from any of them.
     */
    private static Model model() {
        // The default branch is listed last, to see it offered first.
        Model branches = Update.update(
                        loaded(), new LoadMsg.BranchesLoaded(List.of(RELEASE, PUSHED, UNPUSHED, AHEAD, MAIN)))
                .model();
        return Update.update(branches, new PullRequestsMsg.Loaded(List.empty())).model();
    }

    @Test
    void beforeThePullRequestsAreReadNoneIsCreated() {
        Model starting = Update.update(loaded(), new LoadMsg.BranchesLoaded(List.of(PUSHED, MAIN)))
                .model();

        Next asked = Update.update(starting, new PullRequestMsg.Requested(new Branch.Name("feature")));

        assertEquals(
                Option.of(new AppError.PullRequestsNotRead()), asked.model().error());
        assertEquals(List.empty(), asked.cmds());
    }

    @Test
    void aPushedBranchWithoutPullRequestAsksForTheBaseWithTheDefaultBranchFirst() {
        Next asked = Update.update(model(), new PullRequestMsg.Requested(new Branch.Name("feature")));

        assertEquals(
                Option.of(new BasePopup(
                        new Branch.Name("feature"),
                        List.of(new Branch.Name("main"), new Branch.Name("release"), new Branch.Name("ahead")))),
                asked.model().popup());
        assertEquals(List.empty(), asked.cmds());
    }

    @Test
    void choosingTheBaseReadsTheTitleFromTheFirstCommitNotOnTheBaseOnItsRemote() {
        Model asked = Update.update(model(), new PullRequestMsg.Requested(new Branch.Name("feature")))
                .model();

        Next reading = Update.update(asked, new PullRequestMsg.BaseChosen(new Branch.Name("main")));

        assertEquals(
                List.of(new ReadTitle(
                        FEATURE,
                        FEATURE,
                        new Branch.Name("main"),
                        new BranchRef.Remote(new RemoteBranch("origin", new Branch.Name("main"))))),
                reading.cmds());
        assertEquals(Option.none(), reading.model().popup());
    }

    @Test
    void theTitleReadIsOfferedToEdit() {
        Next asking = Update.update(
                model(), new PullRequestMsg.TitleRead(FEATURE, FEATURE, MAIN.name(), Option.of("Add login")));

        assertEquals(
                Option.of(new NewPullRequestPopup(FEATURE, FEATURE, MAIN.name(), "Add login")),
                asking.model().popup());
        assertEquals(List.empty(), asking.cmds());
    }

    @Test
    void withoutACommitOfItsOwnTheTitleIsTheBranch() {
        Next asking =
                Update.update(model(), new PullRequestMsg.TitleRead(FEATURE, FEATURE, MAIN.name(), Option.none()));

        assertEquals(
                Option.of(new NewPullRequestPopup(FEATURE, FEATURE, MAIN.name(), "feature")),
                asking.model().popup());
    }

    @Test
    void confirmingCreatesThePullRequestAsEnteredAndKeepsTheBranchBusy() {
        NewPullRequest entered = new NewPullRequest(" Add login ", "Why", true);

        Next created = Update.update(creating(), new PullRequestMsg.CreateConfirmed(entered));

        assertEquals(
                List.of(new CreatePullRequest(
                        FEATURE, FEATURE, MAIN.name(), new NewPullRequest("Add login", "Why", true))),
                created.cmds());
        assertEquals(Option.none(), created.model().popup());
        assertEquals(Option.of(Operation.CREATE_PR), created.model().busy().get(new BranchRef.Local(FEATURE)));
    }

    @Test
    void aBlankTitleKeepsThePopupOpen() {
        Next next = Update.update(creating(), new PullRequestMsg.CreateConfirmed(new NewPullRequest(" ", "", false)));

        assertEquals(List.empty(), next.cmds());
        assertTrue(next.model().popup(NewPullRequestPopup.class).isDefined());
    }

    @Test
    void cancellingTheNewPullRequestClosesThePopup() {
        assertEquals(
                Option.none(),
                Update.update(creating(), new PullRequestMsg.CreateCancelled())
                        .model()
                        .popup());
    }

    /**
     * Asking what the new pull request of the feature branch into main says.
     */
    private static Model creating() {
        return Update.update(
                        model(), new PullRequestMsg.TitleRead(FEATURE, FEATURE, MAIN.name(), Option.of("Add login")))
                .model();
    }

    @Test
    void cancellingTheBaseClosesThePopup() {
        Model asked = Update.update(model(), new PullRequestMsg.Requested(new Branch.Name("feature")))
                .model();

        assertEquals(
                Option.none(),
                Update.update(asked, new PullRequestMsg.BaseCancelled()).model().popup());
    }

    @Test
    void aBranchThatIsNotPushedOrHasUnpushedCommitsMustBePushedFirst() {
        assertEquals(
                Option.of(new AppError.NotPushed(new Branch.Name("local"))),
                Update.update(model(), new PullRequestMsg.Requested(new Branch.Name("local")))
                        .model()
                        .error());
        assertEquals(
                Option.of(new AppError.NotPushed(new Branch.Name("ahead"))),
                Update.update(model(), new PullRequestMsg.Requested(new Branch.Name("ahead")))
                        .model()
                        .error());
    }

    @Test
    void aBranchWithAPullRequestShowsItWhileItsDetailsAreRead() {
        Next shown = Update.update(withPr(), new PullRequestMsg.Requested(new Branch.Name("feature")));

        assertEquals(
                Option.of(PullRequestPopup.loading(new Branch.Name("feature"), FEATURE_PR)),
                shown.model().popup());
        assertEquals(List.of(new LoadPullRequestDetails(42)), shown.cmds());
    }

    @Test
    void theDetailsReadShowInThePopup() {
        Model shown = Update.update(withPr(), new PullRequestMsg.Requested(new Branch.Name("feature")))
                .model();
        PullRequestDetails details = StubGitHub.empty().pullRequestDetails(42).get();

        Model loaded =
                Update.update(shown, new LoadPullRequestDetails(42).run(env())).model();

        assertEquals(
                Option.of(PullRequestPopup.loading(new Branch.Name("feature"), FEATURE_PR)
                        .with(Either.right(details))),
                loaded.popup());
    }

    @Test
    void failingToReadTheDetailsShowsWhyInThePopup() {
        Model shown = Update.update(withPr(), new PullRequestMsg.Requested(new Branch.Name("feature")))
                .model();
        var failure = refused("gh: not logged in");

        Model failed = Update.update(shown, new PullRequestMsg.DetailsFailed(42, failure))
                .model();

        assertEquals(
                Option.of(PullRequestPopup.loading(new Branch.Name("feature"), FEATURE_PR)
                        .with(Either.left(failure))),
                failed.popup());
        assertEquals(Option.none(), failed.error());
    }

    @Test
    void detailsReadAfterThePopupClosedOrForAnotherPullRequestAreDropped() {
        Model shown = Update.update(withPr(), new PullRequestMsg.Requested(new Branch.Name("feature")))
                .model();
        PullRequestDetails details = StubGitHub.empty().pullRequestDetails(7).get();

        assertEquals(
                shown,
                Update.update(shown, new PullRequestMsg.DetailsLoaded(7, details))
                        .model());
        Model closed = Update.update(shown, new PullRequestMsg.InfoClosed()).model();
        assertEquals(Option.none(), closed.popup());
        assertEquals(
                closed,
                Update.update(closed, new PullRequestMsg.DetailsLoaded(42, details))
                        .model());
    }

    @Test
    void choosingToMergeOrCloseStartsItOnceTheDetailsShowItCan() {
        Model shown = Update.update(withPr(), new PullRequestMsg.Requested(new Branch.Name("feature")))
                .model();
        // Nothing is known about the pull request yet.
        assertEquals(
                List.empty(),
                Update.update(shown, new PullRequestMsg.MergeChosen(MergeMethod.SQUASH))
                        .cmds());
        assertEquals(
                List.empty(),
                Update.update(shown, new PullRequestMsg.CloseChosen()).cmds());

        Model read = Update.update(
                        shown,
                        new PullRequestMsg.DetailsLoaded(
                                42, StubGitHub.empty().pullRequestDetails(42).get()))
                .model();
        Next merging = Update.update(read, new PullRequestMsg.MergeChosen(MergeMethod.SQUASH));
        Next closing = Update.update(read, new PullRequestMsg.CloseChosen());

        assertEquals(List.of(new MergePullRequest(new Branch.Name("feature"), 42, MergeMethod.SQUASH)), merging.cmds());
        assertEquals(Option.none(), merging.model().popup());
        assertEquals(
                Option.of(Operation.MERGE_PR),
                merging.model().busy().get(new BranchRef.Local(new Branch.Name("feature"))));
        assertEquals(List.of(new ClosePullRequest(new Branch.Name("feature"), 42)), closing.cmds());
        assertEquals(
                Option.of(Operation.CLOSE_PR),
                closing.model().busy().get(new BranchRef.Local(new Branch.Name("feature"))));
    }

    @Test
    void aMergedPullRequestIsReadAgainEvenWhileAnEarlierReadRuns() {
        MergePullRequest cmd = new MergePullRequest(new Branch.Name("feature"), 42, MergeMethod.MERGE);
        // The running read started before the merge, and would list the pull request as still open.
        Model merging = withPr().withBusyBranch(new BranchRef.Local(new Branch.Name("feature")), Operation.MERGE_PR)
                .withReadingPullRequests(1);

        Next done = Update.update(merging, cmd.run(new Env(new StubGitProvider() {}, StubGitHub.empty())));

        assertTrue(done.cmds().contains(new LoadPullRequests()), done.cmds().toString());
    }

    @Test
    void aMergedPullRequestIsNoLongerListedAndThePullRequestsAreReadAgain() {
        MergePullRequest cmd = new MergePullRequest(new Branch.Name("feature"), 42, MergeMethod.MERGE);
        Model merging = withPr().withBusyBranch(new BranchRef.Local(new Branch.Name("feature")), Operation.MERGE_PR);

        Next done = Update.update(merging, cmd.run(new Env(new StubGitProvider() {}, StubGitHub.empty())));

        assertEquals(Option.none(), done.model().pullRequests().flatMap(open -> open.get(new Branch.Name("feature"))));
        assertEquals(Option.none(), done.model().busy().get(new BranchRef.Local(new Branch.Name("feature"))));
        assertTrue(done.cmds().contains(new LoadPullRequests()), done.cmds().toString());
        // The merge changed the base branch on the remote.
        assertTrue(done.cmds().contains(new FetchCmd.ListRemotes()), done.cmds().toString());
        assertEquals(
                "gh pr merge 42 --merge",
                done.model().commandLog().last().commands().last().line());
    }

    @Test
    void aMergeThatGitHubRefusesIsShownAsAnErrorAndKeepsThePullRequest() {
        Model shown = Update.update(withPr(), new PullRequestMsg.Requested(new Branch.Name("feature")))
                .model();
        Model read = Update.update(
                        shown,
                        new PullRequestMsg.DetailsLoaded(
                                42, StubGitHub.empty().pullRequestDetails(42).get()))
                .model();
        Next merging = Update.update(read, new PullRequestMsg.MergeChosen(MergeMethod.SQUASH));
        MergePullRequest cmd = (MergePullRequest) merging.cmds().head();
        // What gh says when e.g. a required check fails or a review is missing.
        StubGitHub refusing = new StubGitHub() {
            @Override
            public Either<GhFailure, List<ExecutedCommand>> mergePullRequest(int number, MergeMethod method) {
                return Either.left(new GhFailure(
                        new GhError.CommandFailed(
                                Option.of(
                                        "Pull request #42 is not mergeable: the base branch policy prohibits the merge."),
                                1),
                        List.of(ExecutedCommand.of("gh", "pr", "merge", "42", "--squash"))));
            }
        };

        Next failed = Update.update(merging.model(), cmd.run(new Env(new StubGitProvider() {}, refusing)));

        // The pull request dialog stays closed; the error comes up in its own popup.
        assertEquals(Option.none(), failed.model().popup(PullRequestPopup.class));
        assertTrue(
                failed.model().popup(ErrorPopup.class).isDefined(),
                failed.model().popup().toString());
        assertEquals(
                Option.of("Merging the pull request of feature failed: Pull request #42 is not mergeable: "
                        + "the base branch policy prohibits the merge."),
                errorText(failed.model()));
        assertEquals(Option.none(), failed.model().busy().get(new BranchRef.Local(new Branch.Name("feature"))));
        assertEquals(
                Option.of(FEATURE_PR),
                failed.model().pullRequests().flatMap(open -> open.get(new Branch.Name("feature"))));
    }

    @Test
    void openingThePullRequestInTheBrowserKeepsTheDialogOpen() {
        Model shown = Update.update(withPr(), new PullRequestMsg.Requested(new Branch.Name("feature")))
                .model();

        Next opened = Update.update(shown, new PullRequestMsg.OpenConfirmed());

        assertEquals(List.of(new OpenPullRequest(new Branch.Name("feature"), 42)), opened.cmds());
        assertEquals(shown.popup(), opened.model().popup());

        Next done = Update.update(
                opened.model(), new OpenPullRequest(new Branch.Name("feature"), 42).succeeded(List.empty()));
        assertEquals(shown.popup(), done.model().popup());
    }

    @Test
    void thePullRequestOfABranchIsFoundByItsNameOnTheRemote() {
        Branch renamed = new Branch(
                new Branch.Name("mine"),
                false,
                new Sha("6"),
                Instant.EPOCH,
                true,
                false,
                Option.of(new Upstream(new RemoteBranch("origin", new Branch.Name("feature")), 0, 0)));
        Model withPr = Update.update(model(), new PullRequestsMsg.Loaded(List.of(FEATURE_PR)))
                .model();

        assertEquals(Option.of(FEATURE_PR), withPr.pullRequest(renamed));
        assertEquals(Option.none(), withPr.pullRequest(UNPUSHED));
    }

    @Test
    void thePullRequestOfABranchIsFoundEvenWhenItsRemoteNameHasASlash() {
        Branch onFork = new Branch(
                new Branch.Name("mine"),
                false,
                new Sha("6"),
                Instant.EPOCH,
                true,
                false,
                Option.of(new Upstream(new RemoteBranch("my/fork", new Branch.Name("feature")), 0, 0)));
        Model withPr = Update.update(model(), new PullRequestsMsg.Loaded(List.of(FEATURE_PR)))
                .model();

        assertEquals(Option.of(FEATURE_PR), withPr.pullRequest(onFork));
    }

    @Test
    void askingForAPullRequestAfterFailingToReadThemTellsWhy() {
        var failure = refused("gh: not logged in");
        Model failed =
                Update.update(model(), new PullRequestsMsg.LoadFailed(failure)).model();

        Next asked = Update.update(failed, new PullRequestMsg.Requested(new Branch.Name("feature")));

        assertEquals(
                Option.of(new AppError.PullRequestsUnavailable(failure)),
                asked.model().error());
        assertEquals(List.empty(), asked.cmds());
    }

    @Test
    void aCreatedPullRequestIsLoggedAndThePullRequestsAreReadAgain() {
        CreatePullRequest cmd =
                new CreatePullRequest(FEATURE, FEATURE, MAIN.name(), new NewPullRequest("Login", "", true));
        Model creating = Update.update(creating(), new PullRequestMsg.CreateConfirmed(cmd.pullRequest()))
                .model();

        Next done = Update.update(creating, cmd.run(new Env(new StubGitProvider() {}, StubGitHub.empty())));

        assertTrue(done.cmds().contains(new LoadPullRequests()), done.cmds().toString());
        assertEquals(Option.none(), done.model().busy().get(new BranchRef.Local(new Branch.Name("feature"))));
        assertTrue(done.cmds().contains(new FetchCmd.ListRemotes()), done.cmds().toString());
        assertEquals(
                "gh pr create --head feature --base main --title Login --body '' --draft",
                done.model().commandLog().last().commands().last().line());
    }

    @Test
    void aCreatedPullRequestShowsAtOnceEvenIfGitHubDoesNotListItYet() {
        CreatePullRequest cmd =
                new CreatePullRequest(FEATURE, FEATURE, MAIN.name(), new NewPullRequest("Login", "", false));
        PullRequest created = new PullRequest(43, "feature", false, Checks.NONE, Review.NONE);
        Model creating = Update.update(creating(), new PullRequestMsg.CreateConfirmed(cmd.pullRequest()))
                .model();

        Next done = Update.update(creating, new PullRequestMsg.Created(cmd, Option.of(created), List.empty()));

        assertEquals(
                Option.of(created), done.model().pullRequests().flatMap(open -> open.get(new Branch.Name("feature"))));
        assertTrue(done.cmds().contains(new LoadPullRequests()));
    }
}
