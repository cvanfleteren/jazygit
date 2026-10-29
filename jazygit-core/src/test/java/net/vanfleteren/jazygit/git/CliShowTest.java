package net.vanfleteren.jazygit.git;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.nio.file.Files;
import java.nio.file.Path;
import net.vanfleteren.jazygit.git.model.ChangeType;
import net.vanfleteren.jazygit.git.model.CommitFile;
import net.vanfleteren.jazygit.git.model.DiffOptions;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.model.UnifiedDiff;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zeroturnaround.exec.ProcessExecutor;

class CliShowTest {

    @TempDir
    Path repo;

    @Test
    void listsTheFilesASummaryAndTheDiffInOneCall() throws Exception {
        git("init", "-q");
        git("config", "user.email", "t@t");
        git("config", "user.name", "t");
        Files.writeString(repo.resolve("a.txt"), "one\n");
        Files.writeString(repo.resolve("b.txt"), "x\n");
        git("add", ".");
        git("commit", "-qm", "init");
        Files.writeString(repo.resolve("a.txt"), "one\ntwo\n");
        Files.delete(repo.resolve("b.txt"));
        Files.writeString(repo.resolve("c.txt"), "n\n");
        git("add", "-A");
        git("commit", "-qm", "second", "-m", "body");

        List<String> lines = CliShow.changes(
                        repo, new Sha(git("rev-parse", "HEAD").strip()), DiffOptions.DEFAULT)
                .get()
                .text()
                .lines()
                .collect(List.collector());

        assertEquals(
                List.of(
                        " a.txt | 1 +",
                        " b.txt | 1 -",
                        " c.txt | 1 +",
                        " 3 files changed, 2 insertions(+), 1 deletion(-)",
                        "",
                        "diff --git a/a.txt b/a.txt"),
                lines.take(6));
        assertTrue(lines.contains("+two"));
        assertTrue(lines.contains("-x"));
        assertTrue(lines.contains("+n"));
    }

    @Test
    void ignoringWhitespaceLeavesOutFilesWithChangesToWhitespaceOnly() throws Exception {
        git("init", "-q");
        git("config", "user.email", "t@t");
        git("config", "user.name", "t");
        Files.writeString(repo.resolve("a.txt"), "one\n");
        Files.writeString(repo.resolve("b.txt"), "x\n");
        git("add", ".");
        git("commit", "-qm", "init");
        Files.writeString(repo.resolve("a.txt"), "one \ntwo\n");
        Files.writeString(repo.resolve("b.txt"), "  x\n");
        git("commit", "-qam", "second");

        List<String> lines = CliShow.changes(
                        repo, new Sha(git("rev-parse", "HEAD").strip()), DiffOptions.DEFAULT.withIgnoreWhitespace(true))
                .get()
                .text()
                .lines()
                .collect(List.collector());

        assertEquals(
                List.of(" a.txt | 1 +", " 1 file changed, 1 insertion(+)", "", "diff --git a/a.txt b/a.txt"),
                lines.take(4));
        assertTrue(lines.contains("+two"));
        assertFalse(lines.exists(line -> line.contains("b.txt")));
    }

    @Test
    void listsTheFilesACommitChangedWithItsRenames() throws Exception {
        init();
        Files.writeString(repo.resolve("kept.txt"), "one\n");
        Files.writeString(repo.resolve("gone.txt"), "x\n");
        Files.writeString(repo.resolve("old name.txt"), "a\nb\nc\nd\n");
        commitAll("init");
        Files.writeString(repo.resolve("kept.txt"), "one\ntwo\n");
        Files.delete(repo.resolve("gone.txt"));
        git("mv", "old name.txt", "new name.txt");
        Files.writeString(repo.resolve("added.txt"), "n\n");
        commitAll("second");

        assertEquals(
                List.of(
                        new CommitFile("added.txt", ChangeType.ADDED),
                        new CommitFile("gone.txt", ChangeType.DELETED),
                        new CommitFile("kept.txt", ChangeType.MODIFIED),
                        new CommitFile("new name.txt", ChangeType.RENAMED, Option.of("old name.txt"))),
                CliShow.files(repo, head()).get());
    }

