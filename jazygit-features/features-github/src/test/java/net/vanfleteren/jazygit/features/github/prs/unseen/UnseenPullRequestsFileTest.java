package net.vanfleteren.jazygit.features.github.prs.unseen;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.TreeSet;
import io.vavr.control.Option;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.vanfleteren.jazygit.core.repo.PrNumber;
import net.vanfleteren.jazygit.core.repo.RepoSlug;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UnseenPullRequestsFileTest {

    private static final UnseenPullRequests UNSEEN =
            new UnseenPullRequests(new PrNumber(42), TreeSet.of(new PrNumber(40), new PrNumber(42)));

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
        assertEquals(Option.none(), unseen().load(new RepoSlug("owner/repo")));
        assertFalse(Files.exists(file()));
    }

    @Test
    void whatIsKeptIsReadBackPerRepositoryEvenWithADotInItsName() {
        assertTrue(unseen().save(new RepoSlug("owner/repo.js"), UNSEEN).isSuccess());
        unseen().save(new RepoSlug("owner/other"), UnseenPullRequests.upTo(new PrNumber(7)));

        assertEquals(Option.of(UNSEEN), unseen().load(new RepoSlug("owner/repo.js")));
        assertEquals(Option.of(UnseenPullRequests.upTo(new PrNumber(7))), unseen().load(new RepoSlug("owner/other")));
        assertEquals(Option.none(), unseen().load(new RepoSlug("owner/repo")));
    }

    @Test
    void eachRepositoryHasAFileOfItsOwn() throws IOException {
        unseen().save(new RepoSlug("owner/repo"), UNSEEN);

        String written = Files.readString(file());
        assertTrue(written.startsWith("highest = 42"), written);
    }

    @Test
    void whatIsNoOwnerAndNameIsNeitherReadNorWritten() {
        assertTrue(unseen().save(new RepoSlug("../repo"), UNSEEN).isFailure());
        assertTrue(unseen().save(new RepoSlug("owner/repo/x"), UNSEEN).isFailure());
        assertEquals(Option.none(), unseen().load(new RepoSlug("owner")));
        assertFalse(Files.exists(dir.resolve("jazygit")));
    }

    @Test
    void aFileThatCannotBeReadHasNoneAndIsWrittenAnew() throws IOException {
        write("not = [toml");

        assertEquals(Option.none(), unseen().load(new RepoSlug("owner/repo")));
        assertTrue(unseen().save(new RepoSlug("owner/repo"), UNSEEN).isSuccess());
        assertEquals(Option.of(UNSEEN), unseen().load(new RepoSlug("owner/repo")));
    }

    @Test
    void numbersThatAreNoNumbersAreLeftOut() throws IOException {
        write("""
                highest = 42
                numbers = [40, "x", 42]
                """);

        assertEquals(Option.of(UNSEEN), unseen().load(new RepoSlug("owner/repo")));
    }
}
