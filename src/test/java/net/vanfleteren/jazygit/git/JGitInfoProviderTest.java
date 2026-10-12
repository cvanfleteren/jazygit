package net.vanfleteren.jazygit.git;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.*;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * {@link JGitInfoProvider} against a real repository changed through the git CLI: every call
 * reflects the current state of the repository.
 */
class JGitInfoProviderTest {

    @TempDir
    Path repo;

    private JGitInfoProvider provider;

    @BeforeEach
    void setUp() throws Exception {
        assumeTrue(gitAvailable(), "git is not installed");
        git("init", "-q", "-b", "main");
        Files.writeString(repo.resolve("README.md"), "hello\n");
        git("add", "README.md");
        git("commit", "-q", "-m", "Initial commit");
        provider = JGitInfoProvider.open(repo).get();
    }

    @Test
    void openingFailsOutsideARepositoryAndForABareOne(@TempDir Path elsewhere) throws Exception {
        Path plain = Files.createDirectories(elsewhere.resolve("plain"));
        Path bare = elsewhere.resolve("bare.git");
        gitIn(elsewhere, "init", "-q", "--bare", bare.toString());

        assertInstanceOf(GitError.NoWorkTree.class, JGitInfoProvider.open(bare).getLeft());
        assumeTrue(new FileRepositoryBuilder().findGitDir(plain.toFile()).getGitDir() == null,
                "the temporary directory is inside a repository");
        assertEquals(new GitError.NotARepository(plain), JGitInfoProvider.open(plain).getLeft());
    }

    @AfterEach
    void tearDown() {
        if (provider != null) {
            provider.close();
        }
    }

    @Test
    void statusFollowsTheWorkingTree() throws Exception {
        assertEquals(List.of(), provider.status().get().files());

        Files.writeString(repo.resolve("new.txt"), "new\n");
        assertEquals(List.of(new FileEntry("new.txt", ChangeType.UNTRACKED)), provider.status().get().files());

        git("add", "new.txt");
        assertEquals(List.of(new FileEntry("new.txt", ChangeType.ADDED)), provider.status().get().files());
    }

    @Test
    void commitsAndStatusFollowNewCommits() throws Exception {
        Sha headBefore = provider.status().get().headCommit().get();
        Files.writeString(repo.resolve("new.txt"), "new\n");
        git("add", "new.txt");
        git("commit", "-q", "-m", "Second commit");

        RepoStatus status = provider.status().get();
        assertNotEquals(headBefore, status.headCommit().get());
        assertEquals(List.of(), status.files());
        List<Commit> commits = provider.commits().get();
        assertEquals("Second commit", commits.get(0).message());
        assertEquals(status.headCommit().get(), commits.get(0).sha());
    }

    @Test
    void branchesFollowExternalChanges() throws Exception {
        Sha head = provider.status().get().headCommit().get();
        assertEquals(List.of(new Branch("main", true, head)), withoutTimes(provider.branches(List.empty()).get()));

        git("branch", "foo");
        assertEquals(List.of(new Branch("main", true, head), new Branch("foo", false, head)), withoutTimes(provider.branches(List.empty()).get()));

        git("checkout", "-q", "foo");
        assertEquals(List.of(new Branch("foo", true, head), new Branch("main", false, head)), withoutTimes(provider.branches(List.empty()).get()));

        git("commit", "-q", "--allow-empty", "-m", "Move foo");
        Sha moved = provider.status().get().headCommit().get();
        assertEquals(List.of(new Branch("foo", true, moved), new Branch("main", false, head)), withoutTimes(provider.branches(List.empty()).get()));
    }

    @Test
    void branchesListTheCurrentOneFirstThenTheMostRecentlyCommittedTo() throws Exception {
        commitAt("main", "2020-01-02T00:00:00Z");
        for (String branch : List.of("old", "newest", "middle")) {
            git("branch", branch, "main");
        }
        commitAt("old", "2020-01-03T00:00:00Z");
        commitAt("middle", "2020-01-05T00:00:00Z");
        commitAt("newest", "2020-01-09T00:00:00Z");

        assertEquals(List.of("main", "newest", "middle", "old"),
                provider.branches(List.empty()).get().map(Branch::name).toList());

        git("checkout", "-q", "old");
        assertEquals(List.of("old", "newest", "middle", "main"),
                provider.branches(List.empty()).get().map(Branch::name).toList());
    }