    @Test
    void theFilesOfTheFirstCommitAreAllAdded() throws Exception {
        init();
        Files.writeString(repo.resolve("a.txt"), "one\n");
        commitAll("init");

        assertEquals(
                List.of(new CommitFile("a.txt", ChangeType.ADDED)),
                CliShow.files(repo, head()).get());
    }

    @Test
    void theChangesOfSomeFilesAreOnlyTheirDiffAndARenameShowsAsOne() throws Exception {
        init();
        Files.writeString(repo.resolve("a.txt"), "one\n");
        Files.writeString(repo.resolve("b.txt"), "x\n");
        Files.writeString(repo.resolve("old.txt"), "a\nb\nc\nd\n");
        commitAll("init");
        Files.writeString(repo.resolve("a.txt"), "one\ntwo\n");
        Files.writeString(repo.resolve("b.txt"), "y\n");
        git("mv", "old.txt", "new.txt");
        commitAll("second");
        List<CommitFile> files = CliShow.files(repo, head()).get();

        String a = CliShow.fileChanges(repo, head(), files.filter(f -> f.path().equals("a.txt")), DiffOptions.DEFAULT)
                .get()
                .text();
        String renamed = CliShow.fileChanges(
                        repo, head(), files.filter(f -> f.path().equals("new.txt")), DiffOptions.DEFAULT)
                .get()
                .text();

        assertTrue(a.startsWith("diff --git a/a.txt b/a.txt"), a);
        assertTrue(a.contains("+two"), a);
        assertFalse(a.contains("b.txt"), a);
        assertTrue(renamed.contains("rename from old.txt"), renamed);
        assertTrue(renamed.contains("rename to new.txt"), renamed);
    }

    @Test
    void theFilesOfAMergeAreThoseItBroughtIntoItsFirstParent() throws Exception {
        init();
        Files.writeString(repo.resolve("base.txt"), "base\n");
        commitAll("init");
        git("checkout", "-qb", "side");
        Files.writeString(repo.resolve("side.txt"), "side\n");
        commitAll("side");
        git("checkout", "-q", "-");
        Files.writeString(repo.resolve("main.txt"), "main\n");
        commitAll("main");
        git("merge", "-q", "--no-edit", "side");
        Sha merge = head();

        List<CommitFile> files = CliShow.files(repo, merge).get();
        String changes = CliShow.fileChanges(repo, merge, files, DiffOptions.DEFAULT)
                .get()
                .text();
        String whole = CliShow.changes(repo, merge, DiffOptions.DEFAULT.withFirstParent(true))
                .get()
                .text();
        String combined =
                CliShow.changes(repo, merge, DiffOptions.DEFAULT).get().text();

        assertEquals(List.of(new CommitFile("side.txt", ChangeType.ADDED)), files);
        assertTrue(changes.contains("+side"), changes);
        assertTrue(whole.contains("diff --git a/side.txt b/side.txt"), whole);
        assertFalse(combined.contains("diff --git"), combined);
    }

    @Test
    void theChangesKeepGitsOwnPrefixesWhateverTheUserConfigured() throws Exception {
        init();
        git("config", "diff.noprefix", "true");
        Files.createDirectories(repo.resolve("src"));
        Files.writeString(repo.resolve("src/one.txt"), "one\n");
        commitAll("init");
        Files.writeString(repo.resolve("src/one.txt"), "one\ntwo\n");
        commitAll("second");

        String whole = CliShow.changes(repo, head(), DiffOptions.DEFAULT).get().text();
        String file = CliShow.fileChanges(
                        repo, head(), CliShow.files(repo, head()).get(), DiffOptions.DEFAULT)
                .get()
                .text();

        assertEquals(List.of("src/one.txt"), UnifiedDiff.parse(whole).files().map(UnifiedDiff.FileDiff::path));
        assertEquals(List.of("src/one.txt"), UnifiedDiff.parse(file).files().map(UnifiedDiff.FileDiff::path));
    }

    private void init() throws Exception {
        git("init", "-q");
        git("config", "user.email", "t@t");
        git("config", "user.name", "t");
    }

    private void commitAll(String message) throws Exception {
        git("add", "-A");
        git("commit", "-qm", message);
    }

    private Sha head() throws Exception {
        return new Sha(git("rev-parse", "HEAD").strip());
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
