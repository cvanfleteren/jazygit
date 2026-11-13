package net.vanfleteren.jazygit.feature.github.review;

import static net.vanfleteren.jazygit.feature.github.review.ReviewFixtures.*;
import static net.vanfleteren.jazygit.state.TestModels.loaded;
import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.HashMap;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.feature.diff.CommitFilesCmd;
import net.vanfleteren.jazygit.feature.diff.CommitFilesMsg;
import net.vanfleteren.jazygit.feature.github.GitHubError;
import net.vanfleteren.jazygit.feature.github.GitHubError.CannotReview.Why;
import net.vanfleteren.jazygit.feature.github.review.ReviewPopup.Target;
import net.vanfleteren.jazygit.gh.model.CommentRights;
import net.vanfleteren.jazygit.gh.model.NewReviewThread;
import net.vanfleteren.jazygit.gh.model.PrNumber;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails;
import net.vanfleteren.jazygit.gh.model.Reaction;
import net.vanfleteren.jazygit.gh.model.Reactions;
import net.vanfleteren.jazygit.gh.model.ReviewThread;
import net.vanfleteren.jazygit.gh.model.ReviewThread.Comment;
import net.vanfleteren.jazygit.gh.model.ReviewThread.Side;
import net.vanfleteren.jazygit.git.model.FileVersion;
import net.vanfleteren.jazygit.git.model.Folding;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.keys.Scopes;
import net.vanfleteren.jazygit.state.*;
import net.vanfleteren.jazygit.state.Update.Next;
import net.vanfleteren.jazygit.state.compose.ComposeCmd.OpenInEditor;
import net.vanfleteren.jazygit.state.compose.ComposeMsg;
import net.vanfleteren.jazygit.state.compose.EditorFile;
import net.vanfleteren.jazygit.state.diff.*;
import net.vanfleteren.jazygit.state.load.*;
import org.junit.jupiter.api.Test;

/**
 * Commenting on the lines of the diff of a pull request's files from its cursor (ADR 60), and editing a comment of it
 * (ADR 65).
 */
class ReviewUpdateTest {

    @Test
    void aThreadIsResolvedAtOnceAndTakesNoSecondKeyUntilItIsReadAgainAfterIt() {
        Next resolving = Update.update(files(), new ReviewMsg.ResolveAsked(at(onThread("T1"))));

        ReviewCmd.Resolve resolve = new ReviewCmd.Resolve(new PrNumber(7), new ReviewThread.Id("T1"), true);
        assertEquals(List.of(resolve), resolving.cmds());
        assertEquals(
                HashSet.of(new ReviewThread.Id("T1")), view(resolving.model()).sending());

        Next again = Update.update(resolving.model(), new ReviewMsg.ResolveAsked(at(onThread("T1"))));
        assertEquals(List.empty(), again.cmds());
        assertEquals(Option.of(new GitHubError.CannotReview(Why.SENDING)), error(again.model()));
        assertEquals(
                Option.of(new GitHubError.CannotReview(Why.SENDING)),
                error(Update.update(resolving.model(), new ThreadMsg.ThreadToggled(at(onThread("T1"))))
                        .model()));

        Next resolved = Update.update(resolving.model(), new ReviewMsg.Resolved(resolve, List.empty()));
        assertTrue(resolved.cmds()
                .contains(new CommitFilesCmd.LoadReviewRemarks(
                        new PrNumber(7), HashSet.of(new ReviewThread.Id("T1")), 0)));
        assertEquals(
                HashSet.of(new ReviewThread.Id("T1")), view(resolved.model()).sending());

        Model read = Update.update(
                        resolved.model(),
                        new CommitFilesMsg.RemarksLoaded(
                                new PrNumber(7), remarks(), HashSet.of(new ReviewThread.Id("T1")), 0))
                .model();
        assertEquals(HashSet.empty(), view(read).sending());
    }

    @Test
    void aReadStartedBeforeAResolveWasSentDoesNotLetTheThreadTakeASecondKey() {
        Model resolving = Update.update(files(), new ReviewMsg.ResolveAsked(at(onThread("T1"))))
                .model();
        Next refreshed = Update.update(resolving, new CommitFilesMsg.RemarksRefreshed());
        assertEquals(
                List.of(new CommitFilesCmd.LoadReviewRemarks(new PrNumber(7), HashSet.empty(), 0)), refreshed.cmds());

        // The read asked with R comes back before GitHub resolved the thread: it still shows it open.
        Model stale = Update.update(
                        refreshed.model(),
                        new CommitFilesMsg.RemarksLoaded(new PrNumber(7), remarks(), HashSet.empty(), 0))
                .model();
        assertEquals(HashSet.of(new ReviewThread.Id("T1")), view(stale).sending());
        Next again = Update.update(stale, new ReviewMsg.ResolveAsked(at(onThread("T1"))));
        assertEquals(List.empty(), again.cmds());
        assertEquals(Option.of(new GitHubError.CannotReview(Why.SENDING)), error(again.model()));
    }

