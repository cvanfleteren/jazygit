package net.vanfleteren.jazygit.feature.github.review;

import static net.vanfleteren.jazygit.feature.github.review.ReviewFixtures.*;
import static net.vanfleteren.jazygit.state.TestModels.loaded;
import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.HashMap;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.diff.CommitFilesCmd;
import net.vanfleteren.jazygit.feature.diff.CommitFilesMsg;
import net.vanfleteren.jazygit.feature.github.browse.BrowseRemarkCmd;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails;
import net.vanfleteren.jazygit.gh.model.PullRequestLink;
import net.vanfleteren.jazygit.gh.model.ReviewThread;
import net.vanfleteren.jazygit.gh.model.ReviewThread.Side;
import net.vanfleteren.jazygit.keys.Scopes;
import net.vanfleteren.jazygit.state.*;
import net.vanfleteren.jazygit.state.Update.Next;
import net.vanfleteren.jazygit.state.diff.*;
import org.junit.jupiter.api.Test;

/**
 * Collapsing a review thread or a comment of the diff of a pull request's files, or opening it again (ADR 62, ADR 63), and opening a comment on GitHub (ADR 85).
 */
class ThreadUpdateTest {

    @Test
    void theBrowserOpensOnTheCommentOfTheCursorOrElseOnThePullRequest() {
        assertEquals(
                List.of(new BrowseRemarkCmd(
                        new PullRequestLink("", "owner/repo", 7, Option.of(new ReviewThread.Comment.RestId("1"))))),
                Update.update(files(), new ThreadMsg.OpenAsked(at(onThread("T1"))))
                        .cmds());
        assertEquals(
                List.of(new BrowseRemarkCmd(
                        new PullRequestLink("", "owner/repo", 7, Option.of(new ReviewThread.Comment.RestId("2"))))),
                Update.update(
                                files(),
                                new ThreadMsg.OpenAsked(at(new Anchor.Comment(
                                        new ReviewThread.Comment.Id("R1"), (Anchor.Thread) onThread("T1")))))
                        .cmds());
        assertEquals(
                List.of(new BrowseRemarkCmd(new PullRequestLink("", "owner/repo", 7, Option.none()))),
                Update.update(files(), new ThreadMsg.OpenAsked(at(line(Side.RIGHT, 2))))
                        .cmds());
    }

    @Test
    void theBrowserOpensWhateverThePullRequestIsAt() {
        Next opened = Update.update(
                files(PullRequestDetails.State.MERGED, "c".repeat(40)), new ThreadMsg.OpenAsked(at(onThread("T1"))));

        assertEquals(
                List.of(new BrowseRemarkCmd(
                        new PullRequestLink("", "owner/repo", 7, Option.of(new ReviewThread.Comment.RestId("1"))))),
                opened.cmds());
        assertEquals(Option.none(), error(opened.model()));
    }

    @Test
    void aThreadOrACommentIsCollapsedAndOpenedAgainstItsDefault() {
        Model opened = Update.update(files(), new ThreadMsg.ThreadToggled(at(onThread("T2"))))
                .model();
        assertEquals(
                new Collapsed(HashMap.of(new Collapsed.Key("T2"), true)),
                view(opened).collapsed());
        assertFalse(view(opened).collapsed().thread(SETTLED));

        Model closed = Update.update(opened, new ThreadMsg.ThreadToggled(at(onThread("T2"))))
                .model();
        assertEquals(Collapsed.NONE, view(closed).collapsed());

        // An unresolved thread is collapsed too, and a comment on its own, by its id (ADR 62).
        Model collapsed = Update.update(files(), new ThreadMsg.ThreadToggled(at(onThread("T1"))))
                .model();
        assertTrue(view(collapsed).collapsed().thread(OPEN));
        Model comment = Update.update(
                        files(),
                        new ThreadMsg.ThreadToggled(at(
                                new Anchor.Comment(new ReviewThread.Comment.Id("R1"), (Anchor.Thread) onThread("T1")))))
                .model();
        assertEquals(
                new Collapsed(HashMap.of(new Collapsed.Key("R1"), false)),
                view(comment).collapsed());
    }

    @Test
    void spaceOffAThreadDoesNothing() {
        Model files = files();
        assertEquals(
                files,
                Update.update(files, new ThreadMsg.ThreadToggled(at(line(Side.RIGHT, 2))))
                        .model());
        // A thread no longer read, as after the threads are read again, is off a thread too.
        assertEquals(
                files,
                Update.update(files, new ThreadMsg.ThreadToggled(at(onThread("GONE"))))
                        .model());
    }

    @Test
    void whatIsCollapsedOrOpenedIsKeptForTheNextSession() {
        Next opened = Update.update(files(), new ThreadMsg.ThreadToggled(at(onThread("T2"))));

        // With whether each was collapsed by default, and when it was flipped (ADR 63).
        assertEquals(
                List.of(new CommitFilesCmd.SaveCollapsed(
                        "owner/repo",
                        7,
                        HashMap.of(new Collapsed.Key("T2"), true),
                        opened.model().now())),
                opened.cmds());
        assertEquals(
                List.of(new CommitFilesCmd.SaveCollapsed(
                        "owner/repo", 7, HashMap.empty(), opened.model().now())),
                Update.update(opened.model(), new ThreadMsg.ThreadToggled(at(onThread("T2"))))
                        .cmds());
    }

    @Test
    void whatWasCollapsedOrOpenedInAnEarlierSessionIsAsItWasUnlessItsDefaultChangedSince() {
        // T2 opened while resolved, R1 collapsed, and T1 opened while resolved, which it is no longer.
        CommitFilesMsg.CollapsedLoaded kept = new CommitFilesMsg.CollapsedLoaded(
                7,
                HashMap.of(
                        new Collapsed.Key("T2"), true, new Collapsed.Key("R1"), false, new Collapsed.Key("T1"), true));
        Collapsed expected = new Collapsed(HashMap.of(new Collapsed.Key("T2"), true, new Collapsed.Key("R1"), false));

        Model read = Update.update(files(), kept).model();
        assertEquals(expected, view(read).collapsed());
        assertFalse(view(read).collapsed().thread(SETTLED));
        assertFalse(view(read).collapsed().thread(OPEN));

        // Kept before the threads are read, it is the same once they are.
        Model opened = Update.update(
                        loaded(), new CommitFilesMsg.Opened(new Changes.OfPullRequest(COMMITS), Scopes.BRANCHES.pane()))
                .model();
        Model early = Update.update(opened, kept).model();
        assertEquals(
                expected,
                view(Update.update(early, readAgain(HashSet.empty(), OPEN, SETTLED))
                                .model())
                        .collapsed());

        // What was flipped in this session wins.
        Model collapsed = Update.update(files(), new ThreadMsg.ThreadToggled(at(onThread("T1"))))
                .model();
        assertTrue(view(Update.update(collapsed, kept).model()).collapsed().thread(OPEN));
    }
}
