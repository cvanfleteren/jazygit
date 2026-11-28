package net.vanfleteren.jazygit.core.git.branches;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.nio.file.Path;
import net.vanfleteren.jazygit.core.git.model.BaseCandidate;
import net.vanfleteren.jazygit.core.git.model.BaseCandidates;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.core.repo.Sha;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zeroturnaround.exec.ProcessExecutor;

/**
 * The branches a branch could be built on, and setting the one it is (ADR 143); the branches built on a commit
 * (ADR 148).
 */
class CliBaseCandidatesTest {

    private static final BranchName MAIN = new BranchName("main");
    private static final BranchName STAGING = new BranchName("staging/sync");
    private static final BranchName SIBLING = new BranchName("sibling");
    private static final BranchName TOPIC = new BranchName("topic");

    @TempDir
    Path repo;

    @BeforeEach
    void init() throws Exception {
        git("init", "-q", "-b", "main");
        commit("one");
    }

    @Test
    void eachBranchCountsTheCommitsOfTheTopicSinceTheyForkedAndItsOwn() throws Exception {
        git("checkout", "-q", "-b", "staging/sync");
        commit("staging");
        git("checkout", "-q", "-b", "sibling");
        commit("sibling one");
        commit("sibling two");
        git("checkout", "-q", "-b", "topic", "staging/sync");
        commit("topic one");
        commit("topic two");

        assertEquals(
                List.of(local(MAIN, 3, 0), local(SIBLING, 2, 2), local(STAGING, 2, 0)),
                candidates().candidates().sortBy(candidate -> candidate.name().value()));
    }

    @Test
    void theBranchItselfAndBranchesThatHaveItsTipAreNoCandidates() throws Exception {
        git("checkout", "-q", "-b", "topic");
        commit("topic");
        git("branch", "copy");
        git("checkout", "-q", "-b", "above");
        commit("above");
        git("checkout", "-q", "topic");

        assertEquals(List.of(MAIN), candidates().candidates().map(BaseCandidate::name));
    }

    @Test
    void aBranchBuiltOnTheTopicIsNoCandidateAlsoWhenItsCopyOnOriginIsOlder() throws Exception {
        git("checkout", "-q", "-b", "topic");
        commit("topic");
        git("checkout", "-q", "-b", "above");
        // Pushed before it was rebased onto the topic: the copy on origin does not have the topic's tip.
        git("update-ref", "refs/remotes/origin/above", "main");
        commit("above");
        git("checkout", "-q", "topic");

        assertEquals(List.of(MAIN), candidates().candidates().map(BaseCandidate::name));
    }

    @Test
    void aBranchOnOriginIsCountedInPlaceOfTheLocalOneOfTheSameName() throws Exception {
        git("checkout", "-q", "-b", "staging/sync");
        commit("staging");
        git("update-ref", "refs/remotes/origin/staging/sync", "HEAD");
        commit("not pushed");
        git("update-ref", "refs/remotes/origin/HEAD", "HEAD");
        git("checkout", "-q", "-b", "topic", "origin/staging/sync");
        commit("topic");

        BaseCandidate staging = candidates()
                .candidates()
                .find(candidate -> candidate.name().equals(STAGING))
                .get();
        assertEquals(
                new BaseCandidate(
                        STAGING, new BranchRef.Remote(new RemoteBranch(RemoteBranch.DEFAULT_REMOTE, STAGING)), 1, 0),
                staging);
        assertEquals(2, candidates().candidates().size());
    }

    @Test
    void theReflogTellsWhichBranchItWasMadeFrom() throws Exception {
        git("checkout", "-q", "-b", "staging/sync");
        commit("staging");
        git("checkout", "-q", "-b", "topic", "staging/sync");
        commit("topic");

        assertEquals(Option.of(STAGING), candidates().createdFrom());
    }

