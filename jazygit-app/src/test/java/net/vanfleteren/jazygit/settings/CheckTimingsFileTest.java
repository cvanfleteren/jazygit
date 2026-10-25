package net.vanfleteren.jazygit.settings;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import net.vanfleteren.jazygit.settings.CheckTimingsStore.Run;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CheckTimingsFileTest {

    private static final Instant AT = Instant.parse("2026-10-24T22:22:37Z");
    private static final Run ACCEPTANCE = new Run("Build/acceptance", 121, 192, 254, "pull_request", AT);
    private static final Run VERIFY = new Run("Build.yml/verify", 122, 100, 102, "push", AT.minusSeconds(60));

    @TempDir
    Path dir;

    private Path file() {
        return dir.resolve("jazygit").resolve("check-timings.toml");
    }

    private CheckTimingsFile timings() {
        return new CheckTimingsFile(file());
    }

    private void write(String content) throws IOException {
        Files.createDirectories(file().getParent());
        Files.writeString(file(), content);
    }

    @Test
    void aMissingFileHasNoneAndIsNotCreated() {
        assertEquals(Option.none(), timings().load("owner/repo"));
        assertFalse(Files.exists(file()));
    }

    @Test
    void theRunsAreReadBackPerRepositoryEvenWithDotsInTheNames() {
        assertEquals(
                List.of(ACCEPTANCE, VERIFY),
                timings()
                        .change("owner/repo.js", runs -> runs.appendAll(List.of(ACCEPTANCE, VERIFY)))
                        .get());
        timings().change("owner/other", runs -> runs.append(VERIFY));

        assertEquals(
                List.of(ACCEPTANCE, VERIFY).sortBy(Run::id),
                timings().load("owner/repo.js").get().sortBy(Run::id));
        assertEquals(Option.of(List.of(VERIFY)), timings().load("owner/other"));
        assertEquals(Option.none(), timings().load("owner/repo"));
    }

    @Test
    void aChangeStartsFromWhatAnotherOneKeptMeanwhile() {
        timings().change("owner/repo", runs -> runs.append(ACCEPTANCE));

        // Another jazygit, which did not see the first change.
        List<Run> kept = new CheckTimingsFile(file())
                .change("owner/repo", runs -> runs.append(VERIFY))
                .get();

        assertEquals(List.of(ACCEPTANCE, VERIFY).sortBy(Run::id), kept.sortBy(Run::id));
    }

    @Test
    void theStateFileIsLeftAlone() throws IOException {
        Path state = file().resolveSibling("state.toml");
        new RecentRepositoriesFile(state).opened(Path.of("/repos/a"));

        timings().change("owner/repo", runs -> runs.append(ACCEPTANCE));

        assertEquals(List.of(Path.of("/repos/a")), new RecentRepositoriesFile(state).load());
        assertTrue(Files.readString(file()).contains("at = 2026-10-24T22:22:37Z"), Files.readString(file()));
    }

    @Test
    void aBrokenFileHasNoneAndIsWrittenAnew() throws IOException {
        write("this is [not toml");

        assertEquals(Option.none(), timings().load("owner/repo"));
        assertTrue(
                timings().change("owner/repo", runs -> runs.append(ACCEPTANCE)).isSuccess());
        assertEquals(Option.of(List.of(ACCEPTANCE)), timings().load("owner/repo"));
    }

    @Test
    void aRunThatCannotBeReadIsLeftOut() throws IOException {
        write("""
                [repos."owner/repo"]
                "Build/verify" = [{id = 1, duration = 2}, {id = 3, duration = 4, finished = 5, at = "2026-10-24T22:22:37Z"}]
                """);

        assertEquals(
                Option.of(List.of(new Run("Build/verify", 3, 4, 5, "", AT))),
                timings().load("owner/repo"));
    }
}
