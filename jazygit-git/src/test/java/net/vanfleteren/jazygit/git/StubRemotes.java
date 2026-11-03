package net.vanfleteren.jazygit.git;

import static net.vanfleteren.jazygit.git.StubGitProvider.ran;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.CommitFile;
import net.vanfleteren.jazygit.git.model.DeleteScope;
import net.vanfleteren.jazygit.git.model.PullRequestCommits;
import net.vanfleteren.jazygit.git.model.PushMode;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.git.model.RemoteName;
import net.vanfleteren.jazygit.process.ExecutedCommand;

/**
 * {@link Remotes} for tests: no remotes, and pushing, pulling and fetching succeed without running anything. Fakes
 * override what they need.
 */
public interface StubRemotes extends Remotes {

    @Override
    default Either<ProcessError, List<RemoteName>> list() {
        return Either.right(List.empty());
    }

    @Override
    default Either<ProcessError, Map<String, String>> urls() {
        return Either.right(HashMap.empty());
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
    // A fetch changes what the stub has, as far as anything can tell.
    default Either<GitFailure<ProcessError>, Boolean> fetch(RemoteName remote) {
        return Either.right(true);
    }

    @Override
    default Either<GitFailure<ProcessError>, List<ExecutedCommand>> fastForward(BranchName branch, BranchRef upstream) {
        return ran();
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
    default Either<ProcessError, List<CommitFile>> pullRequestFiles(PullRequestCommits commits) {
        return Either.right(List.empty());
    }
}
