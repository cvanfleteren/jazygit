package net.vanfleteren.jazygit.gh.model;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.model.ReviewThread.Side;
import net.vanfleteren.jazygit.gh.model.ReviewThread.Subject;
import org.junit.jupiter.api.Test;

/**
 * The review threads of a file found by its path, as {@link ReviewThread#isOn} matches them (ADR 55).
 */
class ThreadsByPathTest {

    private static ReviewThread thread(String path, String id) {
        return new ReviewThread(
                path,
                Side.RIGHT,
                Option.none(),
                Option.none(),
                Side.RIGHT,
                Option.none(),
                false,
                false,
                Subject.FILE,
                List.empty(),
                0,
                id,
                ReviewThread.Rights.ALL);
    }

    private static final ReviewThread A = thread("a.txt", "A");
    private static final ReviewThread OLD = thread("old.txt", "OLD");
    private static final ReviewThread NEW = thread("new.txt", "NEW");
    private static final ReviewThread OLD_AGAIN = thread("old.txt", "OLD_AGAIN");
    private static final ThreadsByPath THREADS = ThreadsByPath.of(List.of(OLD, A, NEW, OLD_AGAIN));

    @Test
    void theThreadsOfAFileAreThoseOnItsPath() {
        assertEquals(List.of(A), THREADS.on("a.txt", Option.none()));
        assertEquals(List.empty(), THREADS.on("b.txt", Option.none()));
    }

    @Test
    void aRenamedFileHasTheThreadsOnEitherPathInTheirOrder() {
        assertEquals(List.of(OLD, NEW, OLD_AGAIN), THREADS.on("new.txt", Option.of("old.txt")));
        assertEquals(List.of(A), THREADS.on("a.txt", Option.of("a.txt")));
    }

    @Test
    void itFindsWhatIsOnFinds() {
        List<ReviewThread> all = List.of(OLD, A, NEW, OLD_AGAIN);

        assertEquals(
                all.filter(thread -> thread.isOn("new.txt", Option.of("old.txt"))),
                THREADS.on("new.txt", Option.of("old.txt")));
        assertEquals(all.filter(thread -> thread.isOn("a.txt", Option.none())), THREADS.on("a.txt", Option.none()));
    }
}
