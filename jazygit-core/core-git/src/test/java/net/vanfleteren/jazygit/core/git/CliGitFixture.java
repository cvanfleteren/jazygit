package net.vanfleteren.jazygit.core.git;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.vavr.collection.List;
import io.vavr.control.Either;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeoutException;
import net.vanfleteren.jazygit.core.git.model.Fixtures;
import net.vanfleteren.jazygit.core.git.process.GitError;
import net.vanfleteren.jazygit.core.repo.AllBranches;
import net.vanfleteren.jazygit.core.repo.Branch;
import net.vanfleteren.jazygit.core.repo.Commit;
import net.vanfleteren.jazygit.core.repo.Sha;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;

/**
 * A real repository for the tests of {@link CliGit} and its roles, changed through the git CLI: one commit of a
 * {@code README.md} on {@code main}, and what the tests read of it as the app does.
 */
public final class CliGitFixture {

    private CliGitFixture() {}

    /**
     * Makes the repository in {@code repo} and opens it; the test is skipped where git is not installed.
     */
    public static Git init(Path repo) throws Exception {
        assumeTrue(gitAvailable(), "git is not installed");
        gitIn(repo, "init", "-q", "-b", "main");
        gitIn(repo, "config", "user.name", "Test");
        gitIn(repo, "config", "user.email", "test@example.com");
        gitIn(repo, "config", "commit.gpgsign", "false");
        Files.writeString(repo.resolve("README.md"), "hello\n");
        gitIn(repo, "add", "README.md");
        gitIn(repo, "commit", "-q", "-m", "Initial commit");
        return CliGit.open(repo).get();
    }

    public static List<Branch> withoutTimes(List<Branch> branches) {
        return branches.map(b -> Fixtures.branch(b.name(), b.current(), b.tip()))
                .toList();
    }

    /**
     * Runs git in {@code dir}, failing the test when it fails; what it printed.
     */
    public static String gitIn(Path dir, String... args) throws IOException, InterruptedException, TimeoutException {
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

    public static boolean gitSucceedsIn(Path dir, String... args)
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

    /**
     * Runs git in {@code dir} with {@code variable} set to {@code value}, failing the test when it fails.
     */
    public static void gitWithEnvironment(Path dir, String variable, String value, String... args) throws Exception {
        java.util.List<String> command = new java.util.ArrayList<>(java.util.List.of("git"));
        command.addAll(java.util.List.of(args));
        ProcessResult result = new ProcessExecutor()
                .command(command)
                .directory(dir.toFile())
                .environment(variable, value)
                .redirectErrorStream(true)
                .readOutput(true)
                .exitValueAny()
                .execute();
        assertEquals(0, result.getExitValue(), result::outputUTF8);
    }

    /**
     * The log of HEAD, from where the status says it is, as the app reads it (ADR 49).
     */
    public static Either<GitError, List<Commit>> commits(Git repository) {
        return repository
                .status()
                .read()
                .flatMap(status ->
                        status.headCommit().fold(() -> Either.right(List.empty()), repository.history()::commits));
    }

    /**
     * The commit the local branch {@code branch} is at.
     */
    public static Sha tip(Path repo, String branch) throws IOException, InterruptedException, TimeoutException {
        return new Sha(gitIn(repo, "rev-parse", "refs/heads/" + branch).strip());
    }

    /**
     * How many commits the local branch {@code branch} is ahead of its upstream, and how many behind.
     */
    public static List<Integer> counts(Git repository, String branch) {
        return repository
                .branches()
                .all()
                .map(AllBranches::local)
                .get()
                .filter(b -> b.name().value().equals(branch))
                .map(b -> List.of(b.ahead(), b.behind()))
                .head();
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
