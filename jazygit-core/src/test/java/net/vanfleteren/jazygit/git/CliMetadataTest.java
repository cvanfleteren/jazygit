package net.vanfleteren.jazygit.git;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.vanfleteren.jazygit.git.model.GitMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;

/**
 * The stamps of the git directory against a real repository: each one changes with what it stands for, and not
 * with what only the status shows.
 */
class CliMetadataTest {

    @TempDir
    Path dir;

    private Path main;

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

    private static GitMetadata metadata(Path workTree) {
        return CliGitInfoProvider.open(workTree).get().metadata().get();
    }

    private void commit(Path in, String file) throws Exception {
        Files.writeString(in.resolve(file), file);
        git(in, "add", file);
        git(in, "commit", "-m", file);
    }

    @BeforeEach
    void setUp() throws Exception {
        assumeTrue(gitAvailable());
        main = Files.createDirectories(dir.resolve("main")).toRealPath();
        git(main, "init", "--initial-branch=main");
        commit(main, "a.txt");
    }

    @Test
    void whatOnlyTheStatusShowsChangesNoStamp() throws Exception {
        GitMetadata before = metadata(main);

        Files.writeString(main.resolve("a.txt"), "changed");
        Files.writeString(main.resolve("b.txt"), "new");
        git(main, "add", "a.txt");

        assertEquals(before, metadata(main));
    }

    @Test
    void aCommitChangesTheBranchesAndTheWorktreesButNotTheStashes() throws Exception {
        GitMetadata before = metadata(main);

        commit(main, "b.txt");

        GitMetadata after = metadata(main);
        assertNotEquals(before.branches(), after.branches());
        assertNotEquals(before.worktrees(), after.worktrees());
        assertEquals(before.stashes(), after.stashes());
    }

    @Test
    void movingABranchInADirectoryOfRefsChangesTheBranches() throws Exception {
        commit(main, "b.txt");
        git(main, "branch", "feature/deep/x");
        GitMetadata before = metadata(main);

        git(main, "branch", "--force", "feature/deep/x", "HEAD~1");

        assertNotEquals(before.branches(), metadata(main).branches());
    }

    @Test
    void anUpstreamThatIsSetChangesTheBranches() throws Exception {
        git(main, "branch", "feature");
        GitMetadata before = metadata(main);

        git(main, "branch", "--set-upstream-to=main", "feature");

        assertNotEquals(before.branches(), metadata(main).branches());
    }

    @Test
    void packingTheRefsChangesTheBranches() throws Exception {
        git(main, "branch", "feature");
        GitMetadata before = metadata(main);

        git(main, "pack-refs", "--all");

        assertNotEquals(before.branches(), metadata(main).branches());
    }

    @Test
    void pushingAndDroppingAStashChangesTheStashes() throws Exception {
        GitMetadata before = metadata(main);

        Files.writeString(main.resolve("a.txt"), "changed");
        git(main, "stash", "push");
        GitMetadata pushed = metadata(main);
        Files.writeString(main.resolve("a.txt"), "changed again");
        git(main, "stash", "push");
        GitMetadata pushedTwice = metadata(main);
        git(main, "stash", "drop");

        assertNotEquals(before.stashes(), pushed.stashes());
        assertNotEquals(pushed.stashes(), pushedTwice.stashes());
        assertNotEquals(pushedTwice.stashes(), metadata(main).stashes());
    }

    @Test
    void addingAndLockingAWorktreeChangesTheWorktrees() throws Exception {
        GitMetadata before = metadata(main);

        Path linked = dir.resolve("linked");
        git(main, "worktree", "add", "-b", "feature", linked.toString());
        GitMetadata added = metadata(main);
        git(main, "worktree", "lock", linked.toString());

        assertNotEquals(before.worktrees(), added.worktrees());
        assertNotEquals(added.worktrees(), metadata(main).worktrees());
    }

    @Test
    void aLinkedWorktreeSeesTheCommitsOfTheOthers() throws Exception {
        Path linked = dir.resolve("linked");
        git(main, "worktree", "add", "-b", "feature", linked.toString());
        GitMetadata fromLinked = metadata(linked);
        commit(main, "b.txt");
        GitMetadata fromMain = metadata(main);
        commit(linked, "c.txt");

        assertNotEquals(fromLinked.branches(), metadata(linked).branches());
        assertNotEquals(fromMain.branches(), metadata(main).branches());
        assertNotEquals(fromMain.worktrees(), metadata(main).worktrees());
    }

    @Test
    void checkingOutInALinkedWorktreeChangesTheBranchesOfTheMainOne() throws Exception {
        git(main, "branch", "other");
        Path linked = dir.resolve("linked");
        git(main, "worktree", "add", "-b", "feature", linked.toString());
        GitMetadata before = metadata(main);

        git(linked, "checkout", "other");

        assertNotEquals(before.branches(), metadata(main).branches());
    }
}
