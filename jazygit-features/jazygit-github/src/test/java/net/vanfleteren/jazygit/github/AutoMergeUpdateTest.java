package net.vanfleteren.jazygit.github;

import static net.vanfleteren.jazygit.github.PullRequestFixtures.*;
import static net.vanfleteren.jazygit.state.TestModels.lastRead;
import static net.vanfleteren.jazygit.ui.text.ErrorTexts.errorText;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.StubGitHub;
import net.vanfleteren.jazygit.gh.model.GhCommit;
import net.vanfleteren.jazygit.gh.model.MergeMethod;
import net.vanfleteren.jazygit.gh.model.MergeStatus;
import net.vanfleteren.jazygit.gh.model.PrNumber;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.AutoMerge;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.MergeState;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Merging;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Reviewing;
import net.vanfleteren.jazygit.gh.process.GhError;
import net.vanfleteren.jazygit.gh.process.GhFailure;
import net.vanfleteren.jazygit.gh.pullrequests.StubPullRequests;
import net.vanfleteren.jazygit.git.StubGit;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.github.show.PullRequestCmd;
import net.vanfleteren.jazygit.github.show.PullRequestCmd.DisableAutoMerge;
import net.vanfleteren.jazygit.github.show.PullRequestCmd.EnableAutoMerge;
import net.vanfleteren.jazygit.github.show.PullRequestCmd.LoadPullRequestDetails;
import net.vanfleteren.jazygit.github.show.PullRequestCmd.MergePullRequest;
import net.vanfleteren.jazygit.github.show.PullRequestMsg;
import net.vanfleteren.jazygit.github.show.PullRequestPopup;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;
import net.vanfleteren.jazygit.state.github.prs.LoadRepositoryPullRequests;
import org.junit.jupiter.api.Test;

/**
 * The merge keys of the pull request dialog turning GitHub's auto-merge on and off (ADR 112).
 */
class AutoMergeUpdateTest {

    private static final GhCommit HEAD = new GhCommit("abc123");

    private static final Merging BLOCKED = new Merging(MergeState.BLOCKED, true, Option.none(), true, false);

    private static final AutoMerge SQUASHING = new AutoMerge(MergeMethod.SQUASH, "cvanfleteren", Option.none());

    /**
     * The pull request of the feature branch, at {@link #HEAD}, with node id {@code PR_42}, merging as
     * {@code merging} says.
     */
    private static PullRequestDetails details(Merging merging) {
        PullRequestDetails stub =
                StubGitHub.of().pullRequests().details(new PrNumber(42)).get();
        PullRequestDetails.Conversation talk = stub.conversation();
        return stub.withBranches(new PullRequestDetails.Branches(
                        stub.branches().head(), stub.branches().base(), Option.of(HEAD), Option.none()))
                .withConversation(new PullRequestDetails.Conversation(
                        talk.review(),
                        talk.reviewers(),
                        talk.comments(),
                        talk.reviewThreads(),
                        talk.labels(),
                        new Reviewing("PR_42", false, false)))
                .withMerging(merging);
    }

    /**
     * The dialog of the pull request of the feature branch, showing {@code details}.
     */
    private static Model showing(PullRequestDetails details) {
        Model shown =
                Update.update(withPr(), new PullRequestMsg.Requested(FEATURE)).model();
        return Update.update(shown, new PullRequestMsg.DetailsLoaded(new PrNumber(42), lastRead(shown), details))
                .model();
    }

    @Test
    void aBlockedPullRequestIsMergedWhenReadyAndTheDialogStaysOpen() {
        Next enabling =
                TestModels.stepped(showing(details(BLOCKED)), new PullRequestMsg.MergeChosen(MergeMethod.SQUASH));

        assertEquals(
                List.of(new EnableAutoMerge(FEATURE, new PrNumber(42), "PR_42", MergeMethod.SQUASH, Option.of(HEAD))),
                enabling.cmds());
        assertTrue(enabling.model().popup(PullRequestPopup.class).isDefined());
        assertEquals(
                Option.of(PullRequestCmd.ENABLE_AUTO_MERGE),
                enabling.model().jobs().busy().get(new BranchRef.Local(FEATURE)));
        // While it runs, it is not turned on again.
        assertEquals(
                List.empty(),
                Update.update(enabling.model(), new PullRequestMsg.MergeChosen(MergeMethod.SQUASH))
                        .cmds());
    }

    @Test
    void aPullRequestThatCanBeMergedNowIsMergedNow() {
        Next merging = TestModels.stepped(
                showing(details(new Merging(MergeState.CLEAN, true, Option.none(), false, false))),
                new PullRequestMsg.MergeChosen(MergeMethod.SQUASH));

        assertEquals(
                List.of(new MergePullRequest(FEATURE, new PrNumber(42), Option.of(HEAD), MergeMethod.SQUASH)),
                merging.cmds());
        assertEquals(Option.none(), merging.model().popup());
    }