    private void commitAt(String branch, String date) throws Exception {
        git("checkout", "-q", branch);
        ProcessResult result = new ProcessExecutor()
                .command("git", "-c", "user.name=Test", "-c", "user.email=test@example.com",
                        "-c", "commit.gpgsign=false", "commit", "-q", "--allow-empty", "-m", "at " + date)
                .environment("GIT_COMMITTER_DATE", date)
                .directory(repo.toFile())
                .readOutput(true)
                .redirectErrorStream(true)
                .exitValueAny()
                .execute();
        assertEquals(0, result.getExitValue(), result.outputUTF8());
        git("checkout", "-q", "main");
    }

    @Test
    void logShowsOnlyTheCommitsOfTheBranch() throws Exception {
        git("checkout", "-q", "-b", "side");
        git("commit", "-q", "--allow-empty", "-m", "Side subject", "-m", "Side detail\nsecond line");
        git("checkout", "-q", "main");

        List<Commit> side = provider.log("side").get();
        assertEquals(List.of("Side subject", "Initial commit"), side.map(Commit::message).toList());
        assertEquals("Side detail\nsecond line", side.get(0).body());
        assertEquals("", side.get(1).body());
        assertEquals("Test", side.get(0).authorName());
        assertEquals("test@example.com", side.get(0).authorEmail());

        assertEquals(List.of("Initial commit"), provider.log("main").get().map(Commit::message).toList());
        assertEquals(new GitError.UnknownBranch("gone"), provider.log("gone").getLeft());
    }

    @Test
    void reportsRepositoryNameAndCurrentBranch() throws Exception {
        assertEquals(repo.getFileName().toString(), provider.repositoryName());
        assertEquals("main", provider.status().get().branchLabel());

        git("checkout", "-q", "-b", "feature");
        assertEquals("feature", provider.status().get().branchLabel());

        git("checkout", "-q", "--detach");
        assertEquals("HEAD detached at " + provider.commits().get().get(0).shortSha(), provider.status().get().branchLabel());
    }

    @Test
    void checkoutSwitchesBranchAndCarriesOverLocalChanges() throws Exception {
        git("branch", "feature");
        Files.writeString(repo.resolve("new.txt"), "new\n");

        provider.checkout("feature").get();

        assertEquals("feature", provider.status().get().branchLabel());
        assertEquals(List.of(new FileEntry("new.txt", ChangeType.UNTRACKED)), provider.status().get().files());
    }

    @Test
    void mutatingCommandsReturnTheCommandLinesTheyRan() throws Exception {
        Files.writeString(repo.resolve("README.md"), "changed\n");
        Files.writeString(repo.resolve("new file.txt"), "new\n");

        assertEquals(List.of("git add -- README.md 'new file.txt'"),
                provider.stage(List.of("README.md", "new file.txt")).get());
        assertEquals(List.of("git rm --cached --force --quiet -- 'new file.txt'"),
                provider.unstageNew(List.of("new file.txt")).get());
        assertEquals(List.of(), provider.unstage(List.of()).get());
        assertEquals(List.of("git commit --quiet -m 'a summary' -m details"),
                provider.commit(new CommitMessage("a summary", "details")).get());

        git("branch", "feature");
        Files.writeString(repo.resolve("README.md"), "dirty\n");
        assertEquals(List.of("git stash push --quiet", "git checkout --quiet feature --",
                "git stash pop --quiet"), provider.checkoutWithStash("feature").get());
    }

