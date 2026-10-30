package net.vanfleteren.jazygit.feature.github.review;

import static net.vanfleteren.jazygit.state.TestModels.loaded;
import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.feature.diff.CommitFilesCmd;
import net.vanfleteren.jazygit.feature.diff.CommitFilesMsg;
import net.vanfleteren.jazygit.feature.github.review.ReviewPopup.Target;
import net.vanfleteren.jazygit.gh.model.GhCommit;
import net.vanfleteren.jazygit.gh.model.NewReviewThread;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails;
import net.vanfleteren.jazygit.gh.model.ReviewRemarks;
import net.vanfleteren.jazygit.gh.model.ReviewThread;
import net.vanfleteren.jazygit.gh.model.ReviewThread.Comment;
import net.vanfleteren.jazygit.gh.model.ReviewThread.Side;
import net.vanfleteren.jazygit.git.model.ChangeType;
import net.vanfleteren.jazygit.git.model.CommitFile;
import net.vanfleteren.jazygit.git.model.DiffText;
import net.vanfleteren.jazygit.git.model.PullRequestCommits;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.keys.Scopes;
import net.vanfleteren.jazygit.state.*;
import net.vanfleteren.jazygit.state.AppError.CannotReview.Why;
import net.vanfleteren.jazygit.state.CommitFilesView.Changes;
import net.vanfleteren.jazygit.state.Update.Next;
import org.junit.jupiter.api.Test;

/**
 * Commenting on the lines of the diff of a pull request's files from its cursor (ADR 60).
 */
class ReviewUpdateTest {

    private static final String HEAD = "a".repeat(40);
    private static final String BASE = "b".repeat(40);
    private static final PullRequestCommits COMMITS =
            new PullRequestCommits(7, "owner/repo", "", new Sha(BASE), new Sha(HEAD));
    private static final CommitFile A = new CommitFile("a.txt", ChangeType.MODIFIED);
    private static final String AREA = "commit-file-changes";
    // Line 2 of a.txt changed, in a first hunk; line 10 in a second.
    private static final DiffText DIFF = DiffText.of("""
            diff --git a/a.txt b/a.txt
            --- a/a.txt
            +++ b/a.txt
            @@ -1,3 +1,3 @@
             one
            -two
            +TWO
             three
            @@ -10,1 +10,1 @@
            -ten
            +TEN
            """);
    private static final ScrolledContent CONTENT = new ScrolledContent.FileChanges(new Sha(HEAD), List.of(A), DIFF);

    private static final ReviewThread OPEN = thread("T1", false, ReviewThread.Rights.ALL);
    private static final ReviewThread SETTLED = thread("T2", true, new ReviewThread.Rights(true, true, false));

    private static ReviewThread thread(String id, boolean resolved, ReviewThread.Rights rights) {
        return new ReviewThread(
                "a.txt",
                Side.RIGHT,
                Option.of(2),
                Option.none(),
                Side.RIGHT,
                Option.of(2),
                resolved,
                false,
                ReviewThread.Subject.LINE,
                List.of(new Comment("bob", Instant.EPOCH, "Why?\nReally?", Option.none(), "C", "1")),
                0,
                id,
                rights);
    }

    private static Anchor.OnLine line(Side side, int number) {
        return new Anchor.OnLine("a.txt", side, number);
    }

    private static Model files(PullRequestDetails.State state, String head) {
        Model opened = Update.update(
                        loaded(), new CommitFilesMsg.Opened(new Changes.OfPullRequest(COMMITS), Scopes.BRANCHES.id()))
                .model();
        Model read = Update.update(
                        opened,
                        new CommitFilesMsg.RemarksLoaded(
                                7,
                                new ReviewRemarks(
                                        state, GhCommit.parse(head), GhCommit.parse(BASE), List.of(OPEN, SETTLED))))
                .model();
        return read.mapRepoView(repoView -> repoView.withCommitFiles(repoView.commitFiles()
                .map(view -> view.withDiff(Option.of(new Preview<>(List.of(A), new Loadable.Loaded<>(DIFF)))))));
    }

    private static Model files() {
        return files(PullRequestDetails.State.OPEN, HEAD);
    }

