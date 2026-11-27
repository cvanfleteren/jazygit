package net.vanfleteren.jazygit.core.git.files;

import static net.vanfleteren.jazygit.core.git.CliGitFixture.gitIn;
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
import net.vanfleteren.jazygit.core.git.model.*;
import net.vanfleteren.jazygit.core.git.model.Diffs;
import net.vanfleteren.jazygit.core.git.process.GitError;
import net.vanfleteren.jazygit.core.repo.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The status, the index and the uncommitted changes of a real repository changed through the git CLI.
 */
class CliWorkingTreeTest {

    @TempDir
    Path repo;

    private Git repository;

    @BeforeEach
    void setUp() throws Exception {
        repository = CliGitFixture.init(repo);
    }

    @Test
    void statusFollowsTheWorkingTree() throws Exception {
        assertEquals(List.of(), repository.status().read().get().files());

        Files.writeString(repo.resolve("new.txt"), "new\n");
        assertEquals(
                List.of(Fixtures.fileEntry("new.txt", ChangeType.UNTRACKED)),
                repository.status().read().get().files());

        git("add", "new.txt");
        assertEquals(
                List.of(Fixtures.fileEntry("new.txt", ChangeType.ADDED)),
                repository.status().read().get().files());
    }

    @Test
    void commitsAndStatusFollowNewCommits() throws Exception {
        Sha headBefore = repository.status().read().get().headCommit().get();
        Files.writeString(repo.resolve("new.txt"), "new\n");
        git("add", "new.txt");
        git("commit", "-q", "-m", "Second commit");

        RepoStatus status = repository.status().read().get();
        assertNotEquals(headBefore, status.headCommit().get());
        assertEquals(List.of(), status.files());
        List<Commit> commits = commits().get();
        assertEquals("Second commit", commits.get(0).message().summary());
        assertEquals(status.headCommit().get(), commits.get(0).sha());
    }

    @Test
    void reportsRepositoryNameAndCurrentBranch() throws Exception {
        assertEquals(repo.getFileName().toString(), repository.repositoryName());
        assertEquals(
                Option.of(new BranchName("main")),
                repository.status().read().get().head().branchName());

        git("checkout", "-q", "-b", "feature");
        assertEquals(
                Option.of(new BranchName("feature")),
                repository.status().read().get().head().branchName());

        git("checkout", "-q", "--detach");
        assertEquals(
                new Head.Detached(commits().get().get(0).sha()),
                repository.status().read().get().head());
    }

    @Test
    void stageAndUnstageMoveFilesInAndOutOfTheIndex() throws Exception {
        Files.writeString(repo.resolve("README.md"), "changed\n");
        Files.writeString(repo.resolve("new.txt"), "new\n");

        repository
                .index()
                .stage(List.of(new RepoPath("README.md"), new RepoPath("new.txt")))
                .get();
        assertEquals(
                List.of(
                        new FileEntry(new RepoPath("README.md"), ChangeType.MODIFIED, true, false),
                        new FileEntry(new RepoPath("new.txt"), ChangeType.ADDED, true, false)),
                repository.status().read().get().files());

        repository.index().unstageNew(List.of(new RepoPath("new.txt"))).get();
        repository.index().unstage(List.of(new RepoPath("README.md"))).get();
        assertEquals(
                List.of(
                        new FileEntry(new RepoPath("README.md"), ChangeType.MODIFIED, false, true),
                        new FileEntry(new RepoPath("new.txt"), ChangeType.UNTRACKED, false, true)),
                repository.status().read().get().files());
    }

    @Test
    void aStagedRenameIsOneEntryThatUnstagesAndDiscardsAsAWhole() throws Exception {
        Files.move(repo.resolve("README.md"), repo.resolve("READ.md"));
        assertEquals(
                List.of(
                        Fixtures.fileEntry("READ.md", ChangeType.UNTRACKED),
                        Fixtures.fileEntry("README.md", ChangeType.DELETED)),
                repository.status().read().get().files());

        repository
                .index()
                .stage(List.of(new RepoPath("READ.md"), new RepoPath("README.md")))
                .get();
        List<FileEntry> files = repository.status().read().get().files();
        assertEquals(
                List.of(FileEntry.renamed(new RepoPath("READ.md"), new RepoPath("README.md"), true, false)), files);

        Diffs diffs = repository.diffs().uncommitted(files, DiffOptions.DEFAULT).get();
        assertTrue(diffs.staged().text().contains("rename from README.md"), diffs.toString());

        repository.index().unstageNew(List.of(new RepoPath("READ.md"))).get();
        repository.index().unstage(List.of(new RepoPath("README.md"))).get();
        assertEquals(
                List.of(
                        Fixtures.fileEntry("READ.md", ChangeType.UNTRACKED),
                        Fixtures.fileEntry("README.md", ChangeType.DELETED)),
                repository.status().read().get().files());

        repository
                .index()
                .stage(List.of(new RepoPath("READ.md"), new RepoPath("README.md")))
                .get();
        repository
                .index()
                .discard(new DiscardPlan(
                        List.of(), List.of(), List.of(new RepoPath("README.md")), List.of(new RepoPath("READ.md"))))
                .get();
        assertEquals(List.of(), repository.status().read().get().files());
    }

    @Test
    void diffCombinesStagedUnstagedAndUntrackedChanges() throws Exception {
        Files.writeString(repo.resolve("README.md"), "staged\n");
        git("add", "README.md");
        Files.writeString(repo.resolve("README.md"), "unstaged\n");
        Files.writeString(repo.resolve("new.txt"), "brand new\n");

        Diffs diffs = repository
                .diffs()
                .uncommitted(repository.status().read().get().files(), DiffOptions.DEFAULT)
                .get();

        assertTrue(diffs.staged().text().contains("-hello"), diffs.toString());
        assertTrue(diffs.staged().text().contains("+staged"), diffs.toString());
        assertFalse(diffs.staged().text().contains("unstaged"), diffs.toString());
        assertTrue(diffs.unstaged().text().contains("-staged"), diffs.toString());
        assertTrue(diffs.unstaged().text().contains("+unstaged"), diffs.toString());
        assertTrue(diffs.unstaged().text().contains("+brand new"), diffs.toString());
        assertEquals(
                new Diffs("", ""),
                repository.diffs().uncommitted(List.of(), DiffOptions.DEFAULT).get());
    }

    @Test
    void diffOfOneFileLeavesOutTheOthers() throws Exception {
        Files.writeString(repo.resolve("README.md"), "changed\n");
        Files.writeString(repo.resolve("new.txt"), "new\n");

        Diffs diffs = repository
                .diffs()
                .uncommitted(List.of(Fixtures.fileEntry("new.txt", ChangeType.UNTRACKED)), DiffOptions.DEFAULT)
                .get();

        assertEquals("", diffs.staged().text());
        assertTrue(diffs.unstaged().text().contains("+new"), diffs.unstaged().text());
        assertFalse(
                diffs.unstaged().text().contains("README.md"), diffs.unstaged().text());
    }

    @Test
    void stageFailsWithGitsExplanationForAnUnknownPath() {
        GitError error = repository
                .index()
                .stage(List.of(new RepoPath("missing.txt")))
                .getLeft()
                .error();

        assertTrue(error instanceof GitError.CommandFailed failed && failed.says("missing.txt"), error.toString());
    }

    private String git(String... args) throws IOException, InterruptedException, TimeoutException {
        return gitIn(repo, args);
    }

    private Either<GitError, List<Commit>> commits() {
        return CliGitFixture.commits(repository);
    }
}
