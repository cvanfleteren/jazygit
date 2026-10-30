package net.vanfleteren.jazygit.git;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.control.Option;
import java.nio.file.Files;
import java.nio.file.Path;
import net.vanfleteren.jazygit.git.GitError.StoppedOnConflicts;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.CommitRange;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.git.model.Sha;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zeroturnaround.exec.ProcessExecutor;

/**
 * Moving the newest commits of the checked out branch to a new branch, and reading which commits that would move.
 */
class CliMoveCommitsTest {

    private static final BranchName FEATURE = new BranchName("feature");
    private static final BranchName MOVED = new BranchName("moved");
    private static final BranchRef MAIN = new BranchRef.Local(new BranchName("main"));

    @TempDir
    Path repo;

    private String base;

    @BeforeEach
    void setUp() throws Exception {
        git("init", "-q", "-b", "main");
        git("config", "user.email", "t@t");
        git("config", "user.name", "t");
        commit("a.txt", "one\n", "init");
        base = head();
        git("checkout", "-q", "-b", "feature");
        commit("b.txt", "b\n", "feature work");
        commit("c.txt", "c\n", "first stray");
        commit("d.txt", "d\n", "second stray");
    }

    @Test
    void withoutABaseTheNewBranchStartsAtTheTipAndTheOldOneMovesBack() throws Exception {
        String tip = head();
        Sha oldest = sha("HEAD~1");
        String parent = sha("HEAD~2").value();
        Files.writeString(repo.resolve("a.txt"), "uncommitted\n");

        assertTrue(
                CliMoveCommits.move(repo, FEATURE, oldest, MOVED, Option.none()).isRight());

        assertEquals("moved", git("branch", "--show-current").strip());
        assertEquals(tip, head());
        assertEquals(parent, sha("feature").value());
        assertEquals("uncommitted\n", Files.readString(repo.resolve("a.txt")));
        assertEquals("feature", git("config", "branch.moved.gh-merge-base").strip());
    }

    @Test
    void ontoABaseTheCommitsAreCopiedThere() throws Exception {
        Sha oldest = sha("HEAD~1");
        String parent = sha("HEAD~2").value();

        assertTrue(CliMoveCommits.move(repo, FEATURE, oldest, MOVED, Option.of(MAIN))
                .isRight());

        assertEquals("moved", git("branch", "--show-current").strip());
        assertEquals(base, sha("HEAD~2").value());
        assertEquals("second stray\nfirst stray\ninit\n", git("log", "--format=%s"));
        assertEquals("main", git("config", "branch.moved.gh-merge-base").strip());
        assertEquals(parent, sha("feature").value());
    }

    @Test
    void aConflictStopsTheCopyAndLeavesTheOldBranchAlone() throws Exception {
        git("checkout", "-q", "main");
        commit("c.txt", "other\n", "conflicting");
        git("checkout", "-q", "feature");
        String tip = head();

        var failure = CliMoveCommits.move(repo, FEATURE, sha("HEAD~1"), MOVED, Option.of(MAIN))
                .getLeft();

        assertInstanceOf(StoppedOnConflicts.class, failure.error());
        assertEquals("moved", git("branch", "--show-current").strip());
        assertEquals(tip, sha("feature").value());
    }

    @Test
    void aFailedCheckoutLeavesEverythingAlone() throws Exception {
        String tip = head();

        assertTrue(CliMoveCommits.move(repo, FEATURE, sha("HEAD~1"), new BranchName("main"), Option.none())
                .isLeft());

        assertEquals("feature", git("branch", "--show-current").strip());
        assertEquals(tip, head());
    }

    @Test
    void ontoAnExistingBranchTheCommitsAreAddedOnTop() throws Exception {
        git("checkout", "-q", "main");
        commit("e.txt", "e\n", "main work");
        git("checkout", "-q", "feature");
        Sha oldest = sha("HEAD~1");
        String parent = sha("HEAD~2").value();

        assertTrue(CliMoveCommits.moveTo(repo, FEATURE, oldest, new BranchName("main"))
                .isRight());

        assertEquals("main", git("branch", "--show-current").strip());
        assertEquals("second stray\nfirst stray\nmain work\ninit\n", git("log", "--format=%s"));
        assertEquals(parent, sha("feature").value());
    }

    @Test
    void changesInTheWayOfTheCheckoutAreReportedAsSuch() throws Exception {
        git("checkout", "-q", "main");
        commit("b.txt", "main b\n", "main b");
        git("checkout", "-q", "feature");
        Files.writeString(repo.resolve("b.txt"), "uncommitted\n");
        String tip = head();

        var failure = CliMoveCommits.moveTo(repo, FEATURE, sha("HEAD~1"), new BranchName("main"))
                .getLeft();

        assertInstanceOf(GitError.LocalChanges.class, failure.error());
        assertEquals("feature", git("branch", "--show-current").strip());
        assertEquals(tip, sha("feature").value());
    }