    @Test
    void commitsKnowTheirParentsAndMergesAreFollowedByTheirBranch() throws Exception {
        git("checkout", "-q", "-b", "feature");
        Files.writeString(repo.resolve("f.txt"), "f\n");
        git("add", "f.txt");
        git("commit", "-q", "-m", "Feature work");
        git("checkout", "-q", "main");
        Files.writeString(repo.resolve("m.txt"), "m\n");
        git("add", "m.txt");
        git("commit", "-q", "-m", "Main work");
        git("merge", "-q", "--no-ff", "-m", "Merge feature", "feature");

        List<Commit> commits = provider.commits().get();

        assertEquals(List.of("Merge feature", "Feature work", "Main work"),
                commits.map(Commit::message).take(3));
        assertEquals(2, commits.head().parents().size());
        assertTrue(commits.head().isMerge());
    }

    @Test
    void rewordChangesTheMessageOfAnyCommitAndKeepsTheRest() throws Exception {
        Files.writeString(repo.resolve("a.txt"), "a\n");
        git("add", "a.txt");
        git("commit", "-q", "-m", "Second");
        Files.writeString(repo.resolve("b.txt"), "b\n");
        git("add", "b.txt");
        git("commit", "-q", "-m", "Third");
        Files.writeString(repo.resolve("README.md"), "dirty\n");
        List<Commit> before = provider.commits().get();

        List<String> executed = provider.reword(before.get(1).sha(), new CommitMessage("Second, better", "because")).get();

        assertEquals(List.of("git rebase --interactive --autostash --keep-empty --rebase-merges " + before.get(1).sha() + "^"),
                executed);
        List<Commit> after = provider.commits().get();
        assertEquals(List.of("Third", "Second, better", "Initial commit"),
                after.map(Commit::message).toList());
        assertEquals("because", after.get(1).body());
        assertEquals("hello\n".replace("hello", "dirty"), Files.readString(repo.resolve("README.md")));
        assertTrue(Files.exists(repo.resolve("a.txt")) && Files.exists(repo.resolve("b.txt")));
    }

    @Test
    void rebaseReplaysTheCheckedOutBranchOntoAnotherOne() throws Exception {
        git("checkout", "-q", "-b", "feature");
        Files.writeString(repo.resolve("f.txt"), "f\n");
        git("add", "f.txt");
        git("commit", "-q", "-m", "Feature work");
        git("checkout", "-q", "main");
        Files.writeString(repo.resolve("m.txt"), "m\n");
        git("add", "m.txt");
        git("commit", "-q", "-m", "Main work");
        git("checkout", "-q", "feature");

        List<String> executed = provider.rebase("main").get();

        assertEquals(List.of("git rebase --autostash --keep-empty --rebase-merges --no-autosquash main"), executed);
        assertEquals(List.of("Feature work", "Main work", "Initial commit"),
                provider.commits().get().map(Commit::message).toList());
    }

    @Test
    void rebaseKeepsMergesAndFixupCommitsInPlace() throws Exception {
        git("config", "rebase.autoSquash", "true");
        git("checkout", "-q", "-b", "feature");
        Files.writeString(repo.resolve("f.txt"), "f\n");
        git("add", "f.txt");
        git("commit", "-q", "-m", "Feature work");
        git("checkout", "-q", "-b", "side");
        Files.writeString(repo.resolve("s.txt"), "s\n");
        git("add", "s.txt");
        git("commit", "-q", "-m", "Side work");
        git("checkout", "-q", "feature");
        Files.writeString(repo.resolve("g.txt"), "g\n");
        git("add", "g.txt");
        git("commit", "-q", "-m", "More feature work");
        git("merge", "-q", "--no-ff", "-m", "Merge side", "side");
        Files.writeString(repo.resolve("f2.txt"), "f2\n");
        git("add", "f2.txt");
        git("commit", "-q", "-m", "fixup! Feature work");
        git("checkout", "-q", "main");
        Files.writeString(repo.resolve("m.txt"), "m\n");
        git("add", "m.txt");
        git("commit", "-q", "-m", "Main work");
        git("checkout", "-q", "feature");

        provider.rebase("main").get();

        List<Commit> after = provider.commits().get();
        assertEquals(List.of("fixup! Feature work", "Merge side"),
                after.take(2).map(Commit::message));
        assertTrue(after.get(1).isMerge());
        assertTrue(after.exists(c -> c.message().equals("Main work")));
    }

