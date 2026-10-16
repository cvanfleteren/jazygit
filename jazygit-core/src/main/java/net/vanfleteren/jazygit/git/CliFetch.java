package net.vanfleteren.jazygit.git;

import static net.vanfleteren.jazygit.git.GitCommand.git;

import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.nio.file.Path;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.process.ExecutedCommand;

/**
 * Fetches from the remotes through {@code git fetch}, in the background: nothing may ask the user anything.
 */
final class CliFetch {

    private CliFetch() {}

    /**
     * Runs {@code git fetch --prune <remote>}. It always prunes, whatever {@code fetch.prune} says: the
     * remote-tracking branches of branches deleted on the remote go, so git marks the local branches that
     * followed them as gone. Git's own prompts are already off; ssh would still ask for a
     * passphrase or about an unknown host on the terminal, so it runs in batch mode.
     *
     * @param environment the value of the environment variable {@code GIT_SSH_COMMAND}, if it is set
     * @param credentials environment variables that tell the credential helper which account to use
     */
    static Either<GitFailure<ProcessError>, List<ExecutedCommand>> fetch(
            Path workTree, String remote, Option<String> environment, Map<String, String> credentials) {
        return CliRemote.toRemote(workTree, git("fetch", "--quiet", "--prune", remote), environment, credentials)
                .flatMap(fetch -> GitProcess.log(workTree, fetch));
    }

    /**
     * Runs {@code git fetch <remote> refs/heads/<upstream>:refs/heads/<name>}: fast-forwards {@code name}, which
     * is not checked out, to {@code upstream}, the branch it tracks. Git refuses when that is not a fast-forward.
     */
    static Either<GitFailure<ProcessError>, List<ExecutedCommand>> fastForward(
            Path workTree, Branch.Name name, RemoteBranch upstream, Map<String, String> credentials) {
        return CliRemote.run(
                workTree,
                git("fetch", upstream.remote(), Refs.head(upstream.branch()) + ":" + Refs.head(name)),
                credentials);
    }
}