    @Test
    void aSentResolveIsDoneWithWhenTheReadAfterItFails() {
        Model resolved = Update.update(
                        Update.update(files(), new ReviewMsg.ResolveAsked(at(onThread("T1"))))
                                .model(),
                        new ReviewMsg.Resolved(
                                new ReviewCmd.Resolve(new PrNumber(7), new ReviewThread.Id("T1"), true), List.empty()))
                .model();

        Model failed = Update.update(
                        resolved,
                        new CommitFilesMsg.RemarksFailed(
                                new PrNumber(7),
                                new Failure.Bug(new RuntimeException("offline")),
                                HashSet.of(new ReviewThread.Id("T1")),
                                0))
                .model();

        assertEquals(HashSet.empty(), view(failed).sending());
    }

    @Test
    void aResolveThatFailedCanBeTriedAgain() {
        Next resolving = Update.update(files(), new ReviewMsg.ResolveAsked(at(onThread("T1"))));

        Model failed = Update.update(
                        resolving.model(),
                        new ReviewMsg.ResolveFailed(
                                new ReviewCmd.Resolve(new PrNumber(7), new ReviewThread.Id("T1"), true),
                                new Failure.Bug(new RuntimeException("offline"))))
                .model();

        assertEquals(HashSet.empty(), view(failed).sending());
    }

    @Test
    void aResolvedThreadIsUnresolvedOnlyWithTheRightTo() {
        Next refused = Update.update(files(), new ReviewMsg.ResolveAsked(at(onThread("T2"))));

        assertEquals(List.empty(), refused.cmds());
        assertEquals(Option.of(new GitHubError.CannotReview(Why.NO_UNRESOLVE)), error(refused.model()));
    }

    @Test
    void theKeysOfAThreadNeedTheCursorOnOne() {
        assertEquals(
                Option.of(new GitHubError.CannotReview(Why.NOT_ON_THREAD)),
                error(Update.update(files(), new ReviewMsg.ReplyAsked(at(line(Side.RIGHT, 2))))
                        .model()));
    }

    @Test
    void nothingIsSentForAPullRequestThatMovedOnOrIsMerged() {
        assertEquals(
                Option.of(new GitHubError.CannotReview(Why.MOVED_ON)),
                error(Update.update(
                                files(PullRequestDetails.State.CLOSED, "c".repeat(40)),
                                new ReviewMsg.ResolveAsked(at(onThread("T1"))))
                        .model()));
        assertEquals(
                Option.of(new GitHubError.CannotReview(Why.MERGED)),
                error(Update.update(
                                files(PullRequestDetails.State.MERGED, HEAD),
                                new ReviewMsg.StartAsked(at(line(Side.RIGHT, 2))))
                        .model()));
    }

    @Test
    void aThreadResolvedIsAsItWasReadUntilTheThreadsReadAgainShowItResolved() {
        Model toggled = Update.update(
                        Update.update(files(), new ThreadMsg.ThreadToggled(at(onThread("T1"))))
                                .model(),
                        new ThreadMsg.ThreadToggled(at(
                                new Anchor.Comment(new ReviewThread.Comment.Id("R1"), (Anchor.Thread) onThread("T1")))))
                .model();
        Next resolving = Update.update(toggled, new ReviewMsg.ResolveAsked(at(onThread("T1"))));
        ReviewCmd.Resolve resolve = (ReviewCmd.Resolve) resolving.cmds().head();

        // Until the threads are read again, the thread is drawn as it was read: still collapsed (ADR 62).
        Model resolved = Update.update(resolving.model(), new ReviewMsg.Resolved(resolve, List.empty()))
                .model();
        assertEquals(
                new Collapsed(HashMap.of(new Collapsed.Key("T1"), false, new Collapsed.Key("R1"), false)),
                view(resolved).collapsed());
        assertTrue(view(resolved).collapsed().thread(OPEN));

        // Read again resolved, it is as a resolved thread starts: collapsed, its flip dropped; its comments stay.
        ReviewThread done = thread("T1", true, ReviewThread.Rights.ALL);
        Model read = Update.update(resolved, readAgain(HashSet.of(new ReviewThread.Id("T1")), done, SETTLED))
                .model();
        assertEquals(
                new Collapsed(HashMap.of(new Collapsed.Key("R1"), false)),
                view(read).collapsed());
        assertTrue(view(read).collapsed().thread(done));

        // A read again that fails keeps the flip, as the thread is still drawn as it was read.
        Model failed = Update.update(
                        resolved,
                        new CommitFilesMsg.RemarksFailed(
                                new PrNumber(7),
                                new Failure.Bug(new RuntimeException("offline")),
                                HashSet.of(new ReviewThread.Id("T1")),
                                0))
                .model();
        assertEquals(
                new Collapsed(HashMap.of(new Collapsed.Key("T1"), false, new Collapsed.Key("R1"), false)),
                view(failed).collapsed());
        Model notSent = Update.update(
                        resolving.model(),
                        new ReviewMsg.ResolveFailed(resolve, new Failure.Bug(new RuntimeException("offline"))))
                .model();
        assertEquals(
                new Collapsed(HashMap.of(new Collapsed.Key("T1"), false, new Collapsed.Key("R1"), false)),
                view(notSent).collapsed());
    }

