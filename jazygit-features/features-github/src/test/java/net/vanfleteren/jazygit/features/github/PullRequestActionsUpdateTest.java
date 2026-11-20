package net.vanfleteren.jazygit.features.github;

import static net.vanfleteren.jazygit.features.github.PullRequestFixtures.*;
import static net.vanfleteren.jazygit.kernel.state.TestModels.*;
import static net.vanfleteren.jazygit.kernel.state.TestModels.lastRead;
import static net.vanfleteren.jazygit.kernel.ui.text.ErrorTexts.errorText;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.core.gh.GitHub;
import net.vanfleteren.jazygit.core.gh.StubGitHub;
import net.vanfleteren.jazygit.core.gh.model.GhBranch;
import net.vanfleteren.jazygit.core.gh.model.GhCommit;
import net.vanfleteren.jazygit.core.gh.model.MergeMethod;
import net.vanfleteren.jazygit.core.gh.model.MergeStatus;
import net.vanfleteren.jazygit.core.gh.model.PrNumber;
import net.vanfleteren.jazygit.core.gh.model.PullRequest;
import net.vanfleteren.jazygit.core.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.core.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.core.gh.model.PullRequestDetails;
import net.vanfleteren.jazygit.core.gh.model.PullRequestStack;
import net.vanfleteren.jazygit.core.gh.process.GhError;
import net.vanfleteren.jazygit.core.gh.process.GhFailure;
import net.vanfleteren.jazygit.core.gh.pullrequests.PullRequests;
import net.vanfleteren.jazygit.core.gh.pullrequests.StubPullRequests;
import net.vanfleteren.jazygit.core.git.StubGit;
import net.vanfleteren.jazygit.core.git.model.*;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.core.repo.*;
import net.vanfleteren.jazygit.features.changes.fetch.FetchCmd;
import net.vanfleteren.jazygit.features.github.create.CreatePullRequestCmd.*;
import net.vanfleteren.jazygit.features.github.show.PullRequestCmd;
import net.vanfleteren.jazygit.features.github.show.PullRequestCmd.*;
import net.vanfleteren.jazygit.features.github.show.PullRequestMsg;
import net.vanfleteren.jazygit.features.github.show.PullRequestPopup;
import net.vanfleteren.jazygit.kernel.state.*;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.github.*;
import net.vanfleteren.jazygit.kernel.state.github.GitHubState;
import net.vanfleteren.jazygit.kernel.state.github.prs.LoadRepositoryPullRequests;
import net.vanfleteren.jazygit.kernel.state.load.*;
import net.vanfleteren.jazygit.kernel.state.operation.*;
import net.vanfleteren.jazygit.kernel.state.popup.*;
import net.vanfleteren.jazygit.kernel.state.preferences.*;
import net.vanfleteren.jazygit.kernel.state.view.*;
import org.junit.jupiter.api.Test;

/**
 * Merging, closing and toggling the draft of a pull request, and following a merge GitHub does later.
 */
class PullRequestActionsUpdateTest {

