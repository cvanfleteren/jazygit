package net.vanfleteren.jazygit.model;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * {@link JGitInfoProvider} against a real repository changed through the git CLI: every call
 * reflects the current state of the repository.
 */
class JGitInfoProviderTest {

    @TempDir
    Path repo;

    private JGitInfoProvider provider;

    @BeforeEach
    void setUp() throws Exception {
        assumeTrue(gitAvailable(), "git is not installed");
        git("init", "-q", "-b", "main");
        Files.writeString(repo.resolve("README.md"), "hello\n");
        git("add", "README.md");
        git("commit", "-q", "-m", "Initial commit");
        provider = new JGitInfoProvider(repo);
    }

    @AfterEach
    void tearDown() {
        if (provider != null) {
            provider.close();
        }
    }

    @Test
    void statusFollowsTheWorkingTree() throws Exception {
        assertEquals(List.of(), provider.status().files());

        Files.writeString(repo.resolve("new.txt"), "new\n");
        assertEquals(List.of(new FileEntry("new.txt", ChangeType.UNTRACKED)), provider.status().files());

        git("add", "new.txt");
        assertEquals(List.of(new FileEntry("new.txt", ChangeType.ADDED)), provider.status().files());
    }

    @Test
    void commitsAndStatusFollowNewCommits() throws Exception {
        String headBefore = provider.status().headOid();
        Files.writeString(repo.resolve("new.txt"), "new\n");
        git("add", "new.txt");
        git("commit", "-q", "-m", "Second commit");

        RepoStatus status = provider.status();
        assertNotEquals(headBefore, status.headOid());
        assertEquals(List.of(), status.files());
        List<Commit> commits = provider.commits();
        assertEquals("Second commit", commits.get(0).message());
        assertTrue(status.headOid().startsWith(commits.get(0).shortSha()));
    }

    @Test
    void branchesFollowExternalChanges() throws Exception {
        assertEquals(List.of(new Branch("main", true)), provider.branches());

        git("branch", "foo");
        assertEquals(List.of(new Branch("foo", false), new Branch("main", true)), provider.branches());

        git("checkout", "-q", "foo");
        assertEquals(List.of(new Branch("foo", true), new Branch("main", false)), provider.branches());
    }

    @Test
    void reportsRepositoryNameAndCurrentBranch() throws Exception {
        assertEquals(repo.getFileName().toString(), provider.repositoryName());
        assertEquals("main", provider.status().branchLabel());

        git("checkout", "-q", "-b", "feature");
        assertEquals("feature", provider.status().branchLabel());

        git("checkout", "-q", "--detach");
        assertEquals("HEAD detached at " + provider.commits().get(0).shortSha(), provider.status().branchLabel());
    }

    @Test
    void emptyRepositoryHasNoCommits() throws Exception {
        Path empty = repo.resolve("empty");
        Files.createDirectories(empty);
        gitIn(empty, "init", "-q", "-b", "main");
        try (JGitInfoProvider emptyProvider = new JGitInfoProvider(empty)) {
            assertEquals(RepoStatus.INITIAL, emptyProvider.status().headOid());
            assertEquals(List.of(), emptyProvider.commits());
        }
    }

    private void git(String... args) throws IOException, InterruptedException, TimeoutException {
        gitIn(repo, args);
    }

    private static void gitIn(Path dir, String... args) throws IOException, InterruptedException, TimeoutException {
        List<String> command = new ArrayList<>(List.of("git",
                "-c", "user.name=Test", "-c", "user.email=test@example.com", "-c", "commit.gpgsign=false"));
        command.addAll(List.of(args));
        ProcessResult result = new ProcessExecutor()
                .command(command)
                .directory(dir.toFile())
                .redirectErrorStream(true)
                .readOutput(true)
                .exitValueAny()
                .execute();
        assertEquals(0, result.getExitValue(),
                () -> "git " + String.join(" ", args) + " failed: " + result.outputUTF8());
    }

    private static boolean gitAvailable() {
        try {
            return new ProcessExecutor("git", "--version").execute().getExitValue() == 0;
        } catch (IOException | TimeoutException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