    @Test
    void aThreadResolvedOrUnresolvedElsewhereIsAsItStartsOnceReadAgain() {
        Model opened = Update.update(files(), new ThreadMsg.ThreadToggled(at(onThread("T2"))))
                .model();
        assertEquals(
                new Collapsed(HashMap.of(new Collapsed.Key("T2"), true)),
                view(opened).collapsed());

        // Read again as it was, the flip stays.
        assertEquals(
                new Collapsed(HashMap.of(new Collapsed.Key("T2"), true)),
                view(Update.update(opened, readAgain(HashSet.empty(), OPEN, SETTLED))
                                .model())
                        .collapsed());
        // Unresolved by someone else and read again with R, it is open as an unresolved thread starts.
        ReviewThread unresolved = thread("T2", false, ReviewThread.Rights.ALL);
        Model read = Update.update(opened, readAgain(HashSet.empty(), OPEN, unresolved))
                .model();
        assertEquals(Collapsed.NONE, view(read).collapsed());
        assertFalse(view(read).collapsed().thread(unresolved));
    }

    @Test
    void theKeysOfAThreadWorkOnEachOfItsComments() {
        Anchor onComment = new Anchor.Comment(new ReviewThread.Comment.Id("R1"), (Anchor.Thread) onThread("T1"));

        assertEquals(
                new Target.Reply(new ReviewThread.Id("T1"), new ReviewThread.Comment.RestId("1")),
                Update.update(files(), new ReviewMsg.ReplyAsked(at(onComment)))
                        .model()
                        .popup(ReviewPopup.class)
                        .get()
                        .target());
        assertEquals(
                List.of(new ReviewCmd.Resolve(new PrNumber(7), new ReviewThread.Id("T1"), true)),
                Update.update(files(), new ReviewMsg.ResolveAsked(at(onComment)))
                        .cmds());
    }

    @Test
    void aReplyIsWrittenInAPopupUnderTheLastCommentAndSentFromIt() {
        Model asked = Update.update(files(), new ReviewMsg.ReplyAsked(at(onThread("T1"))))
                .model();
        ReviewPopup popup = asked.popup(ReviewPopup.class).get();
        assertEquals(new Target.Reply(new ReviewThread.Id("T1"), new ReviewThread.Comment.RestId("1")), popup.target());
        assertEquals(List.of("carol:", "Yes"), popup.quoted());

        Model typed = Update.update(asked, new ComposeMsg.Edited("Because")).model();
        Next sending = Update.update(typed, new ComposeMsg.Confirmed());

        ReviewCmd.Send send = new ReviewCmd.Send(
                new PrNumber(7),
                new Target.Reply(new ReviewThread.Id("T1"), new ReviewThread.Comment.RestId("1")),
                "Because");
        assertEquals(List.of(send), sending.cmds());
        assertTrue(sending.model().popup(ReviewPopup.class).get().sending());
        assertEquals(
                HashSet.of(new ReviewThread.Id("T1")), view(sending.model()).sending());

        Next sent = Update.update(sending.model(), new ReviewMsg.Sent(send, List.empty()));
        assertEquals(Option.none(), sent.model().popup(ReviewPopup.class));
        assertTrue(sent.cmds()
                .contains(new CommitFilesCmd.LoadReviewRemarks(
                        new PrNumber(7), HashSet.of(new ReviewThread.Id("T1")), 0)));
    }

