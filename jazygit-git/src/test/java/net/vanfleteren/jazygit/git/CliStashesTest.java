package net.vanfleteren.jazygit.git;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.nio.file.Files;
import java.nio.file.Path;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.ChangeType;
import net.vanfleteren.jazygit.git.model.CommitFile;
import net.vanfleteren.jazygit.git.model.DiffOptions;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.model.Stash;
import net.vanfleteren.jazygit.git.model.UnifiedDiff;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zeroturnaround.exec.ProcessExecutor;

class CliStashesTest {

    @TempDir
    Path repo;

    @Test
    void listsTheBranchAStashWasMadeOn() throws Exception {
        init();
        Files.writeString(repo.resolve("a.txt"), "one\ntwo\n");
        git("stash", "push", "-qm", "my work");

        Stash stash = stashes().list().get().head();

        assertEquals("my work", stash.message());
        assertEquals(Option.of(new BranchName("main")), stash.branch());
    }

    @Test
    void theFilesOfAStashAndTheChangesOfSomeOfThemIncludeItsUntrackedFiles() throws Exception {
        init();
        Files.writeString(repo.resolve("a.txt"), "one\ntwo\n");
        Files.writeString(repo.resolve("untracked.txt"), "u\n");
        Files.writeString(repo.resolve("other.txt"), "o\n");
        git("stash", "push", "-u", "-qm", "my work");

        List<CommitFile> files = stashes().files(stash()).get();
        String tracked = stashes()
                .fileChanges(stash(), files.filter(f -> f.path().equals("a.txt")), DiffOptions.DEFAULT)
                .get()
                .text();
        String untracked = stashes()
                .fileChanges(stash(), files.filter(f -> f.path().equals("untracked.txt")), DiffOptions.DEFAULT)
                .get()
                .text();

        assertEquals(
                List.of(
                        new CommitFile("a.txt", ChangeType.MODIFIED),
                        new CommitFile("other.txt", ChangeType.ADDED),
                        new CommitFile("untracked.txt", ChangeType.ADDED)),
                files);
        assertTrue(tracked.contains("+two"), tracked);
        assertFalse(tracked.contains("untracked"), tracked);
        assertTrue(untracked.contains("+u"), untracked);
        assertFalse(untracked.contains("other"), untracked);
    }

    @Test
    void theChangesOfAStashKeepGitsOwnPrefixesWhateverTheUserConfigured() throws Exception {
        init();
        git("config", "diff.mnemonicPrefix", "true");
        git("config", "diff.srcPrefix", "old/");
        Files.writeString(repo.resolve("a.txt"), "one\ntwo\n");
        Files.writeString(repo.resolve("untracked.txt"), "u\n");
        git("stash", "push", "-u", "-qm", "my work");

        String whole = stashes().changes(stash(), DiffOptions.DEFAULT).get().text();
        String files = stashes()
                .fileChanges(stash(), stashes().files(stash()).get(), DiffOptions.DEFAULT)
                .get()
                .text();

        List<String> paths = List.of("a.txt", "untracked.txt");
        assertEquals(paths, UnifiedDiff.parse(whole).files().map(UnifiedDiff.FileDiff::path));
        assertEquals(paths, UnifiedDiff.parse(files).files().map(UnifiedDiff.FileDiff::path));
    }

    @Test
    void theChangesOfSomeFilesOfAStashWithoutUntrackedFilesAreOnlyItsOwn() throws Exception {
        init();
        Files.writeString(repo.resolve("a.txt"), "one\ntwo\n");
        git("stash", "push", "-qm", "my work");

        String changes = stashes()
                .fileChanges(stash(), List.of(new CommitFile("a.txt", ChangeType.MODIFIED)), DiffOptions.DEFAULT)
                .get()
                .text();

        assertTrue(changes.startsWith("diff --git a/a.txt b/a.txt"), changes);
        assertTrue(changes.contains("+two"), changes);
    }

    @Test
    void theChangesOfAStashAreItsStagedAndUnstagedChangesAndItsUntrackedFiles() throws Exception {
        init();
        Files.writeString(repo.resolve("a.txt"), "one\ntwo\n");
        Files.writeString(repo.resolve("staged.txt"), "s\n");
        git("add", "staged.txt");
        Files.writeString(repo.resolve("untracked.txt"), "u\n");
        git("stash", "push", "-u", "-qm", "my work");
        // What the user set for git stash show does not change what is shown.
        git("config", "stash.showPatch", "false");
        git("config", "stash.showStat", "false");
        git("config", "stash.showIncludeUntracked", "false");

        List<String> lines = stashes()
                .changes(stash(), DiffOptions.DEFAULT)
                .get()
                .text()
                .lines()
                .collect(List.collector());

        assertEquals(
                List.of(
                        " a.txt         | 1 +",
                        " staged.txt    | 1 +",
                        " untracked.txt | 1 +",
                        " 3 files changed, 3 insertions(+)",
                        "",
                        "diff --git a/a.txt b/a.txt"),
                lines.take(6));
        assertTrue(lines.contains("+two"));
        assertTrue(lines.contains("+s"));
        assertTrue(lines.contains("+u"));
    }

