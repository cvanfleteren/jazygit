package net.vanfleteren.jazygit.git;

import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.GitProcess.Output;

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
     * @param hasUpstream    whether the branch already tracks a remote branch
     * @param forceWithLease also overwrite a remote branch that has diverged, unless somebody
     *                       pushed to it since the last fetch
     */
    public static Either<GitFailure<ProcessError>, List<String>> push(Path workTree, String remote, String name,
                                                                    boolean hasUpstream, boolean forceWithLease) {
        GitCommand push = !hasUpstream ? git("push", "--set-upstream", remote, name)
                : forceWithLease ? git("push", "--force-with-lease", remote, name)
                : git("push", remote, name);
        return GitProcess.run(workTree, push).map(Output::log);
    }
}