    private static DiffCursorAt at(Anchor cursor, Option<Anchor> mark) {
        return new DiffCursorAt(
                AREA,
                new Scrolled(CONTENT, 3, 0, Option.of(cursor), mark),
                cursor,
                Option.none(),
                Option.none(),
                Option.none());
    }

    private static DiffCursorAt at(Anchor cursor) {
        return at(cursor, Option.none());
    }

    private static Anchor onThread(String id) {
        return new Anchor.Thread(id, Option.of(line(Side.RIGHT, 2)));
    }

    private static CommitFilesView view(Model model) {
        return model.repoView().commitFiles().get();
    }

    private static Option<AppError> error(Model model) {
        return model.outcome().error();
    }

    @Test
    void aThreadIsResolvedAtOnceAndTakesNoSecondKeyUntilItIsReadAgain() {
        Next resolving = Update.update(files(), new ReviewMsg.ResolveAsked(at(onThread("T1"))));

        ReviewCmd.Resolve resolve = new ReviewCmd.Resolve(7, "T1", true);
        assertEquals(List.of(resolve), resolving.cmds());
        assertEquals(HashSet.of("T1"), view(resolving.model()).sending());

        Next again = Update.update(resolving.model(), new ReviewMsg.ResolveAsked(at(onThread("T1"))));
        assertEquals(List.empty(), again.cmds());
        assertEquals(Option.of(new AppError.CannotReview(Why.SENDING)), error(again.model()));
        assertEquals(
                Option.of(new AppError.CannotReview(Why.SENDING)),
                error(Update.update(resolving.model(), new ReviewMsg.ThreadToggled(at(onThread("T1"))))
                        .model()));

        Next resolved = Update.update(resolving.model(), new ReviewMsg.Resolved(resolve, List.empty()));
        assertTrue(resolved.cmds().contains(new CommitFilesCmd.LoadReviewRemarks(7)));
        assertEquals(HashSet.of("T1"), view(resolved.model()).sending());

        Model read = Update.update(
                        resolved.model(),
                        new CommitFilesMsg.RemarksLoaded(
                                7,
                                new ReviewRemarks(
                                        PullRequestDetails.State.OPEN,
                                        GhCommit.parse(HEAD),
                                        GhCommit.parse(BASE),
                                        List.of(OPEN, SETTLED))))
                .model();
        assertEquals(HashSet.empty(), view(read).sending());
    }

    @Test
    void aResolveThatFailedCanBeTriedAgain() {
        Next resolving = Update.update(files(), new ReviewMsg.ResolveAsked(at(onThread("T1"))));

        Model failed = Update.update(
                        resolving.model(),
                        new ReviewMsg.ResolveFailed(
                                new ReviewCmd.Resolve(7, "T1", true), new Failure.Bug(new RuntimeException("offline"))))
                .model();

        assertEquals(HashSet.empty(), view(failed).sending());
    }

    @Test
    void aResolvedThreadIsUnresolvedOnlyWithTheRightTo() {
        Next refused = Update.update(files(), new ReviewMsg.ResolveAsked(at(onThread("T2"))));

        assertEquals(List.empty(), refused.cmds());
        assertEquals(Option.of(new AppError.CannotReview(Why.NO_UNRESOLVE)), error(refused.model()));
    }

    @Test
    void theKeysOfAThreadNeedTheCursorOnOne() {
        assertEquals(
                Option.of(new AppError.CannotReview(Why.NOT_ON_THREAD)),
                error(Update.update(files(), new ReviewMsg.ReplyAsked(at(line(Side.RIGHT, 2))))
                        .model()));
    }

    @Test
    void nothingIsSentForAPullRequestThatMovedOnOrIsMerged() {
        assertEquals(
                Option.of(new AppError.CannotReview(Why.MOVED_ON)),
                error(Update.update(
                                files(PullRequestDetails.State.CLOSED, "c".repeat(40)),
                                new ReviewMsg.ResolveAsked(at(onThread("T1"))))
                        .model()));
        assertEquals(
                Option.of(new AppError.CannotReview(Why.MERGED)),
                error(Update.update(
                                files(PullRequestDetails.State.MERGED, HEAD),
                                new ReviewMsg.StartAsked(at(line(Side.RIGHT, 2))))
                        .model()));
    }

