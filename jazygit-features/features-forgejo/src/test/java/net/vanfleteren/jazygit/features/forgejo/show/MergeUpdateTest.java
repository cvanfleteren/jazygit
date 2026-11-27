package net.vanfleteren.jazygit.features.forgejo.show;

import static net.vanfleteren.jazygit.features.forgejo.pulls.PullRequestFixtures.FEATURE;
import static net.vanfleteren.jazygit.features.forgejo.pulls.PullRequestFixtures.HEAD;
import static net.vanfleteren.jazygit.features.forgejo.pulls.PullRequestFixtures.LISTED;
import static net.vanfleteren.jazygit.features.forgejo.pulls.PullRequestFixtures.ORIGIN;
import static net.vanfleteren.jazygit.features.forgejo.pulls.PullRequestFixtures.SETTINGS;
import static net.vanfleteren.jazygit.features.forgejo.pulls.PullRequestFixtures.details;
import static net.vanfleteren.jazygit.features.forgejo.pulls.PullRequestFixtures.listed;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.forgejo.CommitStatus;
import net.vanfleteren.jazygit.core.forgejo.ForgejoError;
import net.vanfleteren.jazygit.core.forgejo.ForgejoMergeStyle;
import net.vanfleteren.jazygit.core.forgejo.ForgejoMerged;
import net.vanfleteren.jazygit.core.forgejo.ForgejoPullRequestDetails;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.PrNumber;
import net.vanfleteren.jazygit.core.repo.RemoteName;
import net.vanfleteren.jazygit.core.repo.RepoSlug;
import net.vanfleteren.jazygit.features.forgejo.pulls.ForgejoPullRequests;
import net.vanfleteren.jazygit.features.forgejo.pulls.PullRequestFixtures;
import net.vanfleteren.jazygit.features.forgejo.pulls.PullRequestMsg;
import net.vanfleteren.jazygit.features.forgejo.pulls.ReadPullRequests;
import net.vanfleteren.jazygit.kernel.state.AppError;
import net.vanfleteren.jazygit.kernel.state.HeardEvents;
import net.vanfleteren.jazygit.kernel.state.HeardEvents.Heard;
import net.vanfleteren.jazygit.kernel.state.KernelEvent;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Update;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.operation.Notice;
import org.junit.jupiter.api.Test;

/**
 * Merging a pull request on Forgejo from its dialog (ADR 180).
 */
class MergeUpdateTest {

    private static final FailedMerge.Standing STANDING = new FailedMerge.Standing(HEAD);

    private static final MergePullRequestCmd SQUASH = new MergePullRequestCmd(
            ORIGIN, new BranchName("feature"), new PrNumber(12), ForgejoMergeStyle.SQUASH, STANDING, true);

    /**
     * {@link PullRequestFixtures#listed()} with the dialog of the pull request of {@code feature} showing
     * {@code shown}.
     */
    private static Model showing(ShownPullRequest shown) {
        Model open = Update.update(listed(), new PullRequestMsg.Requested(new BranchName("feature")))
                .model();
        return Update.update(open, new PullRequestMsg.Read(1, Either.right(shown)))
                .model();
    }

    private static ShownPullRequest shown(ForgejoPullRequestDetails pr) {
        return new ShownPullRequest(pr, Option.none(), CommitStatus.NONE, Option.of(SETTINGS));
    }

    private static ForgejoPullRequests prs(Model model) {
        return model.part(ForgejoPullRequests.PART);
    }

    @Test
    void aStyleTheRepositoryAllowsMergesTheHeadAsReadClosingTheDialog() {
        Next next = Update.update(
                showing(shown(details("Merge it"))), new PullRequestMsg.MergeChosen(ForgejoMergeStyle.SQUASH));

        assertEquals(List.of(SQUASH), next.cmds());
        assertEquals(Option.none(), next.model().popup(ForgejoPullRequestPopup.class));
    }