    @Test
    void aReplyThatFailedStaysInItsPopup() {
        Model asked = Update.update(files(), new ReviewMsg.ReplyAsked(at(onThread("T1"))))
                .model();
        Model sending = Update.update(
                        Update.update(asked, new ComposeMsg.Edited("Because")).model(), new ComposeMsg.Confirmed())
                .model();

        Model failed = Update.update(
                        sending,
                        new ReviewMsg.SendFailed(
                                new ReviewCmd.Send(
                                        new PrNumber(7),
                                        new Target.Reply(
                                                new ReviewThread.Id("T1"), new ReviewThread.Comment.RestId("1")),
                                        "Because"),
                                new Failure.Bug(new RuntimeException("offline"))))
                .model();

        assertEquals("Because", failed.popup(ReviewPopup.class).get().text());
        assertFalse(failed.popup(ReviewPopup.class).get().sending());
        assertEquals(HashSet.empty(), view(failed).sending());
    }

    @Test
    void aThreadIsStartedOnARangeFromTheLineTheDiffShowsFirstAndTheCursorGoesToItsLastLine() {
        Model asked = Update.update(
                        files(), new ReviewMsg.StartAsked(at(line(Side.LEFT, 2), Option.of(line(Side.RIGHT, 3)))))
                .model();

        ReviewPopup popup = asked.popup(ReviewPopup.class).get();
        Target.Start start = new Target.Start(
                new Sha(HEAD), "a.txt", Option.of(new NewReviewThread.Lines(Side.LEFT, 2, Side.RIGHT, 3)));
        assertEquals(start, popup.target());
        assertEquals(List.of("-two", "+TWO", " three"), popup.quoted());
        assertEquals(Option.of(new ReviewPopup.Lines(2, 3)), popup.where().lines());

        Model sending = Update.update(
                        Update.update(asked, new ComposeMsg.Edited("Rename")).model(), new ComposeMsg.Confirmed())
                .model();
        ReviewCmd.Send send = new ReviewCmd.Send(new PrNumber(7), start, "Rename");
        Next sent = Update.update(sending, new ReviewMsg.Sent(send, List.empty()));

        assertEquals(Option.none(), sent.model().popup(ReviewPopup.class));
        assertEquals(
                new Scrolled(CONTENT, 3, 0, Option.of(line(Side.RIGHT, 3)), Option.none()),
                sent.model().scrolled(AREA, CONTENT));
    }

    @Test
    void aThreadOnTheBarOfAFileIsAboutTheWholeFile() {
        Model asked = Update.update(files(), new ReviewMsg.StartAsked(at(new Anchor.File("a.txt"))))
                .model();

        assertEquals(
                new Target.Start(new Sha(HEAD), "a.txt", Option.none()),
                asked.popup(ReviewPopup.class).get().target());
    }

    @Test
    void aThreadIsOnTheLinesOfOneHunk() {
        assertEquals(
                Option.of(new GitHubError.CannotReview(Why.NOT_ONE_HUNK)),
                error(Update.update(
                                files(),
                                new ReviewMsg.StartAsked(at(line(Side.RIGHT, 10), Option.of(line(Side.RIGHT, 2)))))
                        .model()));
        assertEquals(
                Option.of(new GitHubError.CannotReview(Why.NOT_ON_LINE)),
                error(Update.update(files(), new ReviewMsg.StartAsked(at(onThread("T1"))))
                        .model()));
    }

    @Test
    void aThreadIsStartedOnExpandedLinesButNotOnARangeAcrossAMarker() {
        // Shown: 1 to 5 and 7 to 13, git's hunks, and 20 and 21 expanded (ADR 77).
        Model expanded = expanded(files(), new Folding.Lines(20, 21));

        Model asked = Update.update(
                        expanded, new ReviewMsg.StartAsked(at(line(Side.RIGHT, 21), Option.of(line(Side.RIGHT, 20)))))
                .model();

        ReviewPopup popup = asked.popup(ReviewPopup.class).get();
        assertEquals(
                new Target.Start(
                        new Sha(HEAD), "a.txt", Option.of(new NewReviewThread.Lines(Side.RIGHT, 20, Side.RIGHT, 21))),
                popup.target());
        assertEquals(List.of(" line20", " line21"), popup.quoted());
        assertEquals(
                Option.of(new GitHubError.CannotReview(Why.NOT_ONE_HUNK)),
                error(Update.update(
                                expanded,
                                new ReviewMsg.StartAsked(at(line(Side.RIGHT, 13), Option.of(line(Side.RIGHT, 20)))))
                        .model()));
    }

