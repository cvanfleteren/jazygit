package net.vanfleteren.jazygit.core.git.history;

import static net.vanfleteren.jazygit.core.git.CliGitFixture.gitIn;
import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeoutException;
import net.vanfleteren.jazygit.core.git.CliGit;
import net.vanfleteren.jazygit.core.git.CliGitFixture;
import net.vanfleteren.jazygit.core.git.Git;
import net.vanfleteren.jazygit.core.git.model.*;
import net.vanfleteren.jazygit.core.git.process.GitError;
import net.vanfleteren.jazygit.core.repo.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Reading the history of a real repository: the log, searching it, and what a branch has that its base has not.
 */
class CliLogTest {

    @TempDir
    Path repo;

    private Git repository;

    @BeforeEach
    void setUp() throws Exception {
        repository = CliGitFixture.init(repo);
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
    void thePickaxeIgnoresCaseUnlessItHasAnUppercaseLetter() throws Exception {
        Files.writeString(repo.resolve("a.txt"), "Token\n");
        git("add", "a.txt");
        git("commit", "-q", "-m", "Add it");

        assertEquals(List.of("Add it"), messages("-S:token"));
        assertEquals(List.of("Add it"), messages("-S:Token"));
        assertEquals(List.of(), messages("-S:TOKEN"));
    }

    @Test
    void searchFindsNothingWithoutCommits(@TempDir Path empty) throws Exception {
        gitIn(empty, "init", "-q");

        assertEquals(
                List.of(),
                CliGit.open(empty)
                        .get()
                        .history()
                        .search(CommitQuery.parse("x"))
                        .get());
    }

    private List<String> messages(String query) {
        return repository
                .history()
                .search(CommitQuery.parse(query))
                .get()
                .map(c -> c.message().summary())
                .toList();
    }

    @Test
    void logShowsOnlyTheCommitsOfTheBranch() throws Exception {
        git("checkout", "-q", "-b", "side");
        git("commit", "-q", "--allow-empty", "-m", "Side subject", "-m", "Side detail\nsecond line");
        git("checkout", "-q", "main");

        List<Commit> side = repository
                .history()
                .log(new BranchRef.Local(new BranchName("side")))
                .get();
        assertEquals(
                List.of("Side subject", "Initial commit"),
                side.map(c -> c.message().summary()).toList());
        assertEquals("Side detail\nsecond line", side.get(0).message().description());
        assertEquals("", side.get(1).message().description());
        assertEquals("Test", side.get(0).authorName());
        assertEquals("test@example.com", side.get(0).authorEmail());

        assertEquals(
                List.of("Initial commit"),
                repository
                        .history()
                        .log(new BranchRef.Local(new BranchName("main")))
                        .get()
                        .map(c -> c.message().summary())
                        .toList());
        assertEquals(
                new GitError.UnknownBranch(new BranchRef.Local(new BranchName("gone"))),
                repository
                        .history()
                        .log(new BranchRef.Local(new BranchName("gone")))
                        .getLeft());
    }

    @Test
    void divergenceListsTheCommitsOnlyOnEitherSide() throws Exception {
        git("checkout", "-q", "-b", "side");
        git("commit", "-q", "--allow-empty", "-m", "Side one");
        git("commit", "-q", "--allow-empty", "-m", "Side two");
        git("checkout", "-q", "main");
        git("commit", "-q", "--allow-empty", "-m", "Main one");
        BranchRef side = new BranchRef.Local(new BranchName("side"));
        BranchRef main = new BranchRef.Local(new BranchName("main"));

        Divergence divergence = repository.history().divergence(side, main).get();

        assertEquals(
                List.of("Side two", "Side one"),
                divergence.ahead().commits().map(c -> c.message().summary()));
        assertEquals(2, divergence.ahead().count());
        assertEquals(
                List.of("Main one"),
                divergence.behind().commits().map(c -> c.message().summary()));
        assertEquals(1, divergence.behind().count());
        BranchRef gone = new BranchRef.Local(new BranchName("gone"));
        assertEquals(
                new GitError.UnknownBranch(gone),
                repository.history().divergence(side, gone).getLeft());
    }

    @Test
    void onlyTheBranchNamedExactlyIsCompared() throws Exception {
        // for-each-ref refs/heads/side also lists refs/heads/side/x: that is no branch side.
        git("branch", "side/x");
        BranchRef side = new BranchRef.Local(new BranchName("side"));
        BranchRef main = new BranchRef.Local(new BranchName("main"));

        assertEquals(
                new GitError.UnknownBranch(side),
                repository.history().divergence(side, main).getLeft());
        assertEquals(
                new GitError.UnknownBranch(side),
                repository.history().firstSubject(main, side).getLeft());
        assertEquals(
                0,
                repository
                        .history()
                        .divergence(new BranchRef.Local(new BranchName("side/x")), main)
                        .get()
                        .ahead()
                        .count());
    }

    @Test
    void aheadListsTheCommitsNotOnTheBase() throws Exception {
        Sha fork = repository.status().read().get().headCommit().get();
        git("checkout", "-q", "-b", "side");
        git("commit", "-q", "--allow-empty", "-m", "Side one");
        git("checkout", "-q", "main");
        git("commit", "-q", "--allow-empty", "-m", "Main one");
        git("checkout", "-q", "side");
        git("merge", "-q", "--no-edit", "main");
        BranchRef main = new BranchRef.Local(new BranchName("main"));
        Sha head = repository.status().read().get().headCommit().get();

        Ahead ahead = repository.history().ahead(head, main, tip("main")).get();

        assertEquals(main, ahead.base());
        assertEquals(
                List.of("Merge branch 'main' into side", "Side one"),
                ahead.commits().map(c -> c.message().summary()));
        assertFalse(ahead.more());
        assertTrue(ahead.forkPoint().exists(commit -> commit.message().summary().equals("Main one")));
        assertFalse(ahead.shared(ahead.commits().head().sha()));
        assertTrue(ahead.shared(fork));
        assertEquals(head, ahead.tip());
    }

    @Test
    void theForkPointOfABranchMadeFromItsBaseIsTheCommitItWasMadeFrom() throws Exception {
        Sha fork = tip("main");
        git("checkout", "-q", "-b", "side");
        git("commit", "-q", "--allow-empty", "-m", "Side one");
        git("checkout", "-q", "main");
        git("commit", "-q", "--allow-empty", "-m", "Main one");
        BranchRef main = new BranchRef.Local(new BranchName("main"));

        Ahead ahead = repository.history().ahead(tip("side"), main, tip("main")).get();

        assertEquals(List.of("Side one"), ahead.commits().map(c -> c.message().summary()));
        assertEquals(Option.of(fork), ahead.forkPoint().map(Commit::sha));
    }

    @Test
    void aBranchWithNothingAheadOfItsBaseForksAtItsOwnTip() throws Exception {
        Sha fork = tip("main");
        git("branch", "side");
        git("commit", "-q", "--allow-empty", "-m", "Main one");
        BranchRef main = new BranchRef.Local(new BranchName("main"));

        Ahead ahead = repository.history().ahead(fork, main, tip("main")).get();

        assertEquals(List.empty(), ahead.commits());
        assertFalse(ahead.more());
        assertEquals(Option.of(fork), ahead.forkPoint().map(Commit::sha));
    }

    @Test
    void historiesThatNeverMetHaveNoForkPoint() throws Exception {
        git("checkout", "-q", "--orphan", "other");
        git("commit", "-q", "--allow-empty", "-m", "Other root");
        BranchRef main = new BranchRef.Local(new BranchName("main"));

        Ahead ahead = repository
                .history()
                .ahead(repository.status().read().get().headCommit().get(), main, tip("main"))
                .get();

        assertEquals(List.of("Other root"), ahead.commits().map(c -> c.message().summary()));
        assertEquals(Option.none(), ahead.forkPoint());
    }

    @Test
    void theFirstSubjectIsThatOfTheOldestCommitNotOnTheBase() throws Exception {
        git("checkout", "-q", "-b", "side");
        git("commit", "-q", "--allow-empty", "-m", "Side one", "-m", "Its body");
        git("commit", "-q", "--allow-empty", "-m", "Side two");
        BranchRef side = new BranchRef.Local(new BranchName("side"));
        BranchRef main = new BranchRef.Local(new BranchName("main"));

        assertEquals(
                Option.of("Side one"),
                repository.history().firstSubject(side, main).get());
        assertEquals(
                Option.none(), repository.history().firstSubject(main, side).get());
    }

    @Test
    void countsTheCommitsNotOnTheBaseUpToTheMaximum() throws Exception {
        git("checkout", "-q", "-b", "side");
        git("commit", "-q", "--allow-empty", "-m", "Side one");
        git("commit", "-q", "--allow-empty", "-m", "Side two");
        git("commit", "-q", "--allow-empty", "-m", "Side three");
        BranchRef side = new BranchRef.Local(new BranchName("side"));
        BranchRef main = new BranchRef.Local(new BranchName("main"));

        assertEquals(3, repository.history().countNotOn(side, main, 1000).get());
        assertEquals(2, repository.history().countNotOn(side, main, 2).get());
        assertEquals(0, repository.history().countNotOn(main, side, 1000).get());
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

        List<Commit> commits = commits().get();

        assertEquals(
                List.of("Merge feature", "Feature work", "Main work"),
                commits.map(c -> c.message().summary()).take(3));
        assertEquals(2, commits.head().parents().size());
        assertTrue(commits.head().isMerge());
    }

    private String git(String... args) throws IOException, InterruptedException, TimeoutException {
        return gitIn(repo, args);
    }

    private Either<GitError, List<Commit>> commits() {
        return CliGitFixture.commits(repository);
    }

    private Sha tip(String branch) throws IOException, InterruptedException, TimeoutException {
        return CliGitFixture.tip(repo, branch);
    }
}
