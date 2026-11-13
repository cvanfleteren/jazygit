package net.vanfleteren.jazygit.git;

import io.vavr.collection.HashMap;
import io.vavr.collection.Map;
import io.vavr.control.Either;
import io.vavr.control.Option;
import io.vavr.control.Try;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;
import net.vanfleteren.jazygit.git.branches.CliBranches;
import net.vanfleteren.jazygit.git.branches.CliCheckout;
import net.vanfleteren.jazygit.git.branches.CliConfig;
import net.vanfleteren.jazygit.git.branches.CliRemotes;
import net.vanfleteren.jazygit.git.branches.CliWorktrees;
import net.vanfleteren.jazygit.git.branches.Remotes;
import net.vanfleteren.jazygit.git.commits.CliCommits;
import net.vanfleteren.jazygit.git.files.CliDiffs;
import net.vanfleteren.jazygit.git.files.CliEditor;
import net.vanfleteren.jazygit.git.files.CliIndex;
import net.vanfleteren.jazygit.git.files.CliMetadata;
import net.vanfleteren.jazygit.git.files.CliStashes;
import net.vanfleteren.jazygit.git.files.CliStatus;
import net.vanfleteren.jazygit.git.files.RepositoryIdentity;
import net.vanfleteren.jazygit.git.history.CliHistory;
import net.vanfleteren.jazygit.git.history.CliInProgress;
import net.vanfleteren.jazygit.git.history.CliIntegrate;
import net.vanfleteren.jazygit.git.process.GitCommand;
import net.vanfleteren.jazygit.git.process.GitError;
import net.vanfleteren.jazygit.git.process.GitError.*;
import net.vanfleteren.jazygit.git.process.GitFailure;
import net.vanfleteren.jazygit.git.process.GitProcess;

/**
 * Gives the {@link Git} of a real repository, whose roles go through the {@code git} command line tool only, so the
 * repository is read and changed exactly as git itself does (ADR 69).
 */
public final class CliGit {

    private CliGit() {}

    /**
     * Opens the repository containing {@code startDir}, searching parent directories like
     * {@code git} itself does. It fails when {@code startDir} is not inside a git repository, the
     * repository has no working tree, or git cannot be run.
     */
    public static Either<GitError, Git> open(Path startDir) {
        return !Files.isDirectory(startDir)
                ? Either.left(new NotARepository(startDir))
                : Either.narrow(GitProcess.read(
                                        startDir,
                                        GitCommand.reading("rev-parse", "--is-bare-repository"),
                                        raw -> Either.right(raw.strip().equals("true")))
                                .mapLeft(error -> notARepository(error, startDir)))
                        .flatMap(bare -> bare
                                ? Either.left(new NoWorktree(startDir))
                                : Either.narrow(GitProcess.read(
                                        startDir, GitCommand.reading("rev-parse", "--show-toplevel"), CliGit::path)))
                        .flatMap(worktree -> Either.narrow(CliInProgress.markers(worktree)
                                .map(markers -> git(worktree, markers, CliMetadata.locate(worktree)))));
    }

    /**
     * The roles of the repository at {@code worktree}. Where git keeps the files that tell what the repository is in
     * the middle of, {@code markers}, and where the files are that tell whether the branches, the stashes or the
     * worktrees changed, {@code metadataAt}, are found once, as is the name of the repository.
     */
    private static Git git(Path worktree, CliInProgress.Markers markers, Option<CliMetadata.Locations> metadataAt) {
        return new Git(
                worktree,
                RepositoryIdentity.labelOf(worktree),
                new CliStatus(worktree, markers, metadataAt),
                new CliBranches(worktree),
                new CliCheckout(worktree),
                new CliHistory(worktree),
                new CliDiffs(worktree),
                new CliStashes(worktree),
                new CliCommits(worktree),
                new CliIntegrate(worktree),
                new CliRemotes(worktree, HashMap::empty),
                new CliIndex(worktree),
                new CliWorktrees(worktree),
                new CliConfig(worktree),
                new CliEditor(worktree));
    }

    /**
     * Makes {@code directory} a new repository with {@code git init}, as the user's git config has it, and opens it.
     * In a repository already, git initialises it again, which changes nothing of what is in it.
     */
    public static Either<GitFailure, Git> init(Path directory) {
        return GitProcess.log(directory, GitCommand.git("init", "--quiet"))
                .flatMap(log -> open(directory).mapLeft(error -> new GitFailure(error, log)));
    }

    private static Either<UnexpectedOutput, Path> path(String raw) {
        return Try.of(() -> Path.of(raw.strip())).toEither().mapLeft(e -> new UnexpectedOutput(raw));
    }

    private static GitError notARepository(GitError error, Path startDir) {
        return error instanceof CommandFailed failed && failed.says("not a git repository")
                ? new NotARepository(startDir)
                : error;
    }

    /**
     * {@code git}, with git told which account to use by the environment variables of {@code credentials} whenever
     * it talks to a remote, for a credential helper that reads them, such as gh's: its {@link Remotes} are those of
     * the git command line tool with those credentials, in place of whatever it had, also when its other roles are
     * not the real ones.
     */
    public static Git withCredentials(Git git, Supplier<Map<String, String>> credentials) {
        return git.withRemotes(new CliRemotes(git.worktree(), credentials));
    }
}
