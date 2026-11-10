package net.vanfleteren.jazygit.git;

import static net.vanfleteren.jazygit.git.StubGit.ran;

import io.vavr.Tuple2;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Either;
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
    default Either<GitError, List<RemoteName>> list() {
        return Either.right(List.empty());
    }

    @Override
    default Either<GitError, Map<String, String>> urls() {
        return Either.right(HashMap.empty());
    }

    @Override
    default Either<GitFailure, List<ExecutedCommand>> push(BranchName branch, RemoteBranch upstream, PushMode mode) {
        return ran();
    }

    @Override
    default Either<GitFailure, List<ExecutedCommand>> pushSettingUpstream(BranchName branch, RemoteBranch upstream) {
        return ran();
    }

    @Override
    default Either<GitFailure, List<ExecutedCommand>> pushAll(
            RemoteName remote, List<Tuple2<BranchName, BranchName>> branches) {
        return ran();
    }

    @Override
    default Either<GitFailure, List<ExecutedCommand>> pull() {
        return ran();
    }

    @Override
    // A fetch changes what the stub has, as far as anything can tell.
    default Either<GitFailure, Boolean> fetch(RemoteName remote) {
        return Either.right(true);
    }

    @Override
    default Either<GitFailure, List<ExecutedCommand>> fetchBranches(RemoteName remote, List<BranchName> branches) {
        return ran();
    }

    @Override
    default Either<GitFailure, List<ExecutedCommand>> fastForward(BranchName branch, BranchRef upstream) {
        return ran();
    }

    @Override
    default Either<GitFailure, List<ExecutedCommand>> deleteBranch(
            BranchName name, RemoteBranch remote, DeleteScope scope) {
        return ran();
    }

    @Override
    default Either<GitFailure, List<ExecutedCommand>> deleteRemoteBranch(RemoteBranch remote) {
        return ran();
    }

    @Override
    default Either<GitFailure, List<ExecutedCommand>> renameUpstream(BranchName branch, RemoteBranch upstream) {
        return ran();
    }

    @Override
    default Either<GitError, List<CommitFile>> pullRequestFiles(PullRequestCommits commits) {
        return Either.right(List.empty());
    }
}