    @Test
    void aConflictingRebaseIsAbortedAndLeavesTheBranchAsItWas() throws Exception {
        git("checkout", "-q", "-b", "feature");
        Files.writeString(repo.resolve("README.md"), "feature\n");
        git("commit", "-q", "-am", "Feature edit");
        git("checkout", "-q", "main");
        Files.writeString(repo.resolve("README.md"), "main\n");
        git("commit", "-q", "-am", "Main edit");
        git("checkout", "-q", "feature");

        assertInstanceOf(GitError.CommandFailed.class, provider.rebase("main").getLeft().error());

        assertEquals(List.of("Feature edit", "Initial commit"),
                provider.commits().get().map(Commit::message).toList());
        assertEquals("feature\n", Files.readString(repo.resolve("README.md")));
    }

    @Test
    void squashFoldsACommitIntoItsParentAndKeepsBothMessages() throws Exception {
        Files.writeString(repo.resolve("a.txt"), "a\n");
        git("add", "a.txt");
        git("commit", "-q", "-m", "Second");
        Files.writeString(repo.resolve("b.txt"), "b\n");
        git("add", "b.txt");
        git("commit", "-q", "-m", "Third");
        Files.writeString(repo.resolve("c.txt"), "c\n");
        git("add", "c.txt");
        git("commit", "-q", "-m", "Fourth");
        Files.writeString(repo.resolve("README.md"), "dirty\n");
        List<Commit> before = provider.commits().get();

        List<String> executed = provider.squash(before.get(1).sha()).get();

        assertTrue(executed.head().startsWith("git rebase --interactive --autostash --keep-empty --rebase-merges"),
                executed.toString());
        List<Commit> after = provider.commits().get();
        assertEquals(List.of("Fourth", "Second", "Initial commit"), after.map(Commit::message).toList());
        assertTrue(after.get(1).body().contains("Third"), after.get(1).body());
        assertTrue(Files.exists(repo.resolve("b.txt")));
        assertEquals("dirty\n", Files.readString(repo.resolve("README.md")));
    }

    @Test
    void squashOfTheFirstCommitsChildRebasesFromTheRoot() throws Exception {
        Files.writeString(repo.resolve("a.txt"), "a\n");
        git("add", "a.txt");
        git("commit", "-q", "-m", "Second");

        provider.squash(provider.commits().get().head().sha()).get();

        assertEquals(List.of("Initial commit"), provider.commits().get().map(Commit::message).toList());
    }

    @Test
    void rewordOfTheFirstCommitRebasesFromTheRoot() throws Exception {
        Files.writeString(repo.resolve("a.txt"), "a\n");
        git("add", "a.txt");
        git("commit", "-q", "-m", "Second");

        provider.reword(provider.commits().get().get(1).sha(), new CommitMessage("Start", "")).get();

        assertEquals(List.of("Second", "Start"), provider.commits().get().map(Commit::message).toList());
    }

    @Test
    void rewordOfALastCommitAmendsIt() throws Exception {
        List<String> executed = provider.reword(provider.commits().get().head().sha(), new CommitMessage("Renamed", "")).get();

        assertEquals(1, executed.size());
        assertTrue(executed.head().startsWith("git commit"), executed.toString());
        assertEquals("Renamed", provider.commits().get().head().message());
    }

    @Test
    void rewordOfAMergeKeepsItsParents() throws Exception {
        git("checkout", "-q", "-b", "feature");
        Files.writeString(repo.resolve("f.txt"), "f\n");
        git("add", "f.txt");
        git("commit", "-q", "-m", "Feature work");
        git("checkout", "-q", "main");
        Files.writeString(repo.resolve("m.txt"), "m\n");
        git("add", "m.txt");
        git("commit", "-q", "-m", "Main work");
        git("merge", "-q", "--no-ff", "-m", "Merge feature", "feature");
        Files.writeString(repo.resolve("after.txt"), "x\n");
        git("add", "after.txt");
        git("commit", "-q", "-m", "After merge");
        Commit merge = provider.commits().get().get(1);

        provider.reword(merge.sha(), new CommitMessage("Merged the feature", "")).get();

        List<Commit> after = provider.commits().get();
        assertEquals("After merge", after.head().message());
        assertEquals("Merged the feature", after.get(1).message());
        assertTrue(after.get(1).isMerge());
    }

