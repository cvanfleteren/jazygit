package net.vanfleteren.jazygit.git;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.git.model.RepoStatus.Head;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.git.GitError.CheckoutError;
import net.vanfleteren.jazygit.git.GitError.DiffError;
import net.vanfleteren.jazygit.git.GitError.DiscardError;
import net.vanfleteren.jazygit.git.GitError.LogError;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.GitError.RepositoryUnreadable;
import net.vanfleteren.jazygit.git.GitError.RewordError;
import net.vanfleteren.jazygit.git.GitError.StashCheckoutError;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.git.model.CommitMessage;
import net.vanfleteren.jazygit.git.model.Diffs;
import net.vanfleteren.jazygit.git.model.DiscardPlan;
import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.git.model.RepoStatus;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.model.Stash;

/**
 * A {@link GitInfoProvider} for tests: an empty repository whose operations all succeed without running
 * anything. Fakes override what they need.
 */
public interface StubGitProvider extends GitInfoProvider {

    /**
     * A successful operation that ran nothing.
     */
    static <E extends GitError> Either<GitFailure<E>, List<String>> ran(String... commands) {
        return Either.right(List.of(commands));
    }

    /**
     * An operation that git refused with {@code explanation}, after running {@code command}.
     */
    static Either<GitFailure<ProcessError>, List<String>> refused(String command, String explanation) {
        return Either.left(new GitFailure<>(new GitError.CommandFailed(explanation, 1), List.of(command)));
    }

    @Override
    default String repositoryName() {
        return "repo";
    }

    @Override
    default Either<ProcessError, RepoStatus> status() {
        return Either.right(new RepoStatus(Head.onBranch("main", new Sha("aaaa")), List.empty()));
    }

    @Override
    default Either<RepositoryUnreadable, List<Branch>> branches(List<Branch> known) {
        return Either.right(List.empty());
    }

    @Override
    default Either<ProcessError, List<Stash>> stashes() {
        return Either.right(List.empty());
    }

    @Override
    default Either<RepositoryUnreadable, List<Commit>> commits() {
        return Either.right(List.empty());
    }

    @Override
    default Either<LogError, List<Commit>> log(String branch) {
        return Either.right(List.empty());
    }

    @Override
    default Either<ProcessError, String> commitChanges(Sha sha) {
        return Either.right("");
    }

    @Override
    default Either<DiffError, Diffs> diff(List<FileEntry> files) {
        return Either.right(new Diffs("", ""));
    }

    @Override
    default Either<GitFailure<CheckoutError>, List<String>> checkout(String branch) {
        return ran();
    }

    @Override
    default Either<GitFailure<StashCheckoutError>, List<String>> checkoutWithStash(String branch) {
        return ran();
    }

    @Override
    default Either<GitFailure<ProcessError>, List<String>> createBranch(String name, String startPoint) {
        return ran();
    }

    @Override
    default Either<GitFailure<ProcessError>, List<String>> deleteBranch(String name, boolean local, boolean remote) {
        return ran();
    }

    @Override
    default Either<GitFailure<ProcessError>, List<String>> rebase(String onto) {
        return ran();
    }

    @Override
    default Either<GitFailure<ProcessError>, List<String>> push(String branch, boolean forceWithLease) {
        return ran();
    }

    @Override
    default Either<GitFailure<ProcessError>, List<String>> stage(List<String> paths) {
        return ran();
    }

    @Override
    default Either<GitFailure<ProcessError>, List<String>> unstageNew(List<String> paths) {
        return ran();
    }

    @Override
    default Either<GitFailure<DiscardError>, List<String>> discard(DiscardPlan plan) {
        return ran();
    }

    @Override
    default Either<GitFailure<ProcessError>, List<String>> unstage(List<String> paths) {
        return ran();
    }

    @Override
    default Either<GitFailure<ProcessError>, List<String>> commit(CommitMessage message) {
        return ran();
    }

    @Override
    default Either<GitFailure<ProcessError>, List<String>> amendLastCommit() {
        return ran();
    }

    @Override
    default Either<GitFailure<RewordError>, List<String>> reword(Sha sha, CommitMessage message) {
        return ran();
    }

    @Override
    default Either<GitFailure<ProcessError>, List<String>> squash(Sha sha) {
        return ran();
    }

    @Override
    default Either<GitFailure<ProcessError>, List<String>> stash(String message) {
        return ran();
    }

    @Override
    default Either<GitFailure<ProcessError>, List<String>> pop(String ref) {
        return ran();
    }

    @Override
    default Either<GitFailure<ProcessError>, List<String>> drop(String ref) {
        return ran();
    }
}
