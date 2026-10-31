package net.vanfleteren.jazygit.settings;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * What was kept of each repository in a file for all of them is deleted (ADR 63).
 */
class FormerStateTest {

    @TempDir
    Path dir;

    private Path state() {
        return dir.resolve("jazygit").resolve("state.toml");
    }

    @Test
    void theCheckTimingsAndTheUnseenPullRequestsAreDeletedAndTheRecentRepositoriesKept() throws IOException {
        Path timings = SettingsPaths.formerCheckTimingsFile(state());
        Files.createDirectories(state().getParent());
        Files.writeString(timings, "[repo.\"owner/repo\"]\n");
        Files.writeString(timings.resolveSibling("check-timings.toml.lock"), "");
        Files.writeString(state(), """
                recent-repositories = ["/repos/a"]

                [unseen-pull-requests."owner/repo"]
                highest = 42
                numbers = []
                """);

        assertTrue(FormerState.forget(state()).isSuccess());

        assertFalse(Files.exists(timings));
        assertFalse(Files.exists(timings.resolveSibling("check-timings.toml.lock")));
        assertFalse(Files.readString(state()).contains("unseen"), Files.readString(state()));
        assertEquals(List.of(Path.of("/repos/a")), new RecentRepositoriesFile(state()).load());
    }

    @Test
    void aStateFileWithoutThemIsNotWritten() throws IOException {
        Files.createDirectories(state().getParent());
        Files.writeString(state(), "recent-repositories = [\"/repos/a\"]\n");

        assertTrue(FormerState.forget(state()).isSuccess());

        assertEquals("recent-repositories = [\"/repos/a\"]\n", Files.readString(state()));
        assertFalse(Files.exists(state().resolveSibling("state.toml.lock")));
    }

    @Test
    void nothingThereIsNothingToDo() {
        assertTrue(FormerState.forget(state()).isSuccess());
        assertFalse(Files.exists(state()));
    }
}
