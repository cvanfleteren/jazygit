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
import net.vanfleteren.jazygit.core.git.process.GitFailure;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.core.repo.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Rewording a commit of a real repository, wherever it is in the history.
 */
class CliRewordTest {

    @TempDir
    Path repo;

    private Git repository;

    @BeforeEach
    void setUp() throws Exception {
        repository = CliGitFixture.init(repo);
    }

    @Test
    void rewordChangesTheMessageOfAnyCommitAndKeepsTheRest() throws Exception {
        Files.writeString(repo.resolve("a.txt"), "a\n");
        git("add", "a.txt");
        git("commit", "-q", "-m", "Second");
        Files.writeString(repo.resolve("b.txt"), "b\n");
        git("add", "b.txt");
        git("commit", "-q", "-m", "Third");
        Files.writeString(repo.resolve("README.md"), "dirty\n");
        List<Commit> before = commits().get();

        List<String> executed = repository
                .commits()
                .reword(before.get(1).sha(), new CommitMessage("Second, better", "because"))
                .get()
                .map(ExecutedCommand::line);

        assertEquals(
                List.of("git -c rebase.abbreviateCommands=false -c core.abbrev=no -c commit.cleanup=whitespace"
                        + " rebase --interactive --autostash --keep-empty --rebase-merges "
                        + before.get(1).sha().value() + "^"),
                executed);
        List<Commit> after = commits().get();
        assertEquals(
                List.of("Third", "Second, better", "Initial commit"),
                after.map(c -> c.message().summary()).toList());
        assertEquals("because", after.get(1).message().description());
        assertEquals("hello\n".replace("hello", "dirty"), Files.readString(repo.resolve("README.md")));
        assertTrue(Files.exists(repo.resolve("a.txt")) && Files.exists(repo.resolve("b.txt")));
    }

    @Test
    void rewordOfTheFirstCommitRebasesFromTheRoot() throws Exception {
        Files.writeString(repo.resolve("a.txt"), "a\n");
        git("add", "a.txt");
        git("commit", "-q", "-m", "Second");

        repository
                .commits()
                .reword(commits().get().get(1).sha(), new CommitMessage("Start", ""))
                .get();

        assertEquals(
                List.of("Second", "Start"),
                commits().get().map(c -> c.message().summary()).toList());
    }

    @Test
    void rewordOfALastCommitAmendsIt() throws Exception {
        List<String> executed = repository
                .commits()
                .reword(commits().get().head().sha(), new CommitMessage("Renamed", ""))
                .get()
                .map(ExecutedCommand::line);

        assertEquals(1, executed.size());
        assertTrue(executed.head().startsWith("git commit"), executed.toString());
        assertEquals("Renamed", commits().get().head().message().summary());
    }

    @Test
    void rewordOfAMergeKeepsItsParents() throws Exception {
        git("checkout", "-q", "-b", "feature");
        Files.writeString(repo.resolve("f.txt"), "f\n");
        git("add", "f.txt");
        git("commit", "-q", "-m", "Feature work");
        git("checkout", "-q", "main");
        Files.writeString(repo.resolve("m.txt"), "m\n");
        git("add", "m.txt");
        git("commit", "-q", "-m", "Main work");
        git("merge", "-q", "--no-ff", "-m", "Merge feature", "feature");
        Files.writeString(repo.resolve("after.txt"), "x\n");
        git("add", "after.txt");
        git("commit", "-q", "-m", "After merge");
        Commit merge = commits().get().get(1);

        repository
                .commits()
                .reword(merge.sha(), new CommitMessage("Merged the feature", ""))
                .get();

        List<Commit> after = commits().get();
        assertEquals("After merge", after.head().message().summary());
        assertEquals("Merged the feature", after.get(1).message().summary());
        assertTrue(after.get(1).isMerge());
    }

    @Test
    void rewordFindsTheCommitWhateverTheTodoListLooksLike() throws Exception {
        git("config", "rebase.abbreviateCommands", "true");
        git("config", "core.abbrev", "5");
        Files.writeString(repo.resolve("b.txt"), "b\n");
        git("add", "b.txt");
        git("commit", "-q", "-m", "Second");
        Commit initial = commits().get().get(1);

        repository
                .commits()
                .reword(initial.sha(), new CommitMessage("First", ""))
                .get();

        assertEquals(
                List.of("Second", "First"),
                commits().get().map(c -> c.message().summary()).toList());
    }

    @Test
    void rewordKeepsLinesThatStartWithAHash() throws Exception {
        Files.writeString(repo.resolve("b.txt"), "b\n");
        git("add", "b.txt");
        git("commit", "-q", "-m", "Second");
        Commit initial = commits().get().get(1);

        repository
                .commits()
                .reword(initial.sha(), new CommitMessage("#12 First", "#13 too"))
                .get();

        Commit reworded = commits().get().get(1);
        assertEquals("#12 First", reworded.message().summary());
        assertEquals("#13 too", reworded.message().description());
    }

    @Test
    void aFailingRebaseIsAbortedAndReported() throws Exception {
        GitFailure failure = repository
                .commits()
                .reword(new Sha("0123456789012345678901234567890123456789"), new CommitMessage("x", ""))
                .getLeft();

        assertTrue(
                failure.commands().head().line().startsWith("git rev-list"),
                failure.commands().toString());
        assertEquals("Initial commit", commits().get().head().message().summary());
        assertTrue(Files.notExists(repo.resolve(".git/rebase-merge")));
    }

    private String git(String... args) throws IOException, InterruptedException, TimeoutException {
        return gitIn(repo, args);
    }

    private Either<GitError, List<Commit>> commits() {
        return CliGitFixture.commits(repository);
    }
}
