package net.vanfleteren.jazygit.core.git;

import static net.vanfleteren.jazygit.core.git.CliGitFixture.gitIn;
import static net.vanfleteren.jazygit.core.git.CliGitFixture.gitSucceedsIn;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeoutException;
import net.vanfleteren.jazygit.core.git.branches.CliRemotes;
import net.vanfleteren.jazygit.core.git.model.*;
import net.vanfleteren.jazygit.core.git.process.GitError;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.core.repo.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Opening, making and configuring a {@link CliGit}, and what its operations report, whichever role they are of.
 */
class CliGitTest {

    @TempDir
    Path repo;

    private Git repository;

    @BeforeEach
    void setUp() throws Exception {
        repository = CliGitFixture.init(repo);
    }

    /**
     * Credentials replace the remotes of a {@code Git} with those of the git command line tool, also when its other
     * roles are stubs, and leave the rest as it was (ADR 69).
     */
    @Test
    void credentialsOnlyReplaceTheRemotes() {
        Git stub = StubGit.of(repo);
        Git withCredentials = CliGit.withCredentials(stub, io.vavr.collection.HashMap::empty);

        assertInstanceOf(CliRemotes.class, withCredentials.remotes());
        assertEquals(stub.withRemotes(withCredentials.remotes()), withCredentials);
    }

    @Test
    void openingFailsOutsideARepositoryAndForABareOne(@TempDir Path elsewhere) throws Exception {
        Path plain = Files.createDirectories(elsewhere.resolve("plain"));
        Path bare = elsewhere.resolve("bare.git");
        gitIn(elsewhere, "init", "-q", "--bare", bare.toString());

        assertInstanceOf(GitError.NoWorktree.class, CliGit.open(bare).getLeft());
        assumeTrue(!gitSucceedsIn(plain, "rev-parse"), "the temporary directory is inside a repository");
        assertEquals(new GitError.NotARepository(plain), CliGit.open(plain).getLeft());
    }

    @Test
    void initMakesARepositoryWithoutCommitsAndOpensIt(@TempDir Path elsewhere) throws Exception {
        Path plain = Files.createDirectories(elsewhere.resolve("plain"));
        assumeTrue(!gitSucceedsIn(plain, "rev-parse"), "the temporary directory is inside a repository");

        Git initialised = CliGit.init(plain).get();

        assertEquals(plain.toRealPath(), initialised.worktree().toRealPath());
        assertEquals(Option.none(), initialised.status().read().get().headCommit());
        assertTrue(gitSucceedsIn(plain, "rev-parse", "--git-dir"));
    }

    @Test
    void initFailsWhereGitCannotMakeARepository(@TempDir Path elsewhere) {
        Path missing = elsewhere.resolve("missing");

        assertTrue(CliGit.init(missing).isLeft());
    }

    @Test
    void emptyRepositoryHasNoCommits() throws Exception {
        Path empty = repo.resolve("empty");
        Files.createDirectories(empty);
        gitIn(empty, "init", "-q", "-b", "main");
        Git emptyRepository = CliGit.open(empty).get();
        // Without a commit in HEAD there is no log to read (ADR 49).
        assertEquals(Option.none(), emptyRepository.status().read().get().headCommit());
        assertEquals(
                List.of(),
                emptyRepository.branches().all().map(AllBranches::local).get());
        // Without a commit there is neither a stash nor a HEAD to fall back to.
        assertEquals(List.of(), emptyRepository.stashes().list().get());
    }

    @Test
    void mutatingCommandsReturnTheCommandLinesTheyRan() throws Exception {
        Files.writeString(repo.resolve("README.md"), "changed\n");
        Files.writeString(repo.resolve("new file.txt"), "new\n");

        assertEquals(
                List.of("git add -- README.md 'new file.txt'"),
                repository
                        .index()
                        .stage(List.of(new RepoPath("README.md"), new RepoPath("new file.txt")))
                        .get()
                        .map(ExecutedCommand::line));
        assertEquals(
                List.of("git rm --cached --force --quiet -- 'new file.txt'"),
                repository
                        .index()
                        .unstageNew(List.of(new RepoPath("new file.txt")))
                        .get()
                        .map(ExecutedCommand::line));
        assertEquals(List.of(), repository.index().unstage(List.of()).get());
        assertEquals(
                List.of("git commit --quiet -m 'a summary' -m details"),
                repository
                        .commits()
                        .commit(new CommitMessage("a summary", "details"))
                        .get()
                        .map(ExecutedCommand::line));

        git("branch", "feature");
        Files.writeString(repo.resolve("README.md"), "dirty\n");
        assertEquals(
                List.of("git stash push --quiet", "git checkout --quiet feature --", "git stash pop --index --quiet"),
                repository
                        .checkout()
                        .branchWithStash(new BranchName("feature"))
                        .get()
                        .map(ExecutedCommand::line));
    }

    @Test
    void aFailingCommandReportsTheCommandsRunUpToAndIncludingIt() {
        assertEquals(
                List.of("git branch --delete -- missing"),
                repository
                        .remotes()
                        .deleteBranch(
                                new BranchName("missing"),
                                new RemoteBranch("origin", new BranchName("missing")),
                                DeleteScope.LOCAL)
                        .getLeft()
                        .commands()
                        .map(ExecutedCommand::line));
        assertEquals(
                List.of("git add -- missing.txt"),
                repository
                        .index()
                        .stage(List.of(new RepoPath("missing.txt")))
                        .getLeft()
                        .commands()
                        .map(ExecutedCommand::line));
    }

    private String git(String... args) throws IOException, InterruptedException, TimeoutException {
        return gitIn(repo, args);
    }
}
