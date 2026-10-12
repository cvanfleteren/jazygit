package net.vanfleteren.jazygit.git;

import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.GitProcess.Output;
import net.vanfleteren.jazygit.git.model.PushMode;
import net.vanfleteren.jazygit.git.model.RemoteBranch;

import java.nio.file.Path;

import static net.vanfleteren.jazygit.git.GitCommand.git;

/**
 * Deletes and pushes branches through the {@code git} command line tool, so the remote is reached with
 * the user's own configuration and credentials.
 */
public final class CliBranch {

    private static final String HEADS = "refs/heads/";

    private CliBranch() {
    }

    /**
     * Runs {@code git branch -D <name>} in {@code workTree}.
     */
    public static Either<GitFailure<ProcessError>, List<String>> deleteLocal(Path workTree, String name) {
        return GitProcess.run(workTree, git("branch", "--delete", "--force", name)).map(Output::log);
    }

    /**
     * Runs {@code git push <remote> --delete <branch>} in {@code workTree}: deletes the branch {@code name}
     * tracks, or the one of the same name on {@code origin}.
     */
    public static Either<GitFailure<ProcessError>, List<String>> deleteRemote(Path workTree, String name) {
        return tracking(workTree, name).flatMap(tracking ->
                GitProcess.run(workTree, git("push", tracking.target().remote(), "--delete", tracking.target().branch()))
                        .map(Output::log));
    }

    /**
     * Pushes {@code name} to the branch it tracks. A branch without an upstream is pushed to the branch of the
     * same name on {@code origin}, which becomes its upstream, as with {@code --set-upstream}.
     */
    public static Either<GitFailure<ProcessError>, List<String>> push(Path workTree, String name, PushMode mode) {
        return tracking(workTree, name).flatMap(tracking -> {
            String refspec = refspec(name, tracking.target());
            GitCommand push = !tracking.hasUpstream()
                    ? git("push", "--set-upstream", tracking.target().remote(), refspec)
                    : switch (mode) {
                        case FAST_FORWARD -> git("push", tracking.target().remote(), refspec);
                        case FORCE_WITH_LEASE -> git("push", "--force-with-lease", tracking.target().remote(), refspec);
                    };
            return GitProcess.run(workTree, push).map(Output::log);
        });
    }

    /**
     * Runs {@code git push --set-upstream <remote> <name>:<branch>}: pushes {@code name} to {@code upstream},
     * which it then tracks. Git only sets the upstream when the push succeeds.
     */
    public static Either<GitFailure<ProcessError>, List<String>> pushSettingUpstream(Path workTree, String name,
                                                                                   RemoteBranch upstream) {
        return GitProcess.run(workTree, git("push", "--set-upstream", upstream.remote(), refspec(name, upstream)))
                .map(Output::log);
    }

    /**
     * What to push {@code name} as: just the name when the remote branch has it too, so the command reads as
     * one would type it.
     */
    private static String refspec(String name, RemoteBranch target) {
        return target.branch().equals(name) ? name : name + ":" + HEADS + target.branch();
    }

    /**
     * Whether a branch has an upstream, and the remote branch it is pushed to: its upstream, or the branch
     * of the same name on {@code origin}.
     */
    private record Tracking(boolean hasUpstream, RemoteBranch target) {
    }

    /**
     * Looks up how {@code name} tracks its remote. Only the command that uses this is logged.
     */
    private static Either<GitFailure<ProcessError>, Tracking> tracking(Path workTree, String name) {
        return GitProcess.run(workTree, git("for-each-ref", "--format=%(upstream:remotename)%1f%(upstream:remoteref)",
                        HEADS + name))
                .map(output -> {
                    String[] fields = output.stdout().strip().split("\u001f", -1);
                    String remote = fields[0];
                    String remoteRef = fields.length > 1 ? fields[1] : "";
                    return remote.isEmpty() || !remoteRef.startsWith(HEADS)
                            ? new Tracking(false, new RemoteBranch(CliBranches.DEFAULT_REMOTE, name))
                            : new Tracking(true, new RemoteBranch(remote, remoteRef.substring(HEADS.length())));
                });
    }
}