    @Test
    void aThreadIsNotStartedInAVersionOfTheFile() {
        ReadVersion read = ReadVersion.of(
                AREA,
                CONTENT,
                new WholeFile.Source.OfFiles(new Changes.OfPullRequest(COMMITS), A),
                "a.txt",
                files().preferences().diffOptions());
        Version shown =
                new Version(CONTENT, "a.txt", Option.of(FileVersion.NEW), read.file(), new Loadable.Loaded<>(DIFF));
        Model inVersion =
                files().mapRepoView(view -> view.withVersions(view.versions().put(AREA, shown)));

        assertEquals(
                Option.of(new GitHubError.CannotReview(Why.IN_VERSION)),
                error(Update.update(inVersion, new ReviewMsg.StartAsked(at(line(Side.RIGHT, 10))))
                        .model()));
        assertEquals(
                Option.of(new GitHubError.CannotReview(Why.IN_VERSION)),
                error(Update.update(inVersion, new ReviewMsg.StartAsked(at(new Anchor.File("a.txt"))))
                        .model()));
    }

    @Test
    void editingTheFirstCommentOfAThreadOnAnExpandedLineQuotesItsLine() {
        ReviewThread onExpanded = new ReviewThread(
                "a.txt",
                Side.RIGHT,
                Option.of(20),
                Option.none(),
                Side.RIGHT,
                Option.of(20),
                false,
                false,
                ReviewThread.Subject.LINE,
                List.of(new Comment(
                        "bob",
                        Instant.EPOCH,
                        "Why?",
                        Option.none(),
                        new ReviewThread.Comment.Id("C3"),
                        new ReviewThread.Comment.RestId("3"),
                        CommentRights.ALL)),
                0,
                new ReviewThread.Id("T3"),
                ReviewThread.Rights.ALL);
        Model expanded =
                expanded(files(PullRequestDetails.State.OPEN, HEAD, List.of(onExpanded)), new Folding.Lines(20, 21));

        Model asked = Update.update(
                        expanded,
                        new ReviewMsg.EditAsked(
                                at(new Anchor.Thread(new ReviewThread.Id("T3"), Option.of(line(Side.RIGHT, 20))))))
                .model();

        assertEquals(List.of(" line20"), asked.popup(ReviewPopup.class).get().quoted());
    }

    @Test
    void aBlankCommentIsNotSent() {
        Model asked = Update.update(files(), new ReviewMsg.StartAsked(at(line(Side.RIGHT, 2))))
                .model();

        assertEquals(
                List.empty(), Update.update(asked, new ComposeMsg.Confirmed()).cmds());
    }

    @Test
    void anEditIsWrittenInThePopupFromTheBodyOfTheCommentAndTakesTheBodyGitHubHolds() {
        Model asked = Update.update(files(), new ReviewMsg.EditAsked(at(onComment("R1", "T1"))))
                .model();
        ReviewPopup popup = asked.popup(ReviewPopup.class).get();
        Target.Edit edit = new Target.Edit(new ReviewThread.Id("T1"), new ReviewThread.Comment.Id("R1"), "Yes");
        assertEquals(edit, popup.target());
        assertEquals("Yes", popup.text());
        // Under the comment before it, on the lines of its thread.
        assertEquals(List.of("bob:", "Why?", "Really?"), popup.quoted());
        assertEquals(Option.of(new ReviewPopup.Lines(2, 2)), popup.where().lines());
        // Not changed yet, it does not stop a quit (ADR 65).
        assertFalse(popup.holdsInput());

        Model typed = Update.update(asked, new ComposeMsg.Edited("Yes, see #3")).model();
        assertTrue(typed.popup(ReviewPopup.class).get().holdsInput());
        Next saving = Update.update(typed, new ComposeMsg.Confirmed());
        ReviewCmd.Send send = new ReviewCmd.Send(new PrNumber(7), edit, "Yes, see #3");
        assertEquals(List.of(send), saving.cmds());
        assertTrue(saving.model().popup(ReviewPopup.class).get().sending());
        assertEquals(HashSet.empty(), view(saving.model()).sending());

        Next saved = Update.update(saving.model(), new ReviewMsg.CommentEdited(send, "Yes, see #3.", List.empty()));
        assertEquals(Option.none(), saved.model().popup(ReviewPopup.class));
        assertFalse(saved.cmds().exists(cmd -> cmd instanceof CommitFilesCmd.LoadReviewRemarks));
        assertEquals(
                List.of("Why?\nReally?", "Yes, see #3."),
                view(saved.model()).threads().head().comments().map(Comment::body));
        assertEquals(1, view(saved.model()).version());
    }

