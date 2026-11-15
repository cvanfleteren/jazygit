package net.vanfleteren.jazygit.settings;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.TreeSet;
import io.vavr.control.Option;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UnseenPullRequestsFileTest {

    private static final UnseenPullRequests UNSEEN = new UnseenPullRequests(42, TreeSet.of(40, 42));

    @TempDir
    Path dir;

    private Path repositories() {
        return dir.resolve("jazygit").resolve("repos");
    }

    private Path file() {
        return repositories().resolve("owner").resolve("repo").resolve("unseen-pull-requests.toml");
    }

    private UnseenPullRequestsFile unseen() {
        return new UnseenPullRequestsFile(repositories());
    }

    private void write(String content) throws IOException {
        Files.createDirectories(file().getParent());
        Files.writeString(file(), content);
    }

    @Test
    void aMissingFileHasNoneAndIsNotCreated() {
        assertEquals(Option.none(), unseen().load("owner/repo"));
        assertFalse(Files.exists(file()));
    }

    @Test
    void whatIsKeptIsReadBackPerRepositoryEvenWithADotInItsName() {
        assertTrue(unseen().save("owner/repo.js", UNSEEN).isSuccess());
        unseen().save("owner/other", UnseenPullRequests.upTo(7));

        assertEquals(Option.of(UNSEEN), unseen().load("owner/repo.js"));
        assertEquals(Option.of(UnseenPullRequests.upTo(7)), unseen().load("owner/other"));
        assertEquals(Option.none(), unseen().load("owner/repo"));
    }

    @Test
    void eachRepositoryHasAFileOfItsOwn() throws IOException {
        unseen().save("owner/repo", UNSEEN);

        String written = Files.readString(file());
        assertTrue(written.startsWith("highest = 42"), written);
    }

    @Test
    void whatIsNoOwnerAndNameIsNeitherReadNorWritten() {
        assertTrue(unseen().save("../repo", UNSEEN).isFailure());
        assertTrue(unseen().save("owner/repo/x", UNSEEN).isFailure());
        assertEquals(Option.none(), unseen().load("owner"));
        assertFalse(Files.exists(dir.resolve("jazygit")));
    }

    @Test
    void aFileThatCannotBeReadHasNoneAndIsWrittenAnew() throws IOException {
        write("not = [toml");

        assertEquals(Option.none(), unseen().load("owner/repo"));
        assertTrue(unseen().save("owner/repo", UNSEEN).isSuccess());
        assertEquals(Option.of(UNSEEN), unseen().load("owner/repo"));
    }

    @Test
    void numbersThatAreNoNumbersAreLeftOut() throws IOException {
        write("""
                highest = 42
                numbers = [40, "x", 42]
                """);

        assertEquals(Option.of(UNSEEN), unseen().load("owner/repo"));
    }
}
