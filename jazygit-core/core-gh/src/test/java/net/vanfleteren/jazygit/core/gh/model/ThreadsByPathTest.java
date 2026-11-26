package net.vanfleteren.jazygit.core.gh.model;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.gh.model.ReviewThread.Side;
import net.vanfleteren.jazygit.core.gh.model.ReviewThread.Subject;
import net.vanfleteren.jazygit.core.repo.RepoPath;
import org.junit.jupiter.api.Test;

/**
 * The review threads of a file found by its path, as {@link ReviewThread#isOn} matches them (ADR 55).
 */
class ThreadsByPathTest {

    private static ReviewThread thread(String path, String id) {
        return new ReviewThread(
                new RepoPath(path),
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
                new ReviewThread.Id(id),
                ReviewThread.Rights.ALL);
    }

    private static final ReviewThread A = thread("a.txt", "A");
    private static final ReviewThread OLD = thread("old.txt", "OLD");
    private static final ReviewThread NEW = thread("new.txt", "NEW");
    private static final ReviewThread OLD_AGAIN = thread("old.txt", "OLD_AGAIN");
    private static final ThreadsByPath THREADS = ThreadsByPath.of(List.of(OLD, A, NEW, OLD_AGAIN));

    @Test
    void theThreadsOfAFileAreThoseOnItsPath() {
        assertEquals(List.of(A), THREADS.on(new RepoPath("a.txt"), Option.none()));
        assertEquals(List.empty(), THREADS.on(new RepoPath("b.txt"), Option.none()));
    }

    @Test
    void aRenamedFileHasTheThreadsOnEitherPathInTheirOrder() {
        assertEquals(
                List.of(OLD, NEW, OLD_AGAIN), THREADS.on(new RepoPath("new.txt"), Option.of(new RepoPath("old.txt"))));
        assertEquals(List.of(A), THREADS.on(new RepoPath("a.txt"), Option.of(new RepoPath("a.txt"))));
    }

    @Test
    void itFindsWhatIsOnFinds() {
        List<ReviewThread> all = List.of(OLD, A, NEW, OLD_AGAIN);

        assertEquals(
                all.filter(thread -> thread.isOn(new RepoPath("new.txt"), Option.of(new RepoPath("old.txt")))),
                THREADS.on(new RepoPath("new.txt"), Option.of(new RepoPath("old.txt"))));
        assertEquals(
                all.filter(thread -> thread.isOn(new RepoPath("a.txt"), Option.none())),
                THREADS.on(new RepoPath("a.txt"), Option.none()));
    }
}
