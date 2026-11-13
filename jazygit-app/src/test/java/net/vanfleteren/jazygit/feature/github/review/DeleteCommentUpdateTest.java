package net.vanfleteren.jazygit.feature.github.review;

import static net.vanfleteren.jazygit.feature.github.review.ReviewFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.diff.CommitFilesCmd;
import net.vanfleteren.jazygit.feature.diff.CommitFilesMsg;
import net.vanfleteren.jazygit.feature.github.GitHubError;
import net.vanfleteren.jazygit.feature.github.GitHubError.CannotReview.Why;
import net.vanfleteren.jazygit.feature.github.review.ReviewPopup.Target;
import net.vanfleteren.jazygit.gh.model.CommentRights;
import net.vanfleteren.jazygit.gh.model.GhCommit;
import net.vanfleteren.jazygit.gh.model.PrNumber;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails;
import net.vanfleteren.jazygit.gh.model.ReviewRemarks;
import net.vanfleteren.jazygit.gh.model.ReviewThread;
import net.vanfleteren.jazygit.state.*;
import net.vanfleteren.jazygit.state.Update.Next;
import net.vanfleteren.jazygit.state.load.*;
import org.junit.jupiter.api.Test;

/**
 * Deleting a comment of a review thread of the diff of a pull request's files, after asking (ADR 64).
 */
class DeleteCommentUpdateTest {

    @Test
    void aCommentIsDeletedAfterAskingAndLeavesItsThreadWithoutReadingTheThreadsAgain() {
        Model asked = Update.update(files(), new DeleteCommentMsg.DeleteAsked(at(onComment("R1", "T1"))))
                .model();
        DeleteCommentPopup popup = asked.popup(DeleteCommentPopup.class).get();
        assertEquals(new ReviewThread.Comment.Id("R1"), popup.comment().id());
        assertEquals(new ReviewThread.Id("T1"), popup.thread());

        Next confirmed = Update.update(asked, new DeleteCommentMsg.DeleteConfirmed());
        DeleteCommentCmd.DeleteComment delete = new DeleteCommentCmd.DeleteComment(
                new PrNumber(7), new ReviewThread.Id("T1"), new ReviewThread.Comment.Id("R1"));
        assertEquals(List.of(delete), confirmed.cmds());
        assertTrue(confirmed.model().popup(DeleteCommentPopup.class).isEmpty());
        assertEquals(
                HashSet.of(new ReviewThread.Comment.Id("R1")),
                view(confirmed.model()).deleting());
        assertEquals(List.of("C1", "R1"), comments(confirmed.model(), "T1"));

        Next deleted = Update.update(confirmed.model(), new DeleteCommentMsg.CommentDeleted(delete, List.empty()));
        assertFalse(deleted.cmds().exists(cmd -> cmd instanceof CommitFilesCmd.LoadReviewRemarks));
        assertEquals(HashSet.empty(), view(deleted.model()).deleting());
        assertEquals(List.of("C1"), comments(deleted.model(), "T1"));
        assertEquals(1, view(deleted.model()).version());
    }

    @Test
    void theStopOfAnOpenThreadIsItsFirstCommentAndOnceThatIsDeletedTheNextIsItsFirst() {
        Next confirmed = deleting(files(), onThread("T1"));
        DeleteCommentCmd.DeleteComment delete = new DeleteCommentCmd.DeleteComment(
                new PrNumber(7), new ReviewThread.Id("T1"), new ReviewThread.Comment.Id("C1"));
        assertEquals(List.of(delete), confirmed.cmds());

        Model deleted = Update.update(confirmed.model(), new DeleteCommentMsg.CommentDeleted(delete, List.empty()))
                .model();

        assertEquals(List.of("R1"), comments(deleted, "T1"));
        // A reply goes to the comment that is first now (ADR 64).
        Model replying = Update.update(deleted, new ReviewMsg.ReplyAsked(at(onThread("T1"))))
                .model();
        assertEquals(
                new Target.Reply(new ReviewThread.Id("T1"), new ReviewThread.Comment.RestId("2")),
                replying.popup(ReviewPopup.class).get().target());
    }

