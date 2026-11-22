package net.vanfleteren.jazygit.features.forgejo.pulls;

import static net.vanfleteren.jazygit.features.forgejo.pulls.PullRequestFixtures.FEATURE;
import static net.vanfleteren.jazygit.features.forgejo.pulls.PullRequestFixtures.LISTED;
import static net.vanfleteren.jazygit.features.forgejo.pulls.PullRequestFixtures.ORIGIN;
import static net.vanfleteren.jazygit.features.forgejo.pulls.PullRequestFixtures.details;
import static net.vanfleteren.jazygit.features.forgejo.pulls.PullRequestFixtures.listed;
import static net.vanfleteren.jazygit.features.forgejo.pulls.PullRequestFixtures.model;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.forgejo.CommitStatus;
import net.vanfleteren.jazygit.core.forgejo.ForgejoError;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.features.forgejo.ForgejoFeature;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.KernelEvent;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Update;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.operation.OperationCmd;
import org.junit.jupiter.api.Test;

/**
 * The pull request of a branch on Forgejo, shown from the list of the open ones and read again as the user asks
 * (ADR 176).
 */
class PullRequestUpdateTest {

    private static final Failure FAILED = new ForgejoFailure.Asked(new ForgejoError.Unreachable("timed out"));

    private static Next requested(Model model, String branch) {
        return Update.update(model, new PullRequestMsg.Requested(new BranchName(branch)));
    }

    private static ShownPullRequest shown(String title) {
        return new ShownPullRequest(details(title), Option.none(), CommitStatus.NONE);
    }

    private static ForgejoPullRequestPopup popup(Model model) {
        return model.popup(ForgejoPullRequestPopup.class).get();
    }

    @Test
    void thePullRequestThatMergesTheBranchFollowedIsShownWhileItIsRead() {
        Next next = requested(listed(), "feature");

        assertEquals(ForgejoPullRequestPopup.loading(FEATURE, LISTED, 1), popup(next.model()));
        assertEquals(List.of(new LoadPullRequest(ORIGIN, 12, 1)), next.cmds());
    }

    @Test
    void whatWasReadShowsAndAReadTheDialogNoLongerWaitsForIsLeftOut() {
        Model open = requested(listed(), "feature").model();

        Model late = Update.update(open, new PullRequestMsg.Read(7, Either.right(shown("late"))))
                .model();
        assertEquals(Option.none(), popup(late).details());

        Model read = Update.update(open, new PullRequestMsg.Read(1, Either.right(shown("read"))))
                .model();
        assertEquals(Option.of(shown("read")), popup(read).shown());
        assertFalse(popup(read).reading());
    }

    @Test
    void readingAgainKeepsTheDetailsShownAndAFailureMarksThemStale() {
        Model open = requested(listed(), "feature").model();
        Model read = Update.update(open, new PullRequestMsg.Read(1, Either.right(shown("read"))))
                .model();

        Next again = Update.update(read, new PullRequestMsg.RefreshChosen());
        assertEquals(List.of(new LoadPullRequest(ORIGIN, 12, 2)), again.cmds());
        assertTrue(popup(again.model()).asked());
        assertEquals(Option.of(shown("read")), popup(again.model()).shown());
        // While it is read, asking again reads nothing more.
        assertEquals(
                List.empty(),
                Update.update(again.model(), new PullRequestMsg.RefreshChosen()).cmds());

        Model failed = Update.update(again.model(), new PullRequestMsg.Read(2, Either.left(FAILED)))
                .model();
        assertEquals(Option.of(shown("read")), popup(failed).shown());
        assertEquals(Option.of(FAILED), popup(failed).refreshFailed());
        assertFalse(popup(failed).reading());

        Model readAgain = Update.update(
                        Update.update(failed, new PullRequestMsg.RefreshChosen())
                                .model(),
                        new PullRequestMsg.Read(3, Either.right(shown("again"))))
                .model();
        assertEquals(Option.of(shown("again")), popup(readAgain).shown());
        assertEquals(Option.none(), popup(readAgain).refreshFailed());
    }

    @Test
    void aFirstReadThatFailsShowsWhy() {
        Model open = requested(listed(), "feature").model();

        Model failed = Update.update(open, new PullRequestMsg.Read(1, Either.left(FAILED)))
                .model();
        assertEquals(Option.of(Either.left(FAILED)), popup(failed).details());
    }

    @Test
    void openingItInTheBrowserOpensItsLink() {
        Model open = requested(listed(), "feature").model();

        List<OpenPullRequestCmd> started = Update.update(open, new PullRequestMsg.OpenChosen())
                .cmds()
                .filter(OperationCmd.class::isInstance)
                .map(OpenPullRequestCmd.class::cast);
        assertEquals(List.of(new OpenPullRequestCmd(LISTED.url())), started);
    }

    @Test
    void aBranchNoPullRequestMergesIsToldSo() {
        Model listedNone = PullRequestUpdate.update(
                        model(), new PullRequestMsg.Listed(Option.of(HashMap.empty()), Option.none()))
                .model();

        Next next = requested(listedNone, "feature");
        assertEquals(
                Option.of(new PullRequestNotice.None(new BranchName("feature"))),
                next.model().outcome().notice());
        assertEquals(Option.none(), next.model().popup(ForgejoPullRequestPopup.class));
    }

    @Test
    void aBranchThatFollowsNoRemoteBranchIsToldSo() {
        assertEquals(
                Option.of(new PullRequestNotice.NotOnForgejo(new BranchName("alone"))),
                requested(listed(), "alone").model().outcome().notice());
    }

    @Test
    void beforeThePullRequestsAreReadOrWhenTheyCouldNotBeItSaysWhy() {
        assertEquals(
                Option.of(new PullRequestsError.NotRead()),
                requested(model(), "feature").model().outcome().error());

        Model failed = PullRequestUpdate.update(model(), new PullRequestMsg.Listed(Option.none(), Option.of(FAILED)))
                .model();
        assertEquals(
                Option.of(new PullRequestsError.Unavailable(FAILED)),
                requested(failed, "feature").model().outcome().error());
    }

    @Test
    void theListReadBeforeStaysKnownWhenReadingItAgainFails() {
        Model failedAgain = PullRequestUpdate.update(
                        listed(), new PullRequestMsg.Listed(Option.none(), Option.of(FAILED)))
                .model();

        assertEquals(LISTED, popup(requested(failedAgain, "feature").model()).pullRequest());
    }

    @Test
    void theyAreReadAfreshAsForgejoTurnsOnAndAgainAfterAFetchOrAPush() {
        Next on = PullRequestReactions.to(listed(), new KernelEvent.Switched(ForgejoFeature.ID, true));
        assertEquals(ForgejoPullRequests.NONE, on.model().part(ForgejoPullRequests.PART));
        assertEquals(List.of(new ReadPullRequests()), on.cmds());

        assertEquals(
                List.of(new ReadPullRequests()),
                PullRequestReactions.to(model(), new KernelEvent.Fetched()).cmds());
        assertEquals(
                List.of(new ReadPullRequests()),
                PullRequestReactions.to(model(), new KernelEvent.OperationDone(true))
                        .cmds());
        assertEquals(
                List.empty(),
                PullRequestReactions.to(model(), new KernelEvent.OperationDone(false))
                        .cmds());
    }
}
