package net.vanfleteren.jazygit.git;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Either;
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
                read(Option.of(MAIN)).get());
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
                read(Option.of(MAIN)).get());
    }

    @Test
    void theMainBranchHasNoBaseNorDoesAnyBranchWithoutOne() throws Exception {
        git("branch", "feature");

        assertEquals(
                HashMap.of(FEATURE, distance(LOCAL_MAIN, "feature", 0, 0)),
                read(Option.of(MAIN)).get());
        assertEquals(HashMap.empty(), read(Option.none()).get());
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
                read(Option.of(MAIN)).get());
    }

    @Test
    void aParentThatIsGoneFallsBackToTheMainBranch() throws Exception {
        git("branch", "stacked");
        git("config", "--local", "branch.stacked.gh-merge-base", "deleted");
        commit("two");

        assertEquals(
                HashMap.of(STACKED, distance(LOCAL_MAIN, "stacked", 0, 1)),
                read(Option.of(MAIN)).get());
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
                read(Option.of(MAIN)).get());
    }

    @Test
    void theParentsAreReadFromTheKeysGhSets() {
        assertEquals(
                HashMap.of(new BranchName("feat.x"), MAIN, STACKED, FEATURE),
                CliBaseDistance.parseParents(
                        "branch.feat.x.gh-merge-base main\nbranch.stacked.gh-merge-base feature\nbranch.odd.gh-merge-base\n"));
    }

    @Test
    void theCountsAreTheTipThenTheCommitsAheadAndBehindOfEachBase() {
        Sha tip = new Sha("a".repeat(40));
        List<String> commits = List.of("1111", "2222");
        HashMap<BranchName, CliBaseDistance.Base> branches = HashMap.of(
                FEATURE,
                new CliBaseDistance.Base(ORIGIN_MAIN, "2222"),
                STACKED,
                new CliBaseDistance.Base(new BranchRef.Local(FEATURE), "1111"));

        assertEquals(
                HashMap.of(
                        FEATURE,
                        new BaseDistance(ORIGIN_MAIN, tip, 1, 58),
                        STACKED,
                        new BaseDistance(new BranchRef.Local(FEATURE), tip, 0, 3)),
                CliBaseDistance.parseCounts(
                                "refs/heads/feature\u001f" + tip.value() + "\u001f9 9\u001f1 58\n"
                                        + "refs/heads/stacked\u001f" + tip.value() + "\u001f0 3\u001f9 9\n"
                                        + "refs/heads/main\u001f" + tip.value() + "\u001f0 0\u001f0 0\n",
                                commits,
                                branches)
                        .get());
        assertEquals(
                true,
                CliBaseDistance.parseCounts(
                                "refs/heads/feature\u001f" + tip.value() + "\u001f9 9\u001f58\n", commits, branches)
                        .isLeft());
        assertEquals(
                true,
                CliBaseDistance.parseCounts(
                                "refs/heads/feature\u001f" + tip.value() + "\u001f9 9\u001fx 58\n", commits, branches)
                        .isLeft());
        assertEquals(
                true,
                CliBaseDistance.parseCounts("refs/heads/feature\u001fnot a sha\u001f9 9\u001f1 58\n", commits, branches)
                        .isLeft());
    }

    @Test
    void aBaseGitCannotFindLeavesOutOnlyItsBranches() throws Exception {
        git("branch", "feature");
        git("branch", "stacked");
        git("config", "--local", "branch.stacked.gh-merge-base", "feature");
        commit("two");
        // feature as the branches were read: since rewritten, and its commit pruned.
        Map<BranchRef, Sha> tips = tips().put(new BranchRef.Local(FEATURE), new Sha("1".repeat(40)));

        assertEquals(
                HashMap.of(FEATURE, distance(LOCAL_MAIN, "feature", 0, 1)),
                CliBaseDistance.read(repo, tips, Option.of(MAIN)).get());
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
                read(Option.of(MAIN)).get());
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
                read(Option.of(MAIN)).get());
    }

    /**
     * How far each branch is from its base, its branches and their bases where they are now.
     */
    private Either<GitError.ProcessError, Map<BranchName, BaseDistance>> read(Option<BranchName> main)
            throws Exception {
        return CliBaseDistance.read(repo, tips(), main);
    }

    /**
     * The commit each local branch and each branch on origin is at now.
     */
    private Map<BranchRef, Sha> tips() throws Exception {
        String raw = new ProcessExecutor()
                .command("git", "-C", repo.toString(), "for-each-ref", "--format=%(refname) %(objectname)")
                .readOutput(true)
                .exitValueNormal()
                .execute()
                .outputUTF8();
        return HashMap.ofEntries(List.of(raw.split("\n"))
                .filter(line -> !line.isBlank())
                .map(line -> line.split(" "))
                .flatMap(fields -> fields[0].startsWith("refs/heads/")
                        ? Option.of(io.vavr.Tuple.<BranchRef, Sha>of(
                                new BranchRef.Local(new BranchName(fields[0].substring("refs/heads/".length()))),
                                new Sha(fields[1])))
                        : fields[0].startsWith("refs/remotes/origin/")
                                ? Option.of(io.vavr.Tuple.<BranchRef, Sha>of(
                                        new BranchRef.Remote(new RemoteBranch(
                                                RemoteBranch.DEFAULT_REMOTE,
                                                new BranchName(fields[0].substring("refs/remotes/origin/".length())))),
                                        new Sha(fields[1])))
                                : Option.none()));
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
