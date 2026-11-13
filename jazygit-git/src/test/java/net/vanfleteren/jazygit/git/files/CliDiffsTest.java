package net.vanfleteren.jazygit.git.files;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.nio.file.Files;
import java.nio.file.Path;
import net.vanfleteren.jazygit.git.CliGit;
import net.vanfleteren.jazygit.git.model.ChangeType;
import net.vanfleteren.jazygit.git.model.CommitFile;
import net.vanfleteren.jazygit.git.model.DiffContext;
import net.vanfleteren.jazygit.git.model.DiffOptions;
import net.vanfleteren.jazygit.git.model.DiffText;
import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.git.model.Fixtures;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.model.UnifiedDiff;
import net.vanfleteren.jazygit.process.Limit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zeroturnaround.exec.ProcessExecutor;

class CliDiffsTest {

    @TempDir
    Path repo;

    @Test
    void showsUntrackedFilesOfARepositoryWithoutAnIndexYet() throws Exception {
        git("init", "-q");
        Files.writeString(repo.resolve("new.txt"), "first\n");

        net.vanfleteren.jazygit.git.model.Diffs diffs = diffs().uncommitted(
                        List.of(Fixtures.fileEntry("new.txt", ChangeType.UNTRACKED)), DiffOptions.DEFAULT)
                .get();

        assertTrue(diffs.unstaged().text().contains("+first"), diffs.unstaged().text());
        assertFalse(Files.exists(repo.resolve(".git/index")));
    }

    @Test
    void showsUnstagedAndUntrackedFilesWithoutTouchingTheIndex() throws Exception {
        git("init", "-q");
        git("config", "user.email", "t@t");
        git("config", "user.name", "t");
        Files.writeString(repo.resolve("a.txt"), "one\n");
        git("add", "a.txt");
        git("commit", "-qm", "init");
        Files.writeString(repo.resolve("a.txt"), "two\n");
        Files.writeString(repo.resolve("new1.txt"), "first\n");
        Files.writeString(repo.resolve("new2.txt"), "second\n");
        String indexBefore = git("ls-files", "--stage");

        net.vanfleteren.jazygit.git.model.Diffs diffs = diffs().uncommitted(
                        List.of(
                                Fixtures.fileEntry("a.txt", ChangeType.MODIFIED),
                                Fixtures.fileEntry("new1.txt", ChangeType.UNTRACKED),
                                Fixtures.fileEntry("new2.txt", ChangeType.UNTRACKED)),
                        DiffOptions.DEFAULT)
                .get();

        assertEquals("", diffs.staged().text());
        assertTrue(diffs.unstaged().text().contains("+two"));
        assertTrue(diffs.unstaged().text().contains("+first"));
        assertTrue(diffs.unstaged().text().contains("+second"));
        assertEquals(indexBefore, git("ls-files", "--stage"));
        assertFalse(git("status", "--porcelain").contains("A  new1.txt"));
    }

    @Test
    void ignoringWhitespaceLeavesOutChangesToWhitespaceOnly() throws Exception {
        git("init", "-q");
        git("config", "user.email", "t@t");
        git("config", "user.name", "t");
        Files.writeString(repo.resolve("spaced.txt"), "one\n");
        Files.writeString(repo.resolve("staged.txt"), "one\n");
        Files.writeString(repo.resolve("changed.txt"), "one\n");
        git("add", ".");
        git("commit", "-qm", "init");
        Files.writeString(repo.resolve("spaced.txt"), "one  \n");
        Files.writeString(repo.resolve("staged.txt"), "\tone\n");
        git("add", "staged.txt");
        Files.writeString(repo.resolve("changed.txt"), "one \ntwo\n");
        Files.writeString(repo.resolve("new.txt"), "first\n");
        List<FileEntry> files = List.of(
                Fixtures.fileEntry("spaced.txt", ChangeType.MODIFIED),
                new FileEntry("staged.txt", ChangeType.MODIFIED, true, false),
                Fixtures.fileEntry("changed.txt", ChangeType.MODIFIED),
                Fixtures.fileEntry("new.txt", ChangeType.UNTRACKED));

        net.vanfleteren.jazygit.git.model.Diffs shown =
                diffs().uncommitted(files, DiffOptions.DEFAULT).get();
        net.vanfleteren.jazygit.git.model.Diffs ignoring = diffs().uncommitted(
                        files, DiffOptions.DEFAULT.withIgnoreWhitespace(true))
                .get();

        assertTrue(shown.staged().text().contains("staged.txt"), shown.staged().text());
        assertTrue(
                shown.unstaged().text().contains("spaced.txt"), shown.unstaged().text());
        assertEquals("", ignoring.staged().text());
        assertFalse(
                ignoring.unstaged().text().contains("spaced.txt"),
                ignoring.unstaged().text());
        assertTrue(
                ignoring.unstaged().text().contains("+two"), ignoring.unstaged().text());
        assertFalse(
                ignoring.unstaged().text().contains("-one"), ignoring.unstaged().text());
        assertTrue(
                ignoring.unstaged().text().contains("+first"),
                ignoring.unstaged().text());
    }

