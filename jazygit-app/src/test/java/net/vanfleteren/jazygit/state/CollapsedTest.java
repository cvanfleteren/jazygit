package net.vanfleteren.jazygit.state;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.gh.model.ReviewThread;
import net.vanfleteren.jazygit.gh.model.ReviewThread.Comment;
import net.vanfleteren.jazygit.gh.model.ReviewThread.Side;
import net.vanfleteren.jazygit.gh.model.ReviewThread.Subject;
import org.junit.jupiter.api.Test;

/**
 * Which review threads and comments are collapsed (ADR 62).
 */
class CollapsedTest {

    private static Comment comment(String id, Option<String> hidden) {
        return new Comment("bob", Instant.EPOCH, id, hidden, id, "1");
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
                "T",
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
        assertFalse(Collapsed.NONE.flipped("T").thread(thread(true, open)));
        assertTrue(Collapsed.NONE.flipped("A").comment(open));
        assertEquals(Collapsed.NONE, Collapsed.NONE.flipped("A").flipped("A"));
    }

    @Test
    void readAgainWhatChangedItsDefaultIsAsItStarts() {
        Comment reply = comment("R", Option.none());
        Collapsed flipped = new Collapsed(HashSet.of("T", "R", "gone"));
        ReviewThread before = thread(false, comment("A", Option.none()), reply);

        assertEquals(flipped, flipped.readAgain(List.of(before), List.of(before)));
        // Resolved, the thread is as a resolved one starts; its reply stays as it was.
        assertEquals(
                new Collapsed(HashSet.of("R", "gone")),
                flipped.readAgain(List.of(before), List.of(thread(true, comment("A", Option.none()), reply))));
        // A reply hidden since is as a hidden one starts.
        assertEquals(
                new Collapsed(HashSet.of("T", "gone")),
                flipped.readAgain(
                        List.of(before),
                        List.of(thread(false, comment("A", Option.none()), comment("R", Option.of("SPAM"))))));
        // A thread read for the first time keeps what was flipped.
        assertEquals(flipped, flipped.readAgain(List.empty(), List.of(thread(true, reply))));
    }
}
