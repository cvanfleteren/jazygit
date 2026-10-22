package net.vanfleteren.jazygit.git;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.vanfleteren.jazygit.git.model.AllBranches;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.ChangeType;
import net.vanfleteren.jazygit.git.model.DiffOptions;
import net.vanfleteren.jazygit.git.model.Diffs;
import net.vanfleteren.jazygit.git.model.FileEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;

/**
 * A repository opened in a linked worktree, whose {@code .git} is a file and whose own state (its index, HEAD and
 * what it is in the middle of) lives in the main repository: it is read as its own.
 */
class LinkedWorktreeTest {

    @TempDir
    Path dir;

    private Path main;
    private Path linked;

    private static void git(Path in, String... args) throws Exception {
        ProcessResult result = run(in, args);
        assertEquals(
                0, result.getExitValue(), () -> "git " + String.join(" ", args) + " failed: " + result.outputUTF8());
    }

    private static void gitAnyExit(Path in, String... args) throws Exception {
        run(in, args);
    }

    private static ProcessResult run(Path in, String... args) throws Exception {
        java.util.List<String> command = new java.util.ArrayList<>(java.util.List.of(
                "git", "-c", "user.name=Test", "-c", "user.email=test@example.com", "-c", "commit.gpgsign=false"));
        command.addAll(java.util.List.of(args));
        return new ProcessExecutor()
                .command(command)
                .directory(in.toFile())
                .redirectErrorStream(true)
                .readOutput(true)
                .exitValueAny()
                .execute();
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
    void setUp() throws Exception {
        assumeTrue(gitAvailable(), "git is not installed");
        main = Files.createDirectories(dir.resolve("main")).toRealPath();
        linked = main.resolveSibling("linked");
        git(main, "init", "-q", "-b", "main");
        Files.writeString(main.resolve("a.txt"), "a\n");
        git(main, "add", "a.txt");
        git(main, "commit", "-q", "-m", "Initial commit");
        git(main, "branch", "feature");
        git(main, "worktree", "add", "-q", linked.toString(), "feature");
    }

    @Test
    void itOpensAtItsOwnRootAndListsItsOwnBranchAsTheCurrentOne() {
        CliGitInfoProvider provider = CliGitInfoProvider.open(linked).get();

        assertEquals(linked, provider.worktree());
        assertTrue(Files.isRegularFile(linked.resolve(".git")));
        List<Branch> branches = provider.allBranches().map(AllBranches::local).get();
        assertEquals(
                List.of("feature", "main"), branches.map(branch -> branch.name().value()));
        assertEquals(List.of(Option.none(), Option.of(main)), branches.map(Branch::worktree));
    }

    @Test
    void theMainWorktreeSeesTheBranchCheckedOutInTheLinkedOne() {
        List<Branch> branches = CliGitInfoProvider.open(main)
                .get()
                .allBranches()
                .map(AllBranches::local)
                .get();

        assertEquals(List.of(Option.none(), Option.of(linked)), branches.map(Branch::worktree));
        assertEquals(List.of(false, true), branches.map(Branch::checkedOutElsewhere));
    }

    @Test
    void theStatusAndTheDiffAreThoseOfTheLinkedWorktree() throws Exception {
        Files.writeString(linked.resolve("new.txt"), "brand new\n");
        CliGitInfoProvider provider = CliGitInfoProvider.open(linked).get();

        List<FileEntry> files = provider.status().get().files();
        Diffs diffs = provider.diff(files, DiffOptions.DEFAULT).get();

        assertEquals(List.of(new FileEntry("new.txt", ChangeType.UNTRACKED)), files);
        assertTrue(diffs.unstaged().text().contains("+brand new"), diffs.toString());
        // The untracked file was diffed in a copy of the index; the real one still does not have it.
        assertEquals(
                List.of(new FileEntry("new.txt", ChangeType.UNTRACKED)),
                provider.status().get().files());
        assertEquals(
                List.of(), CliGitInfoProvider.open(main).get().status().get().files());
    }

    @Test
    void theMainWorktreeSeesWhetherTheLinkedOneHasChanges() throws Exception {
        CliGitInfoProvider provider = CliGitInfoProvider.open(main).get();
        assertFalse(provider.hasChanges(linked).get());

        Files.writeString(linked.resolve(".gitignore"), "ignored.txt\n");
        git(linked, "add", ".gitignore");
        git(linked, "commit", "-q", "-m", "Ignore");
        Files.writeString(linked.resolve("ignored.txt"), "ignored\n");
        assertFalse(provider.hasChanges(linked).get());

        Files.writeString(linked.resolve("new.txt"), "brand new\n");
        assertTrue(provider.hasChanges(linked).get());
        assertFalse(provider.hasChanges(main).get());
    }

    @Test
    void aMergeStoppedInTheLinkedWorktreeIsUnfinishedThereOnly() throws Exception {
        Files.writeString(main.resolve("a.txt"), "main\n");
        git(main, "commit", "-q", "-am", "main changes a");
        Files.writeString(linked.resolve("a.txt"), "feature\n");
        git(linked, "commit", "-q", "-am", "feature changes a");
        gitAnyExit(linked, "merge", "main");

        assertTrue(Unfinished.read(linked).get().merging());
        assertFalse(Unfinished.read(main).get().any());
    }
}
