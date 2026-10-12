package net.vanfleteren.jazygit.git;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
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
import net.vanfleteren.jazygit.git.GitError.UnknownBranch;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.git.model.CommitMessage;
import net.vanfleteren.jazygit.git.model.DeleteScope;
import net.vanfleteren.jazygit.git.model.Diffs;
import net.vanfleteren.jazygit.git.model.DiscardPlan;
import net.vanfleteren.jazygit.git.model.FileEntry;
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
        if (!Files.isDirectory(startDir)) {
            return Either.left(new NotARepository(startDir));
        }
        return Either.<OpenError, GitProcess.Output>narrow(run(startDir, git("rev-parse", "--is-bare-repository"))
                        .mapLeft(error -> notARepository(error, startDir)))
                .flatMap(bare -> bare.stdout().strip().equals("true")
                        ? Either.left(new NoWorkTree(startDir))
                        : Either.narrow(run(startDir, git("rev-parse", "--show-toplevel"))))
                .map(toplevel -> new CliGitInfoProvider(Path.of(toplevel.stdout().strip())));
    }

    private static OpenError notARepository(ProcessError error, Path startDir) {
        return error instanceof CommandFailed(String explanation, int exitCode)
                && explanation.contains("not a git repository")
                ? new NotARepository(startDir)
                : error;
    }

    private static Either<ProcessError, GitProcess.Output> run(Path dir, GitCommand command) {
        return GitProcess.run(dir, command).mapLeft(GitFailure::error);
    }

    @Override
    public String repositoryName() {
        return workTree.getFileName().toString();
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
        return CliLog.resolve(workTree, "HEAD")
                .flatMap(head -> head.fold(() -> Either.right(List.empty()), tip -> CliLog.log(workTree, tip)));
    }

    @Override
    public Either<LogError, List<Commit>> log(String branch) {
        return Either.<LogError, Option<Sha>>narrow(CliLog.resolve(workTree, "refs/heads/" + branch))
                .flatMap(tip -> tip.<LogError>toEither(() -> new UnknownBranch(branch)))
                .flatMap(tip -> Either.narrow(CliLog.log(workTree, tip)));
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
    public Either<GitFailure<ProcessError>, List<String>> rebase(String onto) {
        return CliRebase.onto(workTree, onto);
    }

    @Override
    public Either<GitFailure<ProcessError>, List<String>> deleteBranch(String name, DeleteScope scope) {
        return GitFailure.sequence(
                () -> scope.remote() ? CliBranch.deleteRemote(workTree, name) : Either.right(List.empty()),
                () -> scope.local() ? CliBranch.deleteLocal(workTree, name) : Either.right(List.empty()));
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
        return CliLog.resolve(workTree, "HEAD")
                .mapLeft(error -> new GitFailure<RewordError>(error, List.empty()))
                .flatMap(head -> head.contains(sha)
                        ? GitFailure.widen(CliIndex.reword(workTree, message))
                        : CliRebase.reword(workTree, sha, message));
    }

    @Override
    public Either<GitFailure<ProcessError>, List<String>> squash(Sha sha) {
        return CliRebase.squash(workTree, sha);
    }

    @Override
    public Either<GitFailure<DiscardError>, List<String>> discard(DiscardPlan plan) {
        return GitFailure.<DiscardError>sequence(
                () -> GitFailure.widen(CliIndex.removeAdded(workTree, plan.added())),
                () -> GitFailure.widen(CliIndex.checkoutFromHead(workTree, plan.fromHead())),
                () -> GitFailure.widen(CliIndex.checkoutFromIndex(workTree, plan.fromIndex())),
                () -> CliIndex.deleteUntracked(workTree, plan.untracked()));
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
