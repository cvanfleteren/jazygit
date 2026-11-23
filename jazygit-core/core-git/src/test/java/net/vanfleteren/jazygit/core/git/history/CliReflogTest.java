package net.vanfleteren.jazygit.core.git.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.List;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import net.vanfleteren.jazygit.core.git.process.GitError;
import net.vanfleteren.jazygit.core.repo.Reflog;
import net.vanfleteren.jazygit.core.repo.ReflogEntry;
import net.vanfleteren.jazygit.core.repo.ReflogEntry.Action;
import net.vanfleteren.jazygit.core.repo.ReflogEntry.CommitKind;
import net.vanfleteren.jazygit.core.repo.Sha;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zeroturnaround.exec.ProcessExecutor;

/**
 * Reading the reflog of HEAD from real repositories (ADR 186).
 */
class CliReflogTest {

    private static final String A = "a".repeat(40);

    @TempDir
    Path dir;

    @Test
    void theEntriesAreReadNewestFirstWithTheirTimeActionAndCommit() throws Exception {
        Path repo = init("repo");
        commitAt(repo, "One", 1_000);
        git(repo, 2_000, "checkout", "-q", "-b", "feature-x");
        commitAt(repo, "Two", 3_000);
        git(repo, 4_000, "commit", "-q", "--allow-empty", "--amend", "-m", "Two again");

        List<ReflogEntry> entries = entries(new CliHistory(repo).reflog(0, 10));

        assertEquals(List.of(0, 1, 2, 3), entries.map(ReflogEntry::place));
        assertEquals(
                List.of(
                        new Action.Committed(CommitKind.AMEND, "Two again"),
                        new Action.Committed(CommitKind.PLAIN, "Two"),
                        new Action.Checkout("main", "feature-x"),
                        new Action.Committed(CommitKind.INITIAL, "One")),
                entries.map(ReflogEntry::action));
        // The time of the entry, not of its commit: the amend kept the time Two was made at.
        assertEquals(Instant.ofEpochSecond(4_000), entries.head().time());
        assertEquals(Instant.ofEpochSecond(3_000), entries.head().commit().authorTime());
        assertEquals(Instant.ofEpochSecond(2_000), entries.get(2).time());
        assertEquals(entries.get(3).sha(), entries.get(2).sha());
        assertEquals("Two again", entries.head().commit().message().summary());
        assertEquals("commit (amend): Two again", entries.head().subject());
        assertEquals("HEAD@{2}", entries.get(2).selector());
    }

    @Test
    void aPageStartsWhereThePreviousOneEnded() throws Exception {
        Path repo = init("repo");
        for (int i = 1; i <= 5; i++) {
            commitAt(repo, "Commit " + i, i * 1_000);
        }

        List<ReflogEntry> first = entries(new CliHistory(repo).reflog(0, 2));
        List<ReflogEntry> second = entries(new CliHistory(repo).reflog(2, 2));
        List<ReflogEntry> last = entries(new CliHistory(repo).reflog(4, 2));

        assertEquals(
                List.of(0, 1, 2, 3, 4), first.appendAll(second).appendAll(last).map(ReflogEntry::place));
        assertEquals(
                entries(new CliHistory(repo).reflog(0, 10)),
                first.appendAll(second).appendAll(last));
        assertEquals(
                new Reflog.Entries(List.empty()),
                new CliHistory(repo).reflog(5, 2).get());
    }

    @Test
    void withoutACommitThereIsNoReflogYet() throws Exception {
        Path repo = init("repo");

        assertEquals(Reflog.Empty.NO_COMMITS, new CliHistory(repo).reflog(0, 10).get());
    }

    @Test
    void reflogsTurnedOffSaySo() throws Exception {
        Path repo = init("repo");
        git(repo, 0, "config", "core.logAllRefUpdates", "false");
        commitAt(repo, "One", 1_000);

        assertEquals(Reflog.Empty.OFF, new CliHistory(repo).reflog(0, 10).get());
    }

    @Test
    void anEmptiedReflogIsNoReasonToSayReflogsAreOff() throws Exception {
        Path repo = init("repo");
        commitAt(repo, "One", 1_000);
        git(repo, 0, "reflog", "expire", "--expire=all", "--all");

        assertEquals(
                new Reflog.Entries(List.empty()),
                new CliHistory(repo).reflog(0, 10).get());
    }

