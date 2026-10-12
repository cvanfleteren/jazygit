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
     * Runs {@code git push <remote> --delete <name>} in {@code workTree}.
     */
    public static Either<GitFailure<ProcessError>, List<String>> deleteRemote(Path workTree, String remote,
                                                                            String name) {
        return GitProcess.run(workTree, git("push", remote, "--delete", name)).map(Output::log);
    }

    /**
     * Runs {@code git push <remote> <name>} in {@code workTree}. A branch without an upstream gets one,
     * as with {@code --set-upstream}.
     *
     * @param hasUpstream whether the branch already tracks a remote branch
     */
    public static Either<GitFailure<ProcessError>, List<String>> push(Path workTree, String remote, String name,
                                                                    boolean hasUpstream, PushMode mode) {
        GitCommand push = !hasUpstream ? git("push", "--set-upstream", remote, name)
                : switch (mode) {
                    case FAST_FORWARD -> git("push", remote, name);
                    case FORCE_WITH_LEASE -> git("push", "--force-with-lease", remote, name);
                };
        return GitProcess.run(workTree, push).map(Output::log);
    }
}