    @Test
    void withoutTheSettingsEveryStyleIsOfferedAndTheBranchIsKept() {
        ShownPullRequest unknown = new ShownPullRequest(details("Merge it"), Option.none(), CommitStatus.NONE);

        Next next = Update.update(showing(unknown), new PullRequestMsg.MergeChosen(ForgejoMergeStyle.MERGE));

        assertEquals(
                List.of(new MergePullRequestCmd(
                        ORIGIN, new BranchName("feature"), new PrNumber(12), ForgejoMergeStyle.MERGE, STANDING, false)),
                next.cmds());
    }

    @Test
    void aStyleTheRepositoryDoesNotAllowADraftOrAConflictMergesNothing() {
        ForgejoPullRequestDetails pr = details("Merge it");
        ForgejoPullRequestDetails draft = new ForgejoPullRequestDetails(
                pr.number(),
                pr.title(),
                pr.author(),
                new ForgejoPullRequestDetails.Status(ForgejoPullRequestDetails.State.OPEN, true, false),
                pr.branches(),
                pr.activity(),
                pr.size(),
                pr.conversation(),
                pr.url());
        ForgejoPullRequestDetails conflicting = new ForgejoPullRequestDetails(
                pr.number(),
                pr.title(),
                pr.author(),
                new ForgejoPullRequestDetails.Status(ForgejoPullRequestDetails.State.OPEN, false, false),
                pr.branches(),
                pr.activity(),
                pr.size(),
                pr.conversation(),
                pr.url());

        List.of(
                        Update.update(showing(shown(pr)), new PullRequestMsg.MergeChosen(ForgejoMergeStyle.MERGE)),
                        Update.update(showing(shown(draft)), new PullRequestMsg.MergeChosen(ForgejoMergeStyle.SQUASH)),
                        Update.update(
                                showing(shown(conflicting)), new PullRequestMsg.MergeChosen(ForgejoMergeStyle.SQUASH)))
                .forEach(next -> {
                    assertEquals(List.empty(), next.cmds());
                    assertTrue(next.model().popup(ForgejoPullRequestPopup.class).isDefined());
                });
    }

    @Test
    void nothingIsMergedWhileThePullRequestIsRead() {
        Model reading = Update.update(listed(), new PullRequestMsg.Requested(new BranchName("feature")))
                .model();

        assertEquals(
                List.empty(),
                Update.update(reading, new PullRequestMsg.MergeChosen(ForgejoMergeStyle.SQUASH))
                        .cmds());
    }

    @Test
    void onceMergedTheRemotesAreFetchedAndAFailureBeforeForgotten() {
        Model failedBefore = listed().mapPart(
                        ForgejoPullRequests.PART,
                        prs -> prs.mergeFailed(new FailedMerge(
                                new PrNumber(12),
                                ForgejoMergeStyle.SQUASH,
                                new MergeFailure(new ForgejoError.Unreachable("x"), "h", new RepoSlug("r")),
                                STANDING)));

        Next next = Update.update(
                HeardEvents.hearing(failedBefore), new PullRequestMsg.Merged(SQUASH, new ForgejoMerged.Merged()));

        assertEquals(HashMap.empty(), prs(next.model()).failedMerges());
        // The pull requests are read again as Forgejo's reactions answer the operation, which changed the forge.
        assertTrue(
                next.cmds().contains(new Heard(new KernelEvent.RemoteChanged())),
                next.cmds().toString());
        assertEquals(Option.none(), next.model().outcome().error());
    }

    @Test
    void aBranchForgejoKeptIsToldAsItIsMerged() {
        Next next = Update.update(
                listed(),
                new PullRequestMsg.Merged(SQUASH, new ForgejoMerged.BranchKept("the head branch is protected")));

        assertEquals(
                Option.<Notice>of(new BranchKeptNotice(new BranchName("feature"), "the head branch is protected")),
                next.model().outcome().notice());
        assertEquals(Option.none(), next.model().outcome().error());
    }

