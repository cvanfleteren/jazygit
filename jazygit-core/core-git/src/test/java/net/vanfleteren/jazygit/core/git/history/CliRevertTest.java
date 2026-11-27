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
 * Reverting a commit on the checked out branch of a real repository.
 */
class CliRevertTest {

    @TempDir
    Path repo;

    private Git repository;

    @BeforeEach
    void setUp() throws Exception {
        repository = CliGitFixture.init(repo);
    }

    @Test
    void revertCommitsTheInverseOfACommitWithGitsMessage() throws Exception {
        Files.writeString(repo.resolve("f.txt"), "f\n");
        git("add", "f.txt");
        git("commit", "-q", "-m", "Add f");
        Commit reverted = commits().get().head();

        List<String> executed = repository.integrate().revert(reverted).get().map(ExecutedCommand::line);

        assertEquals(List.of("git revert --no-edit " + reverted.sha().value()), executed);
        Commit revert = commits().get().head();
        assertEquals("Revert \"Add f\"", revert.message().summary());
        assertEquals(
                "This reverts commit " + reverted.sha().value() + ".",
                revert.message().description());
        assertTrue(!Files.exists(repo.resolve("f.txt")));
    }

    @Test
    void aMergeIsRevertedAgainstItsFirstParent() throws Exception {
        git("checkout", "-q", "-b", "feature");
        Files.writeString(repo.resolve("f.txt"), "f\n");
        git("add", "f.txt");
        git("commit", "-q", "-m", "Feature work");
        git("checkout", "-q", "main");
        git("merge", "-q", "--no-ff", "-m", "Merge feature", "feature");
        Commit merge = commits().get().head();

        List<String> executed = repository.integrate().revert(merge).get().map(ExecutedCommand::line);

        assertEquals(List.of("git revert --no-edit -m 1 " + merge.sha().value()), executed);
        assertEquals(
                "Revert \"Merge feature\"", commits().get().head().message().summary());
        assertTrue(!Files.exists(repo.resolve("f.txt")));
    }

    @Test
    void aConflictingRevertIsLeftForTheUserToResolve() throws Exception {
        Files.writeString(repo.resolve("README.md"), "first\n");
        git("commit", "-q", "-am", "First edit");
        Commit reverted = commits().get().head();
        Files.writeString(repo.resolve("README.md"), "second\n");
        git("commit", "-q", "-am", "Second edit");

        GitFailure failure = repository.integrate().revert(reverted).getLeft();

        assertEquals(
                new GitError.StoppedOnConflicts(Option.of(new InProgress.Reverting(reverted.sha())), 1),
                failure.error());
        assertTrue(failure.commands().forAll(command -> !command.line().contains("--abort")));
        assertTrue(gitSucceedsIn(repo, "rev-parse", "--verify", "--quiet", "REVERT_HEAD"));
    }

    @Test
    void aRevertThatChangesNothingFailsAndLeavesTheBranchAlone() throws Exception {
        Files.writeString(repo.resolve("f.txt"), "f\n");
        git("add", "f.txt");
        git("commit", "-q", "-m", "Add f");
        Commit reverted = commits().get().head();
        git("rm", "-q", "f.txt");
        git("commit", "-q", "-m", "Remove f");

        assertInstanceOf(
                GitError.CommandFailed.class,
                repository.integrate().revert(reverted).getLeft().error());

        assertEquals(
                List.of("Remove f", "Add f", "Initial commit"),
                commits().get().map(c -> c.message().summary()).toList());
        assertTrue(!gitSucceedsIn(repo, "rev-parse", "--verify", "--quiet", "REVERT_HEAD"));
    }

    private String git(String... args) throws IOException, InterruptedException, TimeoutException {
        return gitIn(repo, args);
    }

    private Either<GitError, List<Commit>> commits() {
        return CliGitFixture.commits(repository);
    }
}
