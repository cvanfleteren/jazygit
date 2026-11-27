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
 * Adding the staged changes of a real repository to an earlier commit.
 */
class CliAmendTest {

    @TempDir
    Path repo;

    private Git repository;

    @BeforeEach
    void setUp() throws Exception {
        repository = CliGitFixture.init(repo);
    }

    @Test
    void amendCommitAddsTheStagedChangesToAnEarlierCommitAndLeavesTheUnstagedOnes() throws Exception {
        Files.writeString(repo.resolve("a.txt"), "a\n");
        git("add", "a.txt");
        git("commit", "-q", "-m", "Second");
        Files.writeString(repo.resolve("b.txt"), "b\n");
        git("add", "b.txt");
        git("commit", "-q", "-m", "Third");
        Files.writeString(repo.resolve("a.txt"), "amended\n");
        git("add", "a.txt");
        Files.writeString(repo.resolve("README.md"), "dirty\n");
        Sha second = commits().get().get(1).sha();

        repository.commits().amend(second).get();

        assertEquals(
                List.of("Third", "Second", "Initial commit"),
                commits().get().map(c -> c.message().summary()).toList());
        assertEquals("amended\n", git("show", "HEAD~1:a.txt"));
        assertEquals("", git("diff", "--cached", "--name-only"));
        assertEquals("dirty\n", Files.readString(repo.resolve("README.md")));
    }

    @Test
    void amendCommitOfTheLastCommitAmendsIt() throws Exception {
        Files.writeString(repo.resolve("a.txt"), "a\n");
        git("add", "a.txt");
        git("commit", "-q", "-m", "Second");
        Files.writeString(repo.resolve("a.txt"), "amended\n");
        git("add", "a.txt");

        List<String> executed =
                repository.commits().amend(commits().get().head().sha()).get().map(ExecutedCommand::line);

        assertEquals(List.of("git commit --quiet --amend --no-edit"), executed);
        assertEquals(
                List.of("Second", "Initial commit"),
                commits().get().map(c -> c.message().summary()).toList());
        assertEquals("amended\n", git("show", "HEAD:a.txt"));
    }

    @Test
    void amendCommitOfTheFirstCommitRebasesFromTheRoot() throws Exception {
        Files.writeString(repo.resolve("a.txt"), "a\n");
        git("add", "a.txt");
        git("commit", "-q", "-m", "Second");
        Files.writeString(repo.resolve("root.txt"), "root\n");
        git("add", "root.txt");
        Sha root = commits().get().last().sha();

        repository.commits().amend(root).get();

        assertEquals(
                List.of("Second", "Initial commit"),
                commits().get().map(c -> c.message().summary()).toList());
        assertEquals("root\n", git("show", "HEAD~1:root.txt"));
    }

    @Test
    void amendCommitLeavesOtherFixupCommitsAlone() throws Exception {
        Files.writeString(repo.resolve("a.txt"), "a\n");
        git("add", "a.txt");
        git("commit", "-q", "-m", "Second");
        Sha second = commits().get().head().sha();
        Files.writeString(repo.resolve("b.txt"), "b\n");
        git("add", "b.txt");
        git("commit", "-q", "--fixup=" + second.value());
        Files.writeString(repo.resolve("c.txt"), "c\n");
        git("add", "c.txt");

        repository.commits().amend(second).get();

        assertEquals(
                List.of("fixup! Second", "Second", "Initial commit"),
                commits().get().map(c -> c.message().summary()).toList());
        assertEquals("c\n", git("show", "HEAD~1:c.txt"));
    }

    @Test
    void aConflictingAmendCommitIsLeftForTheUserToResolve() throws Exception {
        Files.writeString(repo.resolve("README.md"), "second\n");
        git("commit", "-q", "-am", "Second");
        Files.writeString(repo.resolve("README.md"), "third\n");
        git("commit", "-q", "-am", "Third");
        Sha initial = commits().get().last().sha();
        Files.writeString(repo.resolve("README.md"), "staged\n");
        git("add", "README.md");

        assertInstanceOf(
                GitError.StoppedOnConflicts.class,
                repository.commits().amend(initial).getLeft().error());

        assertTrue(Files.exists(repo.resolve(".git/rebase-merge")));
    }

    @Test
    void amendCommitOfACommitThatIsNotInTheLogFailsAndKeepsTheChangesStaged() throws Exception {
        Files.writeString(repo.resolve("b.txt"), "b\n");
        git("add", "b.txt");
        git("commit", "-q", "-m", "Second");
        git("checkout", "-q", "-b", "elsewhere");
        Files.writeString(repo.resolve("c.txt"), "c\n");
        git("add", "c.txt");
        git("commit", "-q", "-m", "Elsewhere");
        Sha elsewhere = commits().get().head().sha();
        git("checkout", "-q", "main");
        Files.writeString(repo.resolve("b.txt"), "staged\n");
        git("add", "b.txt");

        assertTrue(repository.commits().amend(elsewhere).isLeft());

        assertEquals(
                List.of("Second", "Initial commit"),
                commits().get().map(c -> c.message().summary()).toList());
        assertEquals("b.txt\n", git("diff", "--cached", "--name-only"));
        assertTrue(Files.notExists(repo.resolve(".git/rebase-merge")));
    }

    private String git(String... args) throws IOException, InterruptedException, TimeoutException {
        return gitIn(repo, args);
    }

    private Either<GitError, List<Commit>> commits() {
        return CliGitFixture.commits(repository);
    }
}