    @Test
    void aMergeThatFailedIsShownAndKeptWithHowThePullRequestStood() {
        MergeFailure refused = new MergeFailure(
                new ForgejoError.Refused(
                        405, "{\"message\":\"not allowed to merge [reason: Does not have enough approvals]\"}"),
                "code.example.net",
                new RepoSlug("cvf/nix"));

        Next next = Update.update(listed(), new PullRequestMsg.MergeFailed(SQUASH, refused));

        assertEquals(
                HashMap.of(
                        new PrNumber(12),
                        new FailedMerge(new PrNumber(12), ForgejoMergeStyle.SQUASH, refused, STANDING)),
                prs(next.model()).failedMerges());
        assertEquals(
                Option.of(refused),
                next.model().outcome().error().map(error -> ((AppError.OperationFailed) error).failure()));
    }

    @Test
    void anyMergeThatFailedReadsThePullRequestsAgainAsItMayBeMergedAllTheSame() {
        // Forgejo merges before it deletes the branch: a deletion that fails otherwise than the 403s known still
        // merged.
        MergeFailure deleting = new MergeFailure(
                new ForgejoError.Refused(500, "{\"message\":\"\"}"), "code.example.net", new RepoSlug("cvf/nix"));
        MergeFailure moved = new MergeFailure(
                new ForgejoError.Refused(409, "{\"message\":\"head out of date\"}"),
                "code.example.net",
                new RepoSlug("cvf/nix"));

        List.of(deleting, moved).forEach(failure -> {
            Next next = Update.update(listed(), new PullRequestMsg.MergeFailed(SQUASH, failure));
            assertEquals(
                    List.of(new ReadPullRequests(ORIGIN)),
                    next.cmds().filter(ReadPullRequests.class::isInstance),
                    next.cmds().toString());
        });
    }

    @Test
    void theFailedMergesAreDroppedOnceThePullRequestsAreOfAnotherHome() {
        // Another repository numbers its pull requests anew: its #12 is not the one that failed.
        FailedMerge failed = new FailedMerge(
                new PrNumber(12),
                ForgejoMergeStyle.SQUASH,
                new MergeFailure(new ForgejoError.Unreachable("x"), "h", new RepoSlug("r")),
                STANDING);
        ForgejoPullRequests kept = prs(listed()).mergeFailed(failed);

        ForgejoPullRequests other =
                kept.listed(new RemoteName("upstream"), Option.of(HashMap.of(FEATURE, LISTED)), Option.none());

        assertEquals(HashMap.empty(), other.failedMerges());
    }

    @Test
    void aFailedMergeIsDroppedOnceItsPullRequestIsNoLongerOpen() {
        FailedMerge failed = new FailedMerge(
                new PrNumber(12),
                ForgejoMergeStyle.SQUASH,
                new MergeFailure(new ForgejoError.Unreachable("x"), "h", new RepoSlug("r")),
                STANDING);
        Model kept = listed().mapPart(ForgejoPullRequests.PART, prs -> prs.mergeFailed(failed));

        Model stillOpen = Update.update(
                        kept, new PullRequestMsg.Listed(ORIGIN, Option.of(HashMap.of(FEATURE, LISTED)), Option.none()))
                .model();
        assertEquals(HashMap.of(new PrNumber(12), failed), prs(stillOpen).failedMerges());

        Model unread = Update.update(kept, new PullRequestMsg.Listed(ORIGIN, Option.none(), Option.none()))
                .model();
        assertEquals(HashMap.of(new PrNumber(12), failed), prs(unread).failedMerges());

        Model gone = Update.update(kept, new PullRequestMsg.Listed(ORIGIN, Option.of(HashMap.empty()), Option.none()))
                .model();
        assertEquals(HashMap.empty(), prs(gone).failedMerges());
    }
}
