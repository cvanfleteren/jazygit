package net.vanfleteren.jazygit.git.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.vanfleteren.jazygit.git.CliGit;
import net.vanfleteren.jazygit.git.model.Blame;
import net.vanfleteren.jazygit.git.model.BlameAt;
import net.vanfleteren.jazygit.git.model.BlameOptions;
import net.vanfleteren.jazygit.git.model.Sha;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zeroturnaround.exec.ProcessExecutor;

/**
 * Blame on a version of a file, at each revision a version is read at (ADR 147).
 */
class CliBlameTest {

    @TempDir
    Path repo;

    @Test
    void aCommitAndItsParentAreBlamedAsTheyWere() throws Exception {
        Sha first = commit("f.txt", "a\nb\n", "first");
        Sha second = commit("f.txt", "a\nB\nc\n", "second");

        Blame now = blame(new BlameAt.InCommit(second, "f.txt"));
        assertEquals(List.of(first, second, second), shas(now));
        assertEquals("second", now.at(2).get().commit().summary());
        assertEquals(
                Option.of(new Blame.Previous(first, "f.txt")), now.at(2).get().previous());

        assertEquals(List.of(first, first), shas(blame(new BlameAt.InCommit(second, 1, "f.txt"))));
    }

    @Test
    void aRenamedFileIsBlamedOnTheCommitsOfItsOldPath() throws Exception {
        Sha first = commit("old.txt", "a\n", "first");
        git("mv", "old.txt", "new.txt");
        Sha renamed = commit("new.txt", "a\nb\n", "renamed");

        Blame blame = blame(new BlameAt.InCommit(renamed, "new.txt"));

        assertEquals(List.of(first, renamed), shas(blame));
        assertEquals("old.txt", blame.at(1).get().path());
        assertEquals("new.txt", blame.at(2).get().path());
    }

    @Test
    void theOriginalOfABranchIsBlamedAtItsMergeBase() throws Exception {
        Sha first = commit("f.txt", "a\n", "first");
        git("checkout", "-qb", "topic");
        Sha topic = commit("f.txt", "a\ntopic\n", "topic");
        git("checkout", "-q", "main");
        Sha main = commit("f.txt", "main\n", "main");

        Blame original = blame(new BlameAt.AtMergeBase(main, topic, "f.txt"));

        assertEquals(List.of(first), shas(original));
    }

    @Test
    void theFileOnDiskAndTheIndexHaveLinesNotCommittedYet() throws Exception {
        Sha first = commit("f.txt", "a\n", "first");
        Files.writeString(repo.resolve("f.txt"), "a\nstaged\n");
        git("add", "f.txt");
        Files.writeString(repo.resolve("f.txt"), "a\nstaged\nunstaged\n");

        Blame index = blame(new BlameAt.InIndex("f.txt"));
        assertEquals(2, index.lines().size());
        assertEquals(first, index.at(1).get().commit().sha());
        assertFalse(index.at(2).get().commit().committed());

        Blame disk = blame(new BlameAt.OnDisk("f.txt"));
        assertEquals(3, disk.lines().size());
        assertTrue(disk.at(1).get().commit().committed());
        assertFalse(disk.at(3).get().commit().committed());
    }

    @Test
    void theIndexOfAFileThatIsNotUtf8IsBlamedAsItIsStaged() throws Exception {
        byte[] latin1 = "café\nok\n".getBytes(StandardCharsets.ISO_8859_1);
        Sha first = commit("src/l.txt", latin1, "first");
        Files.write(repo.resolve("src/l.txt"), "café\nok\nstaged\n".getBytes(StandardCharsets.ISO_8859_1));
        git("add", "src/l.txt");

        Blame index = blame(new BlameAt.InIndex("src/l.txt"));

        assertEquals(3, index.lines().size());
        assertEquals(List.of(first, first), shas(index).take(2));
        assertFalse(index.at(3).get().commit().committed());
    }

    @Test
    void theUntrackedFilesOfAStashAreBlamedAtItsThirdParent() throws Exception {
        Sha first = commit("f.txt", "a\n", "first");
        Files.writeString(repo.resolve("f.txt"), "a\nchanged\n");
        Files.writeString(repo.resolve("u.txt"), "u\nv\n");
        git("stash", "-qu");
        Sha stash = Sha.parse(git("rev-parse", "stash@{0}").strip()).get();
        Sha untracked = Sha.parse(git("rev-parse", "stash@{0}^3").strip()).get();

        assertEquals(List.of(untracked, untracked), shas(blame(new BlameAt.InCommit(stash, 3, "u.txt"))));
        assertEquals(List.of(first), shas(blame(new BlameAt.InCommit(stash, 1, "f.txt"))));
    }

