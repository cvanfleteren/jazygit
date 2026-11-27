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
 * Resetting the checked out branch of a real repository.
 */
class CliResetTest {

    @TempDir
    Path repo;

    private Git repository;

    @BeforeEach
    void setUp() throws Exception {
        repository = CliGitFixture.init(repo);
    }

    @Test
    void eachResetMovesTheBranchAndKeepsOrDiscardsTheChangesAsItsModeSays() throws Exception {
        Files.writeString(repo.resolve("a.txt"), "a\n");
        git("add", "a.txt");
        git("commit", "-q", "-m", "Second");
        Sha second = commits().get().head().sha();
        Sha initial = commits().get().get(1).sha();

        List<String> executed = repository
                .commits()
                .reset(new ResetTarget.ToCommit(initial), ResetMode.SOFT)
                .get()
                .map(ExecutedCommand::line);
        assertEquals(List.of("git reset --quiet --soft " + initial.value()), executed);
        assertEquals(
                List.of("Initial commit"),
                commits().get().map(c -> c.message().summary()).toList());
        assertTrue(repository
                .status()
                .read()
                .get()
                .files()
                .exists(file -> file.path().equals(new RepoPath("a.txt")) && file.staged()));

        git("reset", "-q", "--hard", second.value());
        repository
                .commits()
                .reset(new ResetTarget.ToCommit(initial), ResetMode.MIXED)
                .get();
        assertTrue(repository
                .status()
                .read()
                .get()
                .files()
                .exists(file -> file.path().equals(new RepoPath("a.txt")) && !file.staged()));

        git("reset", "-q", "--hard", second.value());
        Files.writeString(repo.resolve("README.md"), "dirty\n");
        Files.writeString(repo.resolve("untracked.txt"), "kept\n");
        repository
                .commits()
                .reset(new ResetTarget.ToCommit(initial), ResetMode.HARD)
                .get();
        assertEquals(
                List.of("Initial commit"),
                commits().get().map(c -> c.message().summary()).toList());
        assertTrue(Files.notExists(repo.resolve("a.txt")));
        assertEquals("hello\n", Files.readString(repo.resolve("README.md")));
        assertEquals("kept\n", Files.readString(repo.resolve("untracked.txt")));
    }

    @Test
    void aResetOntoTheUpstreamMovesTheBranchToWhatWasLastFetched() throws Exception {
        java.nio.file.Path bare = Files.createTempDirectory("remote").resolve("remote.git");
        git("init", "-q", "--bare", "-b", "main", bare.toString());
        git("remote", "add", "origin", bare.toString());
        git("push", "-q", "-u", "origin", "main");
        Sha pushed = commits().get().head().sha();
        git("commit", "-q", "--allow-empty", "-m", "local");
        RemoteBranch upstream = new RemoteBranch("origin", new BranchName("main"));

        List<String> executed = repository
                .commits()
                .reset(new ResetTarget.ToUpstream(new BranchRef.Remote(upstream)), ResetMode.HARD)
                .get()
                .map(ExecutedCommand::line);

        assertEquals(List.of("git reset --quiet --hard refs/remotes/origin/main"), executed);
        assertEquals(pushed, commits().get().head().sha());
    }

    private String git(String... args) throws IOException, InterruptedException, TimeoutException {
        return gitIn(repo, args);
    }

    private Either<GitError, List<Commit>> commits() {
        return CliGitFixture.commits(repository);
    }
}