    @Test
    void aFailingRebaseIsAbortedAndReported() throws Exception {
        GitFailure<GitError.RewordError> failure =
                provider.reword(new Sha("0123456789012345678901234567890123456789"), new CommitMessage("x", "")).getLeft();

        assertTrue(failure.commands().head().startsWith("git rev-list"), failure.commands().toString());
        assertEquals("Initial commit", provider.commits().get().head().message());
        assertTrue(Files.notExists(repo.resolve(".git/rebase-merge")));
    }

    @Test
    void stashSavesTrackedAndUntrackedChangesUnderTheGivenName() throws Exception {
        Files.writeString(repo.resolve("README.md"), "changed\n");
        Files.writeString(repo.resolve("new.txt"), "new\n");

        List<String> executed = provider.stash("my work").get();

        assertEquals(List.of("git stash push --include-untracked --message 'my work'"), executed);
        assertEquals(List.of(), provider.status().get().files());
        assertEquals(List.of(new Stash("stash@{0}", "my work")), provider.stashes().get());
    }

    @Test
    void popRestoresTheChangesAndDropsTheStash() throws Exception {
        Files.writeString(repo.resolve("README.md"), "changed\n");
        provider.stash("work").get();

        List<String> executed = provider.pop("stash@{0}").get();

        assertEquals(1, executed.size());
        assertTrue(executed.head().startsWith("git stash pop"), executed.toString());
        assertEquals("changed\n", Files.readString(repo.resolve("README.md")));
        assertEquals(List.of(), provider.stashes().get());
    }

    @Test
    void dropDeletesTheStashWithoutApplyingIt() throws Exception {
        Files.writeString(repo.resolve("README.md"), "changed\n");
        provider.stash("work").get();

        List<String> executed = provider.drop("stash@{0}").get();

        assertEquals(1, executed.size());
        assertTrue(executed.head().startsWith("git stash drop"), executed.toString());
        assertEquals(List.of(), provider.stashes().get());
        assertEquals("hello\n", Files.readString(repo.resolve("README.md")));
    }

    @Test
    void popOfAnUnknownStashFailsWithTheCommand() {
        assertEquals(1, provider.pop("stash@{3}").getLeft().commands().size());
    }

    @Test
    void stashesListTheNewestFirstAndThereAreNoneInACleanRepository() throws Exception {
        assertEquals(List.of(), provider.stashes().get());
        Files.writeString(repo.resolve("README.md"), "one\n");
        provider.stash("first").get();
        Files.writeString(repo.resolve("README.md"), "two\n");
        provider.stash("second").get();

        assertEquals(List.of(new Stash("stash@{0}", "second"), new Stash("stash@{1}", "first")),
                provider.stashes().get());
    }

    @Test
    void aFailingCommandReportsTheCommandsRunUpToAndIncludingIt() {
        assertEquals(List.of("git branch --delete --force missing"),
                provider.deleteBranch("missing", DeleteScope.LOCAL).getLeft().commands());
        assertEquals(List.of("git add -- missing.txt"), provider.stage(List.of("missing.txt")).getLeft().commands());
    }

    @Test
    void checkoutFailsWithGitsExplanationWhenLocalChangesWouldBeOverwritten() throws Exception {
        git("checkout", "-q", "-b", "feature");
        Files.writeString(repo.resolve("README.md"), "changed on feature\n");
        git("commit", "-q", "-am", "Change README");
        git("checkout", "-q", "main");
        Files.writeString(repo.resolve("README.md"), "local change\n");

        GitError.CheckoutError error = provider.checkout("feature").getLeft().error();

        assertTrue(error instanceof GitError.LocalChanges(String explanation)
                && explanation.contains("would be overwritten"), error.toString());
        assertEquals("main", provider.status().get().branchLabel());
    }