    @Test
    void theDiffsOfTheWorkingTreeKeepGitsOwnPrefixesWhateverTheUserConfigured() throws Exception {
        git("init", "-q");
        git("config", "user.email", "t@t");
        git("config", "user.name", "t");
        git("config", "diff.mnemonicPrefix", "true");
        git("config", "diff.srcPrefix", "old/");
        Files.writeString(repo.resolve("a.txt"), "one\n");
        Files.writeString(repo.resolve("b.txt"), "one\n");
        git("add", "a.txt", "b.txt");
        git("commit", "-qm", "init");
        Files.writeString(repo.resolve("a.txt"), "two\n");
        git("add", "a.txt");
        Files.writeString(repo.resolve("b.txt"), "two\n");
        Files.writeString(repo.resolve("new.txt"), "first\n");

        net.vanfleteren.jazygit.git.model.Diffs diffs = diffs().uncommitted(
                        List.of(
                                new FileEntry("a.txt", ChangeType.MODIFIED, true, false),
                                Fixtures.fileEntry("b.txt", ChangeType.MODIFIED),
                                Fixtures.fileEntry("new.txt", ChangeType.UNTRACKED)),
                        DiffOptions.DEFAULT)
                .get();

        assertTrue(
                diffs.staged().text().contains("--- a/a.txt\n+++ b/a.txt\n"),
                diffs.staged().text());
        assertEquals(
                List.of("b.txt", "new.txt"),
                UnifiedDiff.parse(diffs.unstaged().text()).files().map(UnifiedDiff.FileDiff::path));
        assertTrue(
                diffs.unstaged().text().contains("+++ b/new.txt\n"),
                diffs.unstaged().text());
    }

    @Test
    void aLongDiffIsReadOnlyUpToTheLimitAndSaysSo() throws Exception {
        git("init", "-q");
        Files.writeString(repo.resolve("big.txt"), "line\n".repeat(CliDiffs.LIMIT.lines() + 10));

        DiffText unstaged = diffs().uncommitted(
                        List.of(Fixtures.fileEntry("big.txt", ChangeType.UNTRACKED)), DiffOptions.DEFAULT)
                .get()
                .unstaged();

        assertTrue(unstaged.truncated());
        assertEquals(CliDiffs.LIMIT.lines(), unstaged.text().lines().count());
        assertTrue(unstaged.text().endsWith("+line\n"));
    }

    @Test
    void aDiffOfALineLongerThanTheLimitKeepsOnlyWhatComesBeforeIt() throws Exception {
        git("init", "-q");
        Files.writeString(repo.resolve("long.txt"), "x".repeat(CliDiffs.LIMIT.chars()) + "\n");

        DiffText unstaged = diffs().uncommitted(
                        List.of(Fixtures.fileEntry("long.txt", ChangeType.UNTRACKED)), DiffOptions.DEFAULT)
                .get()
                .unstaged();

        assertTrue(unstaged.truncated());
        assertTrue(unstaged.text().endsWith("@@ -0,0 +1 @@\n"), unstaged.text());
    }

    @Test
    void aDiffIsReadUpTo50000LinesOr4MiB() {
        assertEquals(new Limit(50_000, 4 * 1024 * 1024), CliDiffs.LIMIT);
    }

    @Test
    void listsTheFilesASummaryAndTheDiffOfACommitInOneCall() throws Exception {
        init();
        Files.writeString(repo.resolve("a.txt"), "one\n");
        Files.writeString(repo.resolve("b.txt"), "x\n");
        commitAll("init");
        Files.writeString(repo.resolve("a.txt"), "one\ntwo\n");
        Files.delete(repo.resolve("b.txt"));
        Files.writeString(repo.resolve("c.txt"), "n\n");
        git("add", "-A");
        git("commit", "-qm", "second", "-m", "body");

        List<String> lines = diffs().commitChanges(head(), DiffOptions.DEFAULT)
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
    void ignoringWhitespaceLeavesOutFilesOfACommitWithChangesToWhitespaceOnly() throws Exception {
        init();
        Files.writeString(repo.resolve("a.txt"), "one\n");
        Files.writeString(repo.resolve("b.txt"), "x\n");
        commitAll("init");
        Files.writeString(repo.resolve("a.txt"), "one \ntwo\n");
        Files.writeString(repo.resolve("b.txt"), "  x\n");
        commitAll("second");

        List<String> lines = diffs().commitChanges(head(), DiffOptions.DEFAULT.withIgnoreWhitespace(true))
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
                diffs().commitFiles(head()).get());
    }

