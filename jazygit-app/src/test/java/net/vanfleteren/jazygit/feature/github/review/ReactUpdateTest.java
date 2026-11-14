package net.vanfleteren.jazygit.feature.github.review;

import static net.vanfleteren.jazygit.feature.github.review.ReviewFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.diff.CommitFilesCmd;
import net.vanfleteren.jazygit.feature.github.GitHubError;
import net.vanfleteren.jazygit.feature.github.GitHubError.CannotReview.Why;
import net.vanfleteren.jazygit.gh.model.CommentRights;
import net.vanfleteren.jazygit.gh.model.PrNumber;
import net.vanfleteren.jazygit.gh.model.Reaction;
import net.vanfleteren.jazygit.gh.model.Reactions;
import net.vanfleteren.jazygit.gh.model.ReviewThread;
import net.vanfleteren.jazygit.gh.process.GhError;
import net.vanfleteren.jazygit.state.*;
import net.vanfleteren.jazygit.state.Update.Next;
import org.junit.jupiter.api.Test;

/**
 * Reacting to a comment of a review thread of the diff of a pull request's files (ADR 86).
 */
class ReactUpdateTest {

    @Test
    void aReactionIsChosenInAPopupAndCountedOnceGitHubTookItWithoutReadingTheThreadsAgain() {
        Model asked = Update.update(files(), new ReactMsg.ReactAsked(at(onComment("R1", "T1"))))
                .model();
        ReactionPopup popup = asked.popup(ReactionPopup.class).get();
        assertEquals(
                new ReactionPopup(
                        new PrNumber(7), new ReviewThread.Id("T1"), new ReviewThread.Comment.Id("R1"), Reactions.NONE),
                popup);

        Next chosen = Update.update(asked, new ReactMsg.ReactionChosen(Reaction.EYES));
        ReactCmd.React react = new ReactCmd.React(
                new PrNumber(7), new ReviewThread.Id("T1"), new ReviewThread.Comment.Id("R1"), Reaction.EYES, true);
        assertEquals(List.of(react), chosen.cmds());
        assertTrue(chosen.model().popup(ReactionPopup.class).isEmpty());
        assertEquals(
                HashSet.of(new ReviewThread.Comment.Id("R1")),
                view(chosen.model()).reacting());

        Next reacted = Update.update(chosen.model(), new ReactMsg.Reacted(react, List.empty()));
        assertFalse(reacted.cmds().exists(cmd -> cmd instanceof CommitFilesCmd.LoadReviewRemarks));
        assertEquals(
                new Reactions(List.of(new Reactions.Group(Reaction.EYES, 1, true))), reactions(reacted.model(), "R1"));
        assertEquals(HashSet.empty(), view(reacted.model()).reacting());
        assertEquals(1, view(reacted.model()).version());
    }

    @Test
    void aReactionTheViewerGaveIsTakenBackWhenChosenAgain() {
        Model reacted = Update.update(
                        files(),
                        new ReactMsg.Reacted(
                                new ReactCmd.React(
                                        new PrNumber(7),
                                        new ReviewThread.Id("T1"),
                                        new ReviewThread.Comment.Id("C1"),
                                        Reaction.HEART,
                                        true),
                                List.empty()))
                .model();
        Model asked = Update.update(reacted, new ReactMsg.ReactAsked(at(onThread("T1"))))
                .model();

        Next chosen = Update.update(asked, new ReactMsg.ReactionChosen(Reaction.HEART));

        ReactCmd.React unreact = new ReactCmd.React(
                new PrNumber(7), new ReviewThread.Id("T1"), new ReviewThread.Comment.Id("C1"), Reaction.HEART, false);
        assertEquals(List.of(unreact), chosen.cmds());
        assertEquals(
                Reactions.NONE,
                reactions(
                        Update.update(chosen.model(), new ReactMsg.Reacted(unreact, List.empty()))
                                .model(),
                        "C1"));
    }

    @Test
    void whileAReactionIsSentTheCommentTakesNoOther() {
        Model sending = Update.update(
                        Update.update(files(), new ReactMsg.ReactAsked(at(onComment("R1", "T1"))))
                                .model(),
                        new ReactMsg.ReactionChosen(Reaction.THUMBS_UP))
                .model();

        Next again = Update.update(sending, new ReactMsg.ReactAsked(at(onComment("R1", "T1"))));

        assertTrue(again.model().popup(ReactionPopup.class).isEmpty());
        assertEquals(Option.of(new GitHubError.CannotReview(Why.REACTING)), error(again.model()));
        // Another comment takes one meanwhile.
        assertTrue(Update.update(sending, new ReactMsg.ReactAsked(at(onComment("C1", "T1"))))
                .model()
                .popup(ReactionPopup.class)
                .isDefined());
    }

