package net.vanfleteren.jazygit.features.forgejo.show;

import static net.vanfleteren.jazygit.features.forgejo.pulls.PullRequestFixtures.HEAD;
import static net.vanfleteren.jazygit.features.forgejo.pulls.PullRequestFixtures.ORIGIN;
import static net.vanfleteren.jazygit.features.forgejo.pulls.PullRequestFixtures.SETTINGS;
import static net.vanfleteren.jazygit.features.forgejo.pulls.PullRequestFixtures.TIME;
import static net.vanfleteren.jazygit.features.forgejo.pulls.PullRequestFixtures.details;
import static net.vanfleteren.jazygit.features.forgejo.pulls.PullRequestFixtures.listed;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.forgejo.CommitStatus;
import net.vanfleteren.jazygit.core.forgejo.ForgejoBranchRules;
import net.vanfleteren.jazygit.core.forgejo.ForgejoError;
import net.vanfleteren.jazygit.core.forgejo.ForgejoMergeStyle;
import net.vanfleteren.jazygit.core.forgejo.ForgejoMerged;
import net.vanfleteren.jazygit.core.forgejo.ForgejoScheduledMerge;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.PrNumber;
import net.vanfleteren.jazygit.core.repo.RepoSlug;
import net.vanfleteren.jazygit.features.forgejo.pulls.ForgejoPullRequests;
import net.vanfleteren.jazygit.features.forgejo.pulls.PullRequestMsg;
import net.vanfleteren.jazygit.kernel.state.HeardEvents;
import net.vanfleteren.jazygit.kernel.state.HeardEvents.Heard;
import net.vanfleteren.jazygit.kernel.state.KernelEvent;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Update;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.operation.Notice;
import org.junit.jupiter.api.Test;

/**
 * Merging a pull request on Forgejo when its checks succeed, and cancelling that (ADR 181).
 */
class ScheduleUpdateTest {

    private static final BranchName BRANCH = new BranchName("feature");
    private static final FailedMerge.Standing STANDING = new FailedMerge.Standing(HEAD);
    // The base requires its checks, which one failed on the head (PullRequestFixtures.FakeForgejo.of), and an approval.
    private static final ForgejoBranchRules REQUIRED = new ForgejoBranchRules(true, true, List.empty(), 1);
    private static final CommitStatus FAILING = new CommitStatus(
            CommitStatus.State.FAILED,
            List.of(new CommitStatus.Status("check / lint (push)", CommitStatus.State.FAILED, "")));

    private static final MergePullRequestCmd WHEN_READY = new MergePullRequestCmd(
            ORIGIN, BRANCH, new PrNumber(12), ForgejoMergeStyle.SQUASH, STANDING, true, Option.of(REQUIRED), 1);

    private static final CancelScheduledMergeCmd CANCEL =
            new CancelScheduledMergeCmd(ORIGIN, BRANCH, new PrNumber(12), TIME);

    private static ShownPullRequest shown(CommitStatus checks, Option<ForgejoScheduledMerge> scheduled) {
        return new ShownPullRequest(
                details("Merge it"), Option.none(), checks, Option.of(SETTINGS), Option.of(REQUIRED), 1, scheduled);
    }

    private static Model showing(ShownPullRequest shown) {
        Model open =
                Update.update(listed(), new PullRequestMsg.Requested(BRANCH)).model();
        return Update.update(open, new PullRequestMsg.Read(1, Either.right(shown)))
                .model();
    }

    private static ForgejoPullRequestPopup popup(Model model) {
        return model.popup(ForgejoPullRequestPopup.class).get();
    }

    private static ForgejoPullRequests prs(Model model) {
        return model.part(ForgejoPullRequests.PART);
    }

    @Test
    void aPullRequestItsBaseDoesNotLetBeMergedYetIsScheduledKeepingTheDialog() {
        Next next = Update.update(
                showing(shown(FAILING, Option.none())), new PullRequestMsg.MergeChosen(ForgejoMergeStyle.SQUASH));

        assertEquals(List.of(WHEN_READY), next.cmds());
        assertTrue(next.model().popup(ForgejoPullRequestPopup.class).isDefined());
    }

