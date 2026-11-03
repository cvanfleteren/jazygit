package net.vanfleteren.jazygit.git;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Either;
import io.vavr.control.Option;
import io.vavr.control.Try;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;
import net.vanfleteren.jazygit.git.GitError.*;
import net.vanfleteren.jazygit.git.model.*;
import net.vanfleteren.jazygit.process.ExecutedCommand;

/**
 * {@link GitInfoProvider} backed by a real repository, through the {@code git} command line tool only, so
 * the repository is read and changed exactly as git itself does.
 */
public final class CliGitInfoProvider implements GitInfoProvider {

    private final Path worktree;
    // Where git keeps the files that tell what the repository is in the middle of, found once.
    private final CliInProgress.Markers markers;
    // Where the files are that tell whether the branches, the stashes or the worktrees changed, found once.
    private final Option<CliMetadata.Locations> metadataAt;
    private final CliStatus status;
    private final CliCheckout checkout;
    private final CliStashes stashes;
    private final CliWorktrees worktrees;
    private final CliConfig config;
    private final CliIndex index;
    private final CliEditor editor;
    private final CliDiffs diffs;
    private final CliHistory history;
    private final CliBranches branches;
    private final CliCommits commits;
    private final CliIntegrate integrate;
    private final CliRemotes remotes;

    private CliGitInfoProvider(
            Path worktree,
            CliInProgress.Markers markers,
            Option<CliMetadata.Locations> metadataAt,
            Supplier<Map<String, String>> credentials) {
        this.worktree = worktree;
        this.markers = markers;
        this.metadataAt = metadataAt;
        this.status = new CliStatus(worktree, markers, metadataAt);
        this.checkout = new CliCheckout(worktree);
        this.stashes = new CliStashes(worktree);
        this.worktrees = new CliWorktrees(worktree);
        this.config = new CliConfig(worktree);
        this.index = new CliIndex(worktree);
        this.editor = new CliEditor(worktree);
        this.diffs = new CliDiffs(worktree);
        this.history = new CliHistory(worktree);
        this.branches = new CliBranches(worktree);
        this.commits = new CliCommits(worktree);
        this.integrate = new CliIntegrate(worktree);
        this.remotes = new CliRemotes(worktree, credentials);
    }

    /**
     * Opens the repository containing {@code startDir}, searching parent directories like
     * {@code git} itself does. It fails when {@code startDir} is not inside a git repository, the
     * repository has no working tree, or git cannot be run.
     */
    public static Either<GitError, CliGitInfoProvider> open(Path startDir) {
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
                                        startDir,
                                        GitCommand.reading("rev-parse", "--show-toplevel"),
                                        CliGitInfoProvider::path)))
                        .flatMap(worktree -> Either.narrow(CliInProgress.markers(worktree)
                                .map(markers -> new CliGitInfoProvider(
                                        worktree, markers, CliMetadata.locate(worktree), HashMap::empty))));
    }

    /**
     * Makes {@code directory} a new repository with {@code git init}, as the user's git config has it, and opens it.
     * In a repository already, git initialises it again, which changes nothing of what is in it.
     */
    public static Either<GitFailure<GitError>, CliGitInfoProvider> init(Path directory) {
        return GitFailure.<GitError, List<ExecutedCommand>>widen(
                        GitProcess.log(directory, GitCommand.git("init", "--quiet")))
                .flatMap(log -> open(directory).mapLeft(error -> new GitFailure<>(error, log)));
    }

    private static Either<UnexpectedOutput, Path> path(String raw) {
        return Try.of(() -> Path.of(raw.strip())).toEither().mapLeft(e -> new UnexpectedOutput(raw));
    }

    private static GitError notARepository(ProcessError error, Path startDir) {
        return error instanceof CommandFailed failed && failed.says("not a git repository")
                ? new NotARepository(startDir)
                : error;
    }

    /**
     * This repository, with git told which account to use by the environment variables of {@code credentials}
     * whenever it talks to a remote, for a credential helper that reads them, such as gh's.
     */
    public CliGitInfoProvider withCredentials(Supplier<Map<String, String>> credentials) {
        return new CliGitInfoProvider(worktree, markers, metadataAt, credentials);
    }

    @Override
    public Path worktree() {
        return worktree;
    }

    @Override
    public String repositoryName() {
        return RepositoryIdentity.labelOf(worktree);
    }

    @Override
    public Worktrees worktrees() {
        return worktrees;
    }

    @Override
    public Stashes stashes() {
        return stashes;
    }

    @Override
    public History history() {
        return history;
    }

    @Override
    public Branches branches() {
        return branches;
    }

    @Override
    public Integrate integrate() {
        return integrate;
    }

    @Override
    public Remotes remotes() {
        return remotes;
    }

    @Override
    public Commits commits() {
        return commits;
    }

    @Override
    public Diffs diffs() {
        return diffs;
    }

    @Override
    public Config config() {
        return config;
    }

    @Override
    public Index index() {
        return index;
    }

    @Override
    public Editor editor() {
        return editor;
    }

    @Override
    public Status status() {
        return status;
    }

    @Override
    public Checkout checkout() {
        return checkout;
    }
}