    @Test
    void aBranchMadeFromABranchOnOriginWasMadeFromThatBranch() throws Exception {
        git("update-ref", "refs/remotes/origin/staging/sync", "HEAD");
        git("branch", "topic", "origin/staging/sync");

        assertEquals(Option.of(STAGING), candidates().createdFrom());
    }

    @Test
    void aBranchMadeFromHeadSaysNothingOfWhereItCameFrom() throws Exception {
        git("checkout", "-q", "--detach");
        git("checkout", "-q", "-b", "topic");
        commit("topic");

        assertEquals(Option.none(), candidates().createdFrom());
    }

    @Test
    void theStartsGitWritesNameTheBranch() {
        assertEquals(Option.of(STAGING), CliBaseCandidates.startBranch("staging/sync"));
        assertEquals(Option.of(STAGING), CliBaseCandidates.startBranch("origin/staging/sync"));
        assertEquals(Option.of(STAGING), CliBaseCandidates.startBranch("refs/heads/staging/sync"));
        assertEquals(Option.of(STAGING), CliBaseCandidates.startBranch("refs/remotes/origin/staging/sync"));
        assertEquals(Option.none(), CliBaseCandidates.startBranch("HEAD"));
    }

    @Test
    void theBaseIsRecordedAndRemovedWhereGhKeepsIt() throws Exception {
        git("branch", "topic");
        CliBranches branches = new CliBranches(repo);

        branches.setParent(TOPIC, Option.of(STAGING)).get();
        Option<BranchName> recorded = branches.parent(TOPIC).get();
        branches.setParent(TOPIC, Option.none()).get();

        assertEquals(Option.of(STAGING), recorded);
        assertEquals(Option.none(), branches.parent(TOPIC).get());
        // Removing what is not recorded does nothing.
        branches.setParent(TOPIC, Option.none()).get();
    }

    // ADR 148: the branches built on a commit, and those at it, are the ones that have it.
    @Test
    void theLocalBranchesThatHaveACommitAreThoseAtItAndBuiltOnIt() throws Exception {
        git("checkout", "-q", "-b", "topic");
        commit("topic");
        git("branch", "copy");
        git("checkout", "-q", "-b", "stacked/above");
        commit("above");
        git("checkout", "-q", "-b", "beside", "main");
        commit("beside");

        assertEquals(
                List.of(new BranchName("copy"), new BranchName("stacked/above"), TOPIC),
                new CliBranches(repo).containing(new Sha(tip())).get());
    }

    @Test
    void ofTheGivenBranchesThoseThatHaveACommitAreKeptInOrder() throws Exception {
        git("checkout", "-q", "-b", "topic");
        commit("topic");
        Sha topic = new Sha(tip());
        git("checkout", "-q", "-b", "beside", "main");
        commit("beside");
        BranchRef main = new BranchRef.Local(MAIN);
        BranchRef beside = new BranchRef.Local(new BranchName("beside"));
        BranchRef missing = new BranchRef.Remote(new RemoteBranch(RemoteBranch.DEFAULT_REMOTE, TOPIC));

        CliBranches branches = new CliBranches(repo);

        assertEquals(
                List.of(new BranchRef.Local(TOPIC)),
                branches.containing(topic, List.of(main, beside, new BranchRef.Local(TOPIC), missing))
                        .get());
        assertEquals(List.empty(), branches.containing(topic, List.empty()).get());
    }

    private BaseCandidates candidates() throws Exception {
        return new CliBranches(repo).baseCandidates(TOPIC, new Sha(tip())).get();
    }

    private String tip() throws Exception {
        return new ProcessExecutor()
                .command("git", "-C", repo.toString(), "rev-parse", "topic")
                .readOutput(true)
                .exitValueNormal()
                .execute()
                .outputUTF8()
                .strip();
    }

    private static BaseCandidate local(BranchName name, int own, int theirs) {
        return new BaseCandidate(name, new BranchRef.Local(name), own, theirs);
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
