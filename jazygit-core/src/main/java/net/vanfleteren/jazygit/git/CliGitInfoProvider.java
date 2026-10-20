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
import java.util.function.Supplier;
import net.vanfleteren.jazygit.git.GitError.*;
import net.vanfleteren.jazygit.git.model.*;
import net.vanfleteren.jazygit.process.ExecutedCommand;

/**
 * {@link GitInfoProvider} backed by a real repository, through the {@code git} command line tool only, so
 * the repository is read and changed exactly as git itself does.
 */
public final class CliGitInfoProvider implements GitInfoProvider {

    private final Path workTree;
    // Asked each time git talks to a remote.
    private final Supplier<Map<String, String>> credentials;

    private CliGitInfoProvider(Path workTree, Supplier<Map<String, String>> credentials) {
        this.workTree = workTree;
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
                                ? Either.left(new NoWorkTree(startDir))
                                : Either.narrow(GitProcess.read(
                                        startDir,
                                        GitCommand.reading("rev-parse", "--show-toplevel"),
                                        CliGitInfoProvider::path)))
                        .map(workTree -> new CliGitInfoProvider(workTree, HashMap::empty));
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
        return new CliGitInfoProvider(workTree, credentials);
    }

    @Override
    public Path workTree() {
        return workTree;
    }

    @Override
    public String repositoryName() {
        // The root directory has no name.
        return Option.of(workTree.getFileName()).map(Path::toString).getOrElse(workTree::toString);
    }

    @Override
    public Either<ProcessError, RepoStatus> status() {
        return CliStatus.read(workTree);
    }

    @Override
    public Either<ProcessError, List<Branch>> branches() {
        return CliBranch.list(workTree);
    }

    @Override
    public Either<ProcessError, List<Worktree>> worktrees() {
        return CliWorktree.list(workTree);
    }

    @Override
    public Either<GitFailure<ProcessError>, List<ExecutedCommand>> addWorktree(Path location, Branch.Name branch) {
        return CliWorktree.add(workTree, location, branch);
    }

    @Override
    public Either<GitFailure<GitError>, List<ExecutedCommand>> removeWorktree(Path location, boolean force) {
        return CliWorktree.remove(workTree, location, force);
    }

    @Override
    public Either<GitFailure<GitError>, List<ExecutedCommand>> edit(List<String> files) {
        return CliEditor.edit(workTree, files);
    }

    @Override
    public Either<GitFailure<GitError>, Tuple2<CommitMessage, List<ExecutedCommand>>> editMessage(
            CommitMessage message) {
        return CliEditor.editMessage(workTree, message);
    }

    @Override
    public Either<GitFailure<GitError>, Tuple2<String, List<ExecutedCommand>>> editText(String fileName, String text) {
        return CliEditor.editText(workTree, fileName, text);
    }

    @Override
    public Either<ProcessError, List<Stash>> stashes() {
        return CliStash.list(workTree);
    }

    @Override
    public Either<GitFailure<GitError>, List<ExecutedCommand>> drop(Stash stash) {
        return CliStash.drop(workTree, stash);
    }

    @Override
    public Either<GitFailure<GitError>, List<ExecutedCommand>> pop(Stash stash) {
        return CliStash.pop(workTree, stash);
    }

    @Override
    public Either<GitFailure<ProcessError>, List<ExecutedCommand>> stash(Option<String> message) {
        return CliStash.push(workTree, message);
    }

    @Override
    public Either<ProcessError, List<Commit>> commits() {
        return CliLog.commits(workTree);
    }

    @Override
    public Either<ProcessError, List<Commit>> search(CommitQuery query) {
        return CliLog.search(workTree, query);
    }

    @Override
    public Either<ProcessError, List<RemoteRef>> remoteBranches() {
        return CliBranch.remotes(workTree);
    }

    @Override
    public Either<GitError, List<Commit>> log(BranchRef branch) {
        return CliLog.log(workTree, branch);
    }

    @Override
    public Either<GitError, Divergence> divergence(BranchRef branch, BranchRef other) {
        return CliLog.divergence(workTree, branch, other);
    }

    @Override
    public Either<GitError, Option<String>> firstSubject(BranchRef branch, BranchRef base) {
        return CliLog.firstSubject(workTree, branch, base);
    }

    @Override
    public Either<ProcessError, Integer> countNotOn(BranchRef branch, BranchRef base, int max) {
        return CliLog.countNotOn(workTree, branch, base, max);
    }

    @Override
    public Either<GitError, CommitRange> commitRange(Branch.Name branch, Sha oldest, Option<RemoteBranch> upstream) {
        return CliLog.range(workTree, branch, oldest, upstream);
    }

    @Override
    public Either<GitFailure<GitError>, List<ExecutedCommand>> checkout(Branch.Name branch) {
        return CliCheckout.checkout(workTree, branch);
    }

    @Override
    public Either<GitFailure<GitError>, List<ExecutedCommand>> checkoutWithStash(Branch.Name branch) {
        return CliCheckout.checkoutWithStash(workTree, branch);
    }

    @Override
    public Either<GitFailure<ProcessError>, List<ExecutedCommand>> checkoutNewBranch(
            Branch.Name name, String startPoint, Option<Branch.Name> parent) {
        return CliCheckout.checkoutNewBranch(workTree, name, startPoint, parent);
    }

    @Override
    public Either<GitFailure<ProcessError>, List<ExecutedCommand>> checkoutNewTrackingBranch(
            Branch.Name name, RemoteBranch upstream) {
        return CliCheckout.checkoutNewTrackingBranch(workTree, name, upstream);
    }

    @Override
    public Either<GitFailure<ProcessError>, List<ExecutedCommand>> push(
            Branch.Name branch, RemoteBranch upstream, PushMode mode) {
        return CliPush.push(workTree, branch, upstream, mode, credentials.get());
    }

    @Override
    public Either<GitFailure<ProcessError>, List<ExecutedCommand>> pushSettingUpstream(
            Branch.Name branch, RemoteBranch upstream) {
        return CliPush.pushSettingUpstream(workTree, branch, upstream, credentials.get());
    }

    @Override
    public Either<GitFailure<GitError>, List<ExecutedCommand>> pull() {
        return CliPull.pull(workTree, credentials.get());
    }

    @Override
    public Either<GitFailure<ProcessError>, List<ExecutedCommand>> fastForward(
            Branch.Name branch, RemoteBranch upstream) {
        return CliFetch.fastForward(workTree, branch, upstream, credentials.get());
    }

    @Override
    public Either<GitFailure<GitError>, List<ExecutedCommand>> merge(Branch.Name branch, MergeMode mode) {
        return CliMerge.merge(workTree, branch, mode);
    }

    @Override
    public Either<GitFailure<GitError>, List<ExecutedCommand>> cherryPick(Sha sha) {
        return CliCherryPick.cherryPick(workTree, sha);
    }

    @Override
    public Either<GitFailure<GitError>, List<ExecutedCommand>> continueOrAbort(
            InProgress inProgress, InProgressAction action) {
        return CliInProgress.continueOrAbort(workTree, inProgress, action);
    }

    @Override
    public Either<ProcessError, List<String>> remotes() {
        return CliRemote.remotes(workTree);
    }

    @Override
    public Either<ProcessError, Map<String, String>> remoteUrls() {
        return CliRemote.remoteUrls(workTree);
    }

    @Override
    public Either<ProcessError, Option<String>> localConfig(String key) {
        return CliConfig.get(workTree, key);
    }

    @Override
    public Either<GitFailure<ProcessError>, List<ExecutedCommand>> setLocalConfig(String key, Option<String> value) {
        return CliConfig.set(workTree, key, value);
    }

    @Override
    public Either<ProcessError, Option<Branch.Name>> parentBranch(Branch.Name branch) {
        return CliConfig.parent(workTree, branch);
    }

    @Override
    public Either<GitFailure<ProcessError>, List<ExecutedCommand>> fetch(String remote) {
        return CliFetch.fetch(workTree, remote, Option.of(System.getenv("GIT_SSH_COMMAND")), credentials.get());
    }

    @Override
    public Either<GitFailure<GitError>, List<ExecutedCommand>> rebase(Branch.Name onto) {
        return CliRebase.onto(workTree, onto);
    }

    @Override
    public Either<GitFailure<ProcessError>, List<ExecutedCommand>> deleteBranch(
            Branch.Name name, RemoteBranch remote, DeleteScope scope) {
        return CliBranch.delete(workTree, name, remote, scope, credentials.get());
    }

    @Override
    public Either<GitFailure<ProcessError>, List<ExecutedCommand>> renameBranch(Branch.Name name, Branch.Name newName) {
        return CliBranch.rename(workTree, name, newName);
    }

    @Override
    public Either<GitFailure<ProcessError>, List<ExecutedCommand>> deleteRemoteBranch(RemoteBranch remote) {
        return CliBranch.deleteRemote(workTree, remote, credentials.get());
    }

    @Override
    public Either<GitFailure<ProcessError>, List<ExecutedCommand>> stage(List<String> paths) {
        return CliIndex.add(workTree, paths);
    }

    @Override
    public Either<GitFailure<ProcessError>, List<ExecutedCommand>> unstageNew(List<String> paths) {
        return CliIndex.removeCached(workTree, paths);
    }

    @Override
    public Either<GitFailure<ProcessError>, List<ExecutedCommand>> unstage(List<String> paths) {
        return CliIndex.reset(workTree, paths);
    }

    @Override
    public Either<GitFailure<ProcessError>, List<ExecutedCommand>> commit(CommitMessage message) {
        return CliCommit.commit(workTree, message);
    }

    @Override
    public Either<GitFailure<ProcessError>, List<ExecutedCommand>> amendLastCommit() {
        return CliCommit.amend(workTree);
    }

    @Override
    public Either<GitFailure<GitError>, List<ExecutedCommand>> amendCommit(Sha sha) {
        return CliCommit.amend(workTree, sha);
    }

    @Override
    public Either<GitFailure<GitError>, List<ExecutedCommand>> reword(Sha sha, CommitMessage message) {
        return CliCommit.reword(workTree, sha, message);
    }

    @Override
    public Either<GitFailure<GitError>, List<ExecutedCommand>> squash(Sha sha, SquashMode mode) {
        return CliCommit.squash(workTree, sha, mode);
    }

    @Override
    public Either<GitFailure<GitError>, List<ExecutedCommand>> dropCommit(Sha sha) {
        return CliRebase.drop(workTree, sha);
    }

    @Override
    public Either<GitFailure<GitError>, List<ExecutedCommand>> moveCommits(
            Branch.Name from, Sha oldest, Branch.Name to, Option<BranchRef> onto) {
        return CliMoveCommits.move(workTree, from, oldest, to, onto);
    }

    @Override
    public Either<GitFailure<GitError>, List<ExecutedCommand>> moveCommitsTo(
            Branch.Name from, Sha oldest, Branch.Name to) {
        return CliMoveCommits.moveTo(workTree, from, oldest, to);
    }

    @Override
    public Either<GitFailure<ProcessError>, List<ExecutedCommand>> reset(ResetTarget target, ResetMode mode) {
        return CliReset.reset(workTree, target, mode);
    }

    @Override
    public Either<GitFailure<GitError>, List<ExecutedCommand>> discard(DiscardPlan plan) {
        return CliIndex.discard(workTree, plan);
    }

    @Override
    public Either<ProcessError, DiffText> commitChanges(Sha sha, DiffOptions options) {
        return CliShow.changes(workTree, sha, options);
    }

    @Override
    public Either<GitError, Diffs> diff(List<FileEntry> files, DiffOptions options) {
        return CliDiff.diff(workTree, files, options);
    }
}