    @Test
    void ignoringWhitespaceLeavesOutFilesWithChangesToWhitespaceOnly() throws Exception {
        init();
        Files.writeString(repo.resolve("a.txt"), "one \ntwo\n");
        Files.writeString(repo.resolve("b.txt"), "  x\n");
        git("stash", "push", "-q");

        List<String> lines = stashes()
                .changes(stash(), DiffOptions.DEFAULT.withIgnoreWhitespace(true))
                .get()
                .text()
                .lines()
                .collect(List.collector());

        assertTrue(lines.contains("+two"));
        assertFalse(lines.exists(line -> line.contains("b.txt")));
    }

    @Test
    void popBringsStagedChangesBackStaged() throws Exception {
        init();
        Files.writeString(repo.resolve("a.txt"), "one\nstaged\n");
        git("add", "a.txt");
        Files.writeString(repo.resolve("b.txt"), "x\nunstaged\n");
        git("stash", "push", "-q");

        assertTrue(stashes().pop(stashes().list().get().head()).isRight());

        assertEquals("M  a.txt\n M b.txt\n", git("status", "--porcelain"));
        assertEquals("", git("stash", "list"));
    }

    @Test
    void popBringsStagedChangesBackUnstagedWhenTheyDoNotApplyStaged() throws Exception {
        init();
        Files.writeString(repo.resolve("a.txt"), "1\n2\n3\n4\n5\n6\n7\n8\n");
        git("commit", "-qam", "eight lines");
        Files.writeString(repo.resolve("a.txt"), "1\n2\n3\n4\nstaged\n6\n7\n8\n");
        git("add", "a.txt");
        git("stash", "push", "-q");
        // A change two lines away: the staged patch no longer applies to the index, but merges cleanly.
        Files.writeString(repo.resolve("a.txt"), "1\n2\nlater\n4\n5\n6\n7\n8\n");
        git("commit", "-qam", "later");

        var commands = stashes().pop(stashes().list().get().head()).get();

        assertEquals(
                List.of("git stash pop --index 'stash@{0}'", "git stash pop 'stash@{0}'"),
                commands.map(ExecutedCommand::line));
        assertEquals(" M a.txt\n", git("status", "--porcelain"));
        assertEquals("1\n2\nlater\n4\nstaged\n6\n7\n8\n", Files.readString(repo.resolve("a.txt")));
        assertEquals("", git("stash", "list"));
    }

    @Test
    void popWithSomethingStagedBringsTheStashBackUnstagedAndKeepsWhatIsStaged() throws Exception {
        init();
        Files.writeString(repo.resolve("a.txt"), "one\nstashed\n");
        git("add", "a.txt");
        git("stash", "push", "-q");
        // Git refuses --index with anything staged, and unstages it.
        Files.writeString(repo.resolve("b.txt"), "x\nstaged\n");
        git("add", "b.txt");

        var commands = stashes().pop(stashes().list().get().head()).get();

        assertEquals(List.of("git stash pop 'stash@{0}'"), commands.map(ExecutedCommand::line));
        assertEquals(" M a.txt\nM  b.txt\n", git("status", "--porcelain"));
        assertEquals("", git("stash", "list"));
    }

    @Test
    void popKeepsTheStashWhenItConflictsUnstagedToo() throws Exception {
        init();
        Files.writeString(repo.resolve("a.txt"), "stashed\n");
        git("add", "a.txt");
        git("stash", "push", "-q");
        Files.writeString(repo.resolve("a.txt"), "committed\n");
        git("commit", "-qam", "conflicting");

        var failure = stashes().pop(stashes().list().get().head()).getLeft();

        assertEquals(
                List.of("git stash pop --index 'stash@{0}'", "git stash pop 'stash@{0}'"),
                failure.commands().map(ExecutedCommand::line));
        assertEquals("UU a.txt\n", git("status", "--porcelain"));
        assertEquals(1, git("stash", "list").lines().count());
    }

