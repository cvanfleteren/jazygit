package net.vanfleteren.jazygit.git;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.nio.file.Path;
import net.vanfleteren.jazygit.git.model.BranchName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zeroturnaround.exec.ProcessExecutor;

/**
 * How many commits of its base each local branch does not have (ADR 46).
 */
class CliBehindTest {

    private static final BranchName MAIN = new BranchName("main");
    private static final BranchName FEATURE = new BranchName("feature");
    private static final BranchName STACKED = new BranchName("stacked");

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
                HashMap.of(FEATURE, 2), CliBehind.read(repo, Option.of(MAIN)).get());
    }

    @Test
    void theMainBranchHasNoBaseNorDoesAnyBranchWithoutOne() throws Exception {
        git("branch", "feature");

        assertEquals(
                HashMap.of(FEATURE, 0), CliBehind.read(repo, Option.of(MAIN)).get());
        assertEquals(HashMap.empty(), CliBehind.read(repo, Option.none()).get());
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
                HashMap.of(FEATURE, 0, STACKED, 1),
                CliBehind.read(repo, Option.of(MAIN)).get());
    }

    @Test
    void aParentThatIsGoneFallsBackToTheMainBranch() throws Exception {
        git("branch", "stacked");
        git("config", "--local", "branch.stacked.gh-merge-base", "deleted");
        commit("two");

        assertEquals(
                HashMap.of(STACKED, 1), CliBehind.read(repo, Option.of(MAIN)).get());
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
                HashMap.of(FEATURE, 1), CliBehind.read(repo, Option.of(MAIN)).get());
    }

    @Test
    void theParentsAreReadFromTheKeysGhSets() {
        assertEquals(
                HashMap.of(new BranchName("feat.x"), MAIN, STACKED, FEATURE),
                CliBehind.parseParents(
                        "branch.feat.x.gh-merge-base main\nbranch.stacked.gh-merge-base feature\nbranch.odd.gh-merge-base\n"));
    }

    @Test
    void theCountsAreTheSecondNumberOfEachLine() {
        assertEquals(
                HashMap.of(FEATURE, 58, MAIN, 0),
                CliBehind.parseBehind("refs/heads/feature\u001f1 58\nrefs/heads/main\u001f0 0\n")
                        .get());
        assertEquals(true, CliBehind.parseBehind("refs/heads/feature\u001f58\n").isLeft());
    }

    @Test
    void eachBranchTakesTheCommitOfTheFirstBaseThatIsThere() {
        assertEquals(
                List.of(io.vavr.Tuple.of(FEATURE, "2222"), io.vavr.Tuple.of(STACKED, "1111")),
                CliBehind.bases(
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
                CliBehind.parseRefs("refs/heads/fix)x\u001f1111\nrefs/remotes/origin/main\u001f2222\n")
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
                HashMap.of(new BranchName("fix)x"), 0, STACKED, 1),
                CliBehind.read(repo, Option.of(MAIN)).get());
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
