package net.vanfleteren.jazygit.git;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import io.vavr.control.Try;
import net.vanfleteren.jazygit.git.GitError.CheckoutError;
import net.vanfleteren.jazygit.git.GitError.CommandFailed;
import net.vanfleteren.jazygit.git.GitError.DiffError;
import net.vanfleteren.jazygit.git.GitError.DiscardError;
import net.vanfleteren.jazygit.git.GitError.LogError;
import net.vanfleteren.jazygit.git.GitError.NoWorkTree;
import net.vanfleteren.jazygit.git.GitError.NotARepository;
import net.vanfleteren.jazygit.git.GitError.OpenError;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.GitError.RewordError;
import net.vanfleteren.jazygit.git.GitError.StashCheckoutError;
import net.vanfleteren.jazygit.git.GitError.UnexpectedOutput;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.git.model.CommitMessage;
import net.vanfleteren.jazygit.git.model.DeleteScope;
import net.vanfleteren.jazygit.git.model.Diffs;
import net.vanfleteren.jazygit.git.model.DiscardPlan;
import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.git.model.MergeMode;
import net.vanfleteren.jazygit.git.model.PushMode;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.git.model.RepoStatus;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.model.Stash;

import java.nio.file.Files;
import java.nio.file.Path;

import static net.vanfleteren.jazygit.git.GitCommand.git;

/**
 * {@link GitInfoProvider} backed by a real repository, through the {@code git} command line tool only, so
 * the repository is read and changed exactly as git itself does.
 */
public final class CliGitInfoProvider implements GitInfoProvider {

    private final Path workTree;

    private CliGitInfoProvider(Path workTree) {
        this.workTree = workTree;
    }

    /**
     * Opens the repository containing {@code startDir}, searching parent directories like
     * {@code git} itself does. It fails when {@code startDir} is not inside a git repository, the
     * repository has no working tree, or git cannot be run.
     */
    public static Either<OpenError, CliGitInfoProvider> open(Path startDir) {
        return !Files.isDirectory(startDir)
                ? Either.left(new NotARepository(startDir))
                : Either.<OpenError, Boolean>narrow(GitProcess.read(startDir, git("rev-parse", "--is-bare-repository"),
                                raw -> Either.right(raw.strip().equals("true")))
                        .mapLeft(error -> notARepository(error, startDir)))
                .flatMap(bare -> bare
                        ? Either.left(new NoWorkTree(startDir))
                        : Either.narrow(GitProcess.read(startDir, git("rev-parse", "--show-toplevel"),
                        CliGitInfoProvider::path)))
                .map(CliGitInfoProvider::new);
    }

    private static Either<UnexpectedOutput, Path> path(String raw) {
        return Try.of(() -> Path.of(raw.strip())).toEither().mapLeft(e -> new UnexpectedOutput(raw));
    }

    private static OpenError notARepository(ProcessError error, Path startDir) {
        return error instanceof CommandFailed failed && failed.says("not a git repository")
                ? new NotARepository(startDir)
                : error;
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
        return CliBranches.list(workTree);
    }

    @Override
    public Either<ProcessError, List<Stash>> stashes() {
        return CliStash.list(workTree);
    }

    @Override
    public Either<GitFailure<ProcessError>, List<String>> drop(String ref) {
        return CliStash.drop(workTree, ref);
    }

    @Override
    public Either<GitFailure<ProcessError>, List<String>> pop(String ref) {
        return CliStash.pop(workTree, ref);
    }

    @Override
    public Either<GitFailure<ProcessError>, List<String>> stash(String message) {
        return CliStash.push(workTree, message);
    }

    @Override
    public Either<ProcessError, List<Commit>> commits() {
        return CliLog.commits(workTree);
    }

    @Override
    public Either<LogError, List<Commit>> log(String branch) {
        return CliLog.log(workTree, branch);
    }

    @Override
    public Either<GitFailure<CheckoutError>, List<String>> checkout(String branch) {
        return CliCheckout.checkout(workTree, branch);
    }

    @Override
    public Either<GitFailure<StashCheckoutError>, List<String>> checkoutWithStash(String branch) {
        return CliCheckout.checkoutWithStash(workTree, branch);
    }

    @Override
    public Either<GitFailure<ProcessError>, List<String>> createBranch(String name, String startPoint) {
        return CliCheckout.createBranch(workTree, name, startPoint);
    }

    @Override
    public Either<GitFailure<ProcessError>, List<String>> push(String branch, PushMode mode) {
        return CliBranch.push(workTree, branch, mode);
    }

    @Override
    public Either<GitFailure<ProcessError>, List<String>> pushSettingUpstream(String branch, RemoteBranch upstream) {
        return CliBranch.pushSettingUpstream(workTree, branch, upstream);
    }

    @Override
    public Either<GitFailure<ProcessError>, List<String>> pull() {
        return CliBranch.pull(workTree);
    }

    @Override
    public Either<GitFailure<ProcessError>, List<String>> fastForward(String branch) {
        return CliBranch.fastForward(workTree, branch);
    }

    @Override
    public Either<GitFailure<ProcessError>, List<String>> merge(String branch, MergeMode mode) {
        return CliMerge.merge(workTree, branch, mode);
    }

    @Override
    public Either<ProcessError, List<String>> remotes() {
        return CliRemote.remotes(workTree);
    }

    @Override
    public Either<GitFailure<ProcessError>, List<String>> fetch(String remote) {
        return CliRemote.fetch(workTree, remote, Option.of(System.getenv("GIT_SSH_COMMAND")));
    }

    @Override
    public Either<GitFailure<ProcessError>, List<String>> rebase(String onto) {
        return CliRebase.onto(workTree, onto);
    }

    @Override
    public Either<GitFailure<ProcessError>, List<String>> deleteBranch(String name, DeleteScope scope) {
        return CliBranch.delete(workTree, name, scope);
    }

    @Override
    public Either<GitFailure<ProcessError>, List<String>> stage(List<String> paths) {
        return CliIndex.add(workTree, paths);
    }

    @Override
    public Either<GitFailure<ProcessError>, List<String>> unstageNew(List<String> paths) {
        return CliIndex.removeCached(workTree, paths);
    }

    @Override
    public Either<GitFailure<ProcessError>, List<String>> unstage(List<String> paths) {
        return CliIndex.reset(workTree, paths);
    }

    @Override
    public Either<GitFailure<ProcessError>, List<String>> commit(CommitMessage message) {
        return CliIndex.commit(workTree, message);
    }

    @Override
    public Either<GitFailure<ProcessError>, List<String>> amendLastCommit() {
        return CliIndex.amend(workTree);
    }

    @Override
    public Either<GitFailure<RewordError>, List<String>> reword(Sha sha, CommitMessage message) {
        return CliRebase.rewordAny(workTree, sha, message);
    }

    @Override
    public Either<GitFailure<ProcessError>, List<String>> squash(Sha sha) {
        return CliRebase.squash(workTree, sha);
    }

    @Override
    public Either<GitFailure<DiscardError>, List<String>> discard(DiscardPlan plan) {
        return CliIndex.discard(workTree, plan);
    }

    @Override
    public Either<ProcessError, String> commitChanges(Sha sha) {
        return CliShow.changes(workTree, sha);
    }

    @Override
    public Either<DiffError, Diffs> diff(List<FileEntry> files) {
        return CliDiff.diff(workTree, files);
    }
}
