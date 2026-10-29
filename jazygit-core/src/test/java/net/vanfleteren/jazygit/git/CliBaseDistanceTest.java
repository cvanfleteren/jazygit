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
import net.vanfleteren.jazygit.git.model.Sha;
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
                HashMap.of(FEATURE, distance(LOCAL_MAIN, "feature", 0, 2)),
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
                HashMap.of(FEATURE, distance(LOCAL_MAIN, "feature", 1, 2)),
                CliBaseDistance.read(repo, Option.of(MAIN)).get());
    }

    @Test
    void theMainBranchHasNoBaseNorDoesAnyBranchWithoutOne() throws Exception {
        git("branch", "feature");

        assertEquals(
                HashMap.of(FEATURE, distance(LOCAL_MAIN, "feature", 0, 0)),
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
                        distance(LOCAL_MAIN, "feature", 2, 0),
                        STACKED,
                        distance(new BranchRef.Local(FEATURE), "stacked", 0, 1)),
                CliBaseDistance.read(repo, Option.of(MAIN)).get());
    }

    @Test
    void aParentThatIsGoneFallsBackToTheMainBranch() throws Exception {
        git("branch", "stacked");
        git("config", "--local", "branch.stacked.gh-merge-base", "deleted");
        commit("two");

        assertEquals(
                HashMap.of(STACKED, distance(LOCAL_MAIN, "stacked", 0, 1)),
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
                HashMap.of(FEATURE, distance(ORIGIN_MAIN, "feature", 0, 1)),
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
    void theCountsAreTheTipThenTheCommitsAheadThenThoseBehind() {
        Sha tip = new Sha("a".repeat(40));
        HashMap<BranchName, CliBaseDistance.Base> branches =
                HashMap.of(FEATURE, new CliBaseDistance.Base(ORIGIN_MAIN, "2222"));

        assertEquals(
                HashMap.of(FEATURE, new BaseDistance(ORIGIN_MAIN, tip, 1, 58)),
                CliBaseDistance.parseCounts(
                                "refs/heads/feature\u001f" + tip.value() + "\u001f1 58\nrefs/heads/main\u001f"
                                        + tip.value() + "\u001f0 0\n",
                                branches)
                        .get());
        assertEquals(
                true,
                CliBaseDistance.parseCounts("refs/heads/feature\u001f" + tip.value() + "\u001f58\n", branches)
                        .isLeft());
        assertEquals(
                true,
                CliBaseDistance.parseCounts("refs/heads/feature\u001f" + tip.value() + "\u001fx 58\n", branches)
                        .isLeft());
        assertEquals(
                true,
                CliBaseDistance.parseCounts("refs/heads/feature\u001fnot a sha\u001f1 58\n", branches)
                        .isLeft());
    }

    @Test
    void branchesWhoseBasesAreAtTheSameCommitKeepTheirOwnBase() throws Exception {
        git("branch", "feature");
        git("branch", "stacked");
        git("branch", "other");
        git("config", "--local", "branch.stacked.gh-merge-base", "feature");

        // feature, main and so the bases of stacked and other are all at one commit, counted against in one go.
        assertEquals(
                HashMap.of(
                        FEATURE,
                        distance(LOCAL_MAIN, "feature", 0, 0),
                        STACKED,
                        distance(new BranchRef.Local(FEATURE), "stacked", 0, 0),
                        new BranchName("other"),
                        distance(LOCAL_MAIN, "other", 0, 0)),
                CliBaseDistance.read(repo, Option.of(MAIN)).get());
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
                        distance(LOCAL_MAIN, "fix)x", 1, 0),
                        STACKED,
                        distance(new BranchRef.Local(new BranchName("fix)x")), "stacked", 0, 1)),
                CliBaseDistance.read(repo, Option.of(MAIN)).get());
    }

    /**
     * How far {@code branch} is from {@code base}, counted at the commit it is at now.
     */
    private BaseDistance distance(BranchRef base, String branch, int ahead, int behind) throws Exception {
        String tip = new ProcessExecutor()
                .command("git", "-C", repo.toString(), "rev-parse", "refs/heads/" + branch)
                .readOutput(true)
                .exitValueNormal()
                .execute()
                .outputUTF8()
                .strip();
        return new BaseDistance(base, new Sha(tip), ahead, behind);
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
