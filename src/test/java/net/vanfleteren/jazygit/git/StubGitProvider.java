package net.vanfleteren.jazygit.git;

import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.RemoteRef;
import net.vanfleteren.jazygit.process.ExecutedCommand;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.git.model.PushMode;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.git.model.DeleteScope;
import net.vanfleteren.jazygit.git.model.RepoStatus.Head;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.GitError.CheckoutError;
import net.vanfleteren.jazygit.git.GitError.DiffError;
import net.vanfleteren.jazygit.git.GitError.DiscardError;
import net.vanfleteren.jazygit.git.GitError.LogError;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.GitError.RewordError;
import net.vanfleteren.jazygit.git.GitError.StashCheckoutError;
import net.vanfleteren.jazygit.git.GitError.StashError;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.git.model.CommitMessage;
import net.vanfleteren.jazygit.git.model.Diffs;
import net.vanfleteren.jazygit.git.model.DiscardPlan;
import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.git.model.MergeMode;
import net.vanfleteren.jazygit.git.model.RepoStatus;
import net.vanfleteren.jazygit.git.model.DiffText;
import net.vanfleteren.jazygit.git.model.ResetMode;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.model.SquashMode;
import net.vanfleteren.jazygit.git.model.Stash;

/**
 * A {@link GitInfoProvider} for tests: an empty repository whose operations all succeed without running
 * anything. Fakes override what they need.
 */
public interface StubGitProvider extends GitInfoProvider {

    /**
     * Commands as they would have been executed, each written as its arguments separated by spaces.
     */
    static List<ExecutedCommand> executed(String... lines) {
        return List.of(lines).map(line -> new ExecutedCommand(List.of(line.split(" "))));
    }

    /**
     * A successful operation that ran {@code commands}, or nothing.
     */
    static <E extends GitError> Either<GitFailure<E>, List<ExecutedCommand>> ran(String... commands) {
        return Either.right(executed(commands));
    }

    /**
     * An operation that git refused with {@code explanation}, after running {@code command}.
     */
    static Either<GitFailure<ProcessError>, List<ExecutedCommand>> refused(String command, String explanation) {
        return Either.left(new GitFailure<>(new GitError.CommandFailed(explanation, 1), executed(command)));
    }

    @Override
    default String repositoryName() {
        return "repo";
    }

    @Override
    default Either<ProcessError, RepoStatus> status() {
        return Either.right(new RepoStatus(Head.onBranch(new Branch.Name("main"), new Sha("aaaa")), List.empty()));
    }

    @Override
    default Either<ProcessError, List<Branch>> branches() {
        return Either.right(List.empty());
    }

    @Override
    default Either<ProcessError, List<Stash>> stashes() {
        return Either.right(List.empty());
    }

    @Override
    default Either<ProcessError, List<Commit>> commits() {
        return Either.right(List.empty());
    }

    @Override
    default Either<ProcessError, List<RemoteRef>> remoteBranches() {
        return Either.right(List.empty());
    }

    @Override
    default Either<LogError, List<Commit>> log(BranchRef branch) {
        return Either.right(List.empty());
    }

    @Override
    default Either<ProcessError, DiffText> commitChanges(Sha sha) {
        return Either.right(DiffText.of(""));
    }

    @Override
    default Either<DiffError, Diffs> diff(List<FileEntry> files) {
        return Either.right(new Diffs("", ""));
    }

    @Override
    default Either<GitFailure<CheckoutError>, List<ExecutedCommand>> checkout(Branch.Name branch) {
        return ran();
    }

    @Override
    default Either<GitFailure<StashCheckoutError>, List<ExecutedCommand>> checkoutWithStash(Branch.Name branch) {
        return ran();
    }

    @Override
    default Either<GitFailure<ProcessError>, List<ExecutedCommand>> createBranch(Branch.Name name, String startPoint) {
        return ran();
    }

    @Override
    default Either<GitFailure<ProcessError>, List<ExecutedCommand>> createTrackingBranch(Branch.Name name,
                                                                                        RemoteBranch upstream) {
        return ran();
    }

    @Override
    default Either<GitFailure<ProcessError>, List<ExecutedCommand>> deleteBranch(Branch.Name name, RemoteBranch remote, DeleteScope scope) {
        return ran();
    }

    @Override
    default Either<GitFailure<ProcessError>, List<ExecutedCommand>> merge(Branch.Name branch, MergeMode mode) {
        return ran();
    }

    @Override
    default Either<ProcessError, List<String>> remotes() {
        return Either.right(List.empty());
    }

    @Override
    default Either<GitFailure<ProcessError>, List<ExecutedCommand>> fetch(String remote) {
        return ran();
    }

    @Override
    default Either<GitFailure<ProcessError>, List<ExecutedCommand>> rebase(Branch.Name onto) {
        return ran();
    }

    @Override
    default Either<GitFailure<ProcessError>, List<ExecutedCommand>> push(Branch.Name branch, RemoteBranch upstream, PushMode mode) {
        return ran();
    }

    @Override
    default Either<GitFailure<ProcessError>, List<ExecutedCommand>> pushSettingUpstream(Branch.Name branch, RemoteBranch upstream) {
        return ran();
    }

    @Override
    default Either<GitFailure<ProcessError>, List<ExecutedCommand>> pull() {
        return ran();
    }

    @Override
    default Either<GitFailure<ProcessError>, List<ExecutedCommand>> fastForward(Branch.Name branch, RemoteBranch upstream) {
        return ran();
    }

    @Override
    default Either<GitFailure<ProcessError>, List<ExecutedCommand>> stage(List<String> paths) {
        return ran();
    }

    @Override
    default Either<GitFailure<ProcessError>, List<ExecutedCommand>> unstageNew(List<String> paths) {
        return ran();
    }

    @Override
    default Either<GitFailure<DiscardError>, List<ExecutedCommand>> discard(DiscardPlan plan) {
        return ran();
    }

    @Override
    default Either<GitFailure<ProcessError>, List<ExecutedCommand>> unstage(List<String> paths) {
        return ran();
    }

    @Override
    default Either<GitFailure<ProcessError>, List<ExecutedCommand>> commit(CommitMessage message) {
        return ran();
    }

    @Override
    default Either<GitFailure<ProcessError>, List<ExecutedCommand>> amendLastCommit() {
        return ran();
    }

    @Override
    default Either<GitFailure<RewordError>, List<ExecutedCommand>> reword(Sha sha, CommitMessage message) {
        return ran();
    }

    @Override
    default Either<GitFailure<ProcessError>, List<ExecutedCommand>> squash(Sha sha, SquashMode mode) {
        return ran();
    }

    @Override
    default Either<GitFailure<ProcessError>, List<ExecutedCommand>> reset(Sha sha, ResetMode mode) {
        return ran();
    }

    @Override
    default Either<GitFailure<ProcessError>, List<ExecutedCommand>> stash(Option<String> message) {
        return ran();
    }

    @Override
    default Either<GitFailure<StashError>, List<ExecutedCommand>> pop(Stash stash) {
        return ran();
    }

    @Override
    default Either<GitFailure<StashError>, List<ExecutedCommand>> drop(Stash stash) {
        return ran();
    }
}
