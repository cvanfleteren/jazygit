package net.vanfleteren.jazygit.git;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.control.Option;
import java.nio.file.Files;
import java.nio.file.Path;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.RepoStatus.Head;
import net.vanfleteren.jazygit.git.model.Sha;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HeadFileTest {

    @TempDir
    Path dir;

    private static Option<Head> onBranch(String name) {
        return Option.of(new Head.OnBranch(new Branch.Name(name), Option.none()));
    }

    private Path repo(String head) throws Exception {
        Path worktree = Files.createDirectories(dir.resolve("repo"));
        Files.createDirectories(worktree.resolve(".git"));
        Files.writeString(worktree.resolve(".git/HEAD"), head);
        return worktree;
    }

    @Test
    void readsTheCheckedOutBranch() throws Exception {
        assertEquals(onBranch("feature/x"), HeadFile.read(repo("ref: refs/heads/feature/x\n")));
    }

    @Test
    void readsTheCommitOfADetachedHead() throws Exception {
        String sha = "0123456789abcdef0123456789abcdef01234567";

        assertEquals(Option.of(new Head.Detached(new Sha(sha))), HeadFile.read(repo(sha + "\n")));
    }

    @Test
    void followsTheGitFileOfALinkedWorktree() throws Exception {
        Path gitDir = Files.createDirectories(dir.resolve("main/.git/worktrees/linked"));
        Files.writeString(gitDir.resolve("HEAD"), "ref: refs/heads/linked\n");
        Path worktree = Files.createDirectories(dir.resolve("linked"));
        Files.writeString(worktree.resolve(".git"), "gitdir: " + gitDir + "\n");

        assertEquals(onBranch("linked"), HeadFile.read(worktree));
    }

    @Test
    void followsARelativeGitFile() throws Exception {
        Path gitDir = Files.createDirectories(dir.resolve("main/.git/modules/sub"));
        Files.writeString(gitDir.resolve("HEAD"), "ref: refs/heads/main\n");
        Path worktree = Files.createDirectories(dir.resolve("main/sub"));
        Files.writeString(worktree.resolve(".git"), "gitdir: ../.git/modules/sub\n");

        assertEquals(onBranch("main"), HeadFile.read(worktree));
    }

    @Test
    void nothingWhenItCannotBeRead() throws Exception {
        assertEquals(Option.none(), HeadFile.read(Files.createDirectories(dir.resolve("plain"))));
        assertEquals(Option.none(), HeadFile.read(repo("")));
        assertEquals(Option.none(), HeadFile.read(repo("ref: refs/remotes/origin/main\n")));
    }
}
