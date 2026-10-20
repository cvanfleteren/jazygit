package net.vanfleteren.jazygit.git;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeoutException;
import net.vanfleteren.jazygit.git.model.*;
import net.vanfleteren.jazygit.git.model.RepoStatus.Head;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;

/**
 * {@link CliGitInfoProvider} against a real repository changed through the git CLI: every call
 * reflects the current state of the repository.
 */
class CliGitInfoProviderTest {

    @TempDir
    Path repo;

    private CliGitInfoProvider provider;

    private static List<Branch> withoutTimes(List<Branch> branches) {
        return branches.map(b -> new Branch(b.name(), b.current(), b.tipOid())).toList();
    }

    /**
     * Runs git in {@code dir}, failing the test when it fails; what it printed.
     */
    private static String gitIn(Path dir, String... args) throws IOException, InterruptedException, TimeoutException {
        java.util.List<String> command = new java.util.ArrayList<>(java.util.List.of(
                "git", "-c", "user.name=Test", "-c", "user.email=test@example.com", "-c", "commit.gpgsign=false"));
        command.addAll(java.util.List.of(args));
        ProcessResult result = new ProcessExecutor()
                .command(command)
                .directory(dir.toFile())
                .redirectErrorStream(true)
                .readOutput(true)
                .exitValueAny()
                .execute();
        assertEquals(
                0, result.getExitValue(), () -> "git " + String.join(" ", args) + " failed: " + result.outputUTF8());
        return result.outputUTF8();
    }