    @Test
    void theOriginalOfAMergeIsBlamedAtItsFirstParent() throws Exception {
        Sha base = commit("f.txt", "a\nb\nc\nd\ne\n", "base");
        git("checkout", "-qb", "topic");
        commit("f.txt", "topic\nb\nc\nd\ne\n", "topic");
        git("checkout", "-q", "main");
        Sha main = commit("f.txt", "a\nb\nc\nd\nmain\n", "main");
        git("merge", "-q", "--no-ff", "-m", "merge", "topic");
        Sha merge = Sha.parse(git("rev-parse", "HEAD").strip()).get();

        assertEquals(List.of(base, base, base, base, main), shas(blame(new BlameAt.InCommit(merge, 1, "f.txt"))));
    }

    @Test
    void whitespaceIsIgnoredOnlyWhenAsked() throws Exception {
        Sha first = commit("f.txt", "a\nb\n", "first");
        Sha indented = commit("f.txt", "a\n  b\n", "indented");

        assertEquals(List.of(first, indented), shas(blame(new BlameAt.InCommit(indented, "f.txt"))));
        assertEquals(
                List.of(first, first),
                shas(history()
                        .blame(new BlameAt.InCommit(indented, "f.txt"), new BlameOptions(true, Option.none()))
                        .get()));
    }

    @Test
    void aBlockMovedWithinTheFileKeepsItsAuthor() throws Exception {
        String block = "one\ntwo\nthree\nfour\nfive\nsix\nseven\n";
        Sha first = commit("f.txt", block + "rest\n", "first");
        commit("f.txt", "rest\n" + block, "moved");

        Blame blame = blame(new BlameAt.OnDisk("f.txt"));

        // The line the block moved past is too short to be found moved.
        assertEquals(List.fill(7, first), shas(blame).tail());
    }

    @Test
    void aVersionCutIsBlamedUpToItsLastLine() throws Exception {
        Sha first = commit("f.txt", "a\nb\nc\n", "first");

        Blame blame = history()
                .blame(new BlameAt.InCommit(first, "f.txt"), new BlameOptions(false, Option.of(2)))
                .get();

        assertEquals(List.of(first, first), shas(blame));
    }

    @Test
    void aPathGitWouldTakeForAnOptionOrAPatternIsBlamedAsItIs() throws Exception {
        Sha first = commit("-a [x].txt", "a\n", "first");

        assertEquals(List.of(first), shas(blame(new BlameAt.InCommit(first, "-a [x].txt"))));
    }

    @Test
    void aFileNotInTheRevisionCannotBeBlamed() throws Exception {
        Sha first = commit("f.txt", "a\n", "first");

        assertTrue(history()
                .blame(new BlameAt.InCommit(first, "gone.txt"), BlameOptions.DEFAULT)
                .isLeft());
    }

    @Test
    void theFlagsFollowTheOptions() {
        assertEquals(List.of("--porcelain", "-M"), CliBlame.flags(BlameOptions.DEFAULT));
        assertEquals(
                List.of("--porcelain", "-M", "-w", "-L", "1,40"),
                CliBlame.flags(new BlameOptions(true, Option.of(40))));
    }

    private static List<Sha> shas(Blame blame) {
        return blame.lines().map(line -> line.commit().sha()).toList();
    }

    private Blame blame(BlameAt at) {
        return history().blame(at, BlameOptions.DEFAULT).get();
    }

    // Through the repository, as the app reaches it.
    private History history() {
        return CliGit.open(repo).get().history();
    }

    private Sha commit(String path, String text, String message) throws Exception {
        return commit(path, text.getBytes(StandardCharsets.UTF_8), message);
    }

    private Sha commit(String path, byte[] content, String message) throws Exception {
        if (!Files.exists(repo.resolve(".git"))) {
            git("init", "-q", "-b", "main");
            git("config", "user.email", "t@t");
            git("config", "user.name", "Alex Moreau");
        }
        Files.createDirectories(repo.resolve(path).getParent());
        Files.write(repo.resolve(path), content);
        git("add", "--", path);
        git("commit", "-qm", message);
        return Sha.parse(git("rev-parse", "HEAD").strip()).get();
    }

    private String git(String... args) throws Exception {
        java.util.List<String> command = new java.util.ArrayList<>(java.util.List.of("git"));
        command.addAll(java.util.List.of(args));
        return new ProcessExecutor()
                .command(command)
                .directory(repo.toFile())
                .readOutput(true)
                .redirectErrorStream(true)
                .exitValueNormal()
                .execute()
                .outputUTF8();
    }
}
