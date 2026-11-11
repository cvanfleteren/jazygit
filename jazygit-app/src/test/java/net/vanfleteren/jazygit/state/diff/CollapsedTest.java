package net.vanfleteren.jazygit.state.diff;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.gh.model.CommentRights;
import net.vanfleteren.jazygit.gh.model.ReviewThread;
import net.vanfleteren.jazygit.gh.model.ReviewThread.Comment;
import net.vanfleteren.jazygit.gh.model.ReviewThread.Side;
import net.vanfleteren.jazygit.gh.model.ReviewThread.Subject;
import org.junit.jupiter.api.Test;

/**
 * Which review threads and comments are collapsed (ADR 62, 63).
 */
class CollapsedTest {

    private static Comment comment(String id, Option<String> hidden) {
        return new Comment(
                "bob",
                Instant.EPOCH,
                id,
                hidden,
                new ReviewThread.Comment.Id(id),
                new ReviewThread.Comment.RestId("1"),
                CommentRights.ALL);
    }

    private static ReviewThread thread(boolean resolved, Comment... comments) {
        return new ReviewThread(
                "a.txt",
                Side.RIGHT,
                Option.of(1),
                Option.none(),
                Side.RIGHT,
                Option.of(1),
                resolved,
                false,
                Subject.LINE,
                List.of(comments),
                0,
                new ReviewThread.Id("T"),
                ReviewThread.Rights.ALL);
    }

    @Test
    void aResolvedThreadAThreadWhoseFirstCommentIsHiddenAndAHiddenCommentAreCollapsedByDefault() {
        Comment open = comment("A", Option.none());
        Comment hidden = comment("H", Option.of("SPAM"));

        assertTrue(Collapsed.NONE.thread(thread(true, open)));
        assertTrue(Collapsed.NONE.thread(thread(false, hidden, open)));
        assertFalse(Collapsed.NONE.thread(thread(false, open, hidden)));
        assertTrue(Collapsed.NONE.comment(hidden));
        assertFalse(Collapsed.NONE.comment(open));
        ReviewThread resolved = thread(true, open);
        assertFalse(Collapsed.NONE
                .flipped(new Collapsed.Key("T"), List.of(resolved))
                .thread(resolved));
        ReviewThread replied = thread(false, hidden, open);
        assertTrue(
                Collapsed.NONE.flipped(new Collapsed.Key("A"), List.of(replied)).comment(open));
        assertEquals(
                Collapsed.NONE,
                Collapsed.NONE
                        .flipped(new Collapsed.Key("A"), List.of(replied))
                        .flipped(new Collapsed.Key("A"), List.of(replied)));
        // The first comment is the thread, and an id of none of the threads flips nothing.
        assertEquals(Collapsed.NONE, Collapsed.NONE.flipped(new Collapsed.Key("H"), List.of(replied)));
        assertEquals(Collapsed.NONE, Collapsed.NONE.flipped(new Collapsed.Key("gone"), List.of(replied)));
    }

    @Test
    void eachIdFlippedIsKeptWithWhetherItWasCollapsedByDefault() {
        ReviewThread resolved = thread(true, comment("A", Option.none()), comment("H", Option.of("SPAM")));

        assertEquals(
                new Collapsed(HashMap.of(new Collapsed.Key("T"), true, new Collapsed.Key("H"), true)),
                Collapsed.NONE
                        .flipped(new Collapsed.Key("T"), List.of(resolved))
                        .flipped(new Collapsed.Key("H"), List.of(resolved)));
    }

    @Test
    void anIdFlippedAgainstAnotherDefaultThanItsOwnFlipsNothing() {
        ReviewThread unresolved = thread(false, comment("A", Option.none()));

        // Opened while it was resolved, kept from an earlier session: unresolved since, it is open as it starts.
        Collapsed kept = new Collapsed(HashMap.of(new Collapsed.Key("T"), true));
        assertFalse(kept.thread(unresolved));
        assertEquals(
                new Collapsed(HashMap.of(new Collapsed.Key("T"), false)),
                kept.flipped(new Collapsed.Key("T"), List.of(unresolved)));
    }

    @Test
    void readAgainWhatChangedItsDefaultIsAsItStarts() {
        Comment reply = comment("R", Option.none());
        Collapsed flipped = new Collapsed(HashMap.of(
                new Collapsed.Key("T"), false, new Collapsed.Key("R"), false, new Collapsed.Key("gone"), true));
        ReviewThread before = thread(false, comment("A", Option.none()), reply);

        assertEquals(flipped, flipped.readAgain(List.of(before)));
        // Resolved, the thread is as a resolved one starts; its reply stays as it was.
        assertEquals(
                new Collapsed(HashMap.of(new Collapsed.Key("R"), false, new Collapsed.Key("gone"), true)),
                flipped.readAgain(List.of(thread(true, comment("A", Option.none()), reply))));
        // A reply hidden since is as a hidden one starts.
        assertEquals(
                new Collapsed(HashMap.of(new Collapsed.Key("T"), false, new Collapsed.Key("gone"), true)),
                flipped.readAgain(
                        List.of(thread(false, comment("A", Option.none()), comment("R", Option.of("SPAM"))))));
        // None read, all stay.
        assertEquals(flipped, flipped.readAgain(List.empty()));
    }

    @Test
    void whatWasKeptIsAddedToWhatWasFlippedWhichWins() {
        assertEquals(
                new Collapsed(HashMap.of(new Collapsed.Key("T"), false, new Collapsed.Key("R"), true)),
                new Collapsed(HashMap.of(new Collapsed.Key("T"), false))
                        .with(HashMap.of(new Collapsed.Key("T"), true, new Collapsed.Key("R"), true)));
    }
}
