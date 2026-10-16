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
        Path workTree = Files.createDirectories(dir.resolve("repo"));
        Files.createDirectories(workTree.resolve(".git"));
        Files.writeString(workTree.resolve(".git/HEAD"), head);
        return workTree;
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
        Path workTree = Files.createDirectories(dir.resolve("linked"));
        Files.writeString(workTree.resolve(".git"), "gitdir: " + gitDir + "\n");

        assertEquals(onBranch("linked"), HeadFile.read(workTree));
    }

    @Test
    void followsARelativeGitFile() throws Exception {
        Path gitDir = Files.createDirectories(dir.resolve("main/.git/modules/sub"));
        Files.writeString(gitDir.resolve("HEAD"), "ref: refs/heads/main\n");
        Path workTree = Files.createDirectories(dir.resolve("main/sub"));
        Files.writeString(workTree.resolve(".git"), "gitdir: ../.git/modules/sub\n");

        assertEquals(onBranch("main"), HeadFile.read(workTree));
    }

    @Test
    void nothingWhenItCannotBeRead() throws Exception {
        assertEquals(Option.none(), HeadFile.read(Files.createDirectories(dir.resolve("plain"))));
        assertEquals(Option.none(), HeadFile.read(repo("")));
        assertEquals(Option.none(), HeadFile.read(repo("ref: refs/remotes/origin/main\n")));
    }
}
