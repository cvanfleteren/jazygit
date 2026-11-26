package net.vanfleteren.jazygit.core.git.files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.nio.file.Files;
import java.nio.file.Path;
import net.vanfleteren.jazygit.core.git.CliGit;
import net.vanfleteren.jazygit.core.git.model.DiffText;
import net.vanfleteren.jazygit.core.git.model.FileContent;
import net.vanfleteren.jazygit.core.git.model.ParsedDiff;
import net.vanfleteren.jazygit.core.git.process.GitError;
import net.vanfleteren.jazygit.core.repo.RepoPath;
import net.vanfleteren.jazygit.core.repo.Sha;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zeroturnaround.exec.ProcessExecutor;

/**
 * All files of a commit, and what one holds in it, whatever is checked out (ADR 128).
 */
class CliTreeFilesTest {

    @TempDir
    Path repo;

    @Test
    void allFilesOfACommitAreThoseOfItsTreeSortedWhatever() throws Exception {
        Sha main = init();
        git("checkout", "-qb", "other");
        git("rm", "-q", "a.txt");
        Files.writeString(repo.resolve("z.txt"), "z\n");
        git("add", ".");
        git("commit", "-qm", "other");

        assertEquals(
                List.of(new RepoPath("a.txt"), new RepoPath("dir/b.txt")),
                diffs().treeFiles(main).get());
    }

    @Test
    void aFileIsReadAsItIsInTheCommitNotOnDisk() throws Exception {
        Sha main = init();
        Files.writeString(repo.resolve("dir/b.txt"), "edited\n");

        DiffText diff = assertInstanceOf(
                        FileContent.Text.class,
                        diffs().treeContent(main, new RepoPath("dir/b.txt")).get())
                .asDiff();

        ParsedDiff.ParsedFile file = diff.parsed().files().single();
        assertEquals(new RepoPath("dir/b.txt"), file.file().path());
        assertEquals(
                List.of("one", "two"),
                file.hunks()
                        .flatMap(ParsedDiff.ParsedHunk::lines)
                        .map(line -> line.line().text()));
    }

    @Test
    void aBinaryFileALinkAndASubmoduleSayWhatTheyAre() throws Exception {
        init();
        Files.write(repo.resolve("image.bin"), new byte[] {'P', 'N', 'G', 0, 1, 2});
        Files.createSymbolicLink(repo.resolve("link"), Path.of("a.txt"));
        String submodule = "0123456789abcdef0123456789abcdef01234567";
        git("update-index", "--add", "--cacheinfo", "160000," + submodule + ",sub");
        git("add", "image.bin", "link");
        git("commit", "-qm", "kinds");
        Sha head = Sha.parse(git("rev-parse", "HEAD").strip()).get();

        assertEquals(
                new FileContent.Binary(6),
                diffs().treeContent(head, new RepoPath("image.bin")).get());
        assertEquals(
                new FileContent.Link("a.txt"),
                diffs().treeContent(head, new RepoPath("link")).get());
        assertEquals(
                new FileContent.Submodule(Sha.parse(submodule)),
                diffs().treeContent(head, new RepoPath("sub")).get());
        assertTrue(diffs().treeFiles(head).get().contains(new RepoPath("sub")));
    }

    @Test
    void theTextOfAFileIsReadWholeAsItIsInTheCommit() throws Exception {
        Sha main = init();
        Files.writeString(repo.resolve("dir/b.txt"), "edited\n");

        assertEquals(Either.right("one\ntwo\n"), diffs().treeText(main, new RepoPath("dir/b.txt")));
        assertTrue(diffs().treeText(main, new RepoPath("gone.txt")).isLeft());
    }

    @Test
    void aFileNotInTheCommitCannotBeRead() throws Exception {
        Sha main = init();

        assertInstanceOf(
                GitError.Unreadable.class,
                diffs().treeContent(main, new RepoPath("gone.txt")).getLeft());
    }

    @Test
    void anEntryOfLsTreeIsItsModeItsObjectAndItsPath() {
        assertEquals(
                Option.of(new CliTreeFiles.Entry("100644", "abc", "dir/a b.txt")),
                CliTreeFiles.entry("100644 blob abc\tdir/a b.txt"));
        assertEquals(Option.none(), CliTreeFiles.entry("garbage"));
    }

    // Through the repository, as the app reaches it.
    private Diffs diffs() {
        return CliGit.open(repo).get().diffs();
    }

    private Sha init() throws Exception {
        git("init", "-q", "-b", "main");
        git("config", "user.email", "t@t");
        git("config", "user.name", "t");
        Files.writeString(repo.resolve("a.txt"), "a\n");
        Files.createDirectories(repo.resolve("dir"));
        Files.writeString(repo.resolve("dir/b.txt"), "one\ntwo\n");
        git("add", ".");
        git("commit", "-qm", "init");
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
