package net.vanfleteren.jazygit.feature.github.verdict;

import static net.vanfleteren.jazygit.state.TestModels.loaded;
import static net.vanfleteren.jazygit.state.TestModels.refused;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.feature.diff.CommitFilesCmd.LoadReviewRemarks;
import net.vanfleteren.jazygit.feature.diff.CommitFilesMsg;
import net.vanfleteren.jazygit.feature.github.GitHubError;
import net.vanfleteren.jazygit.feature.github.GitHubError.CannotReview.Why;
import net.vanfleteren.jazygit.feature.github.comment.CommentCmd;
import net.vanfleteren.jazygit.feature.github.show.PullRequestCmd.LoadPullRequestDetails;
import net.vanfleteren.jazygit.feature.github.show.PullRequestPopup;
import net.vanfleteren.jazygit.feature.github.verdict.VerdictCmd.SubmitReview;
import net.vanfleteren.jazygit.gh.StubGitHub;
import net.vanfleteren.jazygit.gh.model.GhBranch;
import net.vanfleteren.jazygit.gh.model.GhCommit;
import net.vanfleteren.jazygit.gh.model.PrNumber;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Conversation;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Reviewing;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.State;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Status;
import net.vanfleteren.jazygit.gh.model.ReviewRemarks;
import net.vanfleteren.jazygit.gh.model.Verdict;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.PullRequestCommits;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.keys.Scopes;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.AppError;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;
import net.vanfleteren.jazygit.state.compose.ComposeCmd.OpenInEditor;
import net.vanfleteren.jazygit.state.compose.ComposeMsg;
import net.vanfleteren.jazygit.state.compose.EditorFile;
import net.vanfleteren.jazygit.state.diff.Changes;
import net.vanfleteren.jazygit.state.github.Reading;
import org.junit.jupiter.api.Test;

/**
 * Approving a pull request, or asking for changes (ADR 100), from its dialog and from the diff of its files.
 */
class VerdictUpdateTest {

    private static final BranchName FEATURE = new BranchName("feature");
    private static final PullRequest FEATURE_PR =
            new PullRequest(new PrNumber(42), new GhBranch("feature"), false, Checks.PASSING, Review.APPROVED);
    private static final Reviewing BY_A_REVIEWER = new Reviewing("PR_42", false, false);
    private static final ExecutedCommand GH = ExecutedCommand.of("gh", "api", "graphql");

    private static PullRequestDetails details(Status status, Reviewing reviewing) {
        PullRequestDetails read =
                StubGitHub.of().pullRequests().details(new PrNumber(42)).get();
        Conversation conversation = read.conversation();
        return read.withStatus(status)
                .withConversation(new Conversation(
                        conversation.review(),
                        conversation.reviewers(),
                        conversation.comments(),
                        conversation.reviewThreads(),
                        conversation.labels(),
                        reviewing));
    }

    private static PullRequestPopup dialog(Status status, Reviewing reviewing) {
        return PullRequestPopup.loading(FEATURE, FEATURE_PR, Instant.EPOCH, 0)
                .with(Either.right(details(status, reviewing)), 0);
    }

    private static final Status OPEN = new Status(State.OPEN, false, PullRequestDetails.Mergeable.MERGEABLE);
    private static final PullRequestPopup SHOWN = dialog(OPEN, BY_A_REVIEWER);

    private static Model reviewing(Verdict verdict, String text) {
        return loaded().openPopup(VerdictPopup.of(SHOWN, BY_A_REVIEWER)
                .withVerdict(verdict)
                .withText(text));
    }

    private static Option<AppError> error(Model model) {
        return model.outcome().error();
    }

    // The files of pull request 7 listed, with its remarks read as remarks says.
    private static Model diff(ReviewRemarks remarks) {
        String head = "a".repeat(40);
        String base = "b".repeat(40);
        Model opened = Update.update(
                        loaded(),
                        new CommitFilesMsg.Opened(
                                new Changes.OfPullRequest(
                                        new PullRequestCommits(7, "owner/repo", "", new Sha(base), new Sha(head))),
                                Scopes.BRANCHES.pane()))
                .model();
        return Update.update(opened, new CommitFilesMsg.RemarksLoaded(new PrNumber(7), remarks, HashSet.empty(), 0))
                .model();
    }

