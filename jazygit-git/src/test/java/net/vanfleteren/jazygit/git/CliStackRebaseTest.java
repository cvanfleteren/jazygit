package net.vanfleteren.jazygit.git;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.Tuple;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import java.nio.file.Files;
import java.nio.file.Path;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
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
    private static final BranchName E = new BranchName("e");
    private static final Revision BASE = new BranchRef.Local(MAIN);
    private static final RemoteName ORIGIN = new RemoteName("origin");

    @TempDir
    Path repo;

    @TempDir
    Path remote;

    @TempDir
    Path other;

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

    private List<StackLayer> layers() {
        return List.of(new StackLayer(C, List.of(BASE)), new StackLayer(D, List.of(new BranchRef.Local(C))));
    }

    private List<StackLayer.Commits> read(List<StackLayer> layers) {
        return new CliHistory(repo).stackCommits(layers, BASE).get();
    }

    @Test
    void theOwnCommitsOfEachBranchAreWhatIsNotBelowIt() throws Exception {
        List<StackLayer.Commits> commits = read(layers());

        assertEquals(List.of(C, D), commits.map(StackLayer.Commits::branch));
        assertEquals(List.of("c1", "c2"), commits.get(0).commits().map(this::subject));
        assertEquals(List.of("d1"), commits.get(1).commits().map(this::subject));
        assertEquals(git("rev-parse", "d"), commits.get(1).tip().value());
    }

    @Test
    void aBranchNotBuiltOnTheOneBelowIsPutOnItAndTheBottomOnTheBase() throws Exception {
        new CliIntegrate(repo).rebaseStack(BASE, read(layers())).get();

        assertEquals("d1 c2 c1 m1 base", log("d"));
        assertEquals("c2 c1 m1 base", log("c"));
        assertEquals("refs/heads/d", git("symbolic-ref", "HEAD"));
    }

    @Test
    void aMergeInABranchIsSaidAndLeftOut() throws Exception {
        git("checkout", "-q", "d");
        git("merge", "-q", "--no-edit", "--no-ff", "c~1", "-m", "Merge c1 into d");
        commit("d2");
        git("checkout", "-q", "main");

        List<StackLayer.Commits> commits = read(layers());

        assertEquals(List.of("d1", "d2"), commits.get(1).commits().map(this::subject));
        assertEquals(List.of("Merge c1 into d"), commits.get(1).merges().map(StackLayer.Merge::subject));

        new CliIntegrate(repo).rebaseStack(BASE, commits).get();

        assertEquals("d2 d1 c2 c1 m1 base", log("d"));
    }

    // e was made from c; then c2 was amended, as one does to a branch below before rebasing the stack.
    @Test
    void aCommitAmendedBelowIsNotReplayedAgain() throws Exception {
        git("checkout", "-q", "c");
        git("checkout", "-qb", "e");
        commit("e1");
        git("checkout", "-q", "c");
        write("c2", "c2 amended\n");
        git("commit", "-qa", "--amend", "-m", "c2 amended");
        git("checkout", "-q", "main");
        List<StackLayer> layers =
                List.of(new StackLayer(C, List.of(BASE)), new StackLayer(E, List.of(new BranchRef.Local(C))));

        List<StackLayer.Commits> commits = read(layers);

        assertEquals(List.of("e1"), commits.get(1).commits().map(this::subject));
        new CliIntegrate(repo).rebaseStack(BASE, commits).get();
        assertEquals("e1 c2 amended c1 m1 base", log("e"));
    }

    // c was squashed into main; d was built on it. Where c was is all that tells its commits apart.
    @Test
    void whatASquashedBranchBelowHadIsLeftOutAlsoWhereGitChecksForMissingCommits() throws Exception {
        git("checkout", "-q", "c");
        git("checkout", "-qb", "e");
        commit("e1");
        git("checkout", "-q", "main");
        git("merge", "-q", "--squash", "c");
        git("commit", "-qm", "squashed c");
        String c = git("rev-parse", "c");
        git("branch", "-qD", "c");
        git("config", "rebase.missingCommitsCheck", "error");
        List<StackLayer> layers = List.of(
                new StackLayer(E, List.of(new Revision.Commit(Sha.parse(c).get()))));

        new CliIntegrate(repo).rebaseStack(BASE, read(layers)).get();

        assertEquals("e1 squashed c m1 base", log("e"));
    }

    @Test
    void aConflictLeavesTheRebaseToBeContinued() throws Exception {
        git("checkout", "-q", "d");
        write("c1", "not as on c\n");
        git("add", ".");
        git("commit", "-qm", "d touches c1");
        git("checkout", "-q", "main");

        GitFailure failure =
                new CliIntegrate(repo).rebaseStack(BASE, read(layers())).getLeft();

        assertInstanceOf(GitError.StoppedOnConflicts.class, failure.error());
        assertTrue(Files.isDirectory(repo.resolve(git("rev-parse", "--git-path", "rebase-merge"))));
    }

    @Test
    void changesThatAreNotCommittedAreNotRebasedOntoTheTop() throws Exception {
        String d = git("rev-parse", "d");
        write("base", "not committed\n");

        assertTrue(new CliIntegrate(repo).rebaseStack(BASE, read(layers())).isLeft());

        assertEquals(d, git("rev-parse", "d"));
        assertEquals("refs/heads/main", git("symbolic-ref", "HEAD"));
        assertEquals("not committed", Files.readString(repo.resolve("base")).strip());
    }

    @Test
    void aBranchThatMovedSinceItWasReadIsNotRebasedWithoutWhatItGot() throws Exception {
        List<StackLayer.Commits> commits = read(layers());
        git("checkout", "-q", "c");
        commit("c3");
        git("checkout", "-q", "main");

        GitFailure failure = new CliIntegrate(repo).rebaseStack(BASE, commits).getLeft();

        assertEquals(new GitError.StackBranchMoved(C), failure.error());
        assertEquals("c3 c2 c1 base", log("c"));
    }

    @Test
    void aBranchCheckedOutInAnotherWorktreeSinceItWasReadIsNotMovedUnderIt() throws Exception {
        List<StackLayer.Commits> commits = read(layers());
        Files.delete(other);
        git("worktree", "add", "-q", other.toString(), "c");

        GitFailure failure = new CliIntegrate(repo).rebaseStack(BASE, commits).getLeft();

        assertInstanceOf(GitError.StackBranchCheckedOutElsewhere.class, failure.error());
        assertEquals(C, ((GitError.StackBranchCheckedOutElsewhere) failure.error()).branch());
        assertEquals("c2 c1 base", log("c"));
    }

    @Test
    void aBranchCheckedOutHereIsRebased() throws Exception {
        git("checkout", "-q", "c");

        new CliIntegrate(repo).rebaseStack(BASE, read(layers())).get();

        assertEquals("c2 c1 m1 base", log("c"));
    }

    @Test
    void theBranchesArePushedAllAtOnceAndFollowWhatTheyWerePushedTo() throws Exception {
        bareOrigin();

        new CliRemotes(repo, HashMap::empty)
                .pushAll(ORIGIN, List.of(Tuple.of(C, C), Tuple.of(D, D)))
                .get();

        assertEquals(git("rev-parse", "c"), git("rev-parse", "origin/c"));
        assertEquals(git("rev-parse", "d"), git("rev-parse", "origin/d"));
        assertEquals("origin/d", git("rev-parse", "--abbrev-ref", "d@{upstream}"));
    }

    @Test
    void aBranchIsPushedToTheBranchItsPullRequestIsOf() throws Exception {
        bareOrigin();
        BranchName feature = new BranchName("feat/c");

        new CliRemotes(repo, HashMap::empty)
                .pushAll(ORIGIN, List.of(Tuple.of(C, feature)))
                .get();

        assertEquals(git("rev-parse", "c"), git("rev-parse", "origin/feat/c"));
        assertEquals("origin/feat/c", git("rev-parse", "--abbrev-ref", "c@{upstream}"));
        assertEquals("", git("branch", "-r", "--list", "origin/c"));
    }

    @Test
    void aRebasedStackIsPushedOverWhatItWas() throws Exception {
        pushedToOrigin();

        new CliIntegrate(repo).rebaseStack(BASE, read(layers())).get();
        new CliRemotes(repo, HashMap::empty)
                .pushAll(ORIGIN, List.of(Tuple.of(C, C), Tuple.of(D, D)))
                .get();

        assertEquals(git("rev-parse", "d"), git("rev-parse", "origin/d"));
    }

    @Test
    void aCommitFetchedButNotInTheBranchIsNotPushedOver() throws Exception {
        pushedToOrigin();
        agentPushesToC();
        String agent = git("rev-parse", "origin/c");

        new CliIntegrate(repo).rebaseStack(BASE, read(layers())).get();

        assertTrue(new CliRemotes(repo, HashMap::empty)
                .pushAll(ORIGIN, List.of(Tuple.of(C, C), Tuple.of(D, D)))
                .isLeft());
        git("fetch", "-q", "origin");
        assertEquals(agent, git("rev-parse", "origin/c"));
    }

    @Test
    void aBranchRewrittenHereHadWhatItWasPushedAs() throws Exception {
        pushedToOrigin();
        git("checkout", "-q", "c");
        write("c2", "c2 amended\n");
        git("commit", "-qa", "--amend", "-m", "c2 amended");

        assertTrue(new CliHistory(repo)
                .everHad(C, new RemoteBranch(ORIGIN, C).tracking())
                .get());
    }

    @Test
    void aBranchNeverHadWhatSomeoneElsePushed() throws Exception {
        pushedToOrigin();
        agentPushesToC();

        assertFalse(new CliHistory(repo)
                .everHad(C, new RemoteBranch(ORIGIN, C).tracking())
                .get());
    }

    private void bareOrigin() throws Exception {
        new ProcessExecutor()
                .command("git", "init", "-q", "--bare", remote.toString())
                .exitValueNormal()
                .execute();
        git("remote", "add", "origin", remote.toString());
    }

    private void pushedToOrigin() throws Exception {
        bareOrigin();
        git("push", "-q", "-u", "origin", "c", "d");
    }

    // An agent pushes to c from a clone of its own, which a fetch here brings in.
    private void agentPushesToC() throws Exception {
        new ProcessExecutor()
                .command("git", "clone", "-q", "-b", "c", remote.toString(), other.toString())
                .exitValueNormal()
                .execute();
        Files.writeString(other.resolve("agent"), "agent\n");
        new ProcessExecutor()
                .command(
                        "sh", "-c", "git add . && git -c user.email=a@a -c user.name=a commit -qm agent && git push -q")
                .directory(other.toFile())
                .exitValueNormal()
                .execute();
        git("fetch", "-q", "origin");
    }

    private String log(String branch) throws Exception {
        return git("log", "--format=%s", branch).replace('\n', ' ');
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