    @Test
    void commitsThatAreOnTheTargetAlreadyAreLeftOut() throws Exception {
        git("checkout", "-q", "main");
        git("cherry-pick", "feature~1");
        git("checkout", "-q", "feature");
        git("commit", "-q", "--allow-empty", "-m", "empty on purpose");
        String parent = sha("HEAD~3").value();

        assertTrue(CliMoveCommits.moveTo(repo, FEATURE, sha("HEAD~2"), new BranchName("main"))
                .isRight());

        assertEquals("main", git("branch", "--show-current").strip());
        assertEquals("empty on purpose\nsecond stray\nfirst stray\ninit\n", git("log", "--format=%s"));
        assertEquals(parent, sha("feature").value());
    }

    @Test
    void aCopyThatFailsOtherwiseLeavesTheExistingBranchAsItWas() throws Exception {
        String main = sha("main").value();
        String tip = head();
        failToSign();

        var failure = CliMoveCommits.moveTo(repo, FEATURE, sha("HEAD~1"), new BranchName("main"))
                .getLeft();

        assertFalse(failure.error() instanceof StoppedOnConflicts);
        assertEquals("feature", git("branch", "--show-current").strip());
        assertEquals(tip, head());
        assertEquals(main, sha("main").value());
        assertFalse(Files.exists(repo.resolve(".git/CHERRY_PICK_HEAD")));
    }

    @Test
    void aCopyThatFailsOtherwiseDeletesTheNewBranch() throws Exception {
        String tip = head();
        failToSign();

        assertTrue(CliMoveCommits.move(repo, FEATURE, sha("HEAD~1"), MOVED, Option.of(MAIN))
                .isLeft());

        assertEquals("feature", git("branch", "--show-current").strip());
        assertEquals(tip, head());
        assertEquals("", git("branch", "--list", "moved"));
    }

    /**
     * Makes every commit fail to be signed, so a cherry-pick fails half-way without conflicts.
     */
    private void failToSign() throws Exception {
        git("config", "commit.gpgsign", "true");
        git("config", "gpg.program", "false");
    }

    @Test
    void theRangeCountsTheCommitsTheMergesAndThePushedOnes() throws Exception {
        git("update-ref", "refs/remotes/origin/feature", "HEAD~1");
        git("checkout", "-q", "-b", "side", "HEAD~1");
        commit("e.txt", "e\n", "side work");
        git("checkout", "-q", "feature");
        git("merge", "-q", "--no-ff", "-m", "merge side", "side");

        assertEquals(
                new CommitRange(5, 1, 2, Option.none()),
                CliLog.range(repo, FEATURE, sha("HEAD~3"), Option.of(new RemoteBranch("origin", FEATURE)))
                        .get());
        assertEquals(
                new CommitRange(5, 1, 0, Option.none()),
                CliLog.range(repo, FEATURE, sha("HEAD~3"), Option.none()).get());
    }

    @Test
    void anOldestCommitWhoseParentCameInThroughAMergeNamesTheMerge() throws Exception {
        mergeSide();

        assertEquals(
                Option.of(sha("HEAD~1")),
                CliLog.range(repo, FEATURE, sha("side"), Option.none()).get().mergedThrough());
    }

    @Test
    void anOldestCommitWhoseParentIsOnTheLineOfTheBranchNamesNoMerge() throws Exception {
        mergeSide();

        assertEquals(
                Option.none(),
                CliLog.range(repo, FEATURE, sha("side~1"), Option.none()).get().mergedThrough());
        assertEquals(
                Option.none(),
                CliLog.range(repo, FEATURE, sha("HEAD~2"), Option.none()).get().mergedThrough());
        assertEquals(
                Option.none(),
                CliLog.range(repo, FEATURE, sha("HEAD~4"), Option.none()).get().mergedThrough());
    }

    /**
     * Merges a branch of two commits off "first stray" into feature, and commits on top: from the tip, the line of
     * feature is "after the merge", the merge, "second stray", "first stray", "feature work" and "init".
     */
    private void mergeSide() throws Exception {
        git("checkout", "-q", "-b", "side", "HEAD~1");
        commit("e.txt", "e\n", "side one");
        commit("f.txt", "f\n", "side two");
        git("checkout", "-q", "feature");
        git("merge", "-q", "--no-ff", "-m", "merge side", "side");
        commit("g.txt", "g\n", "after the merge");
    }

    private void commit(String file, String content, String message) throws Exception {
        Files.writeString(repo.resolve(file), content);
        git("add", ".");
        git("commit", "-qm", message);
    }

    private String head() throws Exception {
        return sha("HEAD").value();
    }

    private Sha sha(String revision) throws Exception {
        return new Sha(git("rev-parse", revision).strip());
    }

    private String git(String... args) throws Exception {
        return new ProcessExecutor()
                .command(io.vavr.collection.List.of(args).prepend("git").toJavaList())
                .directory(repo.toFile())
                .readOutput(true)
                .exitValueNormal()
                .execute()
                .outputUTF8();
    }
}
