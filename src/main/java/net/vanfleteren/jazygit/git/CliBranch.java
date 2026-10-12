package net.vanfleteren.jazygit.git;

import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.GitProcess.Output;
import net.vanfleteren.jazygit.git.model.PushMode;

import java.nio.file.Path;

import static net.vanfleteren.jazygit.git.GitCommand.git;

/**
 * Deletes and pushes branches through the {@code git} command line tool, so the remote is reached with
 * the user's own configuration and credentials.
 */
public final class CliBranch {

    private CliBranch() {
    }

    /**
     * Runs {@code git branch -D <name>} in {@code workTree}.
     */
    public static Either<GitFailure<ProcessError>, List<String>> deleteLocal(Path workTree, String name) {
        return GitProcess.run(workTree, git("branch", "--delete", "--force", name)).map(Output::log);
    }

    /**
     * Runs {@code git push <remote> --delete <name>} in {@code workTree}, where the remote is the one the
     * branch tracks, or {@code origin}.
     */
    public static Either<GitFailure<ProcessError>, List<String>> deleteRemote(Path workTree, String name) {
        return tracking(workTree, name).flatMap(tracking ->
                GitProcess.run(workTree, git("push", tracking.remote(), "--delete", name)).map(Output::log));
    }

    /**
     * Runs {@code git push <remote> <name>} in {@code workTree}, where the remote is the one the branch
     * tracks, or {@code origin}. A branch without an upstream gets one, as with {@code --set-upstream}.
     */
    public static Either<GitFailure<ProcessError>, List<String>> push(Path workTree, String name, PushMode mode) {
        return tracking(workTree, name).flatMap(tracking -> {
            GitCommand push = !tracking.hasUpstream() ? git("push", "--set-upstream", tracking.remote(), name)
                    : switch (mode) {
                        case FAST_FORWARD -> git("push", tracking.remote(), name);
                        case FORCE_WITH_LEASE -> git("push", "--force-with-lease", tracking.remote(), name);
                    };
            return GitProcess.run(workTree, push).map(Output::log);
        });
    }

    /**
     * Whether a branch has an upstream, and the remote it is pushed to.
     */
    private record Tracking(boolean hasUpstream, String remote) {
    }

    /**
     * Looks up how {@code name} tracks its remote. Only the command that uses this is logged.
     */
    private static Either<GitFailure<ProcessError>, Tracking> tracking(Path workTree, String name) {
        return GitProcess.run(workTree, git("for-each-ref", "--format=%(upstream)%1f%(upstream:remotename)",
                        "refs/heads/" + name))
                .map(output -> {
                    String[] fields = output.stdout().strip().split("\u001f", -1);
                    String remote = fields.length > 1 ? fields[1] : "";
                    return new Tracking(!fields[0].isEmpty(), remote.isEmpty() ? CliBranches.DEFAULT_REMOTE : remote);
                });
    }
}
