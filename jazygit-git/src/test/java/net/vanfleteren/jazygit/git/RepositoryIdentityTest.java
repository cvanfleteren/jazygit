package net.vanfleteren.jazygit.git;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.vavr.control.Option;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;

/**
 * Which repository a working tree belongs to, in the layouts git makes: a repository with linked worktrees, and
 * a bare one with worktrees next to it.
 */
class RepositoryIdentityTest {

    @TempDir
    Path dir;

    private static void git(Path in, String... args) throws Exception {
        java.util.List<String> command = new java.util.ArrayList<>(java.util.List.of(
                "git", "-c", "user.name=Test", "-c", "user.email=test@example.com", "-c", "commit.gpgsign=false"));
        command.addAll(java.util.List.of(args));
        ProcessResult result = new ProcessExecutor()
                .command(command)
                .directory(in.toFile())
                .redirectErrorStream(true)
                .readOutput(true)
                .exitValueAny()
                .execute();
        assertEquals(
                0, result.getExitValue(), () -> "git " + String.join(" ", args) + " failed: " + result.outputUTF8());
    }

    private static boolean gitAvailable() {
        try {
            return new ProcessExecutor("git", "--version").execute().getExitValue() == 0;
        } catch (IOException | java.util.concurrent.TimeoutException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @BeforeEach
    void setUp() {
        assumeTrue(gitAvailable(), "git is not installed");
    }

    @Test
    void theMainWorktreeIsTheRepositoryAndALinkedOneIsNamedAfterIt() throws Exception {
        Path main = Files.createDirectories(dir.resolve("app"));
        git(main, "init", "-q", "-b", "main");
        git(main, "commit", "-q", "--allow-empty", "-m", "Initial commit");
        git(main, "worktree", "add", "-q", "-b", "feature", "../app-feature");
        Path linked = dir.resolve("app-feature");

        RepositoryIdentity ofMain = RepositoryIdentity.read(main).get();
        RepositoryIdentity ofLinked = RepositoryIdentity.read(linked).get();

        assertEquals("app", ofMain.label());
        assertEquals("app(app-feature)", ofLinked.label());
        assertEquals(ofMain.commonDir(), ofLinked.commonDir());
    }

    @Test
    void worktreesNextToABareRepositoryAreNamedAfterTheDirectoryHoldingIt() throws Exception {
        Path project = Files.createDirectories(dir.resolve("proj"));
        git(project, "init", "-q", "--bare", "-b", "main", ".bare");
        Files.writeString(project.resolve(".git"), "gitdir: ./.bare\n");
        git(project, "worktree", "add", "-q", "--orphan", "-b", "main", "main");

        assertEquals(
                Option.of("proj(main)"),
                RepositoryIdentity.read(project.resolve("main")).map(RepositoryIdentity::label));
    }

    @Test
    void aBareRepositoryNamedAfterGitLeavesItOut() throws Exception {
        git(dir, "init", "-q", "--bare", "-b", "main", "app.git");
        Path bare = dir.resolve("app.git");
        git(
                bare,
                "worktree",
                "add",
                "-q",
                "--orphan",
                "-b",
                "main",
                dir.resolve("work").toString());

        assertEquals(
                Option.of("app(work)"),
                RepositoryIdentity.read(dir.resolve("work")).map(RepositoryIdentity::label));
    }

    @Test
    void aDirectoryWithoutAGitDirectoryBelongsToNoneAndIsNamedAfterItself() throws Exception {
        Path plain = Files.createDirectories(dir.resolve("plain"));

        assertEquals(Option.none(), RepositoryIdentity.read(plain));
        assertEquals("plain", RepositoryIdentity.labelOf(plain));
        assertEquals("/", RepositoryIdentity.labelOf(Path.of("/")));
    }
}
