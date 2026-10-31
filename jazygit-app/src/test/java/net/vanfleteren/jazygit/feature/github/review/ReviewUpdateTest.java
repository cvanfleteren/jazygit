package net.vanfleteren.jazygit.feature.github.review;

import static net.vanfleteren.jazygit.state.TestModels.loaded;
import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.HashMap;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.collection.Set;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.feature.diff.CommitFilesCmd;
import net.vanfleteren.jazygit.feature.diff.CommitFilesMsg;
import net.vanfleteren.jazygit.feature.github.review.ReviewPopup.Target;
import net.vanfleteren.jazygit.gh.model.CommentRights;
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
    private static final ScrolledContent CONTENT =
            new ScrolledContent.FileChanges(new CommitFilesView.Changes.OfPullRequest(COMMITS), List.of(A), DIFF);

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
                List.of(
                        new Comment(
                                "bob",
                                Instant.EPOCH,
                                "Why?\nReally?",
                                Option.none(),
                                "C" + id.substring(1),
                                "1",
                                CommentRights.ALL),
                        // A reply, R1 on T1.
                        new Comment(
                                "carol",
                                Instant.EPOCH,
                                "Yes",
                                Option.none(),
                                "R" + id.substring(1),
                                "2",
                                CommentRights.ALL)),
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
    void aThreadIsResolvedAtOnceAndTakesNoSecondKeyUntilItIsReadAgainAfterIt() {
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
        assertTrue(resolved.cmds().contains(new CommitFilesCmd.LoadReviewRemarks(7, HashSet.of("T1"))));
        assertEquals(HashSet.of("T1"), view(resolved.model()).sending());

        Model read = Update.update(resolved.model(), new CommitFilesMsg.RemarksLoaded(7, remarks(), HashSet.of("T1")))
                .model();
        assertEquals(HashSet.empty(), view(read).sending());
    }

    @Test
    void aReadStartedBeforeAResolveWasSentDoesNotLetTheThreadTakeASecondKey() {
        Model resolving = Update.update(files(), new ReviewMsg.ResolveAsked(at(onThread("T1"))))
                .model();
        Next refreshed = Update.update(resolving, new CommitFilesMsg.RemarksRefreshed());
        assertEquals(List.of(new CommitFilesCmd.LoadReviewRemarks(7)), refreshed.cmds());

        // The read asked with R comes back before GitHub resolved the thread: it still shows it open.
        Model stale = Update.update(refreshed.model(), new CommitFilesMsg.RemarksLoaded(7, remarks()))
                .model();
        assertEquals(HashSet.of("T1"), view(stale).sending());
        Next again = Update.update(stale, new ReviewMsg.ResolveAsked(at(onThread("T1"))));
        assertEquals(List.empty(), again.cmds());
        assertEquals(Option.of(new AppError.CannotReview(Why.SENDING)), error(again.model()));
    }

    @Test
    void aSentResolveIsDoneWithWhenTheReadAfterItFails() {
        Model resolved = Update.update(
                        Update.update(files(), new ReviewMsg.ResolveAsked(at(onThread("T1"))))
                                .model(),
                        new ReviewMsg.Resolved(new ReviewCmd.Resolve(7, "T1", true), List.empty()))
                .model();

        Model failed = Update.update(
                        resolved,
                        new CommitFilesMsg.RemarksFailed(
                                7, new Failure.Bug(new RuntimeException("offline")), HashSet.of("T1")))
                .model();

        assertEquals(HashSet.empty(), view(failed).sending());
    }

    private static ReviewRemarks remarks() {
        return new ReviewRemarks(
                PullRequestDetails.State.OPEN, GhCommit.parse(HEAD), GhCommit.parse(BASE), List.of(OPEN, SETTLED));
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
    void aThreadOrACommentIsCollapsedAndOpenedAgainstItsDefault() {
        Model opened = Update.update(files(), new ReviewMsg.ThreadToggled(at(onThread("T2"))))
                .model();
        assertEquals(new Collapsed(HashMap.of("T2", true)), view(opened).collapsed());
        assertFalse(view(opened).collapsed().thread(SETTLED));

        Model closed = Update.update(opened, new ReviewMsg.ThreadToggled(at(onThread("T2"))))
                .model();
        assertEquals(Collapsed.NONE, view(closed).collapsed());

        // An unresolved thread is collapsed too, and a comment on its own, by its id (ADR 62).
        Model collapsed = Update.update(files(), new ReviewMsg.ThreadToggled(at(onThread("T1"))))
                .model();
        assertTrue(view(collapsed).collapsed().thread(OPEN));
        Model comment = Update.update(files(), new ReviewMsg.ThreadToggled(at(new Anchor.Comment("R1", (Anchor.Thread)
                        onThread("T1")))))
                .model();
        assertEquals(new Collapsed(HashMap.of("R1", false)), view(comment).collapsed());
    }

    @Test
    void spaceOffAThreadDoesNothing() {
        Model files = files();
        assertEquals(
                files,
                Update.update(files, new ReviewMsg.ThreadToggled(at(line(Side.RIGHT, 2))))
                        .model());
        // A thread no longer read, as after the threads are read again, is off a thread too.
        assertEquals(
                files,
                Update.update(files, new ReviewMsg.ThreadToggled(at(onThread("GONE"))))
                        .model());
    }

    @Test
    void whatIsCollapsedOrOpenedIsKeptForTheNextSession() {
        Next opened = Update.update(files(), new ReviewMsg.ThreadToggled(at(onThread("T2"))));

        // With whether each was collapsed by default, and when it was flipped (ADR 63).
        assertEquals(
                List.of(new CommitFilesCmd.SaveCollapsed(
                        "owner/repo", 7, HashMap.of("T2", true), opened.model().now())),
                opened.cmds());
        assertEquals(
                List.of(new CommitFilesCmd.SaveCollapsed(
                        "owner/repo", 7, HashMap.empty(), opened.model().now())),
                Update.update(opened.model(), new ReviewMsg.ThreadToggled(at(onThread("T2"))))
                        .cmds());
    }

    @Test
    void whatWasCollapsedOrOpenedInAnEarlierSessionIsAsItWasUnlessItsDefaultChangedSince() {
        // T2 opened while resolved, R1 collapsed, and T1 opened while resolved, which it is no longer.
        CommitFilesMsg.CollapsedLoaded kept =
                new CommitFilesMsg.CollapsedLoaded(7, HashMap.of("T2", true, "R1", false, "T1", true));
        Collapsed expected = new Collapsed(HashMap.of("T2", true, "R1", false));

        Model read = Update.update(files(), kept).model();
        assertEquals(expected, view(read).collapsed());
        assertFalse(view(read).collapsed().thread(SETTLED));
        assertFalse(view(read).collapsed().thread(OPEN));

        // Kept before the threads are read, it is the same once they are.
        Model opened = Update.update(
                        loaded(), new CommitFilesMsg.Opened(new Changes.OfPullRequest(COMMITS), Scopes.BRANCHES.id()))
                .model();
        Model early = Update.update(opened, kept).model();
        assertEquals(
                expected,
                view(Update.update(early, readAgain(HashSet.empty(), OPEN, SETTLED))
                                .model())
                        .collapsed());

        // What was flipped in this session wins.
        Model collapsed = Update.update(files(), new ReviewMsg.ThreadToggled(at(onThread("T1"))))
                .model();
        assertTrue(view(Update.update(collapsed, kept).model()).collapsed().thread(OPEN));
    }

    private static CommitFilesMsg.RemarksLoaded readAgain(Set<String> settled, ReviewThread... threads) {
        return new CommitFilesMsg.RemarksLoaded(
                7,
                new ReviewRemarks(
                        PullRequestDetails.State.OPEN, GhCommit.parse(HEAD), GhCommit.parse(BASE), List.of(threads)),
                settled);
    }

    @Test
    void aThreadResolvedIsAsItWasReadUntilTheThreadsReadAgainShowItResolved() {
        Model toggled = Update.update(
                        Update.update(files(), new ReviewMsg.ThreadToggled(at(onThread("T1"))))
                                .model(),
                        new ReviewMsg.ThreadToggled(at(new Anchor.Comment("R1", (Anchor.Thread) onThread("T1")))))
                .model();
        Next resolving = Update.update(toggled, new ReviewMsg.ResolveAsked(at(onThread("T1"))));
        ReviewCmd.Resolve resolve = (ReviewCmd.Resolve) resolving.cmds().head();

        // Until the threads are read again, the thread is drawn as it was read: still collapsed (ADR 62).
        Model resolved = Update.update(resolving.model(), new ReviewMsg.Resolved(resolve, List.empty()))
                .model();
        assertEquals(
                new Collapsed(HashMap.of("T1", false, "R1", false)),
                view(resolved).collapsed());
        assertTrue(view(resolved).collapsed().thread(OPEN));

        // Read again resolved, it is as a resolved thread starts: collapsed, its flip dropped; its comments stay.
        ReviewThread done = thread("T1", true, ReviewThread.Rights.ALL);
        Model read = Update.update(resolved, readAgain(HashSet.of("T1"), done, SETTLED))
                .model();
        assertEquals(new Collapsed(HashMap.of("R1", false)), view(read).collapsed());
        assertTrue(view(read).collapsed().thread(done));

        // A read again that fails keeps the flip, as the thread is still drawn as it was read.
        Model failed = Update.update(
                        resolved,
                        new CommitFilesMsg.RemarksFailed(
                                7, new Failure.Bug(new RuntimeException("offline")), HashSet.of("T1")))
                .model();
        assertEquals(
                new Collapsed(HashMap.of("T1", false, "R1", false)),
                view(failed).collapsed());
        Model notSent = Update.update(
                        resolving.model(),
                        new ReviewMsg.ResolveFailed(resolve, new Failure.Bug(new RuntimeException("offline"))))
                .model();
        assertEquals(
                new Collapsed(HashMap.of("T1", false, "R1", false)),
                view(notSent).collapsed());
    }

    @Test
    void aThreadResolvedOrUnresolvedElsewhereIsAsItStartsOnceReadAgain() {
        Model opened = Update.update(files(), new ReviewMsg.ThreadToggled(at(onThread("T2"))))
                .model();
        assertEquals(new Collapsed(HashMap.of("T2", true)), view(opened).collapsed());

        // Read again as it was, the flip stays.
        assertEquals(
                new Collapsed(HashMap.of("T2", true)),
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
        Anchor onComment = new Anchor.Comment("R1", (Anchor.Thread) onThread("T1"));

        assertEquals(
                new Target.Reply("T1", "1"),
                Update.update(files(), new ReviewMsg.ReplyAsked(at(onComment)))
                        .model()
                        .popup(ReviewPopup.class)
                        .get()
                        .target());
        assertEquals(
                List.of(new ReviewCmd.Resolve(7, "T1", true)),
                Update.update(files(), new ReviewMsg.ResolveAsked(at(onComment)))
                        .cmds());
    }

    @Test
    void aReplyIsWrittenInAPopupUnderTheLastCommentAndSentFromIt() {
        Model asked = Update.update(files(), new ReviewMsg.ReplyAsked(at(onThread("T1"))))
                .model();
        ReviewPopup popup = asked.popup(ReviewPopup.class).get();
        assertEquals(new Target.Reply("T1", "1"), popup.target());
        assertEquals(List.of("carol:", "Yes"), popup.quoted());

        Model typed = Update.update(asked, new ReviewMsg.Edited("Because")).model();
        Next sending = Update.update(typed, new ReviewMsg.Confirmed());

        ReviewCmd.Send send = new ReviewCmd.Send(7, new Target.Reply("T1", "1"), "Because");
        assertEquals(List.of(send), sending.cmds());
        assertTrue(sending.model().popup(ReviewPopup.class).get().sending());
        assertEquals(HashSet.of("T1"), view(sending.model()).sending());

        Next sent = Update.update(sending.model(), new ReviewMsg.Sent(send, List.empty()));
        assertEquals(Option.none(), sent.model().popup(ReviewPopup.class));
        assertTrue(sent.cmds().contains(new CommitFilesCmd.LoadReviewRemarks(7, HashSet.of("T1"))));
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

    private static Anchor onComment(String comment, String thread) {
        return new Anchor.Comment(comment, (Anchor.Thread) onThread(thread));
    }

    private static List<String> comments(Model model, String thread) {
        return view(model)
                .threads()
                .find(each -> each.id().equals(thread))
                .map(each -> each.comments().map(Comment::id))
                .getOrElse(List.empty());
    }

    /**
     * {@code model} once the comment {@code comment} of {@code thread} was asked to be deleted, and that confirmed.
     */
    private static Next deleting(Model model, Anchor on) {
        Model asked = Update.update(model, new ReviewMsg.DeleteAsked(at(on))).model();
        return Update.update(asked, new ReviewMsg.DeleteConfirmed());
    }

    @Test
    void aCommentIsDeletedAfterAskingAndLeavesItsThreadWithoutReadingTheThreadsAgain() {
        Model asked = Update.update(files(), new ReviewMsg.DeleteAsked(at(onComment("R1", "T1"))))
                .model();
        DeleteCommentPopup popup = asked.popup(DeleteCommentPopup.class).get();
        assertEquals("R1", popup.comment().id());
        assertEquals("T1", popup.thread());

        Next confirmed = Update.update(asked, new ReviewMsg.DeleteConfirmed());
        ReviewCmd.DeleteComment delete = new ReviewCmd.DeleteComment(7, "T1", "R1");
        assertEquals(List.of(delete), confirmed.cmds());
        assertTrue(confirmed.model().popup(DeleteCommentPopup.class).isEmpty());
        assertEquals(HashSet.of("R1"), view(confirmed.model()).deleting());
        assertEquals(List.of("C1", "R1"), comments(confirmed.model(), "T1"));

        Next deleted = Update.update(confirmed.model(), new ReviewMsg.CommentDeleted(delete, List.empty()));
        assertFalse(deleted.cmds().exists(cmd -> cmd instanceof CommitFilesCmd.LoadReviewRemarks));
        assertEquals(HashSet.empty(), view(deleted.model()).deleting());
        assertEquals(List.of("C1"), comments(deleted.model(), "T1"));
        assertEquals(1, view(deleted.model()).version());
    }

    @Test
    void theStopOfAnOpenThreadIsItsFirstCommentAndOnceThatIsDeletedTheNextIsItsFirst() {
        Next confirmed = deleting(files(), onThread("T1"));
        ReviewCmd.DeleteComment delete = new ReviewCmd.DeleteComment(7, "T1", "C1");
        assertEquals(List.of(delete), confirmed.cmds());

        Model deleted = Update.update(confirmed.model(), new ReviewMsg.CommentDeleted(delete, List.empty()))
                .model();

        assertEquals(List.of("R1"), comments(deleted, "T1"));
        // A reply goes to the comment that is first now (ADR 64).
        Model replying = Update.update(deleted, new ReviewMsg.ReplyAsked(at(onThread("T1"))))
                .model();
        assertEquals(
                new Target.Reply("T1", "2"),
                replying.popup(ReviewPopup.class).get().target());
    }

    @Test
    void aThreadGoesWithItsLastComment() {
        Model once = Update.update(
                        deleting(files(), onComment("R1", "T1")).model(),
                        new ReviewMsg.CommentDeleted(new ReviewCmd.DeleteComment(7, "T1", "R1"), List.empty()))
                .model();
        Model twice = Update.update(
                        deleting(once, onThread("T1")).model(),
                        new ReviewMsg.CommentDeleted(new ReviewCmd.DeleteComment(7, "T1", "C1"), List.empty()))
                .model();

        assertEquals(List.of("T2"), view(twice).threads().map(ReviewThread::id));
        assertEquals(2, view(twice).version());
    }

    @Test
    void aCollapsedThreadOfSeveralCommentsDoesNotSayWhichToDelete() {
        Next refused = Update.update(files(), new ReviewMsg.DeleteAsked(at(onThread("T2"))));

        assertTrue(refused.model().popup(DeleteCommentPopup.class).isEmpty());
        assertEquals(Option.of(new AppError.CannotReview(Why.COLLAPSED)), error(refused.model()));
    }

    @Test
    void aCommentIsDeletedOnlyWithTheRightTo() {
        Model mine = files();
        Model theirs = mine.mapRepoView(repoView -> repoView.withCommitFiles(repoView.commitFiles()
                .map(view -> view.withRemarks(view.remarks()
                        .map(remarks -> remarks.map(read -> new ReviewRemarks(
                                read.state(),
                                read.head(),
                                read.base(),
                                read.threads()
                                        .map(thread -> new ReviewThread(
                                                thread.path(),
                                                thread.side(),
                                                thread.line(),
                                                thread.startLine(),
                                                thread.startSide(),
                                                thread.originalLine(),
                                                thread.resolved(),
                                                thread.outdated(),
                                                thread.subject(),
                                                thread.comments()
                                                        .map(comment -> new Comment(
                                                                comment.author(),
                                                                comment.at(),
                                                                comment.body(),
                                                                comment.hidden(),
                                                                comment.id(),
                                                                comment.restId(),
                                                                CommentRights.NONE)),
                                                thread.more(),
                                                thread.id(),
                                                thread.rights())))))))));

        Next refused = Update.update(theirs, new ReviewMsg.DeleteAsked(at(onComment("R1", "T1"))));

        assertTrue(refused.model().popup(DeleteCommentPopup.class).isEmpty());
        assertEquals(Option.of(new AppError.CannotReview(Why.NO_DELETE)), error(refused.model()));
    }

    @Test
    void whileItsFirstCommentIsDeletedAThreadTakesNoReplyResolveNorSpaceAndTheCommentNoSecondDelete() {
        Model deleting = deleting(files(), onThread("T1")).model();
        Option<AppError> beingDeleted = Option.of(new AppError.CannotReview(Why.DELETING));

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
                error(Update.update(deleting, new ReviewMsg.ThreadToggled(at(onThread("T1"))))
                        .model()));
        assertEquals(
                beingDeleted,
                error(Update.update(deleting, new ReviewMsg.DeleteAsked(at(onThread("T1"))))
                        .model()));
        // Its other comments are not being deleted.
        assertTrue(Update.update(deleting, new ReviewMsg.DeleteAsked(at(onComment("R1", "T1"))))
                .model()
                .popup(DeleteCommentPopup.class)
                .isDefined());
    }

    @Test
    void aCommentOfAThreadSomethingIsSentForIsNotDeleted() {
        Model resolving = Update.update(files(), new ReviewMsg.ResolveAsked(at(onThread("T1"))))
                .model();

        assertEquals(
                Option.of(new AppError.CannotReview(Why.SENDING)),
                error(Update.update(resolving, new ReviewMsg.DeleteAsked(at(onComment("R1", "T1"))))
                        .model()));
    }

    @Test
    void aCommentIsDeletedWhateverTheCommitsItWasReadAt() {
        Model movedOn = files(PullRequestDetails.State.CLOSED, "c".repeat(40));

        assertEquals(
                List.of(new ReviewCmd.DeleteComment(7, "T1", "R1")),
                deleting(movedOn, onComment("R1", "T1")).cmds());
    }

    @Test
    void aCommentGoneBeforeItsDeleteIsConfirmedIsNotDeleted() {
        Model asked = Update.update(files(), new ReviewMsg.DeleteAsked(at(onComment("R1", "T1"))))
                .model();
        ReviewRemarks without =
                view(asked).withoutComment("R1").remarks().get().toOption().get();
        Model read = Update.update(asked, new CommitFilesMsg.RemarksLoaded(7, without))
                .model();

        Next confirmed = Update.update(read, new ReviewMsg.DeleteConfirmed());

        assertEquals(List.empty(), confirmed.cmds());
        assertTrue(confirmed.model().popup(DeleteCommentPopup.class).isEmpty());
    }

    @Test
    void aDeleteThatFailedLeavesTheComment() {
        ReviewCmd.DeleteComment delete = new ReviewCmd.DeleteComment(7, "T1", "R1");
        Model failed = Update.update(
                        deleting(files(), onComment("R1", "T1")).model(),
                        new ReviewMsg.DeleteFailed(delete, new Failure.Bug(new RuntimeException("offline"))))
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
                new ReviewMsg.Resolved(new ReviewCmd.Resolve(7, "T1", true), List.empty()));
        assertTrue(resolved.cmds().contains(new CommitFilesCmd.LoadReviewRemarks(7, HashSet.of("T1"), 0)));
        Model deleted = Update.update(
                        deleting(resolved.model(), onComment("R2", "T2")).model(),
                        new ReviewMsg.CommentDeleted(new ReviewCmd.DeleteComment(7, "T2", "R2"), List.empty()))
                .model();

        // The read of before the delete comes back with the comment still in it.
        Next stale = Update.update(deleted, new CommitFilesMsg.RemarksLoaded(7, remarks(), HashSet.of("T1"), 0));

        assertEquals(List.of(new CommitFilesCmd.LoadReviewRemarks(7, HashSet.of("T1"), 1)), stale.cmds());
        assertEquals(List.of("C2"), comments(stale.model(), "T2"));
        assertEquals(HashSet.of("T1"), view(stale.model()).sending());

        ReviewRemarks after = view(deleted).remarks().get().toOption().get();
        Model read = Update.update(stale.model(), new CommitFilesMsg.RemarksLoaded(7, after, HashSet.of("T1"), 1))
                .model();
        assertEquals(HashSet.empty(), view(read).sending());
        assertEquals(List.of("C2"), comments(read, "T2"));
    }
}