    @Test
    void theFilesOfTheFirstCommitAreAllAdded() throws Exception {
        init();
        Files.writeString(repo.resolve("a.txt"), "one\n");
        commitAll("init");

        assertEquals(
                List.of(new CommitFile("a.txt", ChangeType.ADDED)),
                diffs().commitFiles(head()).get());
    }

    @Test
    void theChangesOfSomeFilesOfACommitAreOnlyTheirDiffAndARenameShowsAsOne() throws Exception {
        init();
        Files.writeString(repo.resolve("a.txt"), "one\n");
        Files.writeString(repo.resolve("b.txt"), "x\n");
        Files.writeString(repo.resolve("old.txt"), "a\nb\nc\nd\n");
        commitAll("init");
        Files.writeString(repo.resolve("a.txt"), "one\ntwo\n");
        Files.writeString(repo.resolve("b.txt"), "y\n");
        git("mv", "old.txt", "new.txt");
        commitAll("second");
        List<CommitFile> files = diffs().commitFiles(head()).get();

        String a = diffs().commitFileChanges(head(), files.filter(f -> f.path().equals("a.txt")), DiffOptions.DEFAULT)
                .get()
                .text();
        String renamed = diffs().commitFileChanges(
                        head(), files.filter(f -> f.path().equals("new.txt")), DiffOptions.DEFAULT)
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

        List<CommitFile> files = diffs().commitFiles(merge).get();
        String changes = diffs().commitFileChanges(merge, files, DiffOptions.DEFAULT)
                .get()
                .text();
        String whole = diffs().commitChanges(merge, DiffOptions.DEFAULT.withFirstParent(true))
                .get()
                .text();
        String combined =
                diffs().commitChanges(merge, DiffOptions.DEFAULT).get().text();

        assertEquals(List.of(new CommitFile("side.txt", ChangeType.ADDED)), files);
        assertTrue(changes.contains("+side"), changes);
        assertTrue(whole.contains("diff --git a/side.txt b/side.txt"), whole);
        assertFalse(combined.contains("diff --git"), combined);
    }

    @Test
    void theChangesOfACommitKeepGitsOwnPrefixesWhateverTheUserConfigured() throws Exception {
        init();
        git("config", "diff.noprefix", "true");
        Files.createDirectories(repo.resolve("src"));
        Files.writeString(repo.resolve("src/one.txt"), "one\n");
        commitAll("init");
        Files.writeString(repo.resolve("src/one.txt"), "one\ntwo\n");
        commitAll("second");

        String whole = diffs().commitChanges(head(), DiffOptions.DEFAULT).get().text();
        String file = diffs().commitFileChanges(
                        head(), diffs().commitFiles(head()).get(), DiffOptions.DEFAULT)
                .get()
                .text();

        assertEquals(List.of("src/one.txt"), UnifiedDiff.parse(whole).files().map(UnifiedDiff.FileDiff::path));
        assertEquals(List.of("src/one.txt"), UnifiedDiff.parse(file).files().map(UnifiedDiff.FileDiff::path));
    }

    @Test
    void theContextIsThreeLinesWhateverTheUserConfiguredUntilItIsChanged() throws Exception {
        init();
        git("config", "diff.context", "0");
        String numbered = List.rangeClosed(1, 20).map(n -> "line" + n + "\n").mkString();
        Files.writeString(repo.resolve("a.txt"), numbered);
        commitAll("init");
        Files.writeString(repo.resolve("a.txt"), numbered.replace("line10\n", "changed\n"));
        commitAll("second");

        List<String> shown = diffs().commitChanges(head(), DiffOptions.DEFAULT)
                .get()
                .text()
                .lines()
                .collect(List.collector());
        List<String> wider = diffs().commitChanges(head(), DiffOptions.DEFAULT.withContext(new DiffContext(5)))
                .get()
                .text()
                .lines()
                .collect(List.collector());
        List<String> whole = diffs().commitChanges(head(), DiffOptions.DEFAULT.withContext(DiffContext.WHOLE_FILE))
                .get()
                .text()
                .lines()
                .collect(List.collector());

        assertTrue(shown.containsAll(List.of(" line7", " line13")), shown.mkString("\n"));
        assertFalse(shown.exists(line -> line.equals(" line6") || line.equals(" line14")), shown.mkString("\n"));
        assertTrue(wider.containsAll(List.of(" line5", " line15")), wider.mkString("\n"));
        assertFalse(wider.contains(" line4"), wider.mkString("\n"));
        assertTrue(whole.containsAll(List.of(" line1", " line20")), whole.mkString("\n"));
    }

    private Diffs diffs() {
        return CliGit.open(repo).get().diffs();
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