    private static boolean gitSucceedsIn(Path dir, String... args)
            throws IOException, InterruptedException, TimeoutException {
        java.util.List<String> command = new java.util.ArrayList<>(java.util.List.of("git"));
        command.addAll(java.util.List.of(args));
        return new ProcessExecutor()
                        .command(command)
                        .directory(dir.toFile())
                        .readOutput(true)
                        .execute()
                        .getExitValue()
                == 0;
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

    @BeforeEach
    void setUp() throws Exception {
        assumeTrue(gitAvailable(), "git is not installed");
        git("init", "-q", "-b", "main");
        git("config", "user.name", "Test");
        git("config", "user.email", "test@example.com");
        git("config", "commit.gpgsign", "false");
        Files.writeString(repo.resolve("README.md"), "hello\n");
        git("add", "README.md");
        git("commit", "-q", "-m", "Initial commit");
        provider = CliGitInfoProvider.open(repo).get();
    }

    @Test
    void openingFailsOutsideARepositoryAndForABareOne(@TempDir Path elsewhere) throws Exception {
        Path plain = Files.createDirectories(elsewhere.resolve("plain"));
        Path bare = elsewhere.resolve("bare.git");
        gitIn(elsewhere, "init", "-q", "--bare", bare.toString());

        assertInstanceOf(
                GitError.NoWorkTree.class, CliGitInfoProvider.open(bare).getLeft());
        assumeTrue(!gitSucceedsIn(plain, "rev-parse"), "the temporary directory is inside a repository");
        assertEquals(
                new GitError.NotARepository(plain),
                CliGitInfoProvider.open(plain).getLeft());
    }

    @Test
    void statusFollowsTheWorkingTree() throws Exception {
        assertEquals(List.of(), provider.status().get().files());

        Files.writeString(repo.resolve("new.txt"), "new\n");
        assertEquals(
                List.of(new FileEntry("new.txt", ChangeType.UNTRACKED)),
                provider.status().get().files());

        git("add", "new.txt");
        assertEquals(
                List.of(new FileEntry("new.txt", ChangeType.ADDED)),
                provider.status().get().files());
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
    void searchFindsTheCommitsOfHeadThatMatchEveryFilter() throws Exception {
        Files.writeString(repo.resolve("a.txt"), "token\n");
        git("add", "a.txt");
        git("commit", "-q", "-m", "Fix login", "-m", "with a token");
        Files.writeString(repo.resolve("b.txt"), "other\n");
        git("add", "b.txt");
        git("-c", "user.name=Jane Doe", "commit", "-q", "-m", "fix logout");
        Files.writeString(repo.resolve("a.txt"), "no more\n");
        git("commit", "-q", "-am", "Remove it");

        assertEquals(List.of("fix logout", "Fix login"), messages("fix"));
        assertEquals(List.of("Fix login"), messages("Fix"));
        assertEquals(List.of("Fix login"), messages("a token"));
        assertEquals(List.of("fix logout"), messages("author:jane"));
        assertEquals(List.of("fix logout"), messages("fix author:jane"));
        assertEquals(List.of("fix logout", "Fix login"), messages("fix author:jane author:test"));
        assertEquals(List.of("Remove it", "Fix login"), messages("path:a.txt"));
        assertEquals(List.of("Remove it", "fix logout", "Fix login"), messages("path:a.txt path:b.txt"));
        assertEquals(List.of("Remove it", "Fix login"), messages("-S:token"));
        assertEquals(List.of("Fix login"), messages("fix -S:token"));
        // Not as a regular expression.
        assertEquals(List.of(), messages("f.x"));
        assertEquals(List.of(), messages("nothing like it"));
    }

    @Test
    void searchFindsNothingWithoutCommits(@TempDir Path empty) throws Exception {
        gitIn(empty, "init", "-q");

        assertEquals(
                List.of(),
                CliGitInfoProvider.open(empty)
                        .get()
                        .search(CommitQuery.parse("x"))
                        .get());
    }

    private List<String> messages(String query) {
        return provider.search(CommitQuery.parse(query))
                .get()
                .map(Commit::message)
                .toList();
    }

    @Test
    void branchesFollowExternalChanges() throws Exception {
        Sha head = provider.status().get().headCommit().get();
        assertEquals(
                List.of(new Branch(new Branch.Name("main"), true, head)),
                withoutTimes(provider.branches().get()));

        git("branch", "foo");
        assertEquals(
                List.of(
                        new Branch(new Branch.Name("main"), true, head),
                        new Branch(new Branch.Name("foo"), false, head)),
                withoutTimes(provider.branches().get()));

        git("checkout", "-q", "foo");
        assertEquals(
                List.of(
                        new Branch(new Branch.Name("foo"), true, head),
                        new Branch(new Branch.Name("main"), false, head)),
                withoutTimes(provider.branches().get()));

        git("commit", "-q", "--allow-empty", "-m", "Move foo");
        Sha moved = provider.status().get().headCommit().get();
        assertEquals(
                List.of(
                        new Branch(new Branch.Name("foo"), true, moved),
                        new Branch(new Branch.Name("main"), false, head)),
                withoutTimes(provider.branches().get()));
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

        assertEquals(
                List.of("main", "newest", "middle", "old"),
                provider.branches().get().map(b -> b.name().value()).toList());

        git("checkout", "-q", "old");
        assertEquals(
                List.of("old", "newest", "middle", "main"),
                provider.branches().get().map(b -> b.name().value()).toList());
    }

    private void commitAt(String branch, String date) throws Exception {
        git("checkout", "-q", branch);
        ProcessResult result = new ProcessExecutor()
                .command(
                        "git",
                        "-c",
                        "user.name=Test",
                        "-c",
                        "user.email=test@example.com",
                        "-c",
                        "commit.gpgsign=false",
                        "commit",
                        "-q",
                        "--allow-empty",
                        "-m",
                        "at " + date)
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

        List<Commit> side =
                provider.log(new BranchRef.Local(new Branch.Name("side"))).get();
        assertEquals(
                List.of("Side subject", "Initial commit"),
                side.map(Commit::message).toList());
        assertEquals("Side detail\nsecond line", side.get(0).body());
        assertEquals("", side.get(1).body());
        assertEquals("Test", side.get(0).authorName());
        assertEquals("test@example.com", side.get(0).authorEmail());

        assertEquals(
                List.of("Initial commit"),
                provider.log(new BranchRef.Local(new Branch.Name("main")))
                        .get()
                        .map(Commit::message)
                        .toList());
        assertEquals(
                new GitError.UnknownBranch(new BranchRef.Local(new Branch.Name("gone"))),
                provider.log(new BranchRef.Local(new Branch.Name("gone"))).getLeft());
    }

    @Test
    void divergenceListsTheCommitsOnlyOnEitherSide() throws Exception {
        git("checkout", "-q", "-b", "side");
        git("commit", "-q", "--allow-empty", "-m", "Side one");
        git("commit", "-q", "--allow-empty", "-m", "Side two");
        git("checkout", "-q", "main");
        git("commit", "-q", "--allow-empty", "-m", "Main one");
        BranchRef side = new BranchRef.Local(new Branch.Name("side"));
        BranchRef main = new BranchRef.Local(new Branch.Name("main"));

        Divergence divergence = provider.divergence(side, main).get();

        assertEquals(List.of("Side two", "Side one"), divergence.ahead().map(Commit::message));
        assertEquals(2, divergence.aheadCount());
        assertEquals(List.of("Main one"), divergence.behind().map(Commit::message));
        assertEquals(1, divergence.behindCount());
        BranchRef gone = new BranchRef.Local(new Branch.Name("gone"));
        assertEquals(
                new GitError.UnknownBranch(gone),
                provider.divergence(side, gone).getLeft());
    }

    @Test
    void theFirstSubjectIsThatOfTheOldestCommitNotOnTheBase() throws Exception {
        git("checkout", "-q", "-b", "side");
        git("commit", "-q", "--allow-empty", "-m", "Side one", "-m", "Its body");
        git("commit", "-q", "--allow-empty", "-m", "Side two");
        BranchRef side = new BranchRef.Local(new Branch.Name("side"));
        BranchRef main = new BranchRef.Local(new Branch.Name("main"));

        assertEquals(Option.of("Side one"), provider.firstSubject(side, main).get());
        assertEquals(Option.none(), provider.firstSubject(main, side).get());
    }

    @Test
    void countsTheCommitsNotOnTheBaseUpToTheMaximum() throws Exception {
        git("checkout", "-q", "-b", "side");
        git("commit", "-q", "--allow-empty", "-m", "Side one");
        git("commit", "-q", "--allow-empty", "-m", "Side two");
        git("commit", "-q", "--allow-empty", "-m", "Side three");
        BranchRef side = new BranchRef.Local(new Branch.Name("side"));
        BranchRef main = new BranchRef.Local(new Branch.Name("main"));

        assertEquals(3, provider.countNotOn(side, main, 1000).get());
        assertEquals(2, provider.countNotOn(side, main, 2).get());
        assertEquals(0, provider.countNotOn(main, side, 1000).get());
    }

    @Test
    void reportsRepositoryNameAndCurrentBranch() throws Exception {
        assertEquals(repo.getFileName().toString(), provider.repositoryName());
        assertEquals(
                Option.of(new Branch.Name("main")),
                provider.status().get().head().branchName());

        git("checkout", "-q", "-b", "feature");
        assertEquals(
                Option.of(new Branch.Name("feature")),
                provider.status().get().head().branchName());

        git("checkout", "-q", "--detach");
        assertEquals(
                new Head.Detached(provider.commits().get().get(0).sha()),
                provider.status().get().head());
    }

    @Test
    void checkoutSwitchesBranchAndCarriesOverLocalChanges() throws Exception {
        git("branch", "feature");
        Files.writeString(repo.resolve("new.txt"), "new\n");

        provider.checkout(new Branch.Name("feature")).get();

        assertEquals(
                Option.of(new Branch.Name("feature")),
                provider.status().get().head().branchName());
        assertEquals(
                List.of(new FileEntry("new.txt", ChangeType.UNTRACKED)),
                provider.status().get().files());
    }

    @Test
    void mutatingCommandsReturnTheCommandLinesTheyRan() throws Exception {
        Files.writeString(repo.resolve("README.md"), "changed\n");
        Files.writeString(repo.resolve("new file.txt"), "new\n");

        assertEquals(
                List.of("git add -- README.md 'new file.txt'"),
                provider.stage(List.of("README.md", "new file.txt")).get().map(ExecutedCommand::line));
        assertEquals(
                List.of("git rm --cached --force --quiet -- 'new file.txt'"),
                provider.unstageNew(List.of("new file.txt")).get().map(ExecutedCommand::line));
        assertEquals(List.of(), provider.unstage(List.of()).get());
        assertEquals(
                List.of("git commit --quiet -m 'a summary' -m details"),
                provider.commit(new CommitMessage("a summary", "details")).get().map(ExecutedCommand::line));

        git("branch", "feature");
        Files.writeString(repo.resolve("README.md"), "dirty\n");
        assertEquals(
                List.of("git stash push --quiet", "git checkout --quiet feature --", "git stash pop --index --quiet"),
                provider.checkoutWithStash(new Branch.Name("feature")).get().map(ExecutedCommand::line));
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

        assertEquals(
                List.of("Merge feature", "Feature work", "Main work"),
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

        List<String> executed = provider.reword(before.get(1).sha(), new CommitMessage("Second, better", "because"))
                .get()
                .map(ExecutedCommand::line);

        assertEquals(
                List.of("git -c rebase.abbreviateCommands=false -c core.abbrev=no -c commit.cleanup=whitespace"
                        + " rebase --interactive --autostash --keep-empty --rebase-merges "
                        + before.get(1).sha().value() + "^"),
                executed);
        List<Commit> after = provider.commits().get();
        assertEquals(
                List.of("Third", "Second, better", "Initial commit"),
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

        List<String> executed = provider.rebase(new Branch.Name("main")).get().map(ExecutedCommand::line);

        assertEquals(List.of("git rebase --autostash --keep-empty --rebase-merges --no-autosquash main"), executed);
        assertEquals(
                List.of("Feature work", "Main work", "Initial commit"),
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

        provider.rebase(new Branch.Name("main")).get();

        List<Commit> after = provider.commits().get();
        assertEquals(List.of("fixup! Feature work", "Merge side"), after.take(2).map(Commit::message));
        assertTrue(after.get(1).isMerge());
        assertTrue(after.exists(c -> c.message().equals("Main work")));
    }

    @Test
    void aConflictingRebaseIsLeftForTheUserToResolve() throws Exception {
        git("checkout", "-q", "-b", "feature");
        Files.writeString(repo.resolve("README.md"), "feature\n");
        git("commit", "-q", "-am", "Feature edit");
        git("checkout", "-q", "main");
        Files.writeString(repo.resolve("README.md"), "main\n");
        git("commit", "-q", "-am", "Main edit");
        git("checkout", "-q", "feature");

        GitFailure<GitError> failure = provider.rebase(new Branch.Name("main")).getLeft();

        GitError.StoppedOnConflicts stopped = assertInstanceOf(GitError.StoppedOnConflicts.class, failure.error());
        assertInstanceOf(InProgress.Rebasing.class, stopped.inProgress().get());
        assertEquals(1, stopped.conflicts());
        assertTrue(failure.commands().forAll(command -> !command.line().contains("--abort")));
        assertTrue(Files.exists(repo.resolve(".git/rebase-merge")));
        assertTrue(Files.readString(repo.resolve("README.md")).contains("<<<<<<<"));
    }

    @Test
    void cherryPickCopiesACommitOntoTheCheckedOutBranch() throws Exception {
        git("checkout", "-q", "-b", "feature");
        Files.writeString(repo.resolve("f.txt"), "f\n");
        git("add", "f.txt");
        git("commit", "-q", "-m", "Feature work");
        Sha picked = provider.commits().get().head().sha();
        git("checkout", "-q", "main");

        List<String> executed = provider.cherryPick(picked).get().map(ExecutedCommand::line);

        assertEquals(List.of("git cherry-pick " + picked.value()), executed);
        assertEquals(
                List.of("Feature work", "Initial commit"),
                provider.commits().get().map(Commit::message).toList());
        assertEquals("f\n", Files.readString(repo.resolve("f.txt")));
    }

    @Test
    void aConflictingCherryPickIsLeftForTheUserToResolve() throws Exception {
        git("checkout", "-q", "-b", "feature");
        Files.writeString(repo.resolve("README.md"), "feature\n");
        git("commit", "-q", "-am", "Feature edit");
        Sha picked = provider.commits().get().head().sha();
        git("checkout", "-q", "main");
        Files.writeString(repo.resolve("README.md"), "main\n");
        git("commit", "-q", "-am", "Main edit");

        GitFailure<?> failure = provider.cherryPick(picked).getLeft();

        assertEquals(
                new GitError.StoppedOnConflicts(Option.of(new InProgress.CherryPicking(picked)), 1), failure.error());
        assertEquals(
                List.of("git cherry-pick " + picked.value()), failure.commands().map(ExecutedCommand::line));
        assertTrue(gitSucceedsIn(repo, "rev-parse", "--verify", "--quiet", "CHERRY_PICK_HEAD"));
    }

    @Test
    void aCherryPickThatChangesNothingIsAborted() throws Exception {
        git("checkout", "-q", "-b", "feature");
        Files.writeString(repo.resolve("f.txt"), "f\n");
        git("add", "f.txt");
        git("commit", "-q", "-m", "Feature work");
        Sha picked = provider.commits().get().head().sha();
        git("checkout", "-q", "main");
        Files.writeString(repo.resolve("f.txt"), "f\n");
        git("add", "f.txt");
        git("commit", "-q", "-m", "Same work");

        assertInstanceOf(
                GitError.CommandFailed.class,
                provider.cherryPick(picked).getLeft().error());

        assertEquals(
                List.of("Same work", "Initial commit"),
                provider.commits().get().map(Commit::message).toList());
        assertTrue(!gitSucceedsIn(repo, "rev-parse", "--verify", "--quiet", "CHERRY_PICK_HEAD"));
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

        List<String> executed =
                provider.squash(before.get(1).sha(), SquashMode.SQUASH).get().map(ExecutedCommand::line);

        assertTrue(
                executed.head()
                        .startsWith("git -c rebase.abbreviateCommands=false -c core.abbrev=no"
                                + " rebase --interactive --autostash --keep-empty --rebase-merges"),
                executed.toString());
        List<Commit> after = provider.commits().get();
        assertEquals(
                List.of("Fourth", "Second", "Initial commit"),
                after.map(Commit::message).toList());
        assertTrue(after.get(1).body().contains("Third"), after.get(1).body());
        assertTrue(Files.exists(repo.resolve("b.txt")));
        assertEquals("dirty\n", Files.readString(repo.resolve("README.md")));
    }

    @Test
    void squashOfTheFirstCommitsChildRebasesFromTheRoot() throws Exception {
        Files.writeString(repo.resolve("a.txt"), "a\n");
        git("add", "a.txt");
        git("commit", "-q", "-m", "Second");

        provider.squash(provider.commits().get().head().sha(), SquashMode.SQUASH)
                .get();

        assertEquals(
                List.of("Initial commit"),
                provider.commits().get().map(Commit::message).toList());
    }

    @Test
    void fixupFoldsACommitIntoItsParentAndKeepsOnlyTheParentsMessage() throws Exception {
        Files.writeString(repo.resolve("a.txt"), "a\n");
        git("add", "a.txt");
        git("commit", "-q", "-m", "Second");
        Files.writeString(repo.resolve("b.txt"), "b\n");
        git("add", "b.txt");
        git("commit", "-q", "-m", "Third", "-m", "Third body");
        Files.writeString(repo.resolve("c.txt"), "c\n");
        git("add", "c.txt");
        git("commit", "-q", "-m", "Fourth");
        Files.writeString(repo.resolve("README.md"), "dirty\n");
        List<Commit> before = provider.commits().get();

        provider.squash(before.get(1).sha(), SquashMode.FIXUP).get();

        List<Commit> after = provider.commits().get();
        assertEquals(
                List.of("Fourth", "Second", "Initial commit"),
                after.map(Commit::message).toList());
        assertEquals("", after.get(1).body().strip());
        assertTrue(Files.exists(repo.resolve("b.txt")));
        assertEquals("dirty\n", Files.readString(repo.resolve("README.md")));
    }

    @Test
    void fixupKeepingTheMessageTakesTheCommitsMessageAndTheParentsAuthor() throws Exception {
        Files.writeString(repo.resolve("a.txt"), "a\n");
        git("add", "a.txt");
        git("commit", "-q", "-m", "Second");
        Files.writeString(repo.resolve("b.txt"), "b\n");
        git("add", "b.txt");
        git("commit", "-q", "--author", "Other <other@example.com>", "-m", "Third", "-m", "Third body");
        List<Commit> before = provider.commits().get();

        provider.squash(before.head().sha(), SquashMode.FIXUP_KEEP_MESSAGE).get();

        List<Commit> after = provider.commits().get();
        assertEquals(
                List.of("Third", "Initial commit"), after.map(Commit::message).toList());
        assertEquals("Third body", after.head().body().strip());
        assertEquals("Test", after.head().authorName());
        assertTrue(Files.exists(repo.resolve("a.txt")));
        assertTrue(Files.exists(repo.resolve("b.txt")));
    }

    @Test
    void eachResetMovesTheBranchAndKeepsOrDiscardsTheChangesAsItsModeSays() throws Exception {
        Files.writeString(repo.resolve("a.txt"), "a\n");
        git("add", "a.txt");
        git("commit", "-q", "-m", "Second");
        Sha second = provider.commits().get().head().sha();
        Sha initial = provider.commits().get().get(1).sha();

        List<String> executed = provider.reset(new ResetTarget.ToCommit(initial), ResetMode.SOFT)
                .get()
                .map(ExecutedCommand::line);
        assertEquals(List.of("git reset --quiet --soft " + initial.value()), executed);
        assertEquals(
                List.of("Initial commit"),
                provider.commits().get().map(Commit::message).toList());
        assertTrue(provider.status().get().files().exists(file -> file.path().equals("a.txt") && file.staged()));

        git("reset", "-q", "--hard", second.value());
        provider.reset(new ResetTarget.ToCommit(initial), ResetMode.MIXED).get();
        assertTrue(provider.status().get().files().exists(file -> file.path().equals("a.txt") && !file.staged()));

        git("reset", "-q", "--hard", second.value());
        Files.writeString(repo.resolve("README.md"), "dirty\n");
        Files.writeString(repo.resolve("untracked.txt"), "kept\n");
        provider.reset(new ResetTarget.ToCommit(initial), ResetMode.HARD).get();
        assertEquals(
                List.of("Initial commit"),
                provider.commits().get().map(Commit::message).toList());
        assertTrue(Files.notExists(repo.resolve("a.txt")));
        assertEquals("hello\n", Files.readString(repo.resolve("README.md")));
        assertEquals("kept\n", Files.readString(repo.resolve("untracked.txt")));
    }

    @Test
    void aResetOntoTheUpstreamMovesTheBranchToWhatWasLastFetched() throws Exception {
        java.nio.file.Path bare = Files.createTempDirectory("remote").resolve("remote.git");
        git("init", "-q", "--bare", "-b", "main", bare.toString());
        git("remote", "add", "origin", bare.toString());
        git("push", "-q", "-u", "origin", "main");
        Sha pushed = provider.commits().get().head().sha();
        git("commit", "-q", "--allow-empty", "-m", "local");
        RemoteBranch upstream = new RemoteBranch("origin", new Branch.Name("main"));

        List<String> executed = provider.reset(new ResetTarget.ToUpstream(upstream), ResetMode.HARD)
                .get()
                .map(ExecutedCommand::line);

        assertEquals(List.of("git reset --quiet --hard refs/remotes/origin/main"), executed);
        assertEquals(pushed, provider.commits().get().head().sha());
    }

    @Test
    void rewordOfTheFirstCommitRebasesFromTheRoot() throws Exception {
        Files.writeString(repo.resolve("a.txt"), "a\n");
        git("add", "a.txt");
        git("commit", "-q", "-m", "Second");

        provider.reword(provider.commits().get().get(1).sha(), new CommitMessage("Start", ""))
                .get();

        assertEquals(
                List.of("Second", "Start"),
                provider.commits().get().map(Commit::message).toList());
    }

    @Test
    void rewordOfALastCommitAmendsIt() throws Exception {
        List<String> executed = provider.reword(provider.commits().get().head().sha(), new CommitMessage("Renamed", ""))
                .get()
                .map(ExecutedCommand::line);

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

        provider.reword(merge.sha(), new CommitMessage("Merged the feature", ""))
                .get();

        List<Commit> after = provider.commits().get();
        assertEquals("After merge", after.head().message());
        assertEquals("Merged the feature", after.get(1).message());
        assertTrue(after.get(1).isMerge());
    }

    @Test
    void rewordFindsTheCommitWhateverTheTodoListLooksLike() throws Exception {
        git("config", "rebase.abbreviateCommands", "true");
        git("config", "core.abbrev", "5");
        Files.writeString(repo.resolve("b.txt"), "b\n");
        git("add", "b.txt");
        git("commit", "-q", "-m", "Second");
        Commit initial = provider.commits().get().get(1);

        provider.reword(initial.sha(), new CommitMessage("First", "")).get();

        assertEquals(
                List.of("Second", "First"),
                provider.commits().get().map(Commit::message).toList());
    }

    @Test
    void rewordKeepsLinesThatStartWithAHash() throws Exception {
        Files.writeString(repo.resolve("b.txt"), "b\n");
        git("add", "b.txt");
        git("commit", "-q", "-m", "Second");
        Commit initial = provider.commits().get().get(1);

        provider.reword(initial.sha(), new CommitMessage("#12 First", "#13 too"))
                .get();

        Commit reworded = provider.commits().get().get(1);
        assertEquals("#12 First", reworded.message());
        assertEquals("#13 too", reworded.body());
    }

    @Test
    void dropRemovesACommitAndKeepsTheOnesAfterIt() throws Exception {
        Files.writeString(repo.resolve("a.txt"), "a\n");
        git("add", "a.txt");
        git("commit", "-q", "-m", "Second");
        Files.writeString(repo.resolve("b.txt"), "b\n");
        git("add", "b.txt");
        git("commit", "-q", "-m", "Third");
        Files.writeString(repo.resolve("README.md"), "dirty\n");
        List<Commit> before = provider.commits().get();

        List<String> executed = provider.dropCommit(before.get(1).sha()).get().map(ExecutedCommand::line);

        assertTrue(
                executed.head()
                        .startsWith("git -c rebase.abbreviateCommands=false -c core.abbrev=no"
                                + " rebase --interactive --autostash --keep-empty --rebase-merges"),
                executed.toString());
        assertEquals(
                List.of("Third", "Initial commit"),
                provider.commits().get().map(Commit::message).toList());
        assertTrue(Files.notExists(repo.resolve("a.txt")));
        assertTrue(Files.exists(repo.resolve("b.txt")));
        assertEquals("dirty\n", Files.readString(repo.resolve("README.md")));
    }

    @Test
    void dropOfTheFirstCommitRebasesFromTheRoot() throws Exception {
        Files.writeString(repo.resolve("a.txt"), "a\n");
        git("add", "a.txt");
        git("commit", "-q", "-m", "Second");
        Sha root = provider.commits().get().last().sha();

        provider.dropCommit(root).get();

        assertEquals(
                List.of("Second"), provider.commits().get().map(Commit::message).toList());
        assertTrue(Files.notExists(repo.resolve("README.md")));
    }

    @Test
    void aConflictingDropIsLeftForTheUserToResolve() throws Exception {
        Files.writeString(repo.resolve("README.md"), "second\n");
        git("commit", "-q", "-am", "Second");
        Files.writeString(repo.resolve("README.md"), "third\n");
        git("commit", "-q", "-am", "Third");
        Sha second = provider.commits().get().get(1).sha();

        assertInstanceOf(
                GitError.StoppedOnConflicts.class,
                provider.dropCommit(second).getLeft().error());

        assertTrue(Files.exists(repo.resolve(".git/rebase-merge")));
        assertTrue(Files.readString(repo.resolve("README.md")).contains("<<<<<<<"));
    }

    @Test
    void amendCommitAddsTheStagedChangesToAnEarlierCommitAndLeavesTheUnstagedOnes() throws Exception {
        Files.writeString(repo.resolve("a.txt"), "a\n");
        git("add", "a.txt");
        git("commit", "-q", "-m", "Second");
        Files.writeString(repo.resolve("b.txt"), "b\n");
        git("add", "b.txt");
        git("commit", "-q", "-m", "Third");
        Files.writeString(repo.resolve("a.txt"), "amended\n");
        git("add", "a.txt");
        Files.writeString(repo.resolve("README.md"), "dirty\n");
        Sha second = provider.commits().get().get(1).sha();

        provider.amendCommit(second).get();

        assertEquals(
                List.of("Third", "Second", "Initial commit"),
                provider.commits().get().map(Commit::message).toList());
        assertEquals("amended\n", git("show", "HEAD~1:a.txt"));
        assertEquals("", git("diff", "--cached", "--name-only"));
        assertEquals("dirty\n", Files.readString(repo.resolve("README.md")));
    }

    @Test
    void amendCommitOfTheLastCommitAmendsIt() throws Exception {
        Files.writeString(repo.resolve("a.txt"), "a\n");
        git("add", "a.txt");
        git("commit", "-q", "-m", "Second");
        Files.writeString(repo.resolve("a.txt"), "amended\n");
        git("add", "a.txt");

        List<String> executed = provider.amendCommit(
                        provider.commits().get().head().sha())
                .get()
                .map(ExecutedCommand::line);

        assertEquals(List.of("git commit --quiet --amend --no-edit"), executed);
        assertEquals(
                List.of("Second", "Initial commit"),
                provider.commits().get().map(Commit::message).toList());
        assertEquals("amended\n", git("show", "HEAD:a.txt"));
    }

    @Test
    void amendCommitOfTheFirstCommitRebasesFromTheRoot() throws Exception {
        Files.writeString(repo.resolve("a.txt"), "a\n");
        git("add", "a.txt");
        git("commit", "-q", "-m", "Second");
        Files.writeString(repo.resolve("root.txt"), "root\n");
        git("add", "root.txt");
        Sha root = provider.commits().get().last().sha();

        provider.amendCommit(root).get();

        assertEquals(
                List.of("Second", "Initial commit"),
                provider.commits().get().map(Commit::message).toList());
        assertEquals("root\n", git("show", "HEAD~1:root.txt"));
    }

    @Test
    void amendCommitLeavesOtherFixupCommitsAlone() throws Exception {
        Files.writeString(repo.resolve("a.txt"), "a\n");
        git("add", "a.txt");
        git("commit", "-q", "-m", "Second");
        Sha second = provider.commits().get().head().sha();
        Files.writeString(repo.resolve("b.txt"), "b\n");
        git("add", "b.txt");
        git("commit", "-q", "--fixup=" + second.value());
        Files.writeString(repo.resolve("c.txt"), "c\n");
        git("add", "c.txt");

        provider.amendCommit(second).get();

        assertEquals(
                List.of("fixup! Second", "Second", "Initial commit"),
                provider.commits().get().map(Commit::message).toList());
        assertEquals("c\n", git("show", "HEAD~1:c.txt"));
    }

    @Test
    void aConflictingAmendCommitIsLeftForTheUserToResolve() throws Exception {
        Files.writeString(repo.resolve("README.md"), "second\n");
        git("commit", "-q", "-am", "Second");
        Files.writeString(repo.resolve("README.md"), "third\n");
        git("commit", "-q", "-am", "Third");
        Sha initial = provider.commits().get().last().sha();
        Files.writeString(repo.resolve("README.md"), "staged\n");
        git("add", "README.md");

        assertInstanceOf(
                GitError.StoppedOnConflicts.class,
                provider.amendCommit(initial).getLeft().error());

        assertTrue(Files.exists(repo.resolve(".git/rebase-merge")));
    }

    @Test
    void amendCommitOfACommitThatIsNotInTheLogFailsAndKeepsTheChangesStaged() throws Exception {
        Files.writeString(repo.resolve("b.txt"), "b\n");
        git("add", "b.txt");
        git("commit", "-q", "-m", "Second");
        git("checkout", "-q", "-b", "elsewhere");
        Files.writeString(repo.resolve("c.txt"), "c\n");
        git("add", "c.txt");
        git("commit", "-q", "-m", "Elsewhere");
        Sha elsewhere = provider.commits().get().head().sha();
        git("checkout", "-q", "main");
        Files.writeString(repo.resolve("b.txt"), "staged\n");
        git("add", "b.txt");

        assertTrue(provider.amendCommit(elsewhere).isLeft());

        assertEquals(
                List.of("Second", "Initial commit"),
                provider.commits().get().map(Commit::message).toList());
        assertEquals("b.txt\n", git("diff", "--cached", "--name-only"));
        assertTrue(Files.notExists(repo.resolve(".git/rebase-merge")));
    }

    @Test
    void squashOfACommitThatIsNotInTheLogFailsInsteadOfDoingNothing() throws Exception {
        Files.writeString(repo.resolve("b.txt"), "b\n");
        git("add", "b.txt");
        git("commit", "-q", "-m", "Second");
        git("checkout", "-q", "-b", "elsewhere");
        Files.writeString(repo.resolve("c.txt"), "c\n");
        git("add", "c.txt");
        git("commit", "-q", "-m", "Elsewhere");
        Sha elsewhere = provider.commits().get().head().sha();
        git("checkout", "-q", "main");

        assertTrue(provider.squash(elsewhere, SquashMode.SQUASH).isLeft());

        assertEquals(
                List.of("Second", "Initial commit"),
                provider.commits().get().map(Commit::message).toList());
        assertTrue(Files.notExists(repo.resolve(".git/rebase-merge")));
    }

    @Test
    void aRebaseTheUserStoppedIsLeftAlone() throws Exception {
        Files.writeString(repo.resolve("b.txt"), "b\n");
        git("add", "b.txt");
        git("commit", "-q", "-m", "Second");
        git("branch", "other");
        gitWithEnvironment("GIT_SEQUENCE_EDITOR", "sed -i -e 's/^pick/edit/'", "rebase", "-i", "--root");

        GitFailure<GitError> failure = provider.rebase(new Branch.Name("other")).getLeft();

        assertTrue(
                failure.commands().forAll(command -> !command.line().contains("--abort")),
                failure.commands().toString());
        assertTrue(Files.exists(repo.resolve(".git/rebase-merge")));
    }

    @Test
    void aFailingRebaseIsAbortedAndReported() throws Exception {
        GitFailure<GitError> failure = provider.reword(
                        new Sha("0123456789012345678901234567890123456789"), new CommitMessage("x", ""))
                .getLeft();

        assertTrue(
                failure.commands().head().line().startsWith("git rev-list"),
                failure.commands().toString());
        assertEquals("Initial commit", provider.commits().get().head().message());
        assertTrue(Files.notExists(repo.resolve(".git/rebase-merge")));
    }

    @Test
    void stashSavesTrackedAndUntrackedChangesUnderTheGivenName() throws Exception {
        Files.writeString(repo.resolve("README.md"), "changed\n");
        Files.writeString(repo.resolve("new.txt"), "new\n");

        List<String> executed = provider.stash(Option.of("my work")).get().map(ExecutedCommand::line);

        assertEquals(List.of("git stash push --include-untracked --message 'my work'"), executed);
        assertEquals(List.of(), provider.status().get().files());
        assertEquals(
                List.of("stash@{0} my work"),
                provider.stashes().get().map(stash -> stash.ref() + " " + stash.message()));
    }

    @Test
    void stashWithoutANameIsNamedByGit() throws Exception {
        Files.writeString(repo.resolve("README.md"), "changed\n");
        Commit head = provider.commits().get().head();

        List<String> executed = provider.stash(Option.none()).get().map(ExecutedCommand::line);

        assertEquals(List.of("git stash push --include-untracked"), executed);
        assertEquals(
                List.of("WIP on main: " + head.shortSha() + " " + head.message()),
                provider.stashes().get().map(Stash::message));
    }

    @Test
    void popRestoresTheChangesAndDropsTheStash() throws Exception {
        Files.writeString(repo.resolve("README.md"), "changed\n");
        provider.stash(Option.of("work")).get();

        List<String> executed =
                provider.pop(provider.stashes().get().head()).get().map(ExecutedCommand::line);

        assertEquals(1, executed.size());
        assertTrue(executed.head().startsWith("git stash pop"), executed.toString());
        assertEquals("changed\n", Files.readString(repo.resolve("README.md")));
        assertEquals(List.of(), provider.stashes().get());
    }

    @Test
    void dropDeletesTheStashWithoutApplyingIt() throws Exception {
        Files.writeString(repo.resolve("README.md"), "changed\n");
        provider.stash(Option.of("work")).get();

        List<String> executed =
                provider.drop(provider.stashes().get().head()).get().map(ExecutedCommand::line);

        assertEquals(1, executed.size());
        assertTrue(executed.head().startsWith("git stash drop"), executed.toString());
        assertEquals(List.of(), provider.stashes().get());
        assertEquals("hello\n", Files.readString(repo.resolve("README.md")));
    }

    @Test
    void popOfAStashThatIsGoneFailsWithoutRunningAnything() throws Exception {
        Files.writeString(repo.resolve("README.md"), "changed\n");
        provider.stash(Option.of("work")).get();
        Stash work = provider.stashes().get().head();
        provider.drop(work).get();

        GitFailure<GitError> failure = provider.pop(work).getLeft();

        assertEquals(new GitError.StashGone("work"), failure.error());
        assertEquals(List.of(), failure.commands());
    }

    @Test
    void aStashIsFoundUnderItsNewNameOnceAnotherWasAdded() throws Exception {
        Files.writeString(repo.resolve("README.md"), "one\n");
        provider.stash(Option.of("first")).get();
        Stash first = provider.stashes().get().head();
        Files.writeString(repo.resolve("README.md"), "two\n");
        provider.stash(Option.of("second")).get();

        assertEquals(
                List.of("git stash drop 'stash@{1}'"),
                provider.drop(first).get().map(ExecutedCommand::line));

        assertEquals(List.of("second"), provider.stashes().get().map(Stash::message));
    }

    @Test
    void stashesListTheNewestFirstAndThereAreNoneInACleanRepository() throws Exception {
        assertEquals(List.of(), provider.stashes().get());
        Files.writeString(repo.resolve("README.md"), "one\n");
        provider.stash(Option.of("first")).get();
        Files.writeString(repo.resolve("README.md"), "two\n");
        provider.stash(Option.of("second")).get();

        assertEquals(
                List.of("stash@{0} second", "stash@{1} first"),
                provider.stashes().get().map(stash -> stash.ref() + " " + stash.message()));
    }

    @Test
    void aFailingCommandReportsTheCommandsRunUpToAndIncludingIt() {
        assertEquals(
                List.of("git branch --delete --force missing"),
                provider.deleteBranch(
                                new Branch.Name("missing"),
                                new RemoteBranch("origin", new Branch.Name("missing")),
                                DeleteScope.LOCAL)
                        .getLeft()
                        .commands()
                        .map(ExecutedCommand::line));
        assertEquals(
                List.of("git add -- missing.txt"),
                provider.stage(List.of("missing.txt")).getLeft().commands().map(ExecutedCommand::line));
    }

    @Test
    void checkoutFailsWithGitsExplanationWhenLocalChangesWouldBeOverwritten() throws Exception {
        git("checkout", "-q", "-b", "feature");
        Files.writeString(repo.resolve("README.md"), "changed on feature\n");
        git("commit", "-q", "-am", "Change README");
        git("checkout", "-q", "main");
        Files.writeString(repo.resolve("README.md"), "local change\n");

        GitError error = provider.checkout(new Branch.Name("feature")).getLeft().error();

        assertTrue(
                error instanceof GitError.LocalChanges(String explanation)
                        && explanation.contains("would be overwritten"),
                error.toString());
        assertEquals(
                Option.of(new Branch.Name("main")),
                provider.status().get().head().branchName());
    }

    @Test
    void branchesCarryTheTimeOfTheirLastCommit() {
        assertEquals(
                provider.commits().get().get(0).authorTime().getEpochSecond(),
                provider.branches().get().get(0).tipTime().getEpochSecond());
    }

    @Test
    void checkoutNewBranchStartsAtTheGivenBranchAndSwitchesToIt() throws Exception {
        git("checkout", "-q", "-b", "other");
        Files.writeString(repo.resolve("other.txt"), "other\n");
        git("add", "other.txt");
        git("commit", "-q", "-m", "On other");
        git("checkout", "-q", "main");

        provider.checkoutNewBranch(new Branch.Name("topic"), "other", Option.of(new Branch.Name("other")))
                .get();

        assertEquals(
                Option.of(new Branch.Name("topic")),
                provider.status().get().head().branchName());
        assertTrue(Files.exists(repo.resolve("other.txt")));
        assertEquals(
                Option.of(new Branch.Name("other")),
                provider.parentBranch(new Branch.Name("topic")).get());
    }

    @Test
    void aBranchMadeWithoutAParentHasNoneRecorded() throws Exception {
        provider.checkoutNewBranch(new Branch.Name("topic"), "main", Option.none())
                .get();

        assertEquals(
                Option.none(), provider.parentBranch(new Branch.Name("topic")).get());
    }

    @Test
    void failingToRecordTheParentStillMakesTheBranch() throws Exception {
        // git config cannot write the configuration while another writer holds its lock.
        Files.writeString(repo.resolve(".git/config.lock"), "");

        var made = provider.checkoutNewBranch(new Branch.Name("topic"), "main", Option.of(new Branch.Name("main")));

        assertTrue(made.isRight());
        assertEquals(2, made.get().size());
        assertEquals(
                Option.of(new Branch.Name("topic")),
                provider.status().get().head().branchName());
        assertEquals(
                Option.none(), provider.parentBranch(new Branch.Name("topic")).get());
    }

    @Test
    void countsAgainstABranchOfThisRepositoryFollowedAsARemoteOne() throws Exception {
        git("checkout", "-q", "-b", "side");
        git("commit", "-q", "--allow-empty", "-m", "Side one");
        BranchRef side = new BranchRef.Local(new Branch.Name("side"));
        BranchRef main = new BranchRef.Remote(new RemoteBranch(".", new Branch.Name("main")));

        assertEquals(1, provider.countNotOn(side, main, 1000).get());
    }

    @Test
    void branchesKnowWhetherTheyExistOnTheirRemote() throws Exception {
        git("branch", "pushed");
        git("branch", "local-only");
        java.nio.file.Path bare = Files.createTempDirectory("remote").resolve("remote.git");
        git("init", "-q", "--bare", bare.toString());
        git("remote", "add", "origin", bare.toString());
        git("push", "-q", "origin", "pushed");

        assertEquals(
                List.of(true, false),
                provider.branches()
                        .get()
                        .filter(b -> !b.name().value().equals("main"))
                        .sorted(java.util.Comparator.comparing(
                                        (Branch b) -> b.name().value())
                                .reversed())
                        .map(Branch::hasRemote));
    }

    @Test
    void remoteBranchesCanBeListedLoggedAndCheckedOutAsABranchThatFollowsThem() throws Exception {
        git("branch", "feature");
        git("checkout", "-q", "feature");
        git("commit", "-q", "--allow-empty", "-m", "On feature");
        git("checkout", "-q", "main");
        java.nio.file.Path bare = Files.createTempDirectory("remote").resolve("remote.git");
        git("init", "-q", "--bare", "-b", "main", bare.toString());
        git("remote", "add", "origin", bare.toString());
        git("push", "-q", "origin", "main", "feature");
        git("remote", "set-head", "origin", "main");
        git("branch", "-D", "feature");
        RemoteBranch feature = new RemoteBranch("origin", new Branch.Name("feature"));

        assertEquals(
                List.of("origin/feature", "origin/main"),
                provider.remoteBranches()
                        .get()
                        .map(ref -> ref.branch().shortName())
                        .sorted());
        assertEquals(
                List.of("On feature", "Initial commit"),
                provider.log(new BranchRef.Remote(feature))
                        .get()
                        .map(Commit::message)
                        .toList());

        provider.checkoutNewTrackingBranch(new Branch.Name("feature"), feature).get();

        Branch local = provider.branches()
                .get()
                .find(b -> b.name().equals(new Branch.Name("feature")))
                .get();
        assertTrue(local.current());
        assertEquals(Option.of(feature), local.upstream().map(upstream -> upstream.branch()));
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

        provider.pushSettingUpstream(new Branch.Name("main"), new RemoteBranch("origin", new Branch.Name("main")))
                .get();
        assertEquals(List.of(0, 0), counts("main"));
        assertTrue(provider.branches()
                .get()
                .filter(b -> b.name().equals(new Branch.Name("main")))
                .forAll(Branch::hasRemote));

        git("commit", "-q", "--allow-empty", "-m", "local");
        assertEquals(List.of(1, 0), counts("main"));
        provider.push(
                        new Branch.Name("main"),
                        new RemoteBranch("origin", new Branch.Name("main")),
                        PushMode.FAST_FORWARD)
                .get();
        assertEquals(List.of(0, 0), counts("main"));
    }

    @Test
    void anUpstreamUnderAnotherNameIsWherePushingAndDeletingGo() throws Exception {
        java.nio.file.Path bare = Files.createTempDirectory("remote").resolve("remote.git");
        git("init", "-q", "--bare", "-b", "main", bare.toString());
        git("remote", "add", "mirror", bare.toString());

        assertEquals(
                List.of("git push --set-upstream mirror main:refs/heads/shared"),
                provider.pushSettingUpstream(
                                new Branch.Name("main"), new RemoteBranch("mirror", new Branch.Name("shared")))
                        .get()
                        .map(ExecutedCommand::line));
        Branch main = provider.branches().get().head();
        assertEquals(
                Option.of(new Upstream(new RemoteBranch("mirror", new Branch.Name("shared")), 0, 0)), main.upstream());
        assertTrue(main.hasRemote());

        git("commit", "-q", "--allow-empty", "-m", "local");
        assertEquals(
                List.of("git push mirror main:refs/heads/shared"),
                provider.push(new Branch.Name("main"), main.pushTarget(), PushMode.FAST_FORWARD)
                        .get()
                        .map(ExecutedCommand::line));
        assertEquals(List.of(0, 0), counts("main"));

        assertEquals(
                List.of("git push mirror --delete shared"),
                provider.deleteBranch(new Branch.Name("main"), main.pushTarget(), DeleteScope.REMOTE)
                        .get()
                        .map(ExecutedCommand::line));
        assertTrue(gitSucceedsIn(repo, "fetch", "-q", "--prune", "mirror"));
        assertEquals(Option.none(), provider.branches().get().head().upstream());
    }

    @Test
    void aFailedPushSetsNoUpstream() throws Exception {
        assertTrue(provider.pushSettingUpstream(
                        new Branch.Name("main"), new RemoteBranch("nowhere", new Branch.Name("main")))
                .isLeft());

        assertEquals(Option.none(), provider.branches().get().head().upstream());
        assertFalse(gitSucceedsIn(repo, "config", "--get", "branch.main.merge"));
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

        assertTrue(provider.push(
                        new Branch.Name("main"),
                        new RemoteBranch("origin", new Branch.Name("main")),
                        PushMode.FAST_FORWARD)
                .isLeft());

        provider.push(
                        new Branch.Name("main"),
                        new RemoteBranch("origin", new Branch.Name("main")),
                        PushMode.FORCE_WITH_LEASE)
                .get();
        git("fetch", "-q", "origin");
        assertEquals(List.of(0, 0), counts("main"));
    }

    /**
     * A remote that main is pushed to and tracks, and a clone of it in which "theirs" is pushed to
     * {@code branch}; the repository has fetched that commit.
     */
    private Path remoteWithTheirCommitOn(String branch) throws Exception {
        java.nio.file.Path bare = Files.createTempDirectory("remote").resolve("remote.git");
        git("init", "-q", "--bare", "-b", "main", bare.toString());
        git("remote", "add", "origin", bare.toString());
        git("push", "-q", "-u", "origin", "main");
        java.nio.file.Path clone = Files.createTempDirectory("other").resolve("clone");
        gitIn(clone.getParent(), "clone", "-q", bare.toString(), "clone");
        gitIn(clone, "checkout", "-q", "-B", branch);
        Files.writeString(clone.resolve("README.md"), "theirs\n");
        gitIn(clone, "commit", "-q", "-am", "theirs");
        gitIn(clone, "push", "-q", "origin", branch);
        git("fetch", "-q", "origin");
        return clone;
    }

    @Test
    void pullMergesTheUpstreamWithoutAskingForAMessage() throws Exception {
        remoteWithTheirCommitOn("main");
        git("config", "pull.rebase", "false");
        git("config", "pull.ff", "true");
        git("commit", "-q", "--allow-empty", "-m", "ours");
        assertEquals(List.of(1, 1), counts("main"));

        assertEquals(List.of("git pull --no-edit"), provider.pull().get().map(ExecutedCommand::line));

        assertEquals(List.of(2, 0), counts("main"));
        assertEquals("theirs\n", Files.readString(repo.resolve("README.md")));
    }

    @Test
    void aPullThatConflictsIsLeftForTheUserToResolve() throws Exception {
        remoteWithTheirCommitOn("main");
        git("config", "pull.rebase", "false");
        git("config", "pull.ff", "true");
        Files.writeString(repo.resolve("README.md"), "ours\n");
        git("commit", "-q", "-am", "ours");

        GitFailure<GitError> failure = provider.pull().getLeft();

        assertInstanceOf(GitError.StoppedOnConflicts.class, failure.error());
        assertEquals(List.of("git pull --no-edit"), failure.commands().map(ExecutedCommand::line));
        assertTrue(gitSucceedsIn(repo, "rev-parse", "--verify", "--quiet", "MERGE_HEAD"));
    }

    @Test
    void aRebasingPullThatConflictsIsLeftForTheUserToResolve() throws Exception {
        remoteWithTheirCommitOn("main");
        git("config", "pull.rebase", "true");
        Files.writeString(repo.resolve("README.md"), "ours\n");
        git("commit", "-q", "-am", "ours");

        GitFailure<GitError> failure = provider.pull().getLeft();

        assertInstanceOf(GitError.StoppedOnConflicts.class, failure.error());
        assertEquals(List.of("git pull --no-edit"), failure.commands().map(ExecutedCommand::line));
        assertTrue(Files.exists(repo.resolve(".git/rebase-merge")));
    }

    @Test
    void fastForwardMovesABranchThatIsNotCheckedOutToItsUpstream() throws Exception {
        remoteWithTheirCommitOn("main");
        git("checkout", "-q", "-b", "other");
        assertEquals(List.of(0, 1), counts("main"));

        assertEquals(
                List.of("git fetch origin refs/heads/main:refs/heads/main"),
                provider.fastForward(new Branch.Name("main"), new RemoteBranch("origin", new Branch.Name("main")))
                        .get()
                        .map(ExecutedCommand::line));

        assertEquals(List.of(0, 0), counts("main"));
    }

    @Test
    void fastForwardRefusesABranchThatHasDiverged() throws Exception {
        remoteWithTheirCommitOn("main");
        git("commit", "-q", "--allow-empty", "-m", "ours");
        git("checkout", "-q", "-b", "other");

        assertTrue(provider.fastForward(new Branch.Name("main"), new RemoteBranch("origin", new Branch.Name("main")))
                .isLeft());
        assertEquals(List.of(1, 1), counts("main"));
    }

    @Test
    void pushFailsWithGitsExplanationWithoutARemote() {
        assertTrue(provider.push(
                        new Branch.Name("main"),
                        new RemoteBranch("origin", new Branch.Name("main")),
                        PushMode.FAST_FORWARD)
                .isLeft());
    }

    @Test
    void branchesWithoutAnUpstreamAreNeitherAheadNorBehind() throws Exception {
        git("branch", "local-only");
        assertEquals(List.of(0, 0), counts("local-only"));
    }

    private List<Integer> counts(String branch) {
        return provider.branches()
                .get()
                .filter(b -> b.name().value().equals(branch))
                .map(b -> List.of(b.ahead(), b.behind()))
                .head();
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
        return provider.branches()
                .get()
                .filter(Branch::isDefault)
                .map(b -> b.name().value())
                .toList();
    }

    @Test
    void deleteBranchRemovesTheLocalBranch() throws Exception {
        git("branch", "topic");

        provider.deleteBranch(
                        new Branch.Name("topic"),
                        new RemoteBranch("origin", new Branch.Name("topic")),
                        DeleteScope.LOCAL)
                .get();

        assertEquals(
                List.of("main"),
                provider.branches().get().map(b -> b.name().value()).toList());
    }

    @Test
    void deleteBranchFailsForTheCheckedOutBranch() {
        assertTrue(provider.deleteBranch(
                        new Branch.Name("main"), new RemoteBranch("origin", new Branch.Name("main")), DeleteScope.LOCAL)
                .isLeft());
    }

    @Test
    void renameBranchKeepsItsUpstream() throws Exception {
        java.nio.file.Path bare = Files.createTempDirectory("remote").resolve("remote.git");
        git("init", "-q", "--bare", bare.toString());
        git("remote", "add", "origin", bare.toString());
        git("branch", "feature");
        git("push", "-q", "--set-upstream", "origin", "feature");

        assertEquals(
                List.of("git branch --move feature topic"),
                provider.renameBranch(new Branch.Name("feature"), new Branch.Name("topic"))
                        .get()
                        .map(command -> command.line()));

        Branch topic = provider.branches()
                .get()
                .find(b -> b.name().equals(new Branch.Name("topic")))
                .get();
        assertEquals(
                Option.of(new RemoteBranch("origin", new Branch.Name("feature"))),
                topic.upstream().map(upstream -> upstream.branch()));
        assertTrue(provider.branches()
                .get()
                .find(b -> b.name().equals(new Branch.Name("feature")))
                .isEmpty());
    }

    @Test
    void renameBranchFailsWhenTheNameIsTaken() throws Exception {
        git("branch", "feature");
        git("branch", "topic");

        assertTrue(provider.renameBranch(new Branch.Name("feature"), new Branch.Name("topic"))
                .isLeft());
        assertTrue(provider.branches().get().exists(b -> b.name().equals(new Branch.Name("feature"))));
    }

    @Test
    void checkoutNewBranchFailsWhenTheNameIsTaken() throws Exception {
        git("branch", "topic");

        assertTrue(provider.checkoutNewBranch(new Branch.Name("topic"), "main", Option.none())
                .isLeft());
    }

    @Test
    void stageAndUnstageMoveFilesInAndOutOfTheIndex() throws Exception {
        Files.writeString(repo.resolve("README.md"), "changed\n");
        Files.writeString(repo.resolve("new.txt"), "new\n");

        provider.stage(List.of("README.md", "new.txt")).get();
        assertEquals(
                List.of(
                        new FileEntry("README.md", ChangeType.MODIFIED, true, false),
                        new FileEntry("new.txt", ChangeType.ADDED, true, false)),
                provider.status().get().files());

        provider.unstageNew(List.of("new.txt")).get();
        provider.unstage(List.of("README.md")).get();
        assertEquals(
                List.of(
                        new FileEntry("README.md", ChangeType.MODIFIED, false, true),
                        new FileEntry("new.txt", ChangeType.UNTRACKED, false, true)),
                provider.status().get().files());
    }

    @Test
    void aStagedRenameIsOneEntryThatUnstagesAndDiscardsAsAWhole() throws Exception {
        Files.move(repo.resolve("README.md"), repo.resolve("READ.md"));
        assertEquals(
                List.of(new FileEntry("READ.md", ChangeType.UNTRACKED), new FileEntry("README.md", ChangeType.DELETED)),
                provider.status().get().files());

        provider.stage(List.of("READ.md", "README.md")).get();
        List<FileEntry> files = provider.status().get().files();
        assertEquals(List.of(FileEntry.renamed("READ.md", "README.md", true, false)), files);

        Diffs diffs = provider.diff(files, DiffOptions.DEFAULT).get();
        assertTrue(diffs.staged().text().contains("rename from README.md"), diffs.toString());

        provider.unstageNew(List.of("READ.md")).get();
        provider.unstage(List.of("README.md")).get();
        assertEquals(
                List.of(new FileEntry("READ.md", ChangeType.UNTRACKED), new FileEntry("README.md", ChangeType.DELETED)),
                provider.status().get().files());

        provider.stage(List.of("READ.md", "README.md")).get();
        provider.discard(new DiscardPlan(List.of(), List.of(), List.of("README.md"), List.of("READ.md")))
                .get();
        assertEquals(List.of(), provider.status().get().files());
    }

    @Test
    void diffCombinesStagedUnstagedAndUntrackedChanges() throws Exception {
        Files.writeString(repo.resolve("README.md"), "staged\n");
        git("add", "README.md");
        Files.writeString(repo.resolve("README.md"), "unstaged\n");
        Files.writeString(repo.resolve("new.txt"), "brand new\n");

        Diffs diffs = provider.diff(provider.status().get().files(), DiffOptions.DEFAULT)
                .get();

        assertTrue(diffs.staged().text().contains("-hello"), diffs.toString());
        assertTrue(diffs.staged().text().contains("+staged"), diffs.toString());
        assertFalse(diffs.staged().text().contains("unstaged"), diffs.toString());
        assertTrue(diffs.unstaged().text().contains("-staged"), diffs.toString());
        assertTrue(diffs.unstaged().text().contains("+unstaged"), diffs.toString());
        assertTrue(diffs.unstaged().text().contains("+brand new"), diffs.toString());
        assertEquals(
                new Diffs("", ""), provider.diff(List.of(), DiffOptions.DEFAULT).get());
    }

    @Test
    void diffOfOneFileLeavesOutTheOthers() throws Exception {
        Files.writeString(repo.resolve("README.md"), "changed\n");
        Files.writeString(repo.resolve("new.txt"), "new\n");

        Diffs diffs = provider.diff(List.of(new FileEntry("new.txt", ChangeType.UNTRACKED)), DiffOptions.DEFAULT)
                .get();

        assertEquals("", diffs.staged().text());
        assertTrue(diffs.unstaged().text().contains("+new"), diffs.unstaged().text());
        assertFalse(
                diffs.unstaged().text().contains("README.md"), diffs.unstaged().text());
    }

    @Test
    void stageFailsWithGitsExplanationForAnUnknownPath() {
        GitError.ProcessError error =
                provider.stage(List.of("missing.txt")).getLeft().error();

        assertTrue(error instanceof GitError.CommandFailed failed && failed.says("missing.txt"), error.toString());
    }

    @Test
    void emptyRepositoryHasNoCommits() throws Exception {
        Path empty = repo.resolve("empty");
        Files.createDirectories(empty);
        gitIn(empty, "init", "-q", "-b", "main");
        CliGitInfoProvider emptyProvider = CliGitInfoProvider.open(empty).get();
        assertEquals(Option.none(), emptyProvider.status().get().headCommit());
        assertEquals(List.of(), emptyProvider.commits().get());
        assertEquals(List.of(), emptyProvider.branches().get());
    }

    private String git(String... args) throws IOException, InterruptedException, TimeoutException {
        return gitIn(repo, args);
    }

    private void gitWithEnvironment(String variable, String value, String... args) throws Exception {
        java.util.List<String> command = new java.util.ArrayList<>(java.util.List.of("git"));
        command.addAll(java.util.List.of(args));
        ProcessResult result = new ProcessExecutor()
                .command(command)
                .directory(repo.toFile())
                .environment(variable, value)
                .redirectErrorStream(true)
                .readOutput(true)
                .exitValueAny()
                .execute();
        assertEquals(0, result.getExitValue(), result::outputUTF8);
    }
}