    private static ReviewRemarks remarks(State state, boolean draft, Reviewing reviewing) {
        return new ReviewRemarks(
                state, GhCommit.parse("a".repeat(40)), GhCommit.parse("b".repeat(40)), List.empty(), draft, reviewing);
    }

    @Test
    void theDialogOpensAReviewThatApprovesWithNoTextYet() {
        Next next = Update.update(loaded().openPopup(SHOWN), new VerdictMsg.DialogAsked());

        assertEquals(
                Option.of(new VerdictPopup(
                        new PrNumber(42), "PR_42", false, Option.of(SHOWN), Verdict.APPROVE, false, "", false)),
                next.model().popup());
        assertEquals(List.empty(), next.cmds());
    }

    @Test
    void theDialogOffersItOnlyOnAnOpenPullRequestOfAnother() {
        assertTrue(VerdictUpdate.reviewable(SHOWN));
        assertTrue(VerdictUpdate.reviewable(dialog(OPEN.withDraft(true), BY_A_REVIEWER)));
        assertFalse(VerdictUpdate.reviewable(dialog(OPEN, new Reviewing("PR_42", true, false))));
        assertFalse(VerdictUpdate.reviewable(dialog(OPEN.withState(State.CLOSED), BY_A_REVIEWER)));
        assertFalse(VerdictUpdate.reviewable(dialog(OPEN.withState(State.MERGED), BY_A_REVIEWER)));
        assertFalse(VerdictUpdate.reviewable(dialog(OPEN, Reviewing.UNKNOWN)));
        assertFalse(VerdictUpdate.reviewable(PullRequestPopup.loading(FEATURE, FEATURE_PR, Instant.EPOCH, 0)));
    }

    @Test
    void aReviewOfADraftSaysSo() {
        PullRequestPopup draft = dialog(OPEN.withDraft(true), BY_A_REVIEWER);

        Model next = Update.update(loaded().openPopup(draft), new VerdictMsg.DialogAsked())
                .model();

        assertEquals(Option.of(true), next.popup(VerdictPopup.class).map(VerdictPopup::draft));
    }

    @Test
    void aPendingReviewIsToldInThePopupAndStopsSendingIt() {
        PullRequestPopup pending = dialog(OPEN, new Reviewing("PR_42", false, true));

        Model asked = Update.update(loaded().openPopup(pending), new VerdictMsg.DialogAsked())
                .model();
        Next confirmed = Update.update(asked, new ComposeMsg.Confirmed());

        assertTrue(asked.popup(VerdictPopup.class).isDefined());
        assertEquals(Option.of(new GitHubError.CannotReview(Why.PENDING_REVIEW)), error(asked));
        assertEquals(List.empty(), confirmed.cmds());
        assertEquals(Option.of(new GitHubError.CannotReview(Why.PENDING_REVIEW)), error(confirmed.model()));
    }

    @Test
    void eitherVerdictIsChosen() {
        Model switched = Update.update(reviewing(Verdict.APPROVE, ""), new VerdictMsg.Chosen(Verdict.REQUEST_CHANGES))
                .model();
        Model back =
                Update.update(switched, new VerdictMsg.Chosen(Verdict.APPROVE)).model();

        assertEquals(
                Option.of(Verdict.REQUEST_CHANGES),
                switched.popup(VerdictPopup.class).map(VerdictPopup::verdict));
        assertEquals(Option.of(Verdict.APPROVE), back.popup(VerdictPopup.class).map(VerdictPopup::verdict));
    }

    @Test
    void anApprovalIsSentWithoutText() {
        Next sending = Update.update(reviewing(Verdict.APPROVE, ""), new ComposeMsg.Confirmed());

        assertEquals(List.of(new SubmitReview(new PrNumber(42), "PR_42", Verdict.APPROVE, "")), sending.cmds());
        assertEquals(Option.of(true), sending.model().popup(VerdictPopup.class).map(VerdictPopup::sending));
    }