    @Test
    void anEntryWhoseCommitIsGoneIsLeftOut() throws Exception {
        Path repo = init("repo");
        commitAt(repo, "One", 1_000);
        git(repo, 2_000, "checkout", "-q", "-b", "tmp");
        commitAt(repo, "Gone", 3_000);
        String gone = git(repo, 0, "rev-parse", "HEAD").strip();
        git(repo, 4_000, "checkout", "-q", "main");
        git(repo, 0, "branch", "-q", "-D", "tmp");
        Files.delete(repo.resolve(".git/objects/" + gone.substring(0, 2) + "/" + gone.substring(2)));

        List<ReflogEntry> entries = entries(new CliHistory(repo).reflog(0, 10));

        assertEquals(
                List.of(
                        "checkout: moving from tmp to main",
                        "checkout: moving from main to tmp",
                        "commit (initial): One"),
                entries.map(ReflogEntry::subject));
        assertTrue(entries.map(ReflogEntry::sha).forAll(sha -> !sha.value().equals(gone)));
    }

    @Test
    void aReftableRepositoryHasItsReflogToo() throws Exception {
        Path repo = dir.resolve("reftable");
        Files.createDirectories(repo);
        git(repo, 0, "init", "-q", "-b", "main", "--ref-format=reftable");
        configure(repo);
        commitAt(repo, "One", 1_000);
        git(repo, 2_000, "checkout", "-q", "-b", "x");

        assertEquals(
                List.of(new Action.Checkout("main", "x"), new Action.Committed(CommitKind.INITIAL, "One")),
                entries(new CliHistory(repo).reflog(0, 10)).map(ReflogEntry::action));
    }

    @Test
    void eachWorktreeHasAReflogOfItsOwn() throws Exception {
        Path repo = init("repo");
        commitAt(repo, "One", 1_000);
        Path linked = dir.resolve("linked");
        git(repo, 2_000, "worktree", "add", "-q", "-b", "side", linked.toString());
        commitAt(linked, "In the linked worktree", 3_000);
        commitAt(repo, "In the main worktree", 4_000);

        List<ReflogEntry> main = entries(new CliHistory(repo).reflog(0, 10));
        List<ReflogEntry> side = entries(new CliHistory(linked).reflog(0, 10));

        assertEquals(List.of("commit: In the main worktree", "commit (initial): One"), main.map(ReflogEntry::subject));
        assertEquals("commit: In the linked worktree", side.head().subject());
        assertTrue(side.map(ReflogEntry::subject).forAll(subject -> !subject.contains("main worktree")));
    }

    @Test
    void anEntryIsParsedFromItsSelectorSubjectAndCommit() {
        String raw = String.join(
                        "\u001f",
                        "HEAD@{1795463129}",
                        "reset: moving to HEAD~1",
                        A,
                        "",
                        "Ada",
                        "ada@example.com",
                        "60",
                        "Root",
                        "",
                        "Root\n")
                + "\0";

        ReflogEntry entry = CliHistory.parseReflog(raw, 7).get().head();

        assertEquals(7, entry.place());
        assertEquals(Instant.ofEpochSecond(1_795_463_129), entry.time());
        assertEquals(new Action.Reset("HEAD~1"), entry.action());
        assertEquals(new Sha(A), entry.sha());
    }

    @Test
    void aSelectorWithoutATimeIsUnexpected() {
        String raw =
                String.join("\u001f", "HEAD@{3}x", "commit: a", A, "", "Ada", "ada@example.com", "60", "a", "", "a\n");

        assertInstanceOf(
                GitError.UnexpectedOutput.class, CliHistory.parseReflog(raw, 0).getLeft());
    }

    private static List<ReflogEntry> entries(io.vavr.control.Either<GitError, Reflog> read) {
        return ((Reflog.Entries) read.get()).entries();
    }

    private Path init(String name) throws Exception {
        Path repo = dir.resolve(name);
        Files.createDirectories(repo);
        git(repo, 0, "init", "-q", "-b", "main");
        configure(repo);
        return repo;
    }

    private void configure(Path repo) throws Exception {
        git(repo, 0, "config", "user.name", "Test");
        git(repo, 0, "config", "user.email", "test@example.com");
        git(repo, 0, "config", "commit.gpgsign", "false");
    }

    private void commitAt(Path repo, String message, long time) throws Exception {
        git(repo, time, "commit", "-q", "--allow-empty", "-m", message);
    }

    /**
     * Runs git in {@code repo} as if at {@code time}, which becomes the time of the commit and of the reflog entry.
     */
    private String git(Path repo, long time, String... args) throws Exception {
        java.util.List<String> command = new java.util.ArrayList<>(java.util.List.of("git"));
        command.addAll(java.util.List.of(args));
        String date = "@" + time + " +0000";
        return new ProcessExecutor()
                .command(command)
                .directory(repo.toFile())
                .environment("GIT_COMMITTER_DATE", date)
                .environment("GIT_AUTHOR_DATE", date)
                .readOutput(true)
                .redirectErrorStream(true)
                .exitValueNormal()
                .execute()
                .outputUTF8();
    }
}