    @Test
    void aCommentWrittenOnGitHubsSiteIsEditedWithItsLinesEndedAsTheTextAreaEndsThem() {
        ReviewThread written = new ReviewThread(
                OPEN.path(),
                OPEN.side(),
                OPEN.line(),
                OPEN.startLine(),
                OPEN.startSide(),
                OPEN.originalLine(),
                OPEN.resolved(),
                OPEN.outdated(),
                OPEN.subject(),
                OPEN.comments().map(comment -> comment.withBody("a\r\nb")),
                OPEN.more(),
                OPEN.id(),
                OPEN.rights());
        Model asked = Update.update(
                        files(PullRequestDetails.State.OPEN, HEAD, List.of(written, SETTLED)),
                        new ReviewMsg.EditAsked(at(onComment("R1", "T1"))))
                .model();

        ReviewPopup popup = asked.popup(ReviewPopup.class).get();
        assertEquals("a\nb", popup.text());
        assertEquals(
                new Target.Edit(new ReviewThread.Id("T1"), new ReviewThread.Comment.Id("R1"), "a\nb"), popup.target());
        assertFalse(popup.holdsInput());
        assertEquals(
                List.empty(), Update.update(asked, new ComposeMsg.Confirmed()).cmds());
    }

    @Test
    void theFirstCommentOfAThreadIsEditedFromItsStopUnderTheLinesOfTheThread() {
        ReviewPopup popup = Update.update(files(), new ReviewMsg.EditAsked(at(onThread("T1"))))
                .model()
                .popup(ReviewPopup.class)
                .get();

        assertEquals(
                new Target.Edit(new ReviewThread.Id("T1"), new ReviewThread.Comment.Id("C1"), "Why?\nReally?"),
                popup.target());
        assertEquals(List.of("+TWO"), popup.quoted());
    }

    @Test
    void anEditThatChangedNothingSendsNothingAndOneThatEmptiesTheCommentSaysToDeleteIt() {
        Model asked = Update.update(files(), new ReviewMsg.EditAsked(at(onComment("R1", "T1"))))
                .model();

        Next unchanged = Update.update(asked, new ComposeMsg.Confirmed());
        assertEquals(List.empty(), unchanged.cmds());
        assertEquals(Option.none(), unchanged.model().popup(ReviewPopup.class));

        Next emptied = Update.update(
                Update.update(asked, new ComposeMsg.Edited("  \n")).model(), new ComposeMsg.Confirmed());
        assertEquals(List.empty(), emptied.cmds());
        assertFalse(emptied.model().popup(ReviewPopup.class).get().sending());
        assertEquals(Option.of(new GitHubError.CannotReview(Why.EMPTY)), error(emptied.model()));

        // A change of whitespace only is a change.
        assertEquals(
                List.of(new ReviewCmd.Send(
                        new PrNumber(7),
                        new Target.Edit(new ReviewThread.Id("T1"), new ReviewThread.Comment.Id("R1"), "Yes"),
                        "Yes ")),
                Update.update(
                                Update.update(asked, new ComposeMsg.Edited("Yes "))
                                        .model(),
                                new ComposeMsg.Confirmed())
                        .cmds());
    }

    @Test
    void aCommentIsEditedWithTheRightToAndAsADeleteIsWhateverTheCommitsItWasReadAt() {
        assertEquals(
                Option.of(new GitHubError.CannotReview(Why.NO_EDIT)),
                error(Update.update(
                                withRights(files(), new CommentRights(false, true, true)),
                                new ReviewMsg.EditAsked(at(onComment("R1", "T1"))))
                        .model()));
        assertEquals(
                Option.of(new GitHubError.CannotReview(Why.COLLAPSED)),
                error(Update.update(files(), new ReviewMsg.EditAsked(at(onThread("T2"))))
                        .model()));
        Model resolving = Update.update(files(), new ReviewMsg.ResolveAsked(at(onThread("T1"))))
                .model();
        assertEquals(
                Option.of(new GitHubError.CannotReview(Why.SENDING)),
                error(Update.update(resolving, new ReviewMsg.EditAsked(at(onComment("R1", "T1"))))
                        .model()));
        Model deleting = deleting(files(), onComment("R1", "T1")).model();
        assertEquals(
                Option.of(new GitHubError.CannotReview(Why.DELETING)),
                error(Update.update(deleting, new ReviewMsg.EditAsked(at(onComment("R1", "T1"))))
                        .model()));

        Model movedOn = files(PullRequestDetails.State.CLOSED, "c".repeat(40));
        assertTrue(Update.update(movedOn, new ReviewMsg.EditAsked(at(onComment("R1", "T1"))))
                .model()
                .popup(ReviewPopup.class)
                .isDefined());
    }

