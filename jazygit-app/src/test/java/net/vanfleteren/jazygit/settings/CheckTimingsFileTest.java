package net.vanfleteren.jazygit.settings;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.function.UnaryOperator;
import net.vanfleteren.jazygit.settings.CheckTimingsStore.Kept;
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

    private static Kept of(Run... runs) {
        return new Kept(List.of(runs), Option.none());
    }

    private static UnaryOperator<Kept> adding(Run... runs) {
        return kept -> kept.withRuns(kept.runs().appendAll(List.of(runs)));
    }

    private Option<List<Run>> runs(String repository) {
        return timings().load(repository).map(kept -> kept.runs().sortBy(Run::id));
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
                of(ACCEPTANCE, VERIFY),
                timings().change("owner/repo.js", adding(ACCEPTANCE, VERIFY)).get());
        timings().change("owner/other", adding(VERIFY));

        assertEquals(Option.of(List.of(ACCEPTANCE, VERIFY).sortBy(Run::id)), runs("owner/repo.js"));
        assertEquals(Option.of(of(VERIFY)), timings().load("owner/other"));
        assertEquals(Option.none(), timings().load("owner/repo"));
    }

    @Test
    void aChangeStartsFromWhatAnotherOneKeptMeanwhile() {
        timings().change("owner/repo", adding(ACCEPTANCE));

        // Another jazygit, which did not see the first change.
        Kept kept = new CheckTimingsFile(file())
                .change("owner/repo", adding(VERIFY))
                .get();

        assertEquals(List.of(ACCEPTANCE, VERIFY).sortBy(Run::id), kept.runs().sortBy(Run::id));
    }

    @Test
    void theStateFileIsLeftAlone() throws IOException {
        Path state = file().resolveSibling("state.toml");
        new RecentRepositoriesFile(state).opened(Path.of("/repos/a"));

        timings().change("owner/repo", adding(ACCEPTANCE));

        assertEquals(List.of(Path.of("/repos/a")), new RecentRepositoriesFile(state).load());
        assertTrue(Files.readString(file()).contains("at = 2026-10-24T22:22:37Z"), Files.readString(file()));
    }

    @Test
    void aBrokenFileHasNoneAndIsWrittenAnew() throws IOException {
        write("this is [not toml");

        assertEquals(Option.none(), timings().load("owner/repo"));
        assertTrue(timings().change("owner/repo", adding(ACCEPTANCE)).isSuccess());
        assertEquals(Option.of(of(ACCEPTANCE)), timings().load("owner/repo"));
    }

    @Test
    void aRunThatCannotBeReadIsLeftOut() throws IOException {
        write("""
                [repo."owner/repo".checks]
                "Build/verify" = [{id = 1, duration = 2}, {id = 3, duration = 4, finished = 5, at = "2026-10-24T22:22:37Z"}]
                """);

        assertEquals(
                Option.of(of(new Run("Build/verify", 3, 4, 5, "", AT))),
                timings().load("owner/repo"));
    }

    @Test
    void whenItWasToppedUpIsKeptNextToTheChecksAndClashesWithNone() throws IOException {
        Run named = new Run("topped-up", 123, 1, 1, "push", AT);

        timings().change("owner/repo", adding(ACCEPTANCE, named));
        Kept kept = timings()
                .change("owner/repo", before -> before.withToppedUp(AT))
                .get();

        assertEquals(new Kept(List.of(ACCEPTANCE, named), Option.of(AT)), kept);
        assertEquals(Option.of(kept), timings().load("owner/repo"));
        String written = Files.readString(file());
        assertTrue(written.contains("[repo.\"owner/repo\"]"), written);
        assertTrue(written.contains("topped-up = 2026-10-24T22:22:37Z"), written);
        assertTrue(written.contains("[[repo.\"owner/repo\".checks.topped-up]]"), written);
    }

    @Test
    void aRepositoryOnlyToppedUpIsKept() {
        timings().change("owner/repo", before -> before.withToppedUp(AT));

        assertEquals(Option.of(new Kept(List.empty(), Option.of(AT))), timings().load("owner/repo"));
    }
}