    @Test
    void oneThatPassedIsMergedNowAsBefore() {
        CommitStatus passing = new CommitStatus(
                CommitStatus.State.PASSED,
                List.of(new CommitStatus.Status("check / lint (push)", CommitStatus.State.PASSED, "")));

        Next next = Update.update(
                showing(shown(passing, Option.none())), new PullRequestMsg.MergeChosen(ForgejoMergeStyle.SQUASH));

        assertEquals(
                List.of(new MergePullRequestCmd(
                        ORIGIN, BRANCH, new PrNumber(12), ForgejoMergeStyle.SQUASH, STANDING, true, Option.none(), 1)),
                next.cmds());
        assertEquals(Option.none(), next.model().popup(ForgejoPullRequestPopup.class));
    }

    @Test
    void whileAMergeIsScheduledEveryKeyCancelsIt() {
        Model scheduled = showing(shown(FAILING, Option.of(new ForgejoScheduledMerge("anna", TIME))));

        List.of(ForgejoMergeStyle.values())
                .forEach(style -> assertEquals(
                        List.of(CANCEL),
                        Update.update(scheduled, new PullRequestMsg.MergeChosen(style))
                                .cmds()));
    }

    @Test
    void onceScheduledItsStyleIsKeptAFailureBeforeForgottenAndTheDialogReadsItAgain() {
        Model shown = showing(shown(FAILING, Option.none()))
                .mapPart(
                        ForgejoPullRequests.PART,
                        prs -> prs.mergeFailed(new FailedMerge(
                                new PrNumber(12),
                                ForgejoMergeStyle.MERGE,
                                new MergeFailure(new ForgejoError.Unreachable("x"), "h", new RepoSlug("r")),
                                STANDING)));

        Next next = Update.update(
                HeardEvents.hearing(shown), new PullRequestMsg.Merged(WHEN_READY, new ForgejoMerged.Scheduled()));

        assertEquals(Option.of(ForgejoMergeStyle.SQUASH), prs(next.model()).style(new PrNumber(12)));
        assertEquals(HashMap.empty(), prs(next.model()).failedMerges());
        assertTrue(
                next.cmds().contains(new LoadPullRequest(ORIGIN, new PrNumber(12), 2)),
                next.cmds().toString());
        assertTrue(popup(next.model()).asked());
        assertTrue(
                !next.cmds().contains(new Heard(new KernelEvent.RemoteChanged())),
                next.cmds().toString());
    }

    @Test
    void oneMergedNowAsItsChecksPassedMeanwhileIsDoneAsAMergeInTheDialogKeptOpen() {
        Model shown = showing(shown(FAILING, Option.none()))
                .mapPart(
                        ForgejoPullRequests.PART,
                        prs -> prs.mergeScheduled(new PrNumber(12), ForgejoMergeStyle.SQUASH));

        Next next = Update.update(
                HeardEvents.hearing(shown), new PullRequestMsg.Merged(WHEN_READY, new ForgejoMerged.Merged()));

        assertTrue(
                next.cmds().contains(new Heard(new KernelEvent.RemoteChanged())),
                next.cmds().toString());
        assertEquals(Option.none(), prs(next.model()).style(new PrNumber(12)));
        assertTrue(
                next.cmds().contains(new LoadPullRequest(ORIGIN, new PrNumber(12), 2)),
                next.cmds().toString());
        assertTrue(popup(next.model()).asked());
    }

    @Test
    void aMergeScheduledAlreadyIsToldAndReadAgainAsNoFailure() {
        Next next =
                Update.update(showing(shown(FAILING, Option.none())), new PullRequestMsg.AlreadyScheduled(WHEN_READY));

        assertEquals(
                Option.<Notice>of(new ScheduleNotice.AlreadyScheduled(BRANCH)),
                next.model().outcome().notice());
        assertEquals(Option.none(), next.model().outcome().error());
        assertTrue(
                next.cmds().contains(new LoadPullRequest(ORIGIN, new PrNumber(12), 2)),
                next.cmds().toString());
    }

