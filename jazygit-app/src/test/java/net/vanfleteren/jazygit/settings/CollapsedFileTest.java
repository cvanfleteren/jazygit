package net.vanfleteren.jazygit.settings;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.HashMap;
import io.vavr.collection.Map;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
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
        assertEquals(HashMap.empty(), collapsed().load("owner/repo", 42));
        assertFalse(Files.exists(file()));
    }

    @Test
    void whatIsKeptIsReadBackPerPullRequestEvenWithADotInAnId() throws IOException {
        assertEquals(FLIPPED, collapsed().save("owner/repo", 42, FLIPPED, AT).get());
        collapsed().save("owner/repo", 7, HashMap.of("T", false), AT);

        assertEquals(FLIPPED, collapsed().load("owner/repo", 42));
        assertEquals(HashMap.of("T", false), collapsed().load("owner/repo", 7));
        assertEquals(HashMap.empty(), collapsed().load("owner/repo", 8));
        assertEquals(HashMap.empty(), collapsed().load("owner/other", 42));
        String written = Files.readString(file());
        assertTrue(written.contains("touched = 2026-10-31T10:00:00Z"), written);
    }

    @Test
    void aPullRequestWithNoneFlippedIsForgotten() throws IOException {
        collapsed().save("owner/repo", 42, FLIPPED, AT);
        collapsed().save("owner/repo", 42, HashMap.empty(), AT);

        assertEquals(HashMap.empty(), collapsed().load("owner/repo", 42));
        assertFalse(Files.readString(file()).contains("42"), Files.readString(file()));
    }

    @Test
    void aPullRequestNoneWasFlippedOfForThirtyDaysIsForgottenAtTheNextWrite() {
        collapsed().save("owner/repo", 1, HashMap.of("A", true), AT.minus(CollapsedStore.KEPT));
        collapsed()
                .save(
                        "owner/repo",
                        2,
                        HashMap.of("B", true),
                        AT.minus(CollapsedStore.KEPT).minusSeconds(1));

        collapsed().save("owner/repo", 3, HashMap.of("C", true), AT);

        assertEquals(HashMap.of("A", true), collapsed().load("owner/repo", 1));
        assertEquals(HashMap.empty(), collapsed().load("owner/repo", 2));
        assertEquals(HashMap.of("C", true), collapsed().load("owner/repo", 3));
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

        assertEquals(HashMap.of("A", true), collapsed().load("owner/repo", 42));
        assertEquals(HashMap.empty(), collapsed().load("owner/repo", 7));
        // Without a time it was flipped at it is as old as can be.
        collapsed().save("owner/repo", 1, HashMap.of("C", true), AT);
        assertEquals(HashMap.empty(), collapsed().load("owner/repo", 42));
    }

    @Test
    void aBrokenFileHasNoneAndIsWrittenAnew() throws IOException {
        write("this is [not toml");

        assertEquals(HashMap.empty(), collapsed().load("owner/repo", 42));
        assertTrue(collapsed().save("owner/repo", 42, FLIPPED, AT).isSuccess());
        assertEquals(FLIPPED, collapsed().load("owner/repo", 42));
    }

    @Test
    void whatIsNoOwnerAndNameIsNeitherReadNorWritten() {
        assertTrue(collapsed().save("../repo", 42, FLIPPED, AT).isFailure());
        assertEquals(HashMap.empty(), collapsed().load("owner", 42));
        assertFalse(Files.exists(dir.resolve("jazygit")));
    }
}
