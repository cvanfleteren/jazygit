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
import net.vanfleteren.jazygit.core.git.process.GitError;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.core.repo.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Dropping a commit of a real repository.
 */
class CliDropTest {

    @TempDir
    Path repo;

    private Git repository;

    @BeforeEach
    void setUp() throws Exception {
        repository = CliGitFixture.init(repo);
    }

    @Test
    void dropRemovesACommitAndKeepsTheOnesAfterIt() throws Exception {
        Files.writeString(repo.resolve("a.txt"), "a\n");
        git("add", "a.txt");
        git("commit", "-q", "-m", "Second");
        Files.writeString(repo.resolve("b.txt"), "b\n");
        git("add", "b.txt");
        git("commit", "-q", "-m", "Third");
        Files.writeString(repo.resolve("README.md"), "dirty\n");
        List<Commit> before = commits().get();

        List<String> executed =
                repository.commits().drop(before.get(1).sha()).get().map(ExecutedCommand::line);

        assertTrue(
                executed.head()
                        .startsWith("git -c rebase.abbreviateCommands=false -c core.abbrev=no"
                                + " rebase --interactive --autostash --keep-empty --rebase-merges"),
                executed.toString());
        assertEquals(
                List.of("Third", "Initial commit"),
                commits().get().map(c -> c.message().summary()).toList());
        assertTrue(Files.notExists(repo.resolve("a.txt")));
        assertTrue(Files.exists(repo.resolve("b.txt")));
        assertEquals("dirty\n", Files.readString(repo.resolve("README.md")));
    }

    @Test
    void dropOfTheFirstCommitRebasesFromTheRoot() throws Exception {
        Files.writeString(repo.resolve("a.txt"), "a\n");
        git("add", "a.txt");
        git("commit", "-q", "-m", "Second");
        Sha root = commits().get().last().sha();

        repository.commits().drop(root).get();

        assertEquals(
                List.of("Second"),
                commits().get().map(c -> c.message().summary()).toList());
        assertTrue(Files.notExists(repo.resolve("README.md")));
    }

    @Test
    void aConflictingDropIsLeftForTheUserToResolve() throws Exception {
        Files.writeString(repo.resolve("README.md"), "second\n");
        git("commit", "-q", "-am", "Second");
        Files.writeString(repo.resolve("README.md"), "third\n");
        git("commit", "-q", "-am", "Third");
        Sha second = commits().get().get(1).sha();

        assertInstanceOf(
                GitError.StoppedOnConflicts.class,
                repository.commits().drop(second).getLeft().error());

        assertTrue(Files.exists(repo.resolve(".git/rebase-merge")));
        assertTrue(Files.readString(repo.resolve("README.md")).contains("<<<<<<<"));
    }

    private String git(String... args) throws IOException, InterruptedException, TimeoutException {
        return gitIn(repo, args);
    }

    private Either<GitError, List<Commit>> commits() {
        return CliGitFixture.commits(repository);
    }
}