    @Test
    void theKeyOfTheMethodOfAutoMergeCancelsIt() {
        Model shown = showing(details(new Merging(MergeState.BLOCKED, true, Option.of(SQUASHING), false, true)));

        assertEquals(
                List.of(new DisableAutoMerge(FEATURE, new PrNumber(42), "PR_42")),
                TestModels.stepped(shown, new PullRequestMsg.MergeChosen(MergeMethod.SQUASH))
                        .cmds());
        assertEquals(
                List.empty(),
                TestModels.stepped(shown, new PullRequestMsg.MergeChosen(MergeMethod.REBASE))
                        .cmds());
    }

    @Test
    void aBlockedPullRequestGitHubDoesNotLetBeMergedWhenReadyIsNotMerged() {
        Model shown = showing(details(new Merging(MergeState.BLOCKED, false, Option.none(), false, false)));

        assertEquals(
                List.empty(),
                Update.update(shown, new PullRequestMsg.MergeChosen(MergeMethod.SQUASH))
                        .cmds());
    }

    @Test
    void autoMergeTurnedOnLogsItForgetsAnEarlierFailedMergeAndReadsThePullRequestAgain() {
        Model shown = showing(details(BLOCKED));
        MergePullRequest merge = new MergePullRequest(FEATURE, new PrNumber(42), Option.of(HEAD), MergeMethod.SQUASH);
        Model failed = TestModels.done(
                        TestModels.running(shown, merge),
                        merge,
                        new PullRequestMsg.MergeAsked(merge, new MergeStatus.Failed("blocked"), List.empty()))
                .model();
        assertEquals(
                Option.of(new MergeStatus.Failed("blocked")), failed.gitHub().merge(new PrNumber(42)));
        EnableAutoMerge cmd =
                new EnableAutoMerge(FEATURE, new PrNumber(42), "PR_42", MergeMethod.SQUASH, Option.of(HEAD));

        Next done = TestModels.done(TestModels.running(failed, cmd), cmd, cmd.run(env()));

        assertEquals(Option.none(), done.model().gitHub().merge(new PrNumber(42)));
        assertEquals(Option.none(), done.model().jobs().busy().get(new BranchRef.Local(FEATURE)));
        assertTrue(
                done.cmds().contains(new LoadRepositoryPullRequests()),
                done.cmds().toString());
        assertTrue(
                done.cmds().exists(LoadPullRequestDetails.class::isInstance),
                done.cmds().toString());
        assertTrue(
                done.model()
                        .outcome()
                        .commandLog()
                        .last()
                        .commands()
                        .last()
                        .line()
                        .contains("enablePullRequestAutoMerge"),
                done.model().outcome().commandLog().last().commands().toString());
    }

    @Test
    void autoMergeGitHubRefusedShowsTheErrorAndReadsThePullRequestAgain() {
        Model shown = showing(details(BLOCKED));
        EnableAutoMerge cmd =
                new EnableAutoMerge(FEATURE, new PrNumber(42), "PR_42", MergeMethod.SQUASH, Option.of(HEAD));
        Env refusing = new Env(StubGit.of(), StubGitHub.of().withPullRequests(new StubPullRequests() {
            @Override
            public Either<GhFailure, List<ExecutedCommand>> enableAutoMerge(
                    String pullRequest, MergeMethod method, Option<GhCommit> head) {
                return Either.left(new GhFailure(
                        new GhError.CommandFailed(Option.of("Pull request is in clean status"), 1), List.empty()));
            }
        }));

        Next done = TestModels.done(TestModels.running(shown, cmd), cmd, cmd.run(refusing));

        assertEquals(
                Option.of("Enabling auto-merge on the pull request of feature failed: Pull request is in clean status"),
                errorText(done.model()));
        assertTrue(
                done.cmds().exists(LoadPullRequestDetails.class::isInstance),
                done.cmds().toString());
    }

    @Test
    void cancellingAutoMergeReadsThePullRequestAgain() {
        Model shown = showing(details(new Merging(MergeState.BLOCKED, true, Option.of(SQUASHING), false, true)));
        DisableAutoMerge cmd = new DisableAutoMerge(FEATURE, new PrNumber(42), "PR_42");

        Next done = TestModels.done(TestModels.running(shown, cmd), cmd, cmd.run(env()));

        assertTrue(
                done.cmds().exists(LoadPullRequestDetails.class::isInstance),
                done.cmds().toString());
        assertTrue(
                done.model()
                        .outcome()
                        .commandLog()
                        .last()
                        .commands()
                        .last()
                        .line()
                        .contains("disablePullRequestAutoMerge"),
                done.model().outcome().commandLog().last().commands().toString());
    }
}
