package net.vanfleteren.jazygit.core.git.history;

import static net.vanfleteren.jazygit.core.git.CliGitFixture.gitIn;
import static net.vanfleteren.jazygit.core.git.CliGitFixture.gitSucceedsIn;
import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeoutException;
import net.vanfleteren.jazygit.core.git.CliGitFixture;
import net.vanfleteren.jazygit.core.git.Git;
import net.vanfleteren.jazygit.core.git.process.GitError;
import net.vanfleteren.jazygit.core.git.process.GitFailure;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.core.repo.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Cherry-picking a commit onto the checked out branch of a real repository.
 */
class CliCherryPickTest {

    @TempDir
    Path repo;

    private Git repository;

    @BeforeEach
    void setUp() throws Exception {
        repository = CliGitFixture.init(repo);
    }

    @Test
    void cherryPickCopiesACommitOntoTheCheckedOutBranch() throws Exception {
        git("checkout", "-q", "-b", "feature");
        Files.writeString(repo.resolve("f.txt"), "f\n");
        git("add", "f.txt");
        git("commit", "-q", "-m", "Feature work");
        Sha picked = commits().get().head().sha();
        git("checkout", "-q", "main");

        List<String> executed = repository.integrate().cherryPick(picked).get().map(ExecutedCommand::line);

        assertEquals(List.of("git cherry-pick " + picked.value()), executed);
        assertEquals(
                List.of("Feature work", "Initial commit"),
                commits().get().map(c -> c.message().summary()).toList());
        assertEquals("f\n", Files.readString(repo.resolve("f.txt")));
    }

    @Test
    void aConflictingCherryPickIsLeftForTheUserToResolve() throws Exception {
        git("checkout", "-q", "-b", "feature");
        Files.writeString(repo.resolve("README.md"), "feature\n");
        git("commit", "-q", "-am", "Feature edit");
        Sha picked = commits().get().head().sha();
        git("checkout", "-q", "main");
        Files.writeString(repo.resolve("README.md"), "main\n");
        git("commit", "-q", "-am", "Main edit");

        GitFailure failure = repository.integrate().cherryPick(picked).getLeft();

        assertEquals(
                new GitError.StoppedOnConflicts(Option.of(new InProgress.CherryPicking(picked)), 1), failure.error());
        assertEquals(
                List.of("git cherry-pick " + picked.value()), failure.commands().map(ExecutedCommand::line));
        assertTrue(gitSucceedsIn(repo, "rev-parse", "--verify", "--quiet", "CHERRY_PICK_HEAD"));
    }

    @Test
    void aCherryPickThatChangesNothingIsAborted() throws Exception {
        git("checkout", "-q", "-b", "feature");
        Files.writeString(repo.resolve("f.txt"), "f\n");
        git("add", "f.txt");
        git("commit", "-q", "-m", "Feature work");
        Sha picked = commits().get().head().sha();
        git("checkout", "-q", "main");
        Files.writeString(repo.resolve("f.txt"), "f\n");
        git("add", "f.txt");
        git("commit", "-q", "-m", "Same work");

        assertInstanceOf(
                GitError.CommandFailed.class,
                repository.integrate().cherryPick(picked).getLeft().error());

        assertEquals(
                List.of("Same work", "Initial commit"),
                commits().get().map(c -> c.message().summary()).toList());
        assertTrue(!gitSucceedsIn(repo, "rev-parse", "--verify", "--quiet", "CHERRY_PICK_HEAD"));
    }

    @Test
    void aCherryPickThatDoesNotStartIsNotAborted() throws Exception {
        git("checkout", "-q", "-b", "feature");
        Files.writeString(repo.resolve("f.txt"), "f\n");
        git("add", "f.txt");
        git("commit", "-q", "-m", "Feature work");
        Sha picked = commits().get().head().sha();
        git("checkout", "-q", "main");
        // Picking it would overwrite it, so git refuses before it starts.
        Files.writeString(repo.resolve("f.txt"), "untracked\n");

        GitFailure failure = repository.integrate().cherryPick(picked).getLeft();

        assertInstanceOf(GitError.CommandFailed.class, failure.error());
        assertEquals(
                List.of("git cherry-pick " + picked.value()), failure.commands().map(ExecutedCommand::line));
        assertEquals(Option.none(), failure.cleanupFailed());
    }

    private String git(String... args) throws IOException, InterruptedException, TimeoutException {
        return gitIn(repo, args);
    }

    private Either<GitError, List<Commit>> commits() {
        return CliGitFixture.commits(repository);
    }
}