    @Test
    void choosingToMergeOrCloseStartsItOnceTheDetailsShowItCan() {
        Model shown = Update.update(withPr(), new PullRequestMsg.Requested(new BranchName("feature")))
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
                                new PrNumber(42),
                                lastRead(shown),
                                StubGitHub.of()
                                        .pullRequests()
                                        .details(new PrNumber(42))
                                        .get()))
                .model();
        Next merging = TestModels.stepped(read, new PullRequestMsg.MergeChosen(MergeMethod.SQUASH));
        Next closing = TestModels.stepped(read, new PullRequestMsg.CloseChosen());

        assertEquals(
                List.of(new MergePullRequest(
                        new BranchName("feature"), new PrNumber(42), Option.none(), MergeMethod.SQUASH)),
                merging.cmds());
        assertEquals(Option.none(), merging.model().popup());
        assertEquals(
                Option.of(PullRequestCmd.MERGE_PR),
                merging.model().jobs().busy().get(new BranchRef.Local(new BranchName("feature"))));
        assertEquals(List.of(new ClosePullRequest(new BranchName("feature"), new PrNumber(42))), closing.cmds());
        assertEquals(
                Option.of(PullRequestCmd.CLOSE_PR),
                closing.model().jobs().busy().get(new BranchRef.Local(new BranchName("feature"))));
    }

    @Test
    void choosingDraftTogglesItOnceTheDetailsAreReadAndKeepsTheDialogOpen() {
        Model shown =
                Update.update(withPr(), new PullRequestMsg.Requested(FEATURE)).model();
        assertEquals(
                List.empty(),
                Update.update(shown, new PullRequestMsg.DraftChosen()).cmds());

        Model read = Update.update(
                        shown,
                        new PullRequestMsg.DetailsLoaded(
                                new PrNumber(42),
                                lastRead(shown),
                                StubGitHub.of()
                                        .pullRequests()
                                        .details(new PrNumber(42))
                                        .get()))
                .model();
        Next drafting = TestModels.stepped(read, new PullRequestMsg.DraftChosen());

        assertEquals(List.of(new SetDraft(FEATURE, new PrNumber(42), true)), drafting.cmds());
        assertTrue(drafting.model().popup(PullRequestPopup.class).isDefined());
        assertEquals(
                Option.of(PullRequestCmd.DRAFT_PR),
                drafting.model().jobs().busy().get(new BranchRef.Local(FEATURE)));
        // While it runs, it is not started again.
        assertEquals(
                List.empty(),
                Update.update(drafting.model(), new PullRequestMsg.DraftChosen())
                        .cmds());
    }

    @Test
    void aPullRequestThatBecameADraftShowsAsOneAndThePullRequestsAreReadAgain() {
        Model read = Update.update(
                        Update.update(withPr(), new PullRequestMsg.Requested(FEATURE))
                                .model(),
                        new PullRequestMsg.DetailsLoaded(
                                new PrNumber(42),
                                0,
                                StubGitHub.of()
                                        .pullRequests()
                                        .details(new PrNumber(42))
                                        .get()))
                .model();
        Next drafting = Update.update(read, new PullRequestMsg.DraftChosen());
        SetDraft cmd = (SetDraft) drafting.cmds().head();

        Next done = Update.update(drafting.model(), cmd.run(env()));

        assertEquals(Option.of(true), done.model().popup(PullRequestPopup.class).flatMap(PullRequestPopup::draft));
        assertEquals(Option.none(), done.model().jobs().busy().get(new BranchRef.Local(FEATURE)));
        assertTrue(
                done.cmds().contains(new LoadRepositoryPullRequests()),
                done.cmds().toString());
        assertEquals(
                "gh pr ready 42 --undo",
                done.model().outcome().commandLog().last().commands().last().line());
    }

    @Test
    void markingADraftReadyRunsGhPrReady() {
        SetDraft cmd = new SetDraft(FEATURE, new PrNumber(42), false);
        Model marking = TestModels.running(withPr(), cmd);

        Next done = TestModels.done(marking, cmd, cmd.run(env()));

        assertEquals(
                "gh pr ready 42",
                done.model().outcome().commandLog().last().commands().last().line());
    }

    /**
     * GitHub that merges each pull request as soon as it is asked to.
     */
    private static GitHub mergingAtOnce() {
        return StubGitHub.of().withPullRequests(new StubPullRequests() {
            @Override
            public Either<GhFailure, PullRequests.Merging> merge(
                    PrNumber number, Option<GhCommit> head, MergeMethod method) {
                return Either.right(new PullRequests.Merging(new MergeStatus.Merged(), List.empty()));
            }
        });
    }

    /**
     * {@code model} once GitHub was asked to merge pull request 42 of feature, and said it is doing so.
     */
    private static Model asked(Model model) {
        MergePullRequest cmd =
                new MergePullRequest(FEATURE, new PrNumber(42), Option.of(new GhCommit("abc123")), MergeMethod.MERGE);
        return TestModels.done(TestModels.running(model, cmd), cmd, cmd.run(env()))
                .model();
    }

    @Test
    void aMergedPullRequestIsReadAgainOnceAnEarlierReadIsDone() {
        MergePullRequest cmd = new MergePullRequest(FEATURE, new PrNumber(42), Option.none(), MergeMethod.MERGE);
        // The running read started before the merge, and would list the pull request as still open.
        Model merging = TestModels.running(withPr(), cmd, new LoadRepositoryPullRequests());

        Next done = TestModels.done(merging, cmd, cmd.run(new Env(StubGit.of(), mergingAtOnce())));
        Next earlierRead =
                TestModels.done(done.model(), new LoadRepositoryPullRequests(), new PullRequestsMsg.Loaded(List.of()));

        assertFalse(
                done.cmds().contains(new LoadRepositoryPullRequests()),
                done.cmds().toString());
        assertTrue(
                earlierRead.cmds().contains(new LoadRepositoryPullRequests()),
                earlierRead.cmds().toString());
    }

    @Test
    void aPullRequestGitHubMergedAtOnceShowsMergedUntilThePullRequestsAreReadAgain() {
        MergePullRequest cmd = new MergePullRequest(FEATURE, new PrNumber(42), Option.none(), MergeMethod.MERGE);
        Model merging = TestModels.running(withPr(), cmd);

        Next done = TestModels.done(merging, cmd, cmd.run(new Env(StubGit.of(), mergingAtOnce())));

        assertEquals(
                Option.of(FEATURE_PR),
                done.model().part(GitHubState.PART).pullRequests().flatMap(open -> open.get(FEATURE)));
        assertEquals(
                Option.of(new MergeStatus.Merged()),
                done.model().part(GitHubState.PART).merge(new PrNumber(42)));
        assertEquals(Option.none(), done.model().jobs().busy().get(new BranchRef.Local(FEATURE)));
        assertTrue(
                done.cmds().contains(new LoadRepositoryPullRequests()),
                done.cmds().toString());
        // The merge changed the base branch on the remote.
        assertTrue(done.cmds().contains(new FetchCmd.ListRemotes()), done.cmds().toString());
    }

    @Test
    void aMergeGitHubDoesInTheBackgroundLeavesTheBranchFreeAndIsPending() {
        Model merging = asked(withPr());

        assertEquals(Option.none(), merging.jobs().busy().get(new BranchRef.Local(FEATURE)));
        assertEquals(
                Option.of(new MergeStatus.Pending("merge-42")),
                merging.part(GitHubState.PART).merge(new PrNumber(42)));
        assertTrue(merging.part(GitHubState.PART).merging(new PrNumber(42)));
        assertEquals(
                "gh api --method PUT 'repos/{owner}/{repo}/pulls/42/merge-async' -f merge_method=merge -f sha=abc123",
                merging.outcome().commandLog().last().commands().last().line());
    }

    @Test
    void aPullRequestGitHubIsMergingIsNotMergedAgain() {
        Model read = Update.update(
                        Update.update(asked(withPr()), new PullRequestMsg.Requested(FEATURE))
                                .model(),
                        new PullRequestMsg.DetailsLoaded(
                                new PrNumber(42),
                                0,
                                StubGitHub.of()
                                        .pullRequests()
                                        .details(new PrNumber(42))
                                        .get()))
                .model();

        assertEquals(
                List.empty(),
                Update.update(read, new PullRequestMsg.MergeChosen(MergeMethod.SQUASH))
                        .cmds());
    }

    @Test
    void eachTickAsksGitHubOnceHowAPendingMergeIsDoing() {
        Next ticked = Update.update(asked(withPr()), new PullRequestMsg.MergeTick());

        assertEquals(List.of(new CheckMerge(new PrNumber(42), "merge-42")), ticked.cmds());
        // While it is asked, the next tick does not ask again.
        assertEquals(
                List.empty(),
                Update.update(ticked.model(), new PullRequestMsg.MergeTick()).cmds());

        Next stillPending = Update.update(
                ticked.model(),
                new PullRequestMsg.MergeChecked(new PrNumber(42), "merge-42", new MergeStatus.Pending("merge-42")));
        assertEquals(
                List.of(new CheckMerge(new PrNumber(42), "merge-42")),
                Update.update(stillPending.model(), new PullRequestMsg.MergeTick())
                        .cmds());
    }

    @Test
    void noTickAsksAnythingWithoutPendingMerges() {
        Model model = withPr();

        Next ticked = Update.update(model, new PullRequestMsg.MergeTick());

        assertEquals(List.empty(), ticked.cmds());
        assertEquals(model, ticked.model());
    }

    @Test
    void aMergeThatAskingAboutFailedIsAskedAboutAgain() {
        Model checking =
                Update.update(asked(withPr()), new PullRequestMsg.MergeTick()).model();

        Next failed = Update.update(
                checking,
                new PullRequestMsg.MergeCheckFailed(
                        new PrNumber(42), "merge-42", refused("gh: Bad Gateway (HTTP 502)")));

        assertEquals(Option.none(), failed.model().popup(ErrorPopup.class));
        assertEquals(
                List.of(new CheckMerge(new PrNumber(42), "merge-42")),
                Update.update(failed.model(), new PullRequestMsg.MergeTick()).cmds());
    }

    @Test
    void aMergeGitHubFinishedReadsThePullRequestsAndTheRemotesAgain() {
        Model checking =
                Update.update(asked(withPr()), new PullRequestMsg.MergeTick()).model();

        Next merged = Update.update(
                checking, new PullRequestMsg.MergeChecked(new PrNumber(42), "merge-42", new MergeStatus.Merged()));

        assertEquals(
                Option.of(new MergeStatus.Merged()),
                merged.model().part(GitHubState.PART).merge(new PrNumber(42)));
        assertTrue(
                merged.cmds().contains(new LoadRepositoryPullRequests()),
                merged.cmds().toString());
        assertTrue(
                merged.cmds().contains(new FetchCmd.ListRemotes()),
                merged.cmds().toString());
        assertEquals(
                List.empty(),
                Update.update(merged.model(), new PullRequestMsg.MergeTick()).cmds());
    }

    @Test
    void aMergeGitHubFailedIsShownAsAnErrorAndStaysShownWhileThePullRequestStandsTheSame() {
        Model checking =
                Update.update(asked(withPr()), new PullRequestMsg.MergeTick()).model();

        Model failed = Update.update(
                        checking,
                        new PullRequestMsg.MergeChecked(
                                new PrNumber(42),
                                "merge-42",
                                new MergeStatus.Failed("Required status check \"build\" is failing")))
                .model();

        assertEquals(
                Option.of("Merging the pull request of feature failed: Required status check \"build\" is failing"),
                errorText(failed));
        assertEquals(
                Option.of(new MergeStatus.Failed("Required status check \"build\" is failing")),
                Update.update(failed, new PullRequestsMsg.Loaded(List.of(FEATURE_PR)))
                        .model()
                        .part(GitHubState.PART)
                        .merge(new PrNumber(42)));
        assertFalse(failed.part(GitHubState.PART).merging(new PrNumber(42)));
        assertEquals(
                Option.of(new MergeStatus.Pending("merge-42")),
                asked(failed).part(GitHubState.PART).merge(new PrNumber(42)));
        assertEquals(
                Option.none(),
                Update.update(
                                failed,
                                new PullRequestsMsg.Loaded(List.of(new PullRequest(
                                        new PrNumber(42),
                                        new GhBranch("feature"),
                                        false,
                                        Checks.FAILING,
                                        Review.APPROVED))))
                        .model()
                        .part(GitHubState.PART)
                        .merge(new PrNumber(42)));
    }

    @Test
    void theDetailsReadAfterAMergeFailedDropTheFailureOnlyOnceThePullRequestChanged() {
        PullRequestDetails read =
                StubGitHub.of().pullRequests().details(new PrNumber(42)).get();
        Model failed = askedFor(showingDetails(), "feature", 42, new MergeStatus.Failed("conflicts"));
        Model reading = Update.update(
                        Update.update(failed, new PopupMsg.Dismissed(PullRequestPopup.class))
                                .model(),
                        new PullRequestMsg.Requested(FEATURE))
                .model();

        assertEquals(
                Option.of(new MergeStatus.Failed("conflicts")),
                Update.update(reading, new PullRequestMsg.DetailsLoaded(new PrNumber(42), lastRead(reading), read))
                        .model()
                        .part(GitHubState.PART)
                        .merge(new PrNumber(42)));
        assertEquals(
                Option.none(),
                Update.update(
                                reading,
                                new PullRequestMsg.DetailsLoaded(
                                        new PrNumber(42), lastRead(reading), withARunningCheck(read)))
                        .model()
                        .part(GitHubState.PART)
                        .merge(new PrNumber(42)));
    }

    @Test
    void aFailedMergeNoLongerShowsOnceThePullRequestChanged() {
        PullRequest pending =
                new PullRequest(new PrNumber(42), new GhBranch("feature"), false, Checks.PENDING, Review.APPROVED);
        Model failed = askedFor(
                Update.update(model(), new PullRequestsMsg.Loaded(List.of(pending)))
                        .model(),
                "feature",
                42,
                new MergeStatus.Failed("Required status check \"build\" is expected"));

        assertEquals(
                Option.of(new MergeStatus.Failed("Required status check \"build\" is expected")),
                Update.update(failed, new PullRequestsMsg.Loaded(List.of(pending)))
                        .model()
                        .part(GitHubState.PART)
                        .merge(new PrNumber(42)));
        assertEquals(
                Option.none(),
                Update.update(failed, new PullRequestsMsg.Loaded(List.of(FEATURE_PR)))
                        .model()
                        .part(GitHubState.PART)
                        .merge(new PrNumber(42)));
    }

    @Test
    void aFailedMergeOfAStackNoLongerShowsOnceAPullRequestBelowChanged() {
        Model failed = askedFor(stackOfTwo(), "feature", 42, new MergeStatus.Failed("conflicts"));
        List<PullRequest> bottomFixed = failed.part(GitHubState.PART)
                .pullRequests()
                .get()
                .values()
                .toList()
                .map(pr -> pr.number().value() == 41
                        ? new PullRequest(
                                new PrNumber(41), pr.headBranch(), false, Checks.PENDING, pr.review(), pr.stack())
                        : pr);

        Model read =
                Update.update(failed, new PullRequestsMsg.Loaded(bottomFixed)).model();

        assertEquals(Option.none(), read.part(GitHubState.PART).merge(new PrNumber(42)));
        assertEquals(Option.none(), read.part(GitHubState.PART).merge(new PrNumber(41)));
    }

    @Test
    void aPullRequestGitHubAddedToTheMergeQueueSaysSo() {
        Model checking =
                Update.update(asked(withPr()), new PullRequestMsg.MergeTick()).model();

        Model enqueued = Update.update(
                        checking,
                        new PullRequestMsg.MergeChecked(new PrNumber(42), "merge-42", new MergeStatus.Enqueued()))
                .model();

        assertEquals(
                Option.of(new MergeStatus.Enqueued()),
                enqueued.part(GitHubState.PART).merge(new PrNumber(42)));
        assertEquals(
                Option.of(new Notice.MergeEnqueued(FEATURE)), enqueued.outcome().notice());
    }

    @Test
    void anAnswerAboutAnEarlierMergeIsIgnored() {
        Model checking =
                Update.update(asked(withPr()), new PullRequestMsg.MergeTick()).model();

        Next answered = Update.update(
                checking, new PullRequestMsg.MergeChecked(new PrNumber(42), "earlier", new MergeStatus.Merged()));

        assertEquals(checking, answered.model());
    }

    @Test
    void aMergeThatIsPendingForTooLongIsLeftToGitHub() {
        Model merging = asked(withPr().withNow(Instant.parse("2026-10-23T10:00:00Z")));

        Next ticked =
                Update.update(merging.withNow(Instant.parse("2026-10-23T10:10:05Z")), new PullRequestMsg.MergeTick());

        assertEquals(List.empty(), ticked.cmds());
        assertEquals(Option.none(), ticked.model().part(GitHubState.PART).merge(new PrNumber(42)));
        assertEquals(
                Option.of(new Notice.MergeStillPending(FEATURE)),
                ticked.model().outcome().notice());
    }

    /**
     * {@code model} once GitHub said the merge of pull request {@code number}, from {@code branch}, goes as
     * {@code status}.
     */
    private static Model askedFor(Model model, String branch, int number, MergeStatus status) {
        MergePullRequest cmd =
                new MergePullRequest(new BranchName(branch), new PrNumber(number), Option.none(), MergeMethod.MERGE);
        return Update.update(model, new PullRequestMsg.MergeAsked(cmd, status, List.empty()))
                .model();
    }

    /**
     * Stack {@code number} of the open pull requests {@code numbers}, the bottom one first, as seen from the one at
     * {@code position}.
     */
    private static PullRequestStack stack(int number, int position, Integer... numbers) {
        List<PullRequestStack.Entry> entries = List.of(numbers)
                .zipWithIndex((pr, index) ->
                        new PullRequestStack.Entry(index + 1, new PrNumber(pr), PullRequestDetails.State.OPEN));
        return new PullRequestStack(number, position, entries.size(), entries);
    }

    /**
     * Pull request 41 at the bottom of a stack, and 42 of feature on top of it.
     */
    private static Model stackOfTwo() {
        PullRequest bottom = new PullRequest(
                new PrNumber(41),
                new GhBranch("base-work"),
                false,
                Checks.PASSING,
                Review.APPROVED,
                Option.of(stack(7, 1, 41, 42)));
        PullRequest top = new PullRequest(
                new PrNumber(42),
                new GhBranch("feature"),
                false,
                Checks.PASSING,
                Review.APPROVED,
                Option.of(stack(7, 2, 41, 42)));
        return Update.update(model(), new PullRequestsMsg.Loaded(List.of(bottom, top)))
                .model();
    }

    @Test
    void aPullRequestWhoseMergeFailedIsMergingWhileOneAboveItMergesTheStack() {
        Model failedBelow = askedFor(stackOfTwo(), "base-work", 41, new MergeStatus.Failed("conflicts"));

        Model merging = askedFor(failedBelow, "feature", 42, new MergeStatus.Pending("merge-42"));

        assertEquals(
                Option.of(new MergeStatus.Pending("merge-42")),
                merging.part(GitHubState.PART).merge(new PrNumber(41)));
        assertTrue(merging.part(GitHubState.PART).merging(new PrNumber(41)));
    }

    @Test
    void mergingAPullRequestOnItsOwnAfterTheStackFailedLeavesTheFailureAbove() {
        Model failedAbove = askedFor(stackOfTwo(), "feature", 42, new MergeStatus.Failed("conflicts"));

        Model merging = askedFor(failedAbove, "base-work", 41, new MergeStatus.Pending("merge-41"));

        assertEquals(
                Option.of(new MergeStatus.Pending("merge-41")),
                merging.part(GitHubState.PART).merge(new PrNumber(41)));
        assertEquals(
                Option.of(new MergeStatus.Failed("conflicts")),
                merging.part(GitHubState.PART).merge(new PrNumber(42)));
    }

    @Test
    void mergingAStackedPullRequestMergesTheOnesBelowItToo() {
        PullRequest bottom = new PullRequest(
                new PrNumber(41),
                new GhBranch("base-work"),
                false,
                Checks.PASSING,
                Review.APPROVED,
                Option.of(stack(7, 1, 41, 42, 43)));
        PullRequest middle = new PullRequest(
                new PrNumber(42),
                new GhBranch("feature"),
                false,
                Checks.PASSING,
                Review.APPROVED,
                Option.of(stack(7, 2, 41, 42, 43)));
        PullRequest top = new PullRequest(
                new PrNumber(43),
                new GhBranch("on-top"),
                false,
                Checks.PASSING,
                Review.APPROVED,
                Option.of(stack(7, 3, 41, 42, 43)));
        PullRequest other = new PullRequest(
                new PrNumber(40),
                new GhBranch("elsewhere"),
                false,
                Checks.PASSING,
                Review.APPROVED,
                Option.of(stack(8, 1, 40, 44)));
        Model stacked = Update.update(model(), new PullRequestsMsg.Loaded(List.of(bottom, middle, top, other)))
                .model();

        Model merging = asked(stacked);

        assertEquals(
                Option.of(new MergeStatus.Pending("merge-42")),
                merging.part(GitHubState.PART).merge(new PrNumber(41)));
        assertEquals(Option.none(), merging.part(GitHubState.PART).merge(new PrNumber(43)));
        assertEquals(Option.none(), merging.part(GitHubState.PART).merge(new PrNumber(40)));
    }

    @Test
    void aMergeThatGitHubRefusesIsShownAsAnErrorAndKeepsThePullRequest() {
        Model shown = Update.update(withPr(), new PullRequestMsg.Requested(new BranchName("feature")))
                .model();
        Model read = Update.update(
                        shown,
                        new PullRequestMsg.DetailsLoaded(
                                new PrNumber(42),
                                lastRead(shown),
                                StubGitHub.of()
                                        .pullRequests()
                                        .details(new PrNumber(42))
                                        .get()))
                .model();
        Next merging = Update.update(read, new PullRequestMsg.MergeChosen(MergeMethod.SQUASH));
        MergePullRequest cmd = (MergePullRequest) merging.cmds().head();
        // What gh says when e.g. a required check fails or a review is missing.
        GitHub refusing = StubGitHub.of().withPullRequests(new StubPullRequests() {
            @Override
            public Either<GhFailure, PullRequests.Merging> merge(
                    PrNumber number, Option<GhCommit> head, MergeMethod method) {
                return Either.left(new GhFailure(
                        new GhError.CommandFailed(
                                Option.of(
                                        "Pull request #42 is not mergeable: the base branch policy prohibits the merge."),
                                1),
                        List.of(ExecutedCommand.of("gh", "api", "--method", "PUT", "merge-async"))));
            }
        });

        Next failed = Update.update(merging.model(), cmd.run(new Env(StubGit.of(), refusing)));

        // The pull request dialog stays closed; the error comes up in its own popup.
        assertEquals(Option.none(), failed.model().popup(PullRequestPopup.class));
        assertTrue(
                failed.model().popup(ErrorPopup.class).isDefined(),
                failed.model().popup().toString());
        assertEquals(
                Option.of("Merging the pull request of feature failed: Pull request #42 is not mergeable: "
                        + "the base branch policy prohibits the merge."),
                errorText(failed.model()));
        assertEquals(Option.none(), failed.model().jobs().busy().get(new BranchRef.Local(new BranchName("feature"))));
        assertEquals(
                StubGitHub.of().pullRequests().details(new PrNumber(42)).get().listed(),
                failed.model()
                        .part(GitHubState.PART)
                        .pullRequests()
                        .flatMap(open -> open.get(new BranchName("feature"))));
    }
}
