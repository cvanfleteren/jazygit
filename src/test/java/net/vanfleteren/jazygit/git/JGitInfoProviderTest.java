package net.vanfleteren.jazygit.git;

import net.vanfleteren.jazygit.git.model.*;
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
import java.util.List;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
        provider = new JGitInfoProvider(repo);
    }

    @AfterEach
    void tearDown() {
        if (provider != null) {
            provider.close();
        }
    }

    @Test
    void statusFollowsTheWorkingTree() throws Exception {
        assertEquals(List.of(), provider.status().files());

        Files.writeString(repo.resolve("new.txt"), "new\n");
        assertEquals(List.of(new FileEntry("new.txt", ChangeType.UNTRACKED)), provider.status().files());

        git("add", "new.txt");
        assertEquals(List.of(new FileEntry("new.txt", ChangeType.ADDED)), provider.status().files());
    }

    @Test
    void commitsAndStatusFollowNewCommits() throws Exception {
        String headBefore = provider.status().headOid();
        Files.writeString(repo.resolve("new.txt"), "new\n");
        git("add", "new.txt");
        git("commit", "-q", "-m", "Second commit");

        RepoStatus status = provider.status();
        assertNotEquals(headBefore, status.headOid());
        assertEquals(List.of(), status.files());
        List<Commit> commits = provider.commits();
        assertEquals("Second commit", commits.get(0).message());
        assertTrue(status.headOid().startsWith(commits.get(0).shortSha()));
    }

    @Test
    void branchesFollowExternalChanges() throws Exception {
        String head = provider.status().headOid();
        assertEquals(List.of(new Branch("main", true, head)), withoutTimes(provider.branches()));

        git("branch", "foo");
        assertEquals(List.of(new Branch("main", true, head), new Branch("foo", false, head)), withoutTimes(provider.branches()));

        git("checkout", "-q", "foo");
        assertEquals(List.of(new Branch("foo", true, head), new Branch("main", false, head)), withoutTimes(provider.branches()));

        git("commit", "-q", "--allow-empty", "-m", "Move foo");
        String moved = provider.status().headOid();
        assertEquals(List.of(new Branch("foo", true, moved), new Branch("main", false, head)), withoutTimes(provider.branches()));
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
                provider.branches().stream().map(Branch::name).toList());

        git("checkout", "-q", "old");
        assertEquals(List.of("old", "newest", "middle", "main"),
                provider.branches().stream().map(Branch::name).toList());
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

        List<Commit> side = provider.log("side");
        assertEquals(List.of("Side subject", "Initial commit"), side.stream().map(Commit::message).toList());
        assertEquals("Side detail\nsecond line", side.get(0).body());
        assertEquals("", side.get(1).body());
        assertEquals("Test", side.get(0).authorName());
        assertEquals("test@example.com", side.get(0).authorEmail());

        assertEquals(List.of("Initial commit"), provider.log("main").stream().map(Commit::message).toList());
        assertThrows(IllegalStateException.class, () -> provider.log("gone"));
    }

    @Test
    void reportsRepositoryNameAndCurrentBranch() throws Exception {
        assertEquals(repo.getFileName().toString(), provider.repositoryName());
        assertEquals("main", provider.status().branchLabel());

        git("checkout", "-q", "-b", "feature");
        assertEquals("feature", provider.status().branchLabel());

        git("checkout", "-q", "--detach");
        assertEquals("HEAD detached at " + provider.commits().get(0).shortSha(), provider.status().branchLabel());
    }

    @Test
    void checkoutSwitchesBranchAndCarriesOverLocalChanges() throws Exception {
        git("branch", "feature");
        Files.writeString(repo.resolve("new.txt"), "new\n");

        provider.checkout("feature");

        assertEquals("feature", provider.status().branchLabel());
        assertEquals(List.of(new FileEntry("new.txt", ChangeType.UNTRACKED)), provider.status().files());
    }

    @Test
    void checkoutFailsWithGitsExplanationWhenLocalChangesWouldBeOverwritten() throws Exception {
        git("checkout", "-q", "-b", "feature");
        Files.writeString(repo.resolve("README.md"), "changed on feature\n");
        git("commit", "-q", "-am", "Change README");
        git("checkout", "-q", "main");
        Files.writeString(repo.resolve("README.md"), "local change\n");

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> provider.checkout("feature"));

        assertTrue(e.getMessage().contains("would be overwritten"), e.getMessage());
        assertEquals("main", provider.status().branchLabel());
    }

    private static List<Branch> withoutTimes(List<Branch> branches) {
        return branches.stream().map(b -> new Branch(b.name(), b.current(), b.tipOid())).toList();
    }

    @Test
    void branchesCarryTheTimeOfTheirLastCommit() {
        assertEquals(provider.commits().get(0).authorTime().getEpochSecond(), provider.branches().get(0).tipTime().getEpochSecond());
    }

    @Test
    void createBranchStartsAtTheGivenBranchAndSwitchesToIt() throws Exception {
        git("checkout", "-q", "-b", "other");
        Files.writeString(repo.resolve("other.txt"), "other\n");
        git("add", "other.txt");
        git("commit", "-q", "-m", "On other");
        git("checkout", "-q", "main");

        provider.createBranch("topic", "other");

        assertEquals("topic", provider.status().branchLabel());
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

        assertEquals(List.of(true, false), provider.branches().stream()
                .filter(b -> !b.name().equals("main"))
                .sorted(java.util.Comparator.comparing(Branch::name).reversed())
                .map(Branch::hasRemote).toList());
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
    void pushPublishesABranchWithoutUpstreamAndThenSendsNewCommits() throws Exception {
        java.nio.file.Path bare = Files.createTempDirectory("remote").resolve("remote.git");
        git("init", "-q", "--bare", "-b", "main", bare.toString());
        git("remote", "add", "origin", bare.toString());

        provider.push("main");
        assertEquals(List.of(0, 0), counts("main"));
        assertTrue(provider.branches().stream().filter(b -> b.name().equals("main")).allMatch(Branch::hasRemote));

        git("commit", "-q", "--allow-empty", "-m", "local");
        assertEquals(List.of(1, 0), counts("main"));
        provider.push("main");
        assertEquals(List.of(0, 0), counts("main"));
    }

    @Test
    void pushFailsWithGitsExplanationWithoutARemote() {
        assertThrows(IllegalStateException.class, () -> provider.push("main"));
    }

    @Test
    void branchesWithoutAnUpstreamAreNeitherAheadNorBehind() throws Exception {
        git("branch", "local-only");
        assertEquals(List.of(0, 0), counts("local-only"));
    }

    private List<Integer> counts(String branch) {
        return provider.branches().stream().filter(b -> b.name().equals(branch))
                .map(b -> List.of(b.ahead(), b.behind())).findFirst().orElseThrow();
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
        return provider.branches().stream().filter(Branch::isDefault).map(Branch::name).toList();
    }

    @Test
    void deleteBranchRemovesTheLocalBranch() throws Exception {
        git("branch", "topic");

        provider.deleteBranch("topic", true, false);

        assertEquals(List.of("main"), provider.branches().stream().map(Branch::name).toList());
    }

    @Test
    void deleteBranchFailsForTheCheckedOutBranch() {
        assertThrows(IllegalStateException.class, () -> provider.deleteBranch("main", true, false));
    }

    @Test
    void createBranchFailsWhenTheNameIsTaken() throws Exception {
        git("branch", "topic");

        assertThrows(IllegalStateException.class, () -> provider.createBranch("topic", "main"));
    }

    @Test
    void stageAndUnstageMoveFilesInAndOutOfTheIndex() throws Exception {
        Files.writeString(repo.resolve("README.md"), "changed\n");
        Files.writeString(repo.resolve("new.txt"), "new\n");

        provider.stage(List.of("README.md", "new.txt"));
        assertEquals(List.of(new FileEntry("README.md", ChangeType.MODIFIED, true, false),
                new FileEntry("new.txt", ChangeType.ADDED, true, false)), provider.status().files());

        provider.unstageNew(List.of("new.txt"));
        provider.unstage(List.of("README.md"));
        assertEquals(List.of(new FileEntry("README.md", ChangeType.MODIFIED, false, true),
                new FileEntry("new.txt", ChangeType.UNTRACKED, false, true)), provider.status().files());
    }

    @Test
    void diffCombinesStagedUnstagedAndUntrackedChanges() throws Exception {
        Files.writeString(repo.resolve("README.md"), "staged\n");
        git("add", "README.md");
        Files.writeString(repo.resolve("README.md"), "unstaged\n");
        Files.writeString(repo.resolve("new.txt"), "brand new\n");

        Diffs diffs = provider.diff(provider.status().files());

        assertTrue(diffs.staged().contains("-hello"), diffs.toString());
        assertTrue(diffs.staged().contains("+staged"), diffs.toString());
        assertTrue(!diffs.staged().contains("unstaged"), diffs.toString());
        assertTrue(diffs.unstaged().contains("-staged"), diffs.toString());
        assertTrue(diffs.unstaged().contains("+unstaged"), diffs.toString());
        assertTrue(diffs.unstaged().contains("+brand new"), diffs.toString());
        assertEquals(new Diffs("", ""), provider.diff(List.of()));
    }

    @Test
    void diffOfOneFileLeavesOutTheOthers() throws Exception {
        Files.writeString(repo.resolve("README.md"), "changed\n");
        Files.writeString(repo.resolve("new.txt"), "new\n");

        Diffs diffs = provider.diff(List.of(new FileEntry("new.txt", ChangeType.UNTRACKED)));

        assertEquals("", diffs.staged());
        assertTrue(diffs.unstaged().contains("+new"), diffs.unstaged());
        assertTrue(!diffs.unstaged().contains("README.md"), diffs.unstaged());
    }

    @Test
    void stageFailsWithGitsExplanationForAnUnknownPath() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> provider.stage(List.of("missing.txt")));

        assertTrue(e.getMessage().contains("missing.txt"), e.getMessage());
    }

    @Test
    void emptyRepositoryHasNoCommits() throws Exception {
        Path empty = repo.resolve("empty");
        Files.createDirectories(empty);
        gitIn(empty, "init", "-q", "-b", "main");
        try (JGitInfoProvider emptyProvider = new JGitInfoProvider(empty)) {
            assertEquals(RepoStatus.INITIAL, emptyProvider.status().headOid());
            assertEquals(List.of(), emptyProvider.commits());
        }
    }

    private void git(String... args) throws IOException, InterruptedException, TimeoutException {
        gitIn(repo, args);
    }

    private static void gitIn(Path dir, String... args) throws IOException, InterruptedException, TimeoutException {
        List<String> command = new ArrayList<>(List.of("git",
                "-c", "user.name=Test", "-c", "user.email=test@example.com", "-c", "commit.gpgsign=false"));
        command.addAll(List.of(args));
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
