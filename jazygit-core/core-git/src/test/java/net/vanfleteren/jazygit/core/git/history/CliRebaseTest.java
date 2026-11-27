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
import net.vanfleteren.jazygit.core.git.CliGitFixture;
import net.vanfleteren.jazygit.core.git.Git;
import net.vanfleteren.jazygit.core.git.process.GitError;
import net.vanfleteren.jazygit.core.git.process.GitFailure;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.core.repo.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Rebasing the checked out branch of a real repository onto another, and what is left when that stops.
 */
class CliRebaseTest {

    @TempDir
    Path repo;

    private Git repository;

    @BeforeEach
    void setUp() throws Exception {
        repository = CliGitFixture.init(repo);
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

        List<String> executed =
                repository.integrate().rebase(new BranchName("main")).get().map(ExecutedCommand::line);

        assertEquals(List.of("git rebase --autostash --no-autosquash refs/heads/main"), executed);
        assertEquals(
                List.of("Feature work", "Main work", "Initial commit"),
                commits().get().map(c -> c.message().summary()).toList());
    }

    @Test
    void rebaseOntoABranchIgnoresATagWithTheSameName() throws Exception {
        git("checkout", "-q", "-b", "feature");
        Files.writeString(repo.resolve("f.txt"), "f\n");
        git("add", "f.txt");
        git("commit", "-q", "-m", "Feature work");
        git("checkout", "-q", "main");
        git("tag", "other", "main");
        Files.writeString(repo.resolve("m.txt"), "m\n");
        git("add", "m.txt");
        git("commit", "-q", "-m", "Main work");
        git("branch", "other", "main");
        git("checkout", "-q", "feature");

        repository.integrate().rebase(new BranchName("other")).get();

        assertEquals(
                List.of("Feature work", "Main work", "Initial commit"),
                commits().get().map(c -> c.message().summary()).toList());
    }

    @Test
    void rebaseFlattensAMergeOfATopicBranchAndKeepsFixupCommitsInPlace() throws Exception {
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

        repository.integrate().rebase(new BranchName("main")).get();

        List<Commit> after = commits().get();
        assertEquals(
                List.of("fixup! Feature work", "Side work", "More feature work", "Feature work", "Main work"),
                after.take(5).map(c -> c.message().summary()));
        assertTrue(after.forAll(c -> !c.isMerge()));
    }

    @Test
    void rebaseOfABranchThatMergedTheBaseInPutsItsCommitsOnTopOfTheBase() throws Exception {
        git("checkout", "-q", "-b", "feat");
        commitFile("f1.txt", "f1");
        git("checkout", "-q", "main");
        commitFile("m2.txt", "m2");
        git("checkout", "-q", "feat");
        git("merge", "-q", "--no-ff", "-m", "Merge main", "main");
        commitFile("f2.txt", "f2");
        git("checkout", "-q", "main");
        commitFile("m3.txt", "m3");
        git("checkout", "-q", "feat");

        repository.integrate().rebase(new BranchName("main")).get();

        List<Commit> after = commits().get();
        assertEquals(
                List.of("f2", "f1", "m3", "m2", "Initial commit"),
                after.map(c -> c.message().summary()).toList());
        assertTrue(after.forAll(c -> !c.isMerge()));
    }

    @Test
    void rebaseKeepsTheMergesWhenTheUserSetRebaseMerges() throws Exception {
        git("config", "rebase.rebaseMerges", "true");
        git("checkout", "-q", "-b", "feat");
        commitFile("f1.txt", "f1");
        git("checkout", "-q", "-b", "side");
        commitFile("s.txt", "s");
        git("checkout", "-q", "feat");
        commitFile("f2.txt", "f2");
        git("merge", "-q", "--no-ff", "-m", "Merge side", "side");
        git("checkout", "-q", "main");
        commitFile("m2.txt", "m2");
        git("checkout", "-q", "feat");

        repository.integrate().rebase(new BranchName("main")).get();

        assertTrue(commits().get().exists(Commit::isMerge));
    }

    private void commitFile(String name, String summary) throws Exception {
        Files.writeString(repo.resolve(name), summary + "\n");
        git("add", name);
        git("commit", "-q", "-m", summary);
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

        GitFailure failure =
                repository.integrate().rebase(new BranchName("main")).getLeft();

        GitError.StoppedOnConflicts stopped = assertInstanceOf(GitError.StoppedOnConflicts.class, failure.error());
        assertInstanceOf(InProgress.Rebasing.class, stopped.inProgress().get());
        assertEquals(1, stopped.conflicts());
        assertTrue(failure.commands().forAll(command -> !command.line().contains("--abort")));
        assertTrue(Files.exists(repo.resolve(".git/rebase-merge")));
        assertTrue(Files.readString(repo.resolve("README.md")).contains("<<<<<<<"));
    }

    @Test
    void aRebaseThatStopsHalfWayOtherThanOnConflictsIsAborted() throws Exception {
        git("checkout", "-q", "-b", "feature");
        Files.writeString(repo.resolve("x.txt"), "x\n");
        git("add", "x.txt");
        git("commit", "-q", "-m", "Add x");
        git("rm", "-q", "x.txt");
        git("commit", "-q", "-m", "Remove x");
        git("checkout", "-q", "main");
        Files.writeString(repo.resolve("m.txt"), "m\n");
        git("add", "m.txt");
        git("commit", "-q", "-m", "Main work");
        git("checkout", "-q", "feature");
        // Picking "Add x" would overwrite it, so the rebase stops there, without conflicts.
        Files.writeString(repo.resolve("x.txt"), "untracked\n");

        GitFailure failure =
                repository.integrate().rebase(new BranchName("main")).getLeft();

        assertInstanceOf(GitError.CommandFailed.class, failure.error());
        assertEquals(List.of("git rebase --abort"), failure.commands().tail().map(ExecutedCommand::line));
        assertEquals(Option.none(), failure.cleanupFailed());
        assertTrue(Files.notExists(repo.resolve(".git/rebase-merge")));
        assertEquals(
                List.of("Remove x", "Add x", "Initial commit"),
                commits().get().map(c -> c.message().summary()).toList());
    }

    @Test
    void aRebaseThatDoesNotStartIsNotAborted() {
        GitFailure failure =
                repository.integrate().rebase(new BranchName("nope")).getLeft();

        assertInstanceOf(GitError.CommandFailed.class, failure.error());
        assertEquals(1, failure.commands().size(), failure.commands().toString());
        assertEquals(Option.none(), failure.cleanupFailed());
    }

    @Test
    void aRebaseTheUserStoppedIsLeftAlone() throws Exception {
        Files.writeString(repo.resolve("b.txt"), "b\n");
        git("add", "b.txt");
        git("commit", "-q", "-m", "Second");
        git("branch", "other");
        gitWithEnvironment("GIT_SEQUENCE_EDITOR", "sed -i -e 's/^pick/edit/'", "rebase", "-i", "--root");

        GitFailure failure =
                repository.integrate().rebase(new BranchName("other")).getLeft();

        assertTrue(
                failure.commands().forAll(command -> !command.line().contains("--abort")),
                failure.commands().toString());
        assertTrue(Files.exists(repo.resolve(".git/rebase-merge")));
    }

    private String git(String... args) throws IOException, InterruptedException, TimeoutException {
        return gitIn(repo, args);
    }

    private Either<GitError, List<Commit>> commits() {
        return CliGitFixture.commits(repository);
    }

    private void gitWithEnvironment(String variable, String value, String... args) throws Exception {
        CliGitFixture.gitWithEnvironment(repo, variable, value, args);
    }
}