    @Test
    void oneThreadIsOpenedAndClosedAgainstWhatTIsForAllUntilTIsPressed() {
        Model opened = Update.update(files(), new ReviewMsg.ThreadToggled(at(onThread("T2"))))
                .model();
        assertEquals(HashSet.of("T2"), view(opened).toggled());

        Model closed = Update.update(opened, new ReviewMsg.ThreadToggled(at(onThread("T2"))))
                .model();
        assertEquals(HashSet.empty(), view(closed).toggled());

        Model all = Update.update(opened, new CommitFilesMsg.RemarksToggled()).model();
        assertEquals(HashSet.empty(), view(all).toggled());
        assertTrue(view(all).opened());
    }

    @Test
    void aReplyIsWrittenInAPopupUnderTheLastCommentAndSentFromIt() {
        Model asked = Update.update(files(), new ReviewMsg.ReplyAsked(at(onThread("T1"))))
                .model();
        ReviewPopup popup = asked.popup(ReviewPopup.class).get();
        assertEquals(new Target.Reply("T1", "1"), popup.target());
        assertEquals(List.of("bob:", "Why?", "Really?"), popup.quoted());

        Model typed = Update.update(asked, new ReviewMsg.Edited("Because")).model();
        Next sending = Update.update(typed, new ReviewMsg.Confirmed());

        ReviewCmd.Send send = new ReviewCmd.Send(7, new Target.Reply("T1", "1"), "Because");
        assertEquals(List.of(send), sending.cmds());
        assertTrue(sending.model().popup(ReviewPopup.class).get().sending());
        assertEquals(HashSet.of("T1"), view(sending.model()).sending());

        Next sent = Update.update(sending.model(), new ReviewMsg.Sent(send, List.empty()));
        assertEquals(Option.none(), sent.model().popup(ReviewPopup.class));
        assertTrue(sent.cmds().contains(new CommitFilesCmd.LoadReviewRemarks(7)));
    }

    @Test
    void aReplyThatFailedStaysInItsPopup() {
        Model asked = Update.update(files(), new ReviewMsg.ReplyAsked(at(onThread("T1"))))
                .model();
        Model sending = Update.update(
                        Update.update(asked, new ReviewMsg.Edited("Because")).model(), new ReviewMsg.Confirmed())
                .model();

        Model failed = Update.update(
                        sending,
                        new ReviewMsg.SendFailed(
                                new ReviewCmd.Send(7, new Target.Reply("T1", "1"), "Because"),
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
        Target.Start start =
                new Target.Start(HEAD, "a.txt", Option.of(new NewReviewThread.Lines(Side.LEFT, 2, Side.RIGHT, 3)));
        assertEquals(start, popup.target());
        assertEquals(List.of("-two", "+TWO", " three"), popup.quoted());
        assertEquals(Option.of(new ReviewPopup.Lines(2, 3)), popup.where().lines());

        Model sending = Update.update(
                        Update.update(asked, new ReviewMsg.Edited("Rename")).model(), new ReviewMsg.Confirmed())
                .model();
        ReviewCmd.Send send = new ReviewCmd.Send(7, start, "Rename");
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
                new Target.Start(HEAD, "a.txt", Option.none()),
                asked.popup(ReviewPopup.class).get().target());
    }

    @Test
    void aThreadIsOnTheLinesOfOneHunk() {
        assertEquals(
                Option.of(new AppError.CannotReview(Why.NOT_ONE_HUNK)),
                error(Update.update(
                                files(),
                                new ReviewMsg.StartAsked(at(line(Side.RIGHT, 10), Option.of(line(Side.RIGHT, 2)))))
                        .model()));
        assertEquals(
                Option.of(new AppError.CannotReview(Why.NOT_ON_LINE)),
                error(Update.update(files(), new ReviewMsg.StartAsked(at(onThread("T1"))))
                        .model()));
    }

    @Test
    void aBlankCommentIsNotSent() {
        Model asked = Update.update(files(), new ReviewMsg.StartAsked(at(line(Side.RIGHT, 2))))
                .model();

        assertEquals(
                List.empty(), Update.update(asked, new ReviewMsg.Confirmed()).cmds());
    }
}
