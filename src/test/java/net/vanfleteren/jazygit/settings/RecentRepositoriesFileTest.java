package net.vanfleteren.jazygit.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

class RecentRepositoriesFileTest {

    private static final Path A = Path.of("/repos/a");
    private static final Path B = Path.of("/repos/b");
    private static final Path C = Path.of("/repos/c");

    @TempDir
    Path dir;

    private Path file() {
        return dir.resolve("jazygit").resolve("state.toml");
    }

    private RecentRepositoriesFile recent() {
        return new RecentRepositoriesFile(file());
    }

    private void write(String content) throws IOException {
        Files.createDirectories(file().getParent());
        Files.writeString(file(), content);
    }

    @Test
    void aMissingFileHasNoneAndIsNotCreated() {
        assertEquals(List.empty(), recent().load());
        assertFalse(Files.exists(file()));
    }

    @Test
    void theLastOneOpenedComesFirstAndOnlyOnce() {
        recent().opened(A);
        recent().opened(B);
        recent().opened(A);

        assertEquals(List.of(A, B), recent().load());
    }

    @Test
    void theOnesOpenedLongestAgoDropOff() {
        List<Path> many = List.range(0, RecentRepositories.MAX + 5).map(i -> Path.of("/repos/" + i));

        List<Path> kept = many.foldLeft(List.<Path>empty(), RecentRepositories::opened);

        assertEquals(RecentRepositories.MAX, kept.size());
        assertEquals(many.last(), kept.head());
        assertFalse(kept.contains(many.head()));
    }

    @Test
    void forgettingDropsThemAndKeepsTheOrderOfTheOthers() {
        recent().opened(A);
        recent().opened(B);
        recent().opened(C);

        assertEquals(List.of(C, A), recent().forget(List.of(B)).get());
        assertEquals(List.of(C, A), recent().load());
    }

    @Test
    void keepsWhatAnotherJazygitWroteMeanwhile() {
        RecentRepositoriesFile one = recent();
        RecentRepositoriesFile other = recent();

        one.opened(A);
        other.opened(B);

        assertEquals(List.of(B, A), one.load());
    }

    @Test
    void anUnreadableFileHasNoneAndIsWrittenAnew() throws IOException {
        write("this is = = not toml");

        assertEquals(List.empty(), recent().load());
        assertEquals(List.of(A), recent().opened(A).get());
        assertEquals(List.of(A), recent().load());
    }

    @Test
    void valuesThatAreNoAbsolutePathsAreSkipped() throws IOException {
        write("""
                recent-repositories = ["/repos/a", 3, "relative", "/repos/a", "/repos/b"]
                """);

        assertEquals(List.of(A, B), recent().load());
    }

    @Test
    void anUnwritableFileFails() throws IOException {
        Files.writeString(dir.resolve("jazygit"), "a file where the directory should be");

        assertTrue(recent().opened(A).isFailure());
    }
}
