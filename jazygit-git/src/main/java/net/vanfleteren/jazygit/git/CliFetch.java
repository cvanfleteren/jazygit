package net.vanfleteren.jazygit.git;

import static net.vanfleteren.jazygit.git.GitCommand.git;

import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.nio.file.Path;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.git.model.RemoteName;
import net.vanfleteren.jazygit.process.ExecutedCommand;

/**
 * Fetches from the remotes through {@code git fetch}, in the background: nothing may ask the user anything.
 */
final class CliFetch {

    private CliFetch() {}

    /**
     * Runs {@code git fetch --prune --no-write-fetch-head <remote>}. It always prunes, whatever {@code fetch.prune}
     * says: the remote-tracking branches of branches deleted on the remote go, so git marks the local branches that
     * followed them as gone. It leaves {@code FETCH_HEAD} alone, which nothing reads after it, but a pull does
     * after its own fetch. Git's own prompts are already off; ssh would still ask for a
     * passphrase or about an unknown host on the terminal, so it runs in batch mode.
     *
     * @param environment the value of the environment variable {@code GIT_SSH_COMMAND}, if it is set
     * @param credentials environment variables that tell the credential helper which account to use
     */
    static Either<GitFailure<ProcessError>, List<ExecutedCommand>> fetch(
            Path worktree, RemoteName remote, Option<String> environment, Map<String, String> credentials) {
        return CliRemote.toRemote(
                        worktree,
                        git("fetch", "--quiet", "--prune", "--no-write-fetch-head", GitArgs.remote(remote)),
                        environment,
                        credentials)
                .flatMap(fetch -> GitProcess.log(worktree, fetch));
    }

    /**
     * Runs {@code git fetch <remote> refs/heads/<upstream>:refs/heads/<name>}: fast-forwards {@code name}, which
     * is not checked out, to {@code upstream}, the branch it tracks; the remote is {@code .} for a local one. Git
     * refuses when that is not a fast-forward.
     */
    static Either<GitFailure<ProcessError>, List<ExecutedCommand>> fastForward(
            Path worktree, BranchName name, BranchRef upstream, Map<String, String> credentials) {
        return switch (upstream) {
            case BranchRef.Local(BranchName branch) ->
                fastForward(worktree, name, CliRemote.THIS_REPOSITORY, branch, credentials);
            case BranchRef.Remote(RemoteBranch remote) ->
                fastForward(worktree, name, remote.remote(), remote.branch(), credentials);
        };
    }

    private static Either<GitFailure<ProcessError>, List<ExecutedCommand>> fastForward(
            Path worktree, BranchName name, RemoteName remote, BranchName branch, Map<String, String> credentials) {
        return CliRemote.run(
                worktree,
                git(
                        "fetch",
                        GitArgs.remote(remote),
                        GitArgs.refspec(new BranchRef.Local(branch), new BranchRef.Local(name))),
                credentials);
    }
}
