package net.vanfleteren.jazygit.git;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import java.nio.file.Files;
import java.nio.file.Path;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.RemoteName;
import net.vanfleteren.jazygit.git.model.Revision;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.model.StackLayer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zeroturnaround.exec.ProcessExecutor;

/**
 * Rebasing a stack in one rebase whose todo picks the own commits of each branch, and pushing it whole (ADR 74).
 */
class CliStackRebaseTest {

    private static final BranchName MAIN = new BranchName("main");
    private static final BranchName C = new BranchName("c");
    private static final BranchName D = new BranchName("d");
    private static final Revision BASE = new BranchRef.Local(MAIN);

    @TempDir
    Path repo;

    @TempDir
    Path remote;

    // main ← c1 ← c2 (c); d was made from main, not from c: d1 (d). Then main moved on: m1.
    @BeforeEach
    void setUp() throws Exception {
        git("init", "-q", "-b", "main");
        git("config", "user.email", "t@t");
        git("config", "user.name", "t");
        commit("base");
        git("checkout", "-qb", "c");
        commit("c1");
        commit("c2");
        git("checkout", "-q", "main");
        git("checkout", "-qb", "d");
        commit("d1");
        git("checkout", "-q", "main");
        commit("m1");
    }

    private List<StackLayer> layers() throws Exception {
        return List.of(
                new StackLayer(C, BASE),
                new StackLayer(
                        D, new Revision.Commit(Sha.parse(git("rev-parse", "c")).get())));
    }

    @Test
    void theOwnCommitsOfEachBranchAreWhatIsNotBelowIt() throws Exception {
        List<StackLayer.Commits> commits =
                new CliHistory(repo).stackCommits(layers(), BASE).get();

        assertEquals(List.of(C, D), commits.map(StackLayer.Commits::branch));
        assertEquals(List.of("c1", "c2"), commits.get(0).commits().map(this::subject));
        assertEquals(List.of("d1"), commits.get(1).commits().map(this::subject));
    }

    @Test
    void aBranchNotBuiltOnTheOneBelowIsPutOnItAndTheBottomOnTheBase() throws Exception {
        List<StackLayer.Commits> commits =
                new CliHistory(repo).stackCommits(layers(), BASE).get();

        new CliIntegrate(repo).rebaseStack(BASE, commits).get();

        assertEquals("d1 c2 c1 m1 base", git("log", "--format=%s", "d").replace('\n', ' '));
        assertEquals("c2 c1 m1 base", git("log", "--format=%s", "c").replace('\n', ' '));
        assertEquals("refs/heads/d", git("symbolic-ref", "HEAD"));
    }

    @Test
    void aMergeInABranchIsSaidAndLeftOut() throws Exception {
        git("checkout", "-q", "d");
        git("merge", "-q", "--no-edit", "--no-ff", "c~1", "-m", "Merge c1 into d");
        commit("d2");
        git("checkout", "-q", "main");

        List<StackLayer.Commits> commits =
                new CliHistory(repo).stackCommits(layers(), BASE).get();

        assertEquals(List.of("d1", "d2"), commits.get(1).commits().map(this::subject));
        assertEquals(List.of("Merge c1 into d"), commits.get(1).merges().map(StackLayer.Merge::subject));

        new CliIntegrate(repo).rebaseStack(BASE, commits).get();

        assertEquals("d2 d1 c2 c1 m1 base", git("log", "--format=%s", "d").replace('\n', ' '));
    }

    @Test
    void aConflictLeavesTheRebaseToBeContinued() throws Exception {
        git("checkout", "-q", "d");
        write("c1", "not as on c\n");
        git("add", ".");
        git("commit", "-qm", "d touches c1");
        git("checkout", "-q", "main");

        List<StackLayer.Commits> commits =
                new CliHistory(repo).stackCommits(layers(), BASE).get();
        GitFailure<GitError> failure =
                new CliIntegrate(repo).rebaseStack(BASE, commits).getLeft();

        assertInstanceOf(GitError.StoppedOnConflicts.class, failure.error());
        assertTrue(Files.isDirectory(repo.resolve(git("rev-parse", "--git-path", "rebase-merge"))));
    }

    @Test
    void theBranchesArePushedAllAtOnceAndFollowWhatTheyWerePushedTo() throws Exception {
        new ProcessExecutor()
                .command("git", "init", "-q", "--bare", remote.toString())
                .exitValueNormal()
                .execute();
        git("remote", "add", "origin", remote.toString());

        new CliRemotes(repo, HashMap::empty)
                .pushAll(List.of(C, D), new RemoteName("origin"))
                .get();

        assertEquals(git("rev-parse", "c"), git("rev-parse", "origin/c"));
        assertEquals(git("rev-parse", "d"), git("rev-parse", "origin/d"));
        assertEquals("origin/d", git("rev-parse", "--abbrev-ref", "d@{upstream}"));
    }

    private String subject(Sha sha) {
        try {
            return git("log", "-1", "--format=%s", sha.value());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void commit(String name) throws Exception {
        write(name, name + "\n");
        git("add", ".");
        git("commit", "-qm", name);
    }

    private void write(String name, String content) throws Exception {
        Files.writeString(repo.resolve(name), content);
    }

    private String git(String... args) throws Exception {
        return new ProcessExecutor()
                .command(List.of("git").appendAll(List.of(args)).toJavaList())
                .directory(repo.toFile())
                .readOutput(true)
                .exitValueNormal()
                .execute()
                .outputUTF8()
                .strip();
    }
}