    @Test
    void pushSavesTrackedAndUntrackedChangesUnderTheGivenName() throws Exception {
        init();
        Files.writeString(repo.resolve("a.txt"), "changed\n");
        Files.writeString(repo.resolve("new.txt"), "new\n");

        List<String> executed = stashes().push(Option.of("my work")).get().map(ExecutedCommand::line);

        assertEquals(List.of("git stash push --include-untracked --message 'my work'"), executed);
        assertEquals("", git("status", "--porcelain"));
        assertEquals(
                List.of("stash@{0} my work"), stashes().list().get().map(stash -> stash.ref() + " " + stash.message()));
    }

    @Test
    void pushWithoutANameIsNamedByGit() throws Exception {
        init();
        Files.writeString(repo.resolve("a.txt"), "changed\n");
        String head = git("log", "-1", "--format=%h %s").strip();

        List<String> executed = stashes().push(Option.none()).get().map(ExecutedCommand::line);

        assertEquals(List.of("git stash push --include-untracked"), executed);
        assertEquals(List.of("WIP on main: " + head), stashes().list().get().map(Stash::message));
    }

    @Test
    void popRestoresTheChangesAndDropsTheStash() throws Exception {
        init();
        Files.writeString(repo.resolve("a.txt"), "changed\n");
        stashes().push(Option.of("work")).get();

        List<String> executed =
                stashes().pop(stashes().list().get().head()).get().map(ExecutedCommand::line);

        assertEquals(1, executed.size());
        assertTrue(executed.head().startsWith("git stash pop"), executed.toString());
        assertEquals("changed\n", Files.readString(repo.resolve("a.txt")));
        assertEquals(List.of(), stashes().list().get());
    }

    @Test
    void dropDeletesTheStashWithoutApplyingIt() throws Exception {
        init();
        Files.writeString(repo.resolve("a.txt"), "changed\n");
        stashes().push(Option.of("work")).get();

        List<String> executed =
                stashes().drop(stashes().list().get().head()).get().map(ExecutedCommand::line);

        assertEquals(1, executed.size());
        assertTrue(executed.head().startsWith("git stash drop"), executed.toString());
        assertEquals(List.of(), stashes().list().get());
        assertEquals("one\n", Files.readString(repo.resolve("a.txt")));
    }

    @Test
    void popOfAStashThatIsGoneFailsWithoutRunningAnything() throws Exception {
        init();
        Files.writeString(repo.resolve("a.txt"), "changed\n");
        stashes().push(Option.of("work")).get();
        Stash work = stashes().list().get().head();
        stashes().drop(work).get();

        GitFailure<GitError> failure = stashes().pop(work).getLeft();

        assertEquals(new GitError.StashGone("work"), failure.error());
        assertEquals(List.of(), failure.commands());
    }

    @Test
    void aStashIsFoundUnderItsNewNameOnceAnotherWasAdded() throws Exception {
        init();
        Files.writeString(repo.resolve("a.txt"), "first\n");
        stashes().push(Option.of("first")).get();
        Stash first = stashes().list().get().head();
        Files.writeString(repo.resolve("a.txt"), "second\n");
        stashes().push(Option.of("second")).get();

        assertEquals(
                List.of("git stash drop 'stash@{1}'"),
                stashes().drop(first).get().map(ExecutedCommand::line));

        assertEquals(List.of("second"), stashes().list().get().map(Stash::message));
    }

    @Test
    void theNewestIsListedFirstAndThereAreNoneInACleanRepository() throws Exception {
        init();
        assertEquals(List.of(), stashes().list().get());
        Files.writeString(repo.resolve("a.txt"), "first\n");
        stashes().push(Option.of("first")).get();
        Files.writeString(repo.resolve("a.txt"), "second\n");
        stashes().push(Option.of("second")).get();

        assertEquals(
                List.of("stash@{0} second", "stash@{1} first"),
                stashes().list().get().map(stash -> stash.ref() + " " + stash.message()));
    }

    // Through the provider, as the app reaches them.
    private Stashes stashes() {
        return CliGit.open(repo).get().stashes();
    }

    private void init() throws Exception {
        git("init", "-q", "-b", "main");
        git("config", "user.email", "t@t");
        git("config", "user.name", "t");
        Files.writeString(repo.resolve("a.txt"), "one\n");
        Files.writeString(repo.resolve("b.txt"), "x\n");
        git("add", ".");
        git("commit", "-qm", "init");
    }

    private Sha stash() throws Exception {
        return new Sha(git("rev-parse", "stash@{0}").strip());
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
