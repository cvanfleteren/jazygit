package net.vanfleteren.jazygit.git;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.nio.file.Path;
import net.vanfleteren.jazygit.git.model.BaseDistance;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zeroturnaround.exec.ProcessExecutor;

/**
 * How far each local branch is from its base (ADR 46).
 */
class CliBaseDistanceTest {

    private static final BranchName MAIN = new BranchName("main");
    private static final BranchName FEATURE = new BranchName("feature");
    private static final BranchName STACKED = new BranchName("stacked");
    private static final BranchRef LOCAL_MAIN = new BranchRef.Local(MAIN);
    private static final BranchRef ORIGIN_MAIN =
            new BranchRef.Remote(new RemoteBranch(RemoteBranch.DEFAULT_REMOTE, MAIN));

    @TempDir
    Path repo;

    @BeforeEach
    void init() throws Exception {
        git("init", "-q", "-b", "main");
        commit("one");
    }

    @Test
    void aBranchIsBehindTheMainBranchByWhatWasCommittedThereSince() throws Exception {
        git("branch", "feature");
        commit("two");
        commit("three");

        assertEquals(
                HashMap.of(FEATURE, new BaseDistance(LOCAL_MAIN, 0, 2)),
                CliBaseDistance.read(repo, Option.of(MAIN)).get());
    }

    @Test
    void aBranchIsAheadOfTheMainBranchByWhatWasCommittedOnIt() throws Exception {
        git("checkout", "-q", "-b", "feature");
        commit("on feature");
        git("checkout", "-q", "main");
        commit("two");
        commit("three");

        assertEquals(
                HashMap.of(FEATURE, new BaseDistance(LOCAL_MAIN, 1, 2)),
                CliBaseDistance.read(repo, Option.of(MAIN)).get());
    }

    @Test
    void theMainBranchHasNoBaseNorDoesAnyBranchWithoutOne() throws Exception {
        git("branch", "feature");

        assertEquals(
                HashMap.of(FEATURE, new BaseDistance(LOCAL_MAIN, 0, 0)),
                CliBaseDistance.read(repo, Option.of(MAIN)).get());
        assertEquals(HashMap.empty(), CliBaseDistance.read(repo, Option.none()).get());
    }

    @Test
    void aBranchMadeFromAnotherIsComparedWithThatOne() throws Exception {
        git("branch", "feature");
        git("checkout", "-q", "feature");
        commit("on feature");
        git("branch", "stacked");
        git("config", "--local", "branch.stacked.gh-merge-base", "feature");
        commit("more on feature");

        assertEquals(
                HashMap.of(
                        FEATURE,
                        new BaseDistance(LOCAL_MAIN, 2, 0),
                        STACKED,
                        new BaseDistance(new BranchRef.Local(FEATURE), 0, 1)),
                CliBaseDistance.read(repo, Option.of(MAIN)).get());
    }

    @Test
    void aParentThatIsGoneFallsBackToTheMainBranch() throws Exception {
        git("branch", "stacked");
        git("config", "--local", "branch.stacked.gh-merge-base", "deleted");
        commit("two");

        assertEquals(
                HashMap.of(STACKED, new BaseDistance(LOCAL_MAIN, 0, 1)),
                CliBaseDistance.read(repo, Option.of(MAIN)).get());
    }

    @Test
    void theBaseOnOriginCountsBeforeTheLocalOne() throws Exception {
        git("branch", "feature");
        commit("two");
        git("update-ref", "refs/remotes/origin/main", "HEAD");
        commit("three");
        commit("four");

        // origin/main has one commit feature does not; the local main, three.
        assertEquals(
                HashMap.of(FEATURE, new BaseDistance(ORIGIN_MAIN, 0, 1)),
                CliBaseDistance.read(repo, Option.of(MAIN)).get());
    }

    @Test
    void theParentsAreReadFromTheKeysGhSets() {
        assertEquals(
                HashMap.of(new BranchName("feat.x"), MAIN, STACKED, FEATURE),
                CliBaseDistance.parseParents(
                        "branch.feat.x.gh-merge-base main\nbranch.stacked.gh-merge-base feature\nbranch.odd.gh-merge-base\n"));
    }

    @Test
    void theCountsAreTheCommitsAheadThenThoseBehind() {
        assertEquals(
                HashMap.of(FEATURE, io.vavr.Tuple.of(1, 58), MAIN, io.vavr.Tuple.of(0, 0)),
                CliBaseDistance.parseCounts("refs/heads/feature\u001f1 58\nrefs/heads/main\u001f0 0\n")
                        .get());
        assertEquals(
                true,
                CliBaseDistance.parseCounts("refs/heads/feature\u001f58\n").isLeft());
        assertEquals(
                true,
                CliBaseDistance.parseCounts("refs/heads/feature\u001fx 58\n").isLeft());
    }

    @Test
    void eachBranchTakesTheCommitOfTheFirstBaseThatIsThere() {
        assertEquals(
                List.of(
                        io.vavr.Tuple.of(FEATURE, new CliBaseDistance.Base(ORIGIN_MAIN, "2222")),
                        io.vavr.Tuple.of(STACKED, new CliBaseDistance.Base(new BranchRef.Local(FEATURE), "1111"))),
                CliBaseDistance.bases(
                        HashMap.of(
                                "refs/heads/feature", "1111",
                                "refs/heads/main", "3333",
                                "refs/heads/stacked", "4444",
                                "refs/remotes/origin/main", "2222"),
                        HashMap.of(STACKED, FEATURE),
                        Option.of(MAIN)));
    }

    @Test
    void theRefsAreReadWithTheCommitsTheyAreAt() {
        assertEquals(
                HashMap.of("refs/heads/fix)x", "1111", "refs/remotes/origin/main", "2222"),
                CliBaseDistance.parseRefs("refs/heads/fix)x\u001f1111\nrefs/remotes/origin/main\u001f2222\n")
                        .get());
    }

    @Test
    void aBaseWhoseNameHoldsAParenthesisCountsToo() throws Exception {
        git("branch", "fix)x");
        git("branch", "stacked");
        git("config", "--local", "branch.stacked.gh-merge-base", "fix)x");
        git("checkout", "-q", "fix)x");
        commit("on fix)x");

        assertEquals(
                HashMap.of(
                        new BranchName("fix)x"),
                        new BaseDistance(LOCAL_MAIN, 1, 0),
                        STACKED,
                        new BaseDistance(new BranchRef.Local(new BranchName("fix)x")), 0, 1)),
                CliBaseDistance.read(repo, Option.of(MAIN)).get());
    }

    private void commit(String message) throws Exception {
        git("commit", "-q", "--allow-empty", "-m", message);
    }

    private void git(String... args) throws Exception {
        new ProcessExecutor()
                .command(List.of(
                                "git",
                                "-C",
                                repo.toString(),
                                "-c",
                                "user.name=Test",
                                "-c",
                                "user.email=test@example.com",
                                "-c",
                                "commit.gpgsign=false")
                        .appendAll(List.of(args))
                        .toJavaList())
                .exitValueNormal()
                .execute();
    }
}