    @Test
    void askingForChangesNeedsAReasonAndSendsItAsTyped() {
        Next blank = Update.update(reviewing(Verdict.REQUEST_CHANGES, "  \n"), new ComposeMsg.Confirmed());
        Next typed = Update.update(reviewing(Verdict.REQUEST_CHANGES, "  Rename it\n"), new ComposeMsg.Confirmed());

        assertEquals(List.empty(), blank.cmds());
        assertEquals(Option.of(new GitHubError.CannotReview(Why.NO_REASON)), error(blank.model()));
        assertEquals(Option.of(false), blank.model().popup(VerdictPopup.class).map(VerdictPopup::sending));
        assertEquals(
                List.of(new SubmitReview(new PrNumber(42), "PR_42", Verdict.REQUEST_CHANGES, "  Rename it\n")),
                typed.cmds());
    }

    @Test
    void whileSentNothingChangesItNorSendsItAgain() {
        Model sending = Update.update(reviewing(Verdict.APPROVE, "Nice"), new ComposeMsg.Confirmed())
                .model();

        assertEquals(
                sending, Update.update(sending, new ComposeMsg.Edited("Other")).model());
        assertEquals(
                sending,
                Update.update(sending, new VerdictMsg.Chosen(Verdict.REQUEST_CHANGES))
                        .model());
        assertEquals(
                List.empty(), Update.update(sending, new ComposeMsg.Confirmed()).cmds());
        assertEquals(sending, Update.update(sending, new ComposeMsg.Cancelled()).model());
    }

    @Test
    void escapeGoesBackToTheDialogDroppingTheText() {
        Model back = Update.update(reviewing(Verdict.APPROVE, "Nice"), new ComposeMsg.Cancelled())
                .model();

        assertEquals(Option.of(SHOWN), back.popup());
    }

    @Test
    void theEditorWritesTheTextInItsOwnFile() {
        Next asked = Update.update(reviewing(Verdict.APPROVE, "Nice"), new ComposeMsg.EditorRequested());
        Model saved = Update.update(
                        asked.model(), new ComposeMsg.EditorClosed(VerdictCmd.EDITOR, "Nicer", List.empty()))
                .model();

        assertEquals(List.of(new OpenInEditor(VerdictCmd.EDITOR, "Nice")), asked.cmds());
        assertEquals(new EditorFile("PR_REVIEW.md", CommentCmd.EDIT_COMMENT), VerdictCmd.EDITOR);
        assertEquals(Option.of("Nicer"), saved.popup(VerdictPopup.class).map(VerdictPopup::text));
    }

    @Test
    void onceSentTheDialogComesBackReadingItsDetailsAgain() {
        Next sending = Update.update(reviewing(Verdict.APPROVE, ""), new ComposeMsg.Confirmed());
        SubmitReview cmd = (SubmitReview) sending.cmds().head();

        Next sent = Update.update(sending.model(), new VerdictMsg.Sent(cmd, List.of(GH)));

        PullRequestPopup back = sent.model().popup(PullRequestPopup.class).get();
        assertEquals(Reading.BY_ITSELF, back.reading());
        assertInstanceOf(LoadPullRequestDetails.class, sent.cmds().last());
        assertEquals(new PrNumber(42), ((LoadPullRequestDetails) sent.cmds().last()).number());
    }

    @Test
    void aFailureKeepsThePopupOpenWithTheText() {
        Next sending = Update.update(reviewing(Verdict.REQUEST_CHANGES, "Rename it"), new ComposeMsg.Confirmed());
        SubmitReview cmd = (SubmitReview) sending.cmds().head();

        Model failed = Update.update(
                        sending.model(), new VerdictMsg.SendFailed(cmd, refused("can only have one pending review")))
                .model();

        VerdictPopup popup = failed.popup(VerdictPopup.class).get();
        assertFalse(popup.sending());
        assertEquals("Rename it", popup.text());
        assertTrue(error(failed).isDefined());
    }

    @Test
    void thePopupFollowsTheDialogBecomingADraft() {
        VerdictPopup popup = VerdictPopup.of(SHOWN, BY_A_REVIEWER);
        PullRequestPopup drafted = dialog(OPEN.withDraft(true), BY_A_REVIEWER);

        VerdictPopup followed = popup.following(drafted);

        assertTrue(followed.draft());
        assertEquals(Option.of(drafted), followed.from().map(from -> from.pullRequestPopup()));
    }