    @Test
    void aThreadGoesWithItsLastComment() {
        Model once = Update.update(
                        deleting(files(), onComment("R1", "T1")).model(),
                        new DeleteCommentMsg.CommentDeleted(
                                new DeleteCommentCmd.DeleteComment(
                                        new PrNumber(7), new ReviewThread.Id("T1"), new ReviewThread.Comment.Id("R1")),
                                List.empty()))
                .model();
        Model twice = Update.update(
                        deleting(once, onThread("T1")).model(),
                        new DeleteCommentMsg.CommentDeleted(
                                new DeleteCommentCmd.DeleteComment(
                                        new PrNumber(7), new ReviewThread.Id("T1"), new ReviewThread.Comment.Id("C1")),
                                List.empty()))
                .model();

        assertEquals(List.of(new ReviewThread.Id("T2")), view(twice).threads().map(ReviewThread::id));
        assertEquals(2, view(twice).version());
    }

    @Test
    void aCollapsedThreadOfSeveralCommentsDoesNotSayWhichToDelete() {
        Next refused = Update.update(files(), new DeleteCommentMsg.DeleteAsked(at(onThread("T2"))));

        assertTrue(refused.model().popup(DeleteCommentPopup.class).isEmpty());
        assertEquals(Option.of(new GitHubError.CannotReview(Why.COLLAPSED)), error(refused.model()));
    }

    @Test
    void aCommentIsDeletedOnlyWithTheRightTo() {
        Model theirs = withRights(files(), new CommentRights(true, false, true));

        Next refused = Update.update(theirs, new DeleteCommentMsg.DeleteAsked(at(onComment("R1", "T1"))));

        assertTrue(refused.model().popup(DeleteCommentPopup.class).isEmpty());
        assertEquals(Option.of(new GitHubError.CannotReview(Why.NO_DELETE)), error(refused.model()));
    }

    @Test
    void whileItsFirstCommentIsDeletedAThreadTakesNoReplyResolveNorSpaceAndTheCommentNoSecondDelete() {
        Model deleting = deleting(files(), onThread("T1")).model();
        Option<AppError> beingDeleted = Option.of(new GitHubError.CannotReview(Why.DELETING));

        assertEquals(
                beingDeleted,
                error(Update.update(deleting, new ReviewMsg.ReplyAsked(at(onThread("T1"))))
                        .model()));
        assertEquals(
                beingDeleted,
                error(Update.update(deleting, new ReviewMsg.ResolveAsked(at(onThread("T1"))))
                        .model()));
        assertEquals(
                beingDeleted,
                error(Update.update(deleting, new ThreadMsg.ThreadToggled(at(onThread("T1"))))
                        .model()));
        assertEquals(
                beingDeleted,
                error(Update.update(deleting, new DeleteCommentMsg.DeleteAsked(at(onThread("T1"))))
                        .model()));
        // Its other comments are not being deleted.
        assertTrue(Update.update(deleting, new DeleteCommentMsg.DeleteAsked(at(onComment("R1", "T1"))))
                .model()
                .popup(DeleteCommentPopup.class)
                .isDefined());
    }

    @Test
    void aCommentOfAThreadSomethingIsSentForIsNotDeleted() {
        Model resolving = Update.update(files(), new ReviewMsg.ResolveAsked(at(onThread("T1"))))
                .model();

        assertEquals(
                Option.of(new GitHubError.CannotReview(Why.SENDING)),
                error(Update.update(resolving, new DeleteCommentMsg.DeleteAsked(at(onComment("R1", "T1"))))
                        .model()));
    }

    @Test
    void aCommentIsDeletedWhateverTheCommitsItWasReadAt() {
        Model movedOn = files(PullRequestDetails.State.CLOSED, "c".repeat(40));

        assertEquals(
                List.of(new DeleteCommentCmd.DeleteComment(
                        new PrNumber(7), new ReviewThread.Id("T1"), new ReviewThread.Comment.Id("R1"))),
                deleting(movedOn, onComment("R1", "T1")).cmds());
    }