    @Test
    void anEditThatFailedStaysInItsPopupAndLeavesTheComment() {
        Model asked = Update.update(files(), new ReviewMsg.EditAsked(at(onComment("R1", "T1"))))
                .model();
        Model saving = Update.update(
                        Update.update(asked, new ComposeMsg.Edited("Yes!")).model(), new ComposeMsg.Confirmed())
                .model();

        Model failed = Update.update(
                        saving,
                        new ReviewMsg.SendFailed(
                                new ReviewCmd.Send(
                                        new PrNumber(7),
                                        new Target.Edit(
                                                new ReviewThread.Id("T1"), new ReviewThread.Comment.Id("R1"), "Yes"),
                                        "Yes!"),
                                new Failure.Bug(new RuntimeException("offline"))))
                .model();

        assertEquals("Yes!", failed.popup(ReviewPopup.class).get().text());
        assertFalse(failed.popup(ReviewPopup.class).get().sending());
        assertEquals("Yes", view(failed).threads().head().comments().last().body());
        assertEquals(0, view(failed).version());
    }

    @Test
    void aReadStartedBeforeAnEditIsDroppedAndReadAgain() {
        Model saving = Update.update(
                        Update.update(
                                        Update.update(files(), new ReviewMsg.EditAsked(at(onComment("R1", "T1"))))
                                                .model(),
                                        new ComposeMsg.Edited("Yes!"))
                                .model(),
                        new ComposeMsg.Confirmed())
                .model();
        Model saved = Update.update(
                        saving,
                        new ReviewMsg.CommentEdited(
                                new ReviewCmd.Send(
                                        new PrNumber(7),
                                        new Target.Edit(
                                                new ReviewThread.Id("T1"), new ReviewThread.Comment.Id("R1"), "Yes"),
                                        "Yes!"),
                                "Yes!",
                                List.empty()))
                .model();

        Next stale =
                Update.update(saved, new CommitFilesMsg.RemarksLoaded(new PrNumber(7), remarks(), HashSet.empty(), 0));

        assertEquals(List.of(new CommitFilesCmd.LoadReviewRemarks(new PrNumber(7), HashSet.empty(), 1)), stale.cmds());
        assertEquals(
                "Yes!", view(stale.model()).threads().head().comments().last().body());
    }

    @Test
    void nothingIsCommentedOnEditedNorDeletedUntilTheThreadsAreRead() {
        Model reading = Update.update(
                        loaded(), new CommitFilesMsg.Opened(new Changes.OfPullRequest(COMMITS), Scopes.BRANCHES.pane()))
                .model();
        Model failed = Update.update(
                        reading,
                        new CommitFilesMsg.RemarksFailed(
                                new PrNumber(7), new Failure.Bug(new RuntimeException("offline")), HashSet.empty(), 0))
                .model();
        List<Msg> asked = List.of(
                new ReviewMsg.StartAsked(at(line(Side.RIGHT, 2))),
                new ReviewMsg.ReplyAsked(at(onThread("T1"))),
                new ReviewMsg.EditAsked(at(onComment("R1", "T1"))),
                new DeleteCommentMsg.DeleteAsked(at(onComment("R1", "T1"))));

        for (Model model : List.of(reading, failed)) {
            for (Msg msg : asked) {
                Next refused = Update.update(model, msg);
                assertEquals(List.empty(), refused.cmds());
                assertEquals(Option.of(new GitHubError.CannotReview(Why.NOT_READ)), error(refused.model()));
            }
        }
    }

    @Test
    void aThreadIsRepliedToOnlyWithTheRightTo() {
        Model locked = files(
                PullRequestDetails.State.OPEN,
                HEAD,
                List.of(thread("T1", false, new ReviewThread.Rights(false, true, true))));

        Next refused = Update.update(locked, new ReviewMsg.ReplyAsked(at(onThread("T1"))));

        assertTrue(refused.model().popup(ReviewPopup.class).isEmpty());
        assertEquals(Option.of(new GitHubError.CannotReview(Why.NO_REPLY)), error(refused.model()));
    }