    @Test
    void thePopupFollowsAPendingReviewBeingDroppedAndTheErrorThatSaidSoGoes() {
        Model pending = Update.update(
                        loaded().openPopup(dialog(OPEN, new Reviewing("PR_42", false, true))),
                        new VerdictMsg.DialogAsked())
                .model();
        VerdictPopup was = pending.popup(VerdictPopup.class).get();

        VerdictPopup now = was.following(SHOWN);
        Model followed = VerdictUpdate.followed(pending, was, now);

        assertFalse(now.pending());
        assertEquals(Option.none(), error(followed));
        assertEquals(
                List.of(new SubmitReview(new PrNumber(42), "PR_42", Verdict.APPROVE, "")),
                Update.update(followed, new ComposeMsg.Confirmed()).cmds());
    }

    @Test
    void thePopupFollowsAPendingReviewStartedMeanwhile() {
        VerdictPopup now =
                VerdictPopup.of(SHOWN, BY_A_REVIEWER).following(dialog(OPEN, new Reviewing("PR_42", false, true)));

        assertTrue(now.pending());
    }

    @Test
    void anotherErrorStaysWhenThePopupFollowsTheDialog() {
        Model failed = reviewing(Verdict.REQUEST_CHANGES, "").fail(new GitHubError.CannotReview(Why.NO_REASON));
        VerdictPopup was = failed.popup(VerdictPopup.class).get();

        Model followed = VerdictUpdate.followed(failed, was, was.following(SHOWN));

        assertEquals(Option.of(new GitHubError.CannotReview(Why.NO_REASON)), error(followed));
    }

    @Test
    void theDiffOpensAReviewOfItsPullRequest() {
        Model asked = Update.update(
                        diff(remarks(State.OPEN, true, new Reviewing("PR_7", false, false))),
                        new VerdictMsg.DiffAsked())
                .model();

        assertEquals(
                Option.of(new VerdictPopup(
                        new PrNumber(7), "PR_7", true, Option.none(), Verdict.APPROVE, false, "", false)),
                asked.popup());
    }

    @Test
    void theDiffSaysWhyItCannotBeReviewed() {
        assertEquals(
                Option.of(new GitHubError.CannotReview(Why.OWN_PULL_REQUEST)),
                error(Update.update(
                                diff(remarks(State.OPEN, false, new Reviewing("PR_7", true, false))),
                                new VerdictMsg.DiffAsked())
                        .model()));
        assertEquals(
                Option.of(new GitHubError.CannotReview(Why.PENDING_REVIEW)),
                error(Update.update(
                                diff(remarks(State.OPEN, false, new Reviewing("PR_7", false, true))),
                                new VerdictMsg.DiffAsked())
                        .model()));
        assertEquals(
                Option.of(new GitHubError.CannotReview(Why.NOT_OPEN)),
                error(Update.update(
                                diff(remarks(State.CLOSED, false, new Reviewing("PR_7", false, false))),
                                new VerdictMsg.DiffAsked())
                        .model()));
        assertEquals(
                Option.of(new GitHubError.CannotReview(Why.NOT_READ)),
                error(Update.update(loaded(), new VerdictMsg.DiffAsked()).model()));
    }

    @Test
    void onceSentFromTheDiffThePopupClosesAndTheRemarksAreReadAgain() {
        Model asked = Update.update(
                        diff(remarks(State.OPEN, false, new Reviewing("PR_7", false, false))),
                        new VerdictMsg.DiffAsked())
                .model();
        Next sending = Update.update(asked, new ComposeMsg.Confirmed());
        SubmitReview cmd = (SubmitReview) sending.cmds().head();

        Next sent = Update.update(sending.model(), new VerdictMsg.Sent(cmd, List.of(GH)));

        assertEquals(Option.none(), sent.model().popup());
        assertEquals(
                new LoadReviewRemarks(new PrNumber(7), HashSet.empty(), 0),
                sent.cmds().last());
    }
}
