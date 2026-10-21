package net.vanfleteren.jazygit.git;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.model.Worktree;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;

/**
 * Listing, adding and removing worktrees: the parser on fixed output of {@code git worktree list --porcelain -z},
 * and {@link CliGitInfoProvider} against a real repository.
 */
class CliWorktreeTest {

    private static final String A = "a".repeat(40);
    private static final String ZEROES = "0".repeat(40);

    @TempDir
    Path dir;

    private Path main;

    private static String worktree(String... lines) {
        return String.join("\0", lines) + "\0\0";
    }

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

    private static void deleteRecursively(Path directory) throws IOException {
        try (var paths = Files.walk(directory)) {
            for (Path path : List.ofAll(paths::iterator).reverse()) {
                Files.delete(path);
            }
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        main = Files.createDirectories(dir.resolve("main")).toRealPath();
    }

    @Test
    void theWorktreesAreReadWithTheirBranchOrDetachedHeadAndWhatKeepsThemFromBeingRemoved() {
        String raw = worktree("worktree /repos/app", "HEAD " + A, "branch refs/heads/main")
                + worktree("worktree /repos/app feature", "HEAD " + A, "branch refs/heads/feature/x", "locked on usb")
                + worktree("worktree /repos/detached", "HEAD " + A, "detached", "locked")
                + worktree(
                        "worktree /repos/gone",
                        "HEAD " + A,
                        "branch refs/heads/gone",
                        "prunable gitdir file points to non-existent location");

        assertEquals(
                List.of(
                        new Worktree(
                                Path.of("/repos/app"),
                                Option.of(new Sha(A)),
                                Option.of(new Branch.Name("main")),
                                true,
                                false,
                                Option.none(),
                                false),
                        new Worktree(
                                Path.of("/repos/app feature"),
                                Option.of(new Sha(A)),
                                Option.of(new Branch.Name("feature/x")),
                                false,
                                true,
                                Option.of("on usb"),
                                false),
                        new Worktree(
                                Path.of("/repos/detached"),
                                Option.of(new Sha(A)),
                                Option.none(),
                                false,
                                false,
                                Option.of(""),
                                false),
                        new Worktree(
                                Path.of("/repos/gone"),
                                Option.of(new Sha(A)),
                                Option.of(new Branch.Name("gone")),
                                false,
                                false,
                                Option.none(),
                                true)),
                CliWorktree.parse(raw, Path.of("/repos/app feature")).get());
    }

    @Test
    void aBareRepositoryIsLeftOutAndNoneOfItsWorktreesIsTheMainOne() {
        String raw = worktree("worktree /repos/app.git", "bare")
                + worktree("worktree /repos/linked", "HEAD " + A, "branch refs/heads/main");

        assertEquals(
                List.of(new Worktree(
                        Path.of("/repos/linked"),
                        Option.of(new Sha(A)),
                        Option.of(new Branch.Name("main")),
                        false,
                        true,
                        Option.none(),
                        false)),
                CliWorktree.parse(raw, Path.of("/repos/linked")).get());
    }

    @Test
    void aRepositoryWithoutCommitsHasNoHeadAndMalformedOutputIsAnError() {
        assertEquals(
                List.of(Option.none()),
                CliWorktree.parse(
                                worktree("worktree /repos/new", "HEAD " + ZEROES, "branch refs/heads/main"),
                                Path.of("/repos/new"))
                        .get()
                        .map(Worktree::head));
        assertEquals(
                new GitError.UnexpectedOutput("/repos/app"),
                CliWorktree.parse(worktree("/repos/app"), Path.of("/repos/app")).getLeft());
        assertEquals(
                new GitError.UnexpectedOutput("not a sha"),
                CliWorktree.parse(worktree("worktree /repos/app", "HEAD not a sha"), Path.of("/repos/app"))
                        .getLeft());
    }

    @Test
    void aNewWorktreeGoesNextToThisOneNamedAfterItAndTheBranch() {
        Worktree app =
                new Worktree(Path.of("/repos/app"), Option.none(), Option.none(), true, true, Option.none(), false);

        assertEquals("/repos/app-feature-login", app.siblingFor(new Branch.Name("feature/login")));
        assertEquals("app", app.name());
        // Next to a bare repository, worktrees are named after their branch only.
        assertEquals("/repos/feature-login", app.nextTo(new Branch.Name("feature/login")));
    }

    @Test
    void worktreesAreAddedListedAndRemovedAndOneWithChangesOnlyByForce() throws Exception {
        assumeTrue(gitAvailable(), "git is not installed");
        git(main, "init", "-q", "-b", "main");
        git(main, "commit", "-q", "--allow-empty", "-m", "Initial commit");
        git(main, "branch", "feature");
        CliGitInfoProvider provider = CliGitInfoProvider.open(main).get();
        Path linked = main.resolveSibling("linked");

        List<String> added = provider.addWorktree(Path.of("../linked"), new Branch.Name("feature"))
                .get()
                .map(ExecutedCommand::line);

        assertEquals(List.of("git worktree add --quiet " + linked + " feature"), added);
        List<Worktree> worktrees = provider.worktrees().get();
        assertEquals(List.of(main, linked), worktrees.map(Worktree::path));
        assertEquals(List.of(true, false), worktrees.map(Worktree::current));
        assertEquals(List.of(true, false), worktrees.map(Worktree::main));
        assertEquals(
                List.of(Option.of(new Branch.Name("main")), Option.of(new Branch.Name("feature"))),
                worktrees.map(Worktree::branch));
        assertEquals(
                List.of(false, true),
                CliGitInfoProvider.open(linked).get().worktrees().get().map(Worktree::current));

        Files.writeString(linked.resolve("new.txt"), "untracked\n");
        assertInstanceOf(
                GitError.WorktreeHasChanges.class,
                provider.removeWorktree(linked, false).getLeft().error());
        assertTrue(Files.exists(linked.resolve("new.txt")));

        provider.removeWorktree(linked, true).get();
        assertFalse(Files.exists(linked));
        assertEquals(List.of(main), provider.worktrees().get().map(Worktree::path));
    }

    @Test
    void worktreesAreLockedUnlockedAndPrunedOnceTheirDirectoryIsGone() throws Exception {
        assumeTrue(gitAvailable(), "git is not installed");
        git(main, "init", "-q", "-b", "main");
        git(main, "commit", "-q", "--allow-empty", "-m", "Initial commit");
        git(main, "branch", "feature");
        CliGitInfoProvider provider = CliGitInfoProvider.open(main).get();
        Path linked = main.resolveSibling("linked");
        provider.addWorktree(linked, new Branch.Name("feature")).get();

        assertEquals(
                List.of("git worktree lock " + linked),
                provider.lockWorktree(linked, true).get().map(ExecutedCommand::line));
        assertEquals(
                List.of(Option.none(), Option.of("")),
                provider.worktrees().get().map(Worktree::locked));
        assertInstanceOf(
                GitError.CommandFailed.class,
                provider.lockWorktree(main, true).getLeft().error());
        provider.lockWorktree(linked, false).get();
        assertEquals(List.of(false, false), provider.worktrees().get().map(Worktree::isLocked));

        git(main, "worktree", "lock", "--reason", "on usb", linked.toString());
        assertEquals(Option.of("on usb"), provider.worktrees().get().last().locked());
        git(main, "worktree", "unlock", linked.toString());
        deleteRecursively(linked);
        assertTrue(provider.worktrees().get().last().prunable());

        assertEquals(
                List.of("git worktree prune"), provider.pruneWorktrees().get().map(ExecutedCommand::line));
        assertEquals(List.of(main), provider.worktrees().get().map(Worktree::path));
    }

    @Test
    void aNewBranchIsCheckedOutInANewWorktreeFromABranchOrFollowingARemoteOne() throws Exception {
        assumeTrue(gitAvailable(), "git is not installed");
        Path origin = Files.createDirectories(main.resolveSibling("origin"));
        git(origin, "init", "-q", "-b", "main");
        git(origin, "commit", "-q", "--allow-empty", "-m", "Initial commit");
        git(origin, "branch", "shared");
        git(
                main.getParent(),
                "clone",
                "-q",
                origin.toString(),
                main.getFileName().toString());
        CliGitInfoProvider provider = CliGitInfoProvider.open(main).get();
        Path fromLocal = main.resolveSibling("from-local");
        Path fromRemote = main.resolveSibling("from-remote");

        List<String> added = provider.addWorktreeWithNewBranch(
                        fromLocal, new Branch.Name("topic"), "main", Option.of(new Branch.Name("main")), false)
                .get()
                .map(ExecutedCommand::line);
        provider.addWorktreeWithNewBranch(
                        fromRemote, new Branch.Name("shared"), "refs/remotes/origin/shared", Option.none(), true)
                .get();

        assertEquals("git worktree add --quiet -b topic " + fromLocal + " main", added.head());
        assertEquals(
                List.of(
                        Option.of(new Branch.Name("main")),
                        Option.of(new Branch.Name("topic")),
                        Option.of(new Branch.Name("shared"))),
                provider.worktrees().get().map(Worktree::branch));
        assertEquals(
                Option.of(new Branch.Name("main")),
                CliConfig.parent(main, new Branch.Name("topic")).get());
        assertEquals(
                Option.of("origin/shared"),
                provider.branches()
                        .get()
                        .find(branch -> branch.name().value().equals("shared"))
                        .flatMap(branch -> branch.upstream())
                        .map(upstream -> upstream.branch().shortName()));
    }

    @Test
    void aBranchThatIsCheckedOutAlreadyGetsNoWorktree() throws Exception {
        assumeTrue(gitAvailable(), "git is not installed");
        git(main, "init", "-q", "-b", "main");
        git(main, "commit", "-q", "--allow-empty", "-m", "Initial commit");
        CliGitInfoProvider provider = CliGitInfoProvider.open(main).get();

        GitFailure<GitError.ProcessError> failure = provider.addWorktree(
                        main.resolveSibling("other"), new Branch.Name("main"))
                .getLeft();

        assertInstanceOf(GitError.CommandFailed.class, failure.error());
        assertFalse(Files.exists(main.resolveSibling("other")));
    }
}