    @Test
    void onceCancelledItsStyleIsForgottenAndOneNoLongerScheduledIsTold() {
        Model scheduled = showing(shown(FAILING, Option.of(new ForgejoScheduledMerge("anna", TIME))))
                .mapPart(
                        ForgejoPullRequests.PART,
                        prs -> prs.mergeScheduled(new PrNumber(12), ForgejoMergeStyle.SQUASH));

        Next cancelled = Update.update(scheduled, new PullRequestMsg.MergeCancelled(CANCEL));
        assertEquals(Option.none(), prs(cancelled.model()).style(new PrNumber(12)));
        assertEquals(Option.none(), cancelled.model().outcome().notice());
        assertTrue(
                cancelled.cmds().contains(new LoadPullRequest(ORIGIN, new PrNumber(12), 2)),
                cancelled.cmds().toString());

        Next gone = Update.update(scheduled, new PullRequestMsg.NoLongerScheduled(CANCEL));
        assertEquals(Option.none(), prs(gone.model()).style(new PrNumber(12)));
        assertEquals(
                Option.<Notice>of(new ScheduleNotice.NoLongerScheduled(BRANCH)),
                gone.model().outcome().notice());
        assertEquals(Option.none(), gone.model().outcome().error());
    }

    private static Model read(Model model, long read, Option<ForgejoScheduledMerge> scheduled) {
        return Update.update(model, new PullRequestMsg.Read(read, Either.right(shown(FAILING, scheduled))))
                .model();
    }

    private static Model refreshed(Model model) {
        return Update.update(model, new PullRequestMsg.RefreshChosen()).model();
    }

    private static MergeKey key(Model model) {
        return MergeKey.of(popup(model).shown().get(), ForgejoMergeStyle.SQUASH);
    }

    @Test
    void aMergeForgejoDroppedIsNoLongerShownScheduledOnceCancellingItSaidSo() {
        ForgejoScheduledMerge dropped = new ForgejoScheduledMerge("anna", TIME);
        Model gone = Update.update(
                        showing(shown(FAILING, Option.of(dropped))), new PullRequestMsg.NoLongerScheduled(CANCEL))
                .model();

        // The timeline still ends in the merge Forgejo dropped.
        Model readAgain = read(gone, 2, Option.of(dropped));
        assertEquals(Option.none(), popup(readAgain).shown().get().scheduled());
        assertEquals(new MergeKey.WhenChecksSucceed(), key(readAgain));

        // One scheduled since is shown.
        ForgejoScheduledMerge later = new ForgejoScheduledMerge("bob", TIME.plusSeconds(60));
        assertEquals(new MergeKey.Cancel(later), key(read(refreshed(readAgain), 3, Option.of(later))));
    }

    @Test
    void aStyleKeptIsOfTheMergeJazygitScheduledNotOfOneScheduledSinceInTheBrowser() {
        Model scheduled = Update.update(
                        showing(shown(FAILING, Option.none())),
                        new PullRequestMsg.Merged(WHEN_READY, new ForgejoMerged.Scheduled()))
                .model();
        ForgejoScheduledMerge ours = new ForgejoScheduledMerge("anna", TIME);

        Model shownOurs = read(scheduled, 2, Option.of(ours));
        assertEquals(Option.of(ForgejoMergeStyle.SQUASH), prs(shownOurs).style(new PrNumber(12)));
        // A read whose timeline shows none, as it could not be read, keeps it.
        Model unread = read(refreshed(shownOurs), 3, Option.none());
        assertEquals(Option.of(ForgejoMergeStyle.SQUASH), prs(unread).style(new PrNumber(12)));
        assertEquals(
                Option.of(ForgejoMergeStyle.SQUASH),
                prs(read(refreshed(unread), 4, Option.of(ours))).style(new PrNumber(12)));

        // Cancelled and scheduled again in the browser.
        ForgejoScheduledMerge theirs = new ForgejoScheduledMerge("anna", TIME.plusSeconds(60));
        assertEquals(
                Option.none(),
                prs(read(refreshed(unread), 4, Option.of(theirs))).style(new PrNumber(12)));
    }

    @Test
    void aStyleKeptOfAPullRequestNoLongerOpenIsDropped() {
        Model scheduled = listed().mapPart(
                        ForgejoPullRequests.PART,
                        prs -> prs.mergeScheduled(new PrNumber(12), ForgejoMergeStyle.SQUASH));

        Model gone = Update.update(
                        scheduled, new PullRequestMsg.Listed(ORIGIN, Option.of(HashMap.empty()), Option.none()))
                .model();

        assertEquals(Option.none(), prs(gone).style(new PrNumber(12)));
    }
}