    private static List<Branch> withoutTimes(List<Branch> branches) {
        return branches.map(b -> new Branch(b.name(), b.current(), b.tipOid())).toList();
    }

    @Test
    void branchesCarryTheTimeOfTheirLastCommit() {
        assertEquals(provider.commits().get().get(0).authorTime().getEpochSecond(), provider.branches(List.empty()).get().get(0).tipTime().getEpochSecond());
    }

    @Test
    void createBranchStartsAtTheGivenBranchAndSwitchesToIt() throws Exception {
        git("checkout", "-q", "-b", "other");
        Files.writeString(repo.resolve("other.txt"), "other\n");
        git("add", "other.txt");
        git("commit", "-q", "-m", "On other");
        git("checkout", "-q", "main");

        provider.createBranch("topic", "other").get();

        assertEquals("topic", provider.status().get().branchLabel());
        assertTrue(Files.exists(repo.resolve("other.txt")));
    }

    @Test
    void branchesKnowWhetherTheyExistOnTheirRemote() throws Exception {
        git("branch", "pushed");
        git("branch", "local-only");
        java.nio.file.Path bare = Files.createTempDirectory("remote").resolve("remote.git");
        git("init", "-q", "--bare", bare.toString());
        git("remote", "add", "origin", bare.toString());
        git("push", "-q", "origin", "pushed");

        assertEquals(List.of(true, false), provider.branches(List.empty()).get()
                .filter(b -> !b.name().equals("main"))
                .sorted(java.util.Comparator.comparing(Branch::name).reversed())
                .map(Branch::hasRemote));
    }

    @Test
    void branchesCountTheCommitsNotYetPushedAndNotYetPulled() throws Exception {
        java.nio.file.Path bare = Files.createTempDirectory("remote").resolve("remote.git");
        git("init", "-q", "--bare", "-b", "main", bare.toString());
        git("remote", "add", "origin", bare.toString());
        git("push", "-q", "-u", "origin", "main");
        assertEquals(List.of(0, 0), counts("main"));

        git("commit", "-q", "--allow-empty", "-m", "local one");
        git("commit", "-q", "--allow-empty", "-m", "local two");
        assertEquals(List.of(2, 0), counts("main"));

        // Someone else pushes a commit; fetching makes it incoming.
        java.nio.file.Path other = Files.createTempDirectory("other");
        gitIn(other, "clone", "-q", bare.toString(), "clone");
        gitIn(other.resolve("clone"), "commit", "-q", "--allow-empty", "-m", "theirs");
        gitIn(other.resolve("clone"), "push", "-q", "origin", "main");
        git("fetch", "-q", "origin");
        assertEquals(List.of(2, 1), counts("main"));
    }

    @Test
    void countsOfKnownBranchesAreReusedUntilATipMoves() throws Exception {
        java.nio.file.Path bare = Files.createTempDirectory("remote").resolve("remote.git");
        git("init", "-q", "--bare", "-b", "main", bare.toString());
        git("remote", "add", "origin", bare.toString());
        git("push", "-q", "-u", "origin", "main");
        Branch main = provider.branches(List.empty()).get().head();
        // Counts no revwalk would give: seeing them back shows they were reused.
        Branch known = new Branch(main.name(), main.current(), main.tipOid(), main.tipTime(), main.hasRemote(),
                main.isDefault(), main.upstream().map(u -> new Upstream(u.ref(), u.tip(), 7, 9)));

        assertEquals(List.of(7, 9), provider.branches(List.of(known)).get().map(b -> List.of(b.ahead(), b.behind())).head());

        git("commit", "-q", "--allow-empty", "-m", "local one");
        assertEquals(List.of(1, 0), provider.branches(List.of(known)).get().map(b -> List.of(b.ahead(), b.behind())).head());
    }