    @Test
    void aCommentGoneBeforeItsDeleteIsConfirmedIsNotDeleted() {
        Model asked = Update.update(files(), new DeleteCommentMsg.DeleteAsked(at(onComment("R1", "T1"))))
                .model();
        ReviewRemarks without = view(asked)
                .withoutComment(new ReviewThread.Comment.Id("R1"))
                .remarks()
                .get()
                .toOption()
                .get();
        Model read = Update.update(
                        asked, new CommitFilesMsg.RemarksLoaded(new PrNumber(7), without, HashSet.empty(), 0))
                .model();

        Next confirmed = Update.update(read, new DeleteCommentMsg.DeleteConfirmed());

        assertEquals(List.empty(), confirmed.cmds());
        assertTrue(confirmed.model().popup(DeleteCommentPopup.class).isEmpty());
    }

    @Test
    void aDeleteThatFailedLeavesTheComment() {
        DeleteCommentCmd.DeleteComment delete = new DeleteCommentCmd.DeleteComment(
                new PrNumber(7), new ReviewThread.Id("T1"), new ReviewThread.Comment.Id("R1"));
        Model failed = Update.update(
                        deleting(files(), onComment("R1", "T1")).model(),
                        new DeleteCommentMsg.DeleteFailed(delete, new Failure.Bug(new RuntimeException("offline"))))
                .model();

        assertEquals(HashSet.empty(), view(failed).deleting());
        assertEquals(List.of("C1", "R1"), comments(failed, "T1"));
        assertEquals(0, view(failed).version());
    }

    @Test
    void aReadStartedBeforeADeleteIsDroppedAndReadAgainForTheThreadsItSettles() {
        // A resolve of T1 was taken, so the threads are read again; then a comment of T2 is deleted.
        Next resolved = Update.update(
                Update.update(files(), new ReviewMsg.ResolveAsked(at(onThread("T1"))))
                        .model(),
                new ReviewMsg.Resolved(
                        new ReviewCmd.Resolve(new PrNumber(7), new ReviewThread.Id("T1"), true), List.empty()));
        assertTrue(resolved.cmds()
                .contains(new CommitFilesCmd.LoadReviewRemarks(
                        new PrNumber(7), HashSet.of(new ReviewThread.Id("T1")), 0)));
        Model deleted = Update.update(
                        deleting(resolved.model(), onComment("R2", "T2")).model(),
                        new DeleteCommentMsg.CommentDeleted(
                                new DeleteCommentCmd.DeleteComment(
                                        new PrNumber(7), new ReviewThread.Id("T2"), new ReviewThread.Comment.Id("R2")),
                                List.empty()))
                .model();

        // The read of before the delete comes back with the comment still in it.
        Next stale = Update.update(
                deleted,
                new CommitFilesMsg.RemarksLoaded(new PrNumber(7), remarks(), HashSet.of(new ReviewThread.Id("T1")), 0));

        assertEquals(
                List.of(new CommitFilesCmd.LoadReviewRemarks(
                        new PrNumber(7), HashSet.of(new ReviewThread.Id("T1")), 1)),
                stale.cmds());
        assertEquals(List.of("C2"), comments(stale.model(), "T2"));
        assertEquals(HashSet.of(new ReviewThread.Id("T1")), view(stale.model()).sending());

        ReviewRemarks after = view(deleted).remarks().get().toOption().get();
        Model read = Update.update(
                        stale.model(),
                        new CommitFilesMsg.RemarksLoaded(
                                new PrNumber(7), after, HashSet.of(new ReviewThread.Id("T1")), 1))
                .model();
        assertEquals(HashSet.empty(), view(read).sending());
        assertEquals(List.of("C2"), comments(read, "T2"));
    }