    @Test
    void aReactionIsGivenOrTakenBackAsTheCommentHasItWhenChosenNotAsThePopupOpenedOnIt() {
        Model asked = Update.update(files(), new ReactMsg.ReactAsked(at(onComment("C1", "T1"))))
                .model();
        // Meanwhile the viewer's heart arrives, as from a read of the threads.
        Model meanwhile = Update.update(
                        asked,
                        new ReactMsg.Reacted(
                                new ReactCmd.React(
                                        new PrNumber(7),
                                        new ReviewThread.Id("T1"),
                                        new ReviewThread.Comment.Id("C1"),
                                        Reaction.HEART,
                                        true),
                                List.empty()))
                .model();
        assertEquals(Reactions.NONE, meanwhile.popup(ReactionPopup.class).get().reactions());

        Next chosen = Update.update(meanwhile, new ReactMsg.ReactionChosen(Reaction.HEART));

        assertEquals(
                List.of(new ReactCmd.React(
                        new PrNumber(7),
                        new ReviewThread.Id("T1"),
                        new ReviewThread.Comment.Id("C1"),
                        Reaction.HEART,
                        false)),
                chosen.cmds());
    }

    @Test
    void aCommentGoneBeforeItsReactionIsChosenTakesNone() {
        Model asked = Update.update(files(), new ReactMsg.ReactAsked(at(onComment("R1", "T1"))))
                .model();
        Model deleted = Update.update(
                        asked,
                        new DeleteCommentMsg.CommentDeleted(
                                new DeleteCommentCmd.DeleteComment(
                                        new PrNumber(7), new ReviewThread.Id("T1"), new ReviewThread.Comment.Id("R1")),
                                List.empty()))
                .model();

        Next chosen = Update.update(deleted, new ReactMsg.ReactionChosen(Reaction.EYES));

        assertEquals(List.empty(), chosen.cmds());
        assertTrue(chosen.model().popup(ReactionPopup.class).isEmpty());
        assertTrue(error(chosen.model()).isDefined());
    }

    @Test
    void aCommentGoneFromTheThreadsReadAgainBeforeItsReactionIsChosenTakesNone() {
        Model asked = Update.update(files(), new ReactMsg.ReactAsked(at(onComment("R1", "T1"))))
                .model();
        // Read again, the thread has only its first comment: the reply was deleted elsewhere.
        Model read = Update.update(
                        asked,
                        readAgain(
                                HashSet.empty(),
                                OPEN.withComments(OPEN.comments().take(1))))
                .model();
        assertTrue(read.popup(ReactionPopup.class).isDefined());

        Next chosen = Update.update(read, new ReactMsg.ReactionChosen(Reaction.EYES));

        assertEquals(List.empty(), chosen.cmds());
        assertTrue(chosen.model().popup(ReactionPopup.class).isEmpty());
        assertEquals(Option.of(new GitHubError.CannotReview(Why.NOT_ON_THREAD)), error(chosen.model()));
        assertEquals(HashSet.empty(), view(chosen.model()).reacting());
    }

    @Test
    void aCommentIsReactedToOnlyWithTheRightTo() {
        Model locked = withRights(files(), new CommentRights(true, true, false));

        Next refused = Update.update(locked, new ReactMsg.ReactAsked(at(onComment("R1", "T1"))));

        assertTrue(refused.model().popup(ReactionPopup.class).isEmpty());
        assertEquals(Option.of(new GitHubError.CannotReview(Why.NO_REACT)), error(refused.model()));
    }

    @Test
    void aReactionThatFailedLeavesTheCommentAndTakesAnotherAgain() {
        Model sending = Update.update(
                        Update.update(files(), new ReactMsg.ReactAsked(at(onComment("R1", "T1"))))
                                .model(),
                        new ReactMsg.ReactionChosen(Reaction.ROCKET))
                .model();
        ReactCmd.React react = new ReactCmd.React(
                new PrNumber(7), new ReviewThread.Id("T1"), new ReviewThread.Comment.Id("R1"), Reaction.ROCKET, true);

        Model failed = Update.update(
                        sending, new ReactMsg.ReactFailed(react, new Failure.Bug(new RuntimeException("offline"))))
                .model();

        assertEquals(Reactions.NONE, reactions(failed, "R1"));
        assertEquals(HashSet.empty(), view(failed).reacting());
        assertTrue(error(failed).isDefined());
        assertEquals(List.of("C1", "R1"), comments(failed, "T1"));
    }

    @Test
    void aReactionToACommentGitHubNoLongerHasTakesItOutOfItsThreadAndSaysSo() {
        ReactCmd.React react = new ReactCmd.React(
                new PrNumber(7), new ReviewThread.Id("T1"), new ReviewThread.Comment.Id("R1"), Reaction.ROCKET, true);

        Next failed = Update.update(
                files(), new ReactMsg.ReactFailed(react, new Failure.GitHub(new GhError.Gone(), List.empty())));

        assertEquals(List.of("C1"), comments(failed.model(), "T1"));
        assertEquals(1, view(failed.model()).version());
        assertTrue(error(failed.model()).isDefined());
        assertFalse(failed.cmds().exists(cmd -> cmd instanceof CommitFilesCmd.LoadReviewRemarks));
    }
}
