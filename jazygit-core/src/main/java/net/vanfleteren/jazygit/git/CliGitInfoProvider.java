package net.vanfleteren.jazygit.git;

import io.vavr.Tuple2;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Either;
import io.vavr.control.Option;
import io.vavr.control.Try;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
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
    // Asked each time git talks to a remote.
    private final Supplier<Map<String, String>> credentials;

    private CliGitInfoProvider(
            Path worktree,
            CliInProgress.Markers markers,
            Option<CliMetadata.Locations> metadataAt,
            Supplier<Map<String, String>> credentials) {
        this.worktree = worktree;
        this.markers = markers;
        this.metadataAt = metadataAt;
        this.credentials = credentials;
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
    public Either<ProcessError, RepoStatus> status() {
        return CliStatus.read(worktree, markers);
    }

    @Override
    public Option<GitMetadata> metadata() {
        return metadataAt.map(at -> CliMetadata.read(at, Instant.now()));
    }

    @Override
    public Either<ProcessError, List<Worktree>> worktrees() {
        return CliWorktree.list(worktree);
    }

    @Override
    public Either<ProcessError, Boolean> hasChanges(Path worktree) {
        return CliWorktree.hasChanges(worktree);
    }

    @Override
    public Either<GitFailure<ProcessError>, List<ExecutedCommand>> addWorktree(Path location, BranchName branch) {
        return CliWorktree.add(worktree, location, branch);
    }

    @Override
    public Either<GitFailure<ProcessError>, List<ExecutedCommand>> addDetachedWorktree(
            Path location, Revision startPoint) {
        return CliWorktree.addDetached(worktree, location, startPoint);
    }

    @Override
    public Either<GitFailure<GitError>, List<ExecutedCommand>> removeWorktree(Path location, boolean force) {
        return CliWorktree.remove(worktree, location, force);
    }

    @Override
    public Either<GitFailure<ProcessError>, List<ExecutedCommand>> removeWorktreeAnyway(Path location) {
        return CliWorktree.removeAnyway(worktree, location);
    }

    @Override
    public Either<GitFailure<ProcessError>, List<ExecutedCommand>> addWorktreeWithNewBranch(
            Path location, BranchName name, NewBranchStart start) {
        return CliWorktree.addNewBranch(worktree, location, name, start);
    }

    @Override
    public Either<GitFailure<ProcessError>, List<ExecutedCommand>> pruneWorktrees() {
        return CliWorktree.prune(worktree);
    }

    @Override
    public Either<GitFailure<ProcessError>, List<ExecutedCommand>> lockWorktree(Path location, boolean lock) {
        return CliWorktree.lock(worktree, location, lock);
    }

    @Override
    public Either<GitFailure<ProcessError>, List<ExecutedCommand>> lockWorktree(Path location, String reason) {
        return CliWorktree.lock(worktree, location, reason);
    }

    @Override
    public Either<GitFailure<GitError>, List<ExecutedCommand>> edit(List<String> files) {
        return CliEditor.edit(worktree, files);
    }

    @Override
    public Either<GitFailure<GitError>, Tuple2<CommitMessage, List<ExecutedCommand>>> editMessage(
            CommitMessage message) {
        return CliEditor.editMessage(worktree, message);
    }

    @Override
    public Either<GitFailure<GitError>, Tuple2<String, List<ExecutedCommand>>> editText(String fileName, String text) {
        return CliEditor.editText(worktree, fileName, text);
    }

    @Override
    public Either<ProcessError, List<Stash>> stashes() {
        return CliStash.list(worktree);
    }

    @Override
    public Either<ProcessError, DiffText> stashChanges(Sha sha, DiffOptions options) {
        return CliStash.changes(worktree, sha, options);
    }

    @Override
    public Either<ProcessError, List<CommitFile>> stashFiles(Sha sha) {
        return CliStash.files(worktree, sha);
    }

    @Override
    public Either<ProcessError, DiffText> stashFileChanges(Sha sha, List<CommitFile> files, DiffOptions options) {
        return CliStash.fileChanges(worktree, sha, files, options);
    }

    @Override
    public Either<GitFailure<GitError>, List<ExecutedCommand>> drop(Stash stash) {
        return CliStash.drop(worktree, stash);
    }

    @Override
    public Either<GitFailure<GitError>, List<ExecutedCommand>> pop(Stash stash) {
        return CliStash.pop(worktree, stash);
    }

    @Override
    public Either<GitFailure<ProcessError>, List<ExecutedCommand>> stash(Option<String> message) {
        return CliStash.push(worktree, message);
    }

    @Override
    public Either<ProcessError, List<Commit>> commits() {
        return CliLog.commits(worktree);
    }

    @Override
    public Either<ProcessError, List<Commit>> search(CommitQuery query, Option<BranchRef> notOn) {
        return CliLog.search(worktree, query, notOn);
    }

    @Override
    public Either<GitError, Option<Ahead>> ahead(Option<BranchRef> branch, List<BranchRef> bases) {
        return CliLog.ahead(worktree, branch, bases, MAX_COMMITS);
    }

    @Override
    public Either<ProcessError, AllBranches> allBranches() {
        return CliBranch.all(worktree);
    }

    @Override
    public Either<GitError, List<Commit>> log(BranchRef branch) {
        return CliLog.log(worktree, branch);
    }

    @Override
    public Either<GitError, Divergence> divergence(BranchRef branch, BranchRef other) {
        return CliLog.divergence(worktree, branch, other);
    }

    @Override
    public Either<GitError, Option<String>> firstSubject(BranchRef branch, BranchRef base) {
        return CliLog.firstSubject(worktree, branch, base);
    }

    @Override
    public Either<ProcessError, Integer> countNotOn(BranchRef branch, BranchRef base, int max) {
        return CliLog.countNotOn(worktree, branch, base, max);
    }

    @Override
    public Either<GitError, CommitRange> commitRange(BranchName branch, Sha oldest, Option<RemoteBranch> upstream) {
        return CliLog.range(worktree, branch, oldest, upstream);
    }

    @Override
    public Either<GitFailure<GitError>, List<ExecutedCommand>> checkout(BranchName branch) {
        return CliCheckout.checkout(worktree, branch);
    }

    @Override
    public Either<GitFailure<GitError>, List<ExecutedCommand>> checkoutWithStash(BranchName branch) {
        return CliCheckout.checkoutWithStash(worktree, branch);
    }

    @Override
    public Either<GitFailure<ProcessError>, List<ExecutedCommand>> checkoutNewBranch(
            BranchName name, Revision startPoint, Option<BranchName> parent) {
        return CliCheckout.checkoutNewBranch(worktree, name, startPoint, parent);
    }

    @Override
    public Either<GitFailure<ProcessError>, Option<Sha>> resolve(String text) {
        return CliLog.resolve(worktree, text);
    }

    @Override
    public Either<GitFailure<ProcessError>, List<ExecutedCommand>> checkoutNewTrackingBranch(
            BranchName name, RemoteBranch upstream) {
        return CliCheckout.checkoutNewTrackingBranch(worktree, name, upstream);
    }

    @Override
    public Either<GitFailure<GitError>, List<ExecutedCommand>> push(
            BranchName branch, RemoteBranch upstream, PushMode mode) {
        return CliPush.push(worktree, branch, upstream, mode, credentials.get());
    }

    @Override
    public Either<GitFailure<ProcessError>, List<ExecutedCommand>> pushSettingUpstream(
            BranchName branch, RemoteBranch upstream) {
        return CliPush.pushSettingUpstream(worktree, branch, upstream, credentials.get());
    }

    @Override
    public Either<GitFailure<GitError>, List<ExecutedCommand>> pull() {
        return CliPull.pull(worktree, credentials.get());
    }

    @Override
    public Either<GitFailure<ProcessError>, List<ExecutedCommand>> fastForward(BranchName branch, BranchRef upstream) {
        return CliFetch.fastForward(worktree, branch, upstream, credentials.get());
    }

    @Override
    public Either<GitFailure<GitError>, List<ExecutedCommand>> merge(BranchName branch, MergeMode mode) {
        return CliMerge.merge(worktree, branch, mode);
    }

    @Override
    public Either<GitFailure<GitError>, List<ExecutedCommand>> cherryPick(Sha sha) {
        return CliCherryPick.cherryPick(worktree, sha);
    }

    @Override
    public Either<GitFailure<GitError>, List<ExecutedCommand>> revert(Commit commit) {
        return CliRevert.revert(worktree, commit);
    }

    @Override
    public Either<GitFailure<GitError>, List<ExecutedCommand>> continueOrAbort(
            InProgress inProgress, InProgressAction action) {
        return CliInProgress.continueOrAbort(worktree, inProgress, action);
    }

    @Override
    public Either<ProcessError, List<RemoteName>> remotes() {
        return CliRemote.remotes(worktree);
    }

    @Override
    public Either<ProcessError, Map<String, String>> remoteUrls() {
        return CliRemote.remoteUrls(worktree);
    }

    @Override
    public Either<ProcessError, Option<String>> localConfig(String key) {
        return CliConfig.get(worktree, key);
    }

    @Override
    public Either<GitFailure<ProcessError>, List<ExecutedCommand>> setLocalConfig(String key, Option<String> value) {
        return CliConfig.set(worktree, key, value);
    }

    @Override
    public Either<ProcessError, Option<BranchName>> parentBranch(BranchName branch) {
        return CliConfig.parent(worktree, branch);
    }

    @Override
    public Either<ProcessError, Map<BranchName, BaseDistance>> baseDistance(
            Map<BranchRef, Sha> tips, Option<BranchName> main) {
        return CliBaseDistance.read(worktree, tips, main);
    }

    @Override
    public Either<GitFailure<ProcessError>, List<ExecutedCommand>> fetch(RemoteName remote) {
        return CliFetch.fetch(worktree, remote, Option.of(System.getenv("GIT_SSH_COMMAND")), credentials.get());
    }

    @Override
    public Either<GitFailure<GitError>, List<ExecutedCommand>> rebase(BranchName onto) {
        return CliRebase.onto(worktree, onto);
    }

    @Override
    public Either<GitFailure<GitError>, List<ExecutedCommand>> deleteBranch(
            BranchName name, RemoteBranch remote, DeleteScope scope) {
        return CliBranch.delete(worktree, name, remote, scope, credentials.get());
    }

    @Override
    public Either<GitFailure<GitError>, List<ExecutedCommand>> deleteLocalBranch(BranchName name) {
        return CliBranch.deleteLocal(worktree, name);
    }

    @Override
    public Either<GitFailure<ProcessError>, List<ExecutedCommand>> forceDeleteLocalBranch(BranchName name) {
        return CliBranch.forceDeleteLocal(worktree, name);
    }

    @Override
    public Either<ProcessError, Integer> countOnlyOn(BranchName name) {
        return CliBranch.countOnlyOn(worktree, name);
    }

    @Override
    public Either<GitFailure<ProcessError>, List<ExecutedCommand>> renameBranch(BranchName name, BranchName newName) {
        return CliBranch.rename(worktree, name, newName);
    }

    @Override
    public Either<GitFailure<ProcessError>, List<ExecutedCommand>> deleteRemoteBranch(RemoteBranch remote) {
        return CliBranch.deleteRemote(worktree, remote, credentials.get());
    }

    @Override
    public Either<GitFailure<ProcessError>, List<ExecutedCommand>> stage(List<String> paths) {
        return CliIndex.add(worktree, paths);
    }

    @Override
    public Either<GitFailure<ProcessError>, List<ExecutedCommand>> unstageNew(List<String> paths) {
        return CliIndex.removeCached(worktree, paths);
    }

    @Override
    public Either<GitFailure<ProcessError>, List<ExecutedCommand>> unstage(List<String> paths) {
        return CliIndex.reset(worktree, paths);
    }

    @Override
    public Either<GitFailure<ProcessError>, List<ExecutedCommand>> commit(CommitMessage message) {
        return CliCommit.commit(worktree, message);
    }

    @Override
    public Either<GitFailure<ProcessError>, List<ExecutedCommand>> amendLastCommit() {
        return CliCommit.amend(worktree);
    }

    @Override
    public Either<GitFailure<GitError>, List<ExecutedCommand>> amendCommit(Sha sha) {
        return CliCommit.amend(worktree, sha);
    }

    @Override
    public Either<GitFailure<GitError>, List<ExecutedCommand>> reword(Sha sha, CommitMessage message) {
        return CliCommit.reword(worktree, sha, message);
    }

    @Override
    public Either<GitFailure<GitError>, List<ExecutedCommand>> squash(Sha sha, SquashMode mode) {
        return CliCommit.squash(worktree, sha, mode);
    }

    @Override
    public Either<GitFailure<GitError>, List<ExecutedCommand>> dropCommit(Sha sha) {
        return CliRebase.drop(worktree, sha);
    }

    @Override
    public Either<GitFailure<GitError>, List<ExecutedCommand>> moveCommits(
            BranchName from, Sha oldest, BranchName to, Option<BranchRef> onto) {
        return CliMoveCommits.move(worktree, from, oldest, to, onto);
    }

    @Override
    public Either<GitFailure<GitError>, List<ExecutedCommand>> moveCommitsTo(
            BranchName from, Sha oldest, BranchName to) {
        return CliMoveCommits.moveTo(worktree, from, oldest, to);
    }

    @Override
    public Either<GitFailure<ProcessError>, List<ExecutedCommand>> reset(ResetTarget target, ResetMode mode) {
        return CliReset.reset(worktree, target, mode);
    }

    @Override
    public Either<GitFailure<GitError>, List<ExecutedCommand>> discard(DiscardPlan plan) {
        return CliIndex.discard(worktree, plan);
    }

    @Override
    public Either<ProcessError, DiffText> commitChanges(Sha sha, DiffOptions options) {
        return CliShow.changes(worktree, sha, options);
    }

    @Override
    public Either<ProcessError, List<CommitFile>> commitFiles(Sha sha) {
        return CliShow.files(worktree, sha);
    }

    @Override
    public Either<ProcessError, DiffText> commitFileChanges(Sha sha, List<CommitFile> files, DiffOptions options) {
        return CliShow.fileChanges(worktree, sha, files, options);
    }

    @Override
    public Either<GitError, Diffs> diff(List<FileEntry> files, DiffOptions options) {
        return CliDiff.diff(worktree, files, options);
    }
}
