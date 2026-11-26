package net.vanfleteren.jazygit.features.github.remarks;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.HashMap;
import io.vavr.collection.Map;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import net.vanfleteren.jazygit.core.repo.PrNumber;
import net.vanfleteren.jazygit.core.repo.RepoSlug;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CollapsedFileTest {

    private static final Instant AT = Instant.parse("2026-10-31T10:00:00Z");
    private static final Map<String, Boolean> FLIPPED = HashMap.of("PRRT_kwDO.1", true, "IC_kwDO", false);

    @TempDir
    Path dir;

    private Path repositories() {
        return dir.resolve("jazygit").resolve("repos");
    }

    private Path file() {
        return repositories().resolve("owner").resolve("repo").resolve("collapsed.toml");
    }

    private CollapsedFile collapsed() {
        return new CollapsedFile(repositories());
    }

    private void write(String content) throws IOException {
        Files.createDirectories(file().getParent());
        Files.writeString(file(), content);
    }

    @Test
    void aMissingFileHasNoneAndIsNotCreated() {
        assertEquals(HashMap.empty(), collapsed().load(new RepoSlug("owner/repo"), new PrNumber(42)));
        assertFalse(Files.exists(file()));
    }

    @Test
    void whatIsKeptIsReadBackPerPullRequestEvenWithADotInAnId() throws IOException {
        assertEquals(
                FLIPPED,
                collapsed()
                        .save(new RepoSlug("owner/repo"), new PrNumber(42), FLIPPED, AT)
                        .get());
        collapsed().save(new RepoSlug("owner/repo"), new PrNumber(7), HashMap.of("T", false), AT);

        assertEquals(FLIPPED, collapsed().load(new RepoSlug("owner/repo"), new PrNumber(42)));
        assertEquals(HashMap.of("T", false), collapsed().load(new RepoSlug("owner/repo"), new PrNumber(7)));
        assertEquals(HashMap.empty(), collapsed().load(new RepoSlug("owner/repo"), new PrNumber(8)));
        assertEquals(HashMap.empty(), collapsed().load(new RepoSlug("owner/other"), new PrNumber(42)));
        String written = Files.readString(file());
        assertTrue(written.contains("touched = 2026-10-31T10:00:00Z"), written);
    }

    @Test
    void aPullRequestWithNoneFlippedIsForgotten() throws IOException {
        collapsed().save(new RepoSlug("owner/repo"), new PrNumber(42), FLIPPED, AT);
        collapsed().save(new RepoSlug("owner/repo"), new PrNumber(42), HashMap.empty(), AT);

        assertEquals(HashMap.empty(), collapsed().load(new RepoSlug("owner/repo"), new PrNumber(42)));
        assertFalse(Files.readString(file()).contains("42"), Files.readString(file()));
    }

    @Test
    void aPullRequestNoneWasFlippedOfForThirtyDaysIsForgottenAtTheNextWrite() {
        collapsed()
                .save(
                        new RepoSlug("owner/repo"),
                        new PrNumber(1),
                        HashMap.of("A", true),
                        AT.minus(CollapsedStore.KEPT));
        collapsed()
                .save(
                        new RepoSlug("owner/repo"),
                        new PrNumber(2),
                        HashMap.of("B", true),
                        AT.minus(CollapsedStore.KEPT).minusSeconds(1));

        collapsed().save(new RepoSlug("owner/repo"), new PrNumber(3), HashMap.of("C", true), AT);

        assertEquals(HashMap.of("A", true), collapsed().load(new RepoSlug("owner/repo"), new PrNumber(1)));
        assertEquals(HashMap.empty(), collapsed().load(new RepoSlug("owner/repo"), new PrNumber(2)));
        assertEquals(HashMap.of("C", true), collapsed().load(new RepoSlug("owner/repo"), new PrNumber(3)));
    }

    @Test
    void whatCannotBeReadIsLeftOut() throws IOException {
        write("""
                [42]
                touched = "yesterday"

                [42.flipped]
                A = true
                B = "yes"

                [7]
                flipped = 3
                """);

        assertEquals(HashMap.of("A", true), collapsed().load(new RepoSlug("owner/repo"), new PrNumber(42)));
        assertEquals(HashMap.empty(), collapsed().load(new RepoSlug("owner/repo"), new PrNumber(7)));
        // Without a time it was flipped at it is as old as can be.
        collapsed().save(new RepoSlug("owner/repo"), new PrNumber(1), HashMap.of("C", true), AT);
        assertEquals(HashMap.empty(), collapsed().load(new RepoSlug("owner/repo"), new PrNumber(42)));
    }

    @Test
    void aBrokenFileHasNoneAndIsWrittenAnew() throws IOException {
        write("this is [not toml");

        assertEquals(HashMap.empty(), collapsed().load(new RepoSlug("owner/repo"), new PrNumber(42)));
        assertTrue(collapsed()
                .save(new RepoSlug("owner/repo"), new PrNumber(42), FLIPPED, AT)
                .isSuccess());
        assertEquals(FLIPPED, collapsed().load(new RepoSlug("owner/repo"), new PrNumber(42)));
    }

    @Test
    void whatIsNoOwnerAndNameIsNeitherReadNorWritten() {
        assertTrue(collapsed()
                .save(new RepoSlug("../repo"), new PrNumber(42), FLIPPED, AT)
                .isFailure());
        assertEquals(HashMap.empty(), collapsed().load(new RepoSlug("owner"), new PrNumber(42)));
        assertFalse(Files.exists(dir.resolve("jazygit")));
    }
}