    @Test
    void pushPublishesABranchWithoutUpstreamAndThenSendsNewCommits() throws Exception {
        java.nio.file.Path bare = Files.createTempDirectory("remote").resolve("remote.git");
        git("init", "-q", "--bare", "-b", "main", bare.toString());
        git("remote", "add", "origin", bare.toString());

        provider.push("main", PushMode.FAST_FORWARD).get();
        assertEquals(List.of(0, 0), counts("main"));
        assertTrue(provider.branches(List.empty()).get().filter(b -> b.name().equals("main")).forAll(Branch::hasRemote));

        git("commit", "-q", "--allow-empty", "-m", "local");
        assertEquals(List.of(1, 0), counts("main"));
        provider.push("main", PushMode.FAST_FORWARD).get();
        assertEquals(List.of(0, 0), counts("main"));
    }

    @Test
    void aDivergedBranchOnlyPushesWithForceWithLease() throws Exception {
        java.nio.file.Path bare = Files.createTempDirectory("remote").resolve("remote.git");
        git("init", "-q", "--bare", "-b", "main", bare.toString());
        git("remote", "add", "origin", bare.toString());
        git("push", "-q", "-u", "origin", "main");

        // The remote gets a commit that this repository fetches, while the local branch is rewritten.
        java.nio.file.Path other = Files.createTempDirectory("other");
        gitIn(other, "clone", "-q", bare.toString(), "clone");
        gitIn(other.resolve("clone"), "commit", "-q", "--allow-empty", "-m", "theirs");
        gitIn(other.resolve("clone"), "push", "-q", "origin", "main");
        git("fetch", "-q", "origin");
        git("commit", "-q", "--allow-empty", "-m", "ours");
        assertEquals(List.of(1, 1), counts("main"));

        assertTrue(provider.push("main", PushMode.FAST_FORWARD).isLeft());

        provider.push("main", PushMode.FORCE_WITH_LEASE).get();
        git("fetch", "-q", "origin");
        assertEquals(List.of(0, 0), counts("main"));
    }

    @Test
    void pushFailsWithGitsExplanationWithoutARemote() {
        assertTrue(provider.push("main", PushMode.FAST_FORWARD).isLeft());
    }

    @Test
    void branchesWithoutAnUpstreamAreNeitherAheadNorBehind() throws Exception {
        git("branch", "local-only");
        assertEquals(List.of(0, 0), counts("local-only"));
    }

    private List<Integer> counts(String branch) {
        return provider.branches(List.empty()).get().filter(b -> b.name().equals(branch))
                .map(b -> List.of(b.ahead(), b.behind())).head();
    }

    @Test
    void theDefaultBranchIsTheOneOriginHeadPointsToElseMainOrMaster() throws Exception {
        git("branch", "trunk");
        assertEquals(List.of("main"), defaultBranches());

        git("update-ref", "refs/remotes/origin/trunk", "HEAD");
        git("symbolic-ref", "refs/remotes/origin/HEAD", "refs/remotes/origin/trunk");
        assertEquals(List.of("trunk"), defaultBranches());
    }

    private List<String> defaultBranches() {
        return provider.branches(List.empty()).get().filter(Branch::isDefault).map(Branch::name).toList();
    }

    @Test
    void deleteBranchRemovesTheLocalBranch() throws Exception {
        git("branch", "topic");

        provider.deleteBranch("topic", DeleteScope.LOCAL).get();

        assertEquals(List.of("main"), provider.branches(List.empty()).get().map(Branch::name).toList());
    }

    @Test
    void deleteBranchFailsForTheCheckedOutBranch() {
        assertTrue(provider.deleteBranch("main", DeleteScope.LOCAL).isLeft());
    }

    @Test
    void createBranchFailsWhenTheNameIsTaken() throws Exception {
        git("branch", "topic");

        assertTrue(provider.createBranch("topic", "main").isLeft());
    }

