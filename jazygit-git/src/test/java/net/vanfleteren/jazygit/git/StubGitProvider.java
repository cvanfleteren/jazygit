package net.vanfleteren.jazygit.git;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Either;
import java.nio.file.Path;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.git.model.CommitFile;
import net.vanfleteren.jazygit.git.model.DeleteScope;
import net.vanfleteren.jazygit.git.model.InProgress;
import net.vanfleteren.jazygit.git.model.InProgressAction;
import net.vanfleteren.jazygit.git.model.MergeMode;
import net.vanfleteren.jazygit.git.model.PullRequestCommits;
import net.vanfleteren.jazygit.git.model.PushMode;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.git.model.RemoteName;
import net.vanfleteren.jazygit.git.model.Sha;
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
    default Path worktree() {
        return Path.of("/repos/repo");
    }

    @Override
    default Branches branches() {
        return new StubBranches() {};
    }

    @Override
    default Stashes stashes() {
        return new StubStashes() {};
    }

    /**
     * Only the main worktree, at {@link #worktree()}.
     */
    @Override
    default Worktrees worktrees() {
        Path shown = worktree();
        return new StubWorktrees() {
            @Override
            public Path shown() {
                return shown;
            }
        };
    }

    @Override
    default History history() {
        return new StubHistory() {};
    }

    @Override
    default Diffs diffs() {
        return new StubDiffs() {};
    }

    @Override
    default Either<ProcessError, List<CommitFile>> pullRequestFiles(PullRequestCommits commits) {
        return Either.right(List.empty());
    }

    @Override
    default Either<GitFailure<GitError>, List<ExecutedCommand>> deleteBranch(
            BranchName name, RemoteBranch remote, DeleteScope scope) {
        return ran();
    }

    @Override
    default Either<GitFailure<ProcessError>, List<ExecutedCommand>> deleteRemoteBranch(RemoteBranch remote) {
        return ran();
    }

    @Override
    default Either<GitFailure<GitError>, List<ExecutedCommand>> merge(BranchName branch, MergeMode mode) {
        return ran();
    }

    @Override
    default Either<ProcessError, List<RemoteName>> remotes() {
        return Either.right(List.empty());
    }

    @Override
    default Either<ProcessError, Map<String, String>> remoteUrls() {
        return Either.right(HashMap.empty());
    }

    @Override
    // A fetch changes what the stub has, as far as anything can tell.
    default Either<GitFailure<ProcessError>, Boolean> fetch(RemoteName remote) {
        return Either.right(true);
    }

    @Override
    default Either<GitFailure<GitError>, List<ExecutedCommand>> rebase(BranchName onto) {
        return ran();
    }

    @Override
    default Either<GitFailure<GitError>, List<ExecutedCommand>> push(
            BranchName branch, RemoteBranch upstream, PushMode mode) {
        return ran();
    }

    @Override
    default Either<GitFailure<ProcessError>, List<ExecutedCommand>> pushSettingUpstream(
            BranchName branch, RemoteBranch upstream) {
        return ran();
    }

    @Override
    default Either<GitFailure<GitError>, List<ExecutedCommand>> pull() {
        return ran();
    }

    @Override
    default Either<GitFailure<ProcessError>, List<ExecutedCommand>> fastForward(BranchName branch, BranchRef upstream) {
        return ran();
    }

    @Override
    default Commits commits() {
        return new StubCommits() {};
    }

    @Override
    default Either<GitFailure<GitError>, List<ExecutedCommand>> cherryPick(Sha sha) {
        return ran();
    }

    @Override
    default Either<GitFailure<GitError>, List<ExecutedCommand>> revert(Commit commit) {
        return ran();
    }

    @Override
    default Either<GitFailure<GitError>, List<ExecutedCommand>> continueOrAbort(
            InProgress inProgress, InProgressAction action) {
        return ran();
    }

    @Override
    default Config config() {
        return new StubConfig() {};
    }

    @Override
    default Index index() {
        return new StubIndex() {};
    }

    @Override
    default Editor editor() {
        return new StubEditor() {};
    }

    @Override
    default Status status() {
        return new StubStatus() {};
    }

    @Override
    default Checkout checkout() {
        return new StubCheckout() {};
    }
}