    @Test
    void aCollapsedThreadOfOneCommentIsDeletedWithItButNotOneGitHubHasMoreCommentsOf() {
        Model one = files(PullRequestDetails.State.OPEN, HEAD, List.of(OPEN, firstOnly(SETTLED, 0)));
        Next confirmed = deleting(one, onThread("T2"));
        assertEquals(
                List.of(new DeleteCommentCmd.DeleteComment(
                        new PrNumber(7), new ReviewThread.Id("T2"), new ReviewThread.Comment.Id("C2"))),
                confirmed.cmds());
        Model deleted = Update.update(
                        confirmed.model(),
                        new DeleteCommentMsg.CommentDeleted(
                                new DeleteCommentCmd.DeleteComment(
                                        new PrNumber(7), new ReviewThread.Id("T2"), new ReviewThread.Comment.Id("C2")),
                                List.empty()))
                .model();
        assertEquals(List.of(new ReviewThread.Id("T1")), view(deleted).threads().map(ReviewThread::id));

        Model more = files(PullRequestDetails.State.OPEN, HEAD, List.of(OPEN, firstOnly(SETTLED, 4)));
        assertEquals(
                Option.of(new GitHubError.CannotReview(Why.COLLAPSED)),
                error(Update.update(more, new DeleteCommentMsg.DeleteAsked(at(onThread("T2"))))
                        .model()));
        // Both comments read, and one more on GitHub.
        Model bothAndMore = files(PullRequestDetails.State.OPEN, HEAD, List.of(OPEN, withMore(SETTLED, 1)));
        assertEquals(
                Option.of(new GitHubError.CannotReview(Why.COLLAPSED)),
                error(Update.update(bothAndMore, new DeleteCommentMsg.DeleteAsked(at(onThread("T2"))))
                        .model()));
    }

    @Test
    void aThreadWhoseCommentsReadAreAllDeletedIsReadAgainAndTakesNoReplyUntilThen() {
        Model more = files(PullRequestDetails.State.OPEN, HEAD, List.of(firstOnly(OPEN, 60), SETTLED));
        DeleteCommentCmd.DeleteComment delete = new DeleteCommentCmd.DeleteComment(
                new PrNumber(7), new ReviewThread.Id("T1"), new ReviewThread.Comment.Id("C1"));

        Next deleted = Update.update(
                deleting(more, onThread("T1")).model(), new DeleteCommentMsg.CommentDeleted(delete, List.empty()));

        assertEquals(List.empty(), comments(deleted.model(), "T1"));
        assertTrue(deleted.cmds().contains(new CommitFilesCmd.LoadReviewRemarks(new PrNumber(7), HashSet.empty(), 1)));
        Model replying = Update.update(deleted.model(), new ReviewMsg.ReplyAsked(at(onThread("T1"))))
                .model();
        assertTrue(replying.popup(ReviewPopup.class).isEmpty());
        assertEquals(Option.of(new GitHubError.CannotReview(Why.NOT_READ)), error(replying));
    }

    @Test
    void whatIsBeingDeletedAndTheVersionStayWhenTheFilesAreReadAgainAtCommitsThatMovedOn() {
        Model confirmed = deleting(files(), onComment("R1", "T1")).model();

        // The threads read at a head that moved on, before the delete is taken: the files are read again there.
        Next moved = Update.update(
                confirmed,
                new CommitFilesMsg.RemarksLoaded(
                        new PrNumber(7),
                        new ReviewRemarks(
                                PullRequestDetails.State.OPEN,
                                GhCommit.parse("c".repeat(40)),
                                GhCommit.parse(BASE),
                                List.of(OPEN, SETTLED)),
                        HashSet.empty(),
                        0));
        assertNotEquals(view(confirmed).of(), view(moved.model()).of());
        assertEquals(
                HashSet.of(new ReviewThread.Comment.Id("R1")),
                view(moved.model()).deleting());

        Model deleted = Update.update(
                        moved.model(),
                        new DeleteCommentMsg.CommentDeleted(
                                new DeleteCommentCmd.DeleteComment(
                                        new PrNumber(7), new ReviewThread.Id("T1"), new ReviewThread.Comment.Id("R1")),
                                List.empty()))
                .model();
        Model rebuilt = Update.update(
                        deleted,
                        new CommitFilesMsg.RemarksLoaded(
                                new PrNumber(7),
                                new ReviewRemarks(
                                        PullRequestDetails.State.OPEN,
                                        GhCommit.parse("d".repeat(40)),
                                        GhCommit.parse(BASE),
                                        view(deleted).threads()),
                                HashSet.empty(),
                                1))
                .model();
        assertEquals(1, view(rebuilt).version());

        Next stale = Update.update(
                rebuilt, new CommitFilesMsg.RemarksLoaded(new PrNumber(7), remarks(), HashSet.empty(), 0));
        assertEquals(List.of(new CommitFilesCmd.LoadReviewRemarks(new PrNumber(7), HashSet.empty(), 1)), stale.cmds());
        assertEquals(List.of("C1"), comments(stale.model(), "T1"));
    }
}