    @Test
    void stageAndUnstageMoveFilesInAndOutOfTheIndex() throws Exception {
        Files.writeString(repo.resolve("README.md"), "changed\n");
        Files.writeString(repo.resolve("new.txt"), "new\n");

        provider.stage(List.of("README.md", "new.txt")).get();
        assertEquals(List.of(new FileEntry("README.md", ChangeType.MODIFIED, true, false),
                new FileEntry("new.txt", ChangeType.ADDED, true, false)), provider.status().get().files());

        provider.unstageNew(List.of("new.txt")).get();
        provider.unstage(List.of("README.md")).get();
        assertEquals(List.of(new FileEntry("README.md", ChangeType.MODIFIED, false, true),
                new FileEntry("new.txt", ChangeType.UNTRACKED, false, true)), provider.status().get().files());
    }

    @Test
    void diffCombinesStagedUnstagedAndUntrackedChanges() throws Exception {
        Files.writeString(repo.resolve("README.md"), "staged\n");
        git("add", "README.md");
        Files.writeString(repo.resolve("README.md"), "unstaged\n");
        Files.writeString(repo.resolve("new.txt"), "brand new\n");

        Diffs diffs = provider.diff(provider.status().get().files()).get();

        assertTrue(diffs.staged().contains("-hello"), diffs.toString());
        assertTrue(diffs.staged().contains("+staged"), diffs.toString());
        assertTrue(!diffs.staged().contains("unstaged"), diffs.toString());
        assertTrue(diffs.unstaged().contains("-staged"), diffs.toString());
        assertTrue(diffs.unstaged().contains("+unstaged"), diffs.toString());
        assertTrue(diffs.unstaged().contains("+brand new"), diffs.toString());
        assertEquals(new Diffs("", ""), provider.diff(List.of()).get());
    }

    @Test
    void diffOfOneFileLeavesOutTheOthers() throws Exception {
        Files.writeString(repo.resolve("README.md"), "changed\n");
        Files.writeString(repo.resolve("new.txt"), "new\n");

        Diffs diffs = provider.diff(List.of(new FileEntry("new.txt", ChangeType.UNTRACKED))).get();

        assertEquals("", diffs.staged());
        assertTrue(diffs.unstaged().contains("+new"), diffs.unstaged());
        assertTrue(!diffs.unstaged().contains("README.md"), diffs.unstaged());
    }

    @Test
    void stageFailsWithGitsExplanationForAnUnknownPath() {
        GitError.ProcessError error = provider.stage(List.of("missing.txt")).getLeft().error();

        assertTrue(error instanceof GitError.CommandFailed(String explanation, int ignored)
                && explanation.contains("missing.txt"), error.toString());
    }

    @Test
    void emptyRepositoryHasNoCommits() throws Exception {
        Path empty = repo.resolve("empty");
        Files.createDirectories(empty);
        gitIn(empty, "init", "-q", "-b", "main");
        try (JGitInfoProvider emptyProvider = JGitInfoProvider.open(empty).get()) {
            assertEquals(Option.none(), emptyProvider.status().get().headCommit());
            assertEquals(List.of(), emptyProvider.commits().get());
        }
    }

    private void git(String... args) throws IOException, InterruptedException, TimeoutException {
        gitIn(repo, args);
    }

    private static void gitIn(Path dir, String... args) throws IOException, InterruptedException, TimeoutException {
        java.util.List<String> command = new java.util.ArrayList<>(java.util.List.of("git",
                "-c", "user.name=Test", "-c", "user.email=test@example.com", "-c", "commit.gpgsign=false"));
        command.addAll(java.util.List.of(args));
        ProcessResult result = new ProcessExecutor()
                .command(command)
                .directory(dir.toFile())
                .redirectErrorStream(true)
                .readOutput(true)
                .exitValueAny()
                .execute();
        assertEquals(0, result.getExitValue(),
                () -> "git " + String.join(" ", args) + " failed: " + result.outputUTF8());
    }

    private static boolean gitAvailable() {
        try {
            return new ProcessExecutor("git", "--version").execute().getExitValue() == 0;
        } catch (IOException | TimeoutException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