    @Test
    void theThreadsOfAPullRequestNotListedAreReadAgainAtTheFirstVersion() {
        Model deleted = Update.update(
                        deleting(files(), onComment("R1", "T1")).model(),
                        new DeleteCommentMsg.CommentDeleted(
                                new DeleteCommentCmd.DeleteComment(
                                        new PrNumber(7), new ReviewThread.Id("T1"), new ReviewThread.Comment.Id("R1")),
                                List.empty()))
                .model();
        assertEquals(1, view(deleted).version());

        Next resolved = Update.update(
                deleted,
                new ReviewMsg.Resolved(
                        new ReviewCmd.Resolve(new PrNumber(8), new ReviewThread.Id("T9"), true), List.empty()));

        assertTrue(resolved.cmds()
                .contains(new CommitFilesCmd.LoadReviewRemarks(
                        new PrNumber(8), HashSet.of(new ReviewThread.Id("T9")), 0)));
    }

    @Test
    void aDeleteOrAReactionConfirmedForAnotherPullRequestIsNotSent() {
        Model deleteOther = files().openPopup(new DeleteCommentPopup(
                new PrNumber(8), new ReviewThread.Id("T1"), OPEN.comments().last(), Instant.EPOCH));
        Next deleted = Update.update(deleteOther, new DeleteCommentMsg.DeleteConfirmed());
        assertEquals(List.empty(), deleted.cmds());
        assertTrue(deleted.model().popup(DeleteCommentPopup.class).isEmpty());
        assertEquals(HashSet.empty(), view(deleted.model()).deleting());
        assertEquals(Option.of(new GitHubError.CannotReview(Why.NOT_READ)), error(deleted.model()));

        Model reactOther = files().openPopup(new ReactionPopup(
                new PrNumber(8), new ReviewThread.Id("T1"), new ReviewThread.Comment.Id("R1"), Reactions.NONE));
        Next reacted = Update.update(reactOther, new ReactMsg.ReactionChosen(Reaction.EYES));
        assertEquals(List.empty(), reacted.cmds());
        assertTrue(reacted.model().popup(ReactionPopup.class).isEmpty());
        assertEquals(HashSet.empty(), view(reacted.model()).reacting());
        assertEquals(Option.of(new GitHubError.CannotReview(Why.NOT_READ)), error(reacted.model()));

        // Without its popup open, confirming does nothing.
        assertEquals(
                List.empty(),
                Update.update(files(), new DeleteCommentMsg.DeleteConfirmed()).cmds());
        assertEquals(
                List.empty(),
                Update.update(files(), new ReactMsg.ReactionChosen(Reaction.EYES))
                        .cmds());
    }

    @Test
    void whileItsCommentIsSentThePopupKeepsItAndTakesNoKey() {
        Model asked = Update.update(files(), new ReviewMsg.ReplyAsked(at(onThread("T1"))))
                .model();
        Model sending = Update.update(
                        Update.update(asked, new ComposeMsg.Edited("Because")).model(), new ComposeMsg.Confirmed())
                .model();

        Model typed =
                Update.update(sending, new ComposeMsg.Edited("Because not")).model();
        assertEquals("Because", typed.popup(ReviewPopup.class).get().text());
        assertEquals(
                List.empty(), Update.update(sending, new ComposeMsg.Confirmed()).cmds());
        assertTrue(Update.update(sending, new ComposeMsg.Cancelled())
                .model()
                .popup(ReviewPopup.class)
                .isDefined());
        assertEquals(
                List.empty(),
                Update.update(sending, new ComposeMsg.EditorRequested()).cmds());
    }

    @Test
    void aCommentIsWrittenInTheEditorTooUnlessItsPopupWasCancelledMeanwhile() {
        Model typed = Update.update(
                        Update.update(files(), new ReviewMsg.ReplyAsked(at(onThread("T1"))))
                                .model(),
                        new ComposeMsg.Edited("Bec"))
                .model();

        Next editing = Update.update(typed, new ComposeMsg.EditorRequested());
        assertEquals(List.of(new OpenInEditor(ReviewCmd.EDITOR, "Bec")), editing.cmds());
        assertEquals(new EditorFile("PR_REVIEW_COMMENT.md", ReviewCmd.EDIT_COMMENT), ReviewCmd.EDITOR);

        Model written = Update.update(
                        editing.model(), new ComposeMsg.EditorClosed(ReviewCmd.EDITOR, "Because", List.empty()))
                .model();
        assertEquals("Because", written.popup(ReviewPopup.class).get().text());

        Model cancelled =
                Update.update(editing.model(), new ComposeMsg.Cancelled()).model();
        assertTrue(cancelled.popup(ReviewPopup.class).isEmpty());
        assertTrue(Update.update(cancelled, new ComposeMsg.EditorClosed(ReviewCmd.EDITOR, "Because", List.empty()))
                .model()
                .popup(ReviewPopup.class)
                .isEmpty());
    }
}
