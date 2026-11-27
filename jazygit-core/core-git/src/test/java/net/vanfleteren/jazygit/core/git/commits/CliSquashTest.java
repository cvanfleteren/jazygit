package net.vanfleteren.jazygit.core.git.commits;

import static net.vanfleteren.jazygit.core.git.CliGitFixture.gitIn;
import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.control.Either;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeoutException;
import net.vanfleteren.jazygit.core.git.CliGitFixture;
import net.vanfleteren.jazygit.core.git.Git;
import net.vanfleteren.jazygit.core.git.model.*;
import net.vanfleteren.jazygit.core.git.process.GitError;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.core.repo.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Squashing a commit of a real repository into its parent, or fixing it up.
 */
class CliSquashTest {

    @TempDir
    Path repo;

    private Git repository;

    @BeforeEach
    void setUp() throws Exception {
        repository = CliGitFixture.init(repo);
    }

    @Test
    void squashFoldsACommitIntoItsParentAndKeepsBothMessages() throws Exception {
        Files.writeString(repo.resolve("a.txt"), "a\n");
        git("add", "a.txt");
        git("commit", "-q", "-m", "Second");
        Files.writeString(repo.resolve("b.txt"), "b\n");
        git("add", "b.txt");
        git("commit", "-q", "-m", "Third");
        Files.writeString(repo.resolve("c.txt"), "c\n");
        git("add", "c.txt");
        git("commit", "-q", "-m", "Fourth");
        Files.writeString(repo.resolve("README.md"), "dirty\n");
        List<Commit> before = commits().get();

        List<String> executed = repository
                .commits()
                .squash(before.get(1).sha(), SquashMode.SQUASH)
                .get()
                .map(ExecutedCommand::line);

        assertTrue(
                executed.head()
                        .startsWith("git -c rebase.abbreviateCommands=false -c core.abbrev=no"
                                + " rebase --interactive --autostash --keep-empty --rebase-merges"),
                executed.toString());
        List<Commit> after = commits().get();
        assertEquals(
                List.of("Fourth", "Second", "Initial commit"),
                after.map(c -> c.message().summary()).toList());
        assertTrue(
                after.get(1).message().description().contains("Third"),
                after.get(1).message().description());
        assertTrue(Files.exists(repo.resolve("b.txt")));
        assertEquals("dirty\n", Files.readString(repo.resolve("README.md")));
    }

    @Test
    void squashOfTheFirstCommitsChildRebasesFromTheRoot() throws Exception {
        Files.writeString(repo.resolve("a.txt"), "a\n");
        git("add", "a.txt");
        git("commit", "-q", "-m", "Second");

        repository
                .commits()
                .squash(commits().get().head().sha(), SquashMode.SQUASH)
                .get();

        assertEquals(
                List.of("Initial commit"),
                commits().get().map(c -> c.message().summary()).toList());
    }

    @Test
    void fixupFoldsACommitIntoItsParentAndKeepsOnlyTheParentsMessage() throws Exception {
        Files.writeString(repo.resolve("a.txt"), "a\n");
        git("add", "a.txt");
        git("commit", "-q", "-m", "Second");
        Files.writeString(repo.resolve("b.txt"), "b\n");
        git("add", "b.txt");
        git("commit", "-q", "-m", "Third", "-m", "Third body");
        Files.writeString(repo.resolve("c.txt"), "c\n");
        git("add", "c.txt");
        git("commit", "-q", "-m", "Fourth");
        Files.writeString(repo.resolve("README.md"), "dirty\n");
        List<Commit> before = commits().get();

        repository.commits().squash(before.get(1).sha(), SquashMode.FIXUP).get();

        List<Commit> after = commits().get();
        assertEquals(
                List.of("Fourth", "Second", "Initial commit"),
                after.map(c -> c.message().summary()).toList());
        assertEquals("", after.get(1).message().description().strip());
        assertTrue(Files.exists(repo.resolve("b.txt")));
        assertEquals("dirty\n", Files.readString(repo.resolve("README.md")));
    }

    @Test
    void fixupKeepingTheMessageTakesTheCommitsMessageAndTheParentsAuthor() throws Exception {
        Files.writeString(repo.resolve("a.txt"), "a\n");
        git("add", "a.txt");
        git("commit", "-q", "-m", "Second");
        Files.writeString(repo.resolve("b.txt"), "b\n");
        git("add", "b.txt");
        git("commit", "-q", "--author", "Other <other@example.com>", "-m", "Third", "-m", "Third body");
        List<Commit> before = commits().get();

        repository
                .commits()
                .squash(before.head().sha(), SquashMode.FIXUP_KEEP_MESSAGE)
                .get();

        List<Commit> after = commits().get();
        assertEquals(
                List.of("Third", "Initial commit"),
                after.map(c -> c.message().summary()).toList());
        assertEquals("Third body", after.head().message().description().strip());
        assertEquals("Test", after.head().authorName());
        assertTrue(Files.exists(repo.resolve("a.txt")));
        assertTrue(Files.exists(repo.resolve("b.txt")));
    }

    @Test
    void squashOfACommitThatIsNotInTheLogFailsInsteadOfDoingNothing() throws Exception {
        Files.writeString(repo.resolve("b.txt"), "b\n");
        git("add", "b.txt");
        git("commit", "-q", "-m", "Second");
        git("checkout", "-q", "-b", "elsewhere");
        Files.writeString(repo.resolve("c.txt"), "c\n");
        git("add", "c.txt");
        git("commit", "-q", "-m", "Elsewhere");
        Sha elsewhere = commits().get().head().sha();
        git("checkout", "-q", "main");

        assertTrue(repository.commits().squash(elsewhere, SquashMode.SQUASH).isLeft());

        assertEquals(
                List.of("Second", "Initial commit"),
                commits().get().map(c -> c.message().summary()).toList());
        assertTrue(Files.notExists(repo.resolve(".git/rebase-merge")));
    }

    private String git(String... args) throws IOException, InterruptedException, TimeoutException {
        return gitIn(repo, args);
    }

    private Either<GitError, List<Commit>> commits() {
        return CliGitFixture.commits(repository);
    }
}
