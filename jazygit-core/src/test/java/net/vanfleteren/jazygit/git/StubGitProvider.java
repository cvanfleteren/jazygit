package net.vanfleteren.jazygit.git;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.nio.file.Path;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.git.model.CommitMessage;
import net.vanfleteren.jazygit.git.model.CommitQuery;
import net.vanfleteren.jazygit.git.model.CommitRange;
import net.vanfleteren.jazygit.git.model.DeleteScope;
import net.vanfleteren.jazygit.git.model.DiffOptions;
import net.vanfleteren.jazygit.git.model.DiffText;
import net.vanfleteren.jazygit.git.model.Diffs;
import net.vanfleteren.jazygit.git.model.DiscardPlan;
import net.vanfleteren.jazygit.git.model.Divergence;
import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.git.model.InProgress;
import net.vanfleteren.jazygit.git.model.InProgressAction;
import net.vanfleteren.jazygit.git.model.MergeMode;
import net.vanfleteren.jazygit.git.model.PushMode;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.git.model.RemoteRef;
import net.vanfleteren.jazygit.git.model.RepoStatus;
import net.vanfleteren.jazygit.git.model.RepoStatus.Head;
import net.vanfleteren.jazygit.git.model.ResetMode;
import net.vanfleteren.jazygit.git.model.ResetTarget;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.model.SquashMode;
import net.vanfleteren.jazygit.git.model.Stash;
import net.vanfleteren.jazygit.git.model.Worktree;
import net.vanfleteren.jazygit.process.ExecutedCommand;

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
    default Path workTree() {
        return Path.of("/repos/repo");
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

    /**
     * Only the main worktree, which is the one shown.
     */
    @Override
    default Either<ProcessError, List<Worktree>> worktrees() {
        return Either.right(List.of(new Worktree(
                workTree(), Option.of(new Sha("aaaa")), Option.of(new Branch.Name("main")), true, true, false, false)));
    }

    @Override
    default Either<GitFailure<ProcessError>, List<ExecutedCommand>> addWorktree(Path location, Branch.Name branch) {
        return ran();
    }

    @Override
    default Either<GitFailure<GitError>, List<ExecutedCommand>> removeWorktree(Path location, boolean force) {
        return ran();
    }

    @Override
    default Either<GitFailure<GitError>, List<ExecutedCommand>> edit(List<String> files) {
        return ran();
    }

    /**
     * Saves the text as it is.
     */
    @Override
    default Either<GitFailure<GitError>, Tuple2<String, List<ExecutedCommand>>> editText(String fileName, String text) {
        return Either.right(Tuple.of(text, List.empty()));
    }

    /**
     * Saves the message as it is.
     */
    @Override
    default Either<GitFailure<GitError>, Tuple2<CommitMessage, List<ExecutedCommand>>> editMessage(
            CommitMessage message) {
        return Either.right(Tuple.of(message, List.empty()));
    }

    @Override
    default Either<ProcessError, List<Commit>> commits() {
        return Either.right(List.empty());
    }

    @Override
    default Either<ProcessError, List<Commit>> search(CommitQuery query) {
        return Either.right(List.empty());
    }

    @Override
    default Either<ProcessError, List<RemoteRef>> remoteBranches() {
        return Either.right(List.empty());
    }

    @Override
    default Either<GitError, List<Commit>> log(BranchRef branch) {
        return Either.right(List.empty());
    }

    @Override
    default Either<GitError, Divergence> divergence(BranchRef branch, BranchRef other) {
        return Either.right(new Divergence(List.empty(), 0, List.empty(), 0));
    }

    @Override
    default Either<GitError, Option<String>> firstSubject(BranchRef branch, BranchRef base) {
        return Either.right(Option.none());
    }

    @Override
    default Either<ProcessError, Integer> countNotOn(BranchRef branch, BranchRef base, int max) {
        return Either.right(0);
    }

    @Override
    default Either<GitError, CommitRange> commitRange(Branch.Name branch, Sha oldest, Option<RemoteBranch> upstream) {
        return Either.right(new CommitRange(1, 0, 0));
    }

    @Override
    default Either<ProcessError, DiffText> commitChanges(Sha sha, DiffOptions options) {
        return Either.right(DiffText.of(""));
    }

    @Override
    default Either<GitError, Diffs> diff(List<FileEntry> files, DiffOptions options) {
        return Either.right(new Diffs("", ""));
    }

    @Override
    default Either<GitFailure<GitError>, List<ExecutedCommand>> checkout(Branch.Name branch) {
        return ran();
    }

    @Override
    default Either<GitFailure<GitError>, List<ExecutedCommand>> checkoutWithStash(Branch.Name branch) {
        return ran();
    }

    @Override
    default Either<GitFailure<ProcessError>, List<ExecutedCommand>> checkoutNewBranch(
            Branch.Name name, String startPoint, Option<Branch.Name> parent) {
        return ran();
    }

    @Override
    default Either<GitFailure<ProcessError>, List<ExecutedCommand>> checkoutNewTrackingBranch(
            Branch.Name name, RemoteBranch upstream) {
        return ran();
    }

    @Override
    default Either<GitFailure<ProcessError>, List<ExecutedCommand>> deleteBranch(
            Branch.Name name, RemoteBranch remote, DeleteScope scope) {
        return ran();
    }

    @Override
    default Either<GitFailure<ProcessError>, List<ExecutedCommand>> renameBranch(
            Branch.Name name, Branch.Name newName) {
        return ran();
    }

    @Override
    default Either<GitFailure<ProcessError>, List<ExecutedCommand>> deleteRemoteBranch(RemoteBranch remote) {
        return ran();
    }

    @Override
    default Either<GitFailure<GitError>, List<ExecutedCommand>> merge(Branch.Name branch, MergeMode mode) {
        return ran();
    }

    @Override
    default Either<ProcessError, List<String>> remotes() {
        return Either.right(List.empty());
    }

    @Override
    default Either<ProcessError, Map<String, String>> remoteUrls() {
        return Either.right(HashMap.empty());
    }

    @Override
    default Either<ProcessError, Option<String>> localConfig(String key) {
        return Either.right(Option.none());
    }

    @Override
    default Either<ProcessError, Option<Branch.Name>> parentBranch(Branch.Name branch) {
        return Either.right(Option.none());
    }

    @Override
    default Either<GitFailure<ProcessError>, List<ExecutedCommand>> setLocalConfig(String key, Option<String> value) {
        return ran();
    }

    @Override
    default Either<GitFailure<ProcessError>, List<ExecutedCommand>> fetch(String remote) {
        return ran();
    }

    @Override
    default Either<GitFailure<GitError>, List<ExecutedCommand>> rebase(Branch.Name onto) {
        return ran();
    }

    @Override
    default Either<GitFailure<ProcessError>, List<ExecutedCommand>> push(
            Branch.Name branch, RemoteBranch upstream, PushMode mode) {
        return ran();
    }

    @Override
    default Either<GitFailure<ProcessError>, List<ExecutedCommand>> pushSettingUpstream(
            Branch.Name branch, RemoteBranch upstream) {
        return ran();
    }

    @Override
    default Either<GitFailure<GitError>, List<ExecutedCommand>> pull() {
        return ran();
    }

    @Override
    default Either<GitFailure<ProcessError>, List<ExecutedCommand>> fastForward(
            Branch.Name branch, RemoteBranch upstream) {
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
    default Either<GitFailure<GitError>, List<ExecutedCommand>> discard(DiscardPlan plan) {
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
    default Either<GitFailure<GitError>, List<ExecutedCommand>> amendCommit(Sha sha) {
        return ran();
    }

    @Override
    default Either<GitFailure<GitError>, List<ExecutedCommand>> reword(Sha sha, CommitMessage message) {
        return ran();
    }

    @Override
    default Either<GitFailure<GitError>, List<ExecutedCommand>> squash(Sha sha, SquashMode mode) {
        return ran();
    }

    @Override
    default Either<GitFailure<GitError>, List<ExecutedCommand>> dropCommit(Sha sha) {
        return ran();
    }

    @Override
    default Either<GitFailure<GitError>, List<ExecutedCommand>> moveCommits(
            Branch.Name from, Sha oldest, Branch.Name to, Option<BranchRef> onto) {
        return ran();
    }

    @Override
    default Either<GitFailure<GitError>, List<ExecutedCommand>> moveCommitsTo(
            Branch.Name from, Sha oldest, Branch.Name to) {
        return ran();
    }

    @Override
    default Either<GitFailure<ProcessError>, List<ExecutedCommand>> reset(ResetTarget target, ResetMode mode) {
        return ran();
    }

    @Override
    default Either<GitFailure<GitError>, List<ExecutedCommand>> cherryPick(Sha sha) {
        return ran();
    }

    @Override
    default Either<GitFailure<GitError>, List<ExecutedCommand>> continueOrAbort(
            InProgress inProgress, InProgressAction action) {
        return ran();
    }

    @Override
    default Either<GitFailure<ProcessError>, List<ExecutedCommand>> stash(Option<String> message) {
        return ran();
    }

    @Override
    default Either<GitFailure<GitError>, List<ExecutedCommand>> pop(Stash stash) {
        return ran();
    }

    @Override
    default Either<GitFailure<GitError>, List<ExecutedCommand>> drop(Stash stash) {
        return ran();
    }
}
