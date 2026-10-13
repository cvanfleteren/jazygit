package net.vanfleteren.jazygit.git;

import net.vanfleteren.jazygit.process.ExecutedCommand;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.model.DeleteScope;
import net.vanfleteren.jazygit.git.model.PushMode;
import net.vanfleteren.jazygit.git.model.RemoteBranch;

import java.nio.file.Path;
import java.time.Duration;

import static net.vanfleteren.jazygit.git.GitCommand.git;

/**
 * Deletes, pushes and pulls branches through the {@code git} command line tool, so the remote is reached with
 * the user's own configuration and credentials.
 */
public final class CliBranch {

    // Pulling fetches first, which takes a while for a big repository.
    private static final Duration PULL_TIMEOUT = Duration.ofMinutes(2);
    private static final GitCommand MERGE_ABORT = git("merge", "--abort");
    private static final GitCommand REBASE_ABORT = git("rebase", "--abort");

    private CliBranch() {
    }

    /**
     * Deletes the branch {@code name} where {@code scope} says: locally, and/or {@code remote}, the branch it is
     * pushed to. With both, the remote one goes first, so a failure leaves the local branch in place.
     */
    public static Either<GitFailure<ProcessError>, List<ExecutedCommand>> delete(Path workTree, String name,
                                                                          RemoteBranch remote, DeleteScope scope) {
        return GitFailure.sequence(
                () -> scope.remote() ? deleteRemote(workTree, remote) : GitFailure.nothing(),
                () -> scope.local() ? deleteLocal(workTree, name) : GitFailure.nothing());
    }

    /**
     * Runs {@code git branch -D <name>} in {@code workTree}.
     */
    public static Either<GitFailure<ProcessError>, List<ExecutedCommand>> deleteLocal(Path workTree, String name) {
        return GitProcess.log(workTree, git("branch", "--delete", "--force", name));
    }

    /**
     * Runs {@code git push <remote> --delete <branch>} in {@code workTree}.
     */
    public static Either<GitFailure<ProcessError>, List<ExecutedCommand>> deleteRemote(Path workTree, RemoteBranch remote) {
        return GitProcess.log(workTree, git("push", remote.remote(), "--delete", remote.branch()));
    }

    /**
     * Pushes {@code name} to {@code upstream}, the branch it tracks.
     */
    public static Either<GitFailure<ProcessError>, List<ExecutedCommand>> push(Path workTree, String name,
                                                                        RemoteBranch upstream, PushMode mode) {
        return GitProcess.log(workTree, git(List.of("push")
                .appendAll(mode == PushMode.FORCE_WITH_LEASE ? List.of("--force-with-lease") : List.empty())
                .append(upstream.remote())
                .append(refspec(name, upstream))));
    }

    /**
     * Runs {@code git push --set-upstream <remote> <name>:<branch>}: pushes {@code name} to {@code upstream},
     * which it then tracks. Git only sets the upstream when the push succeeds.
     */
    public static Either<GitFailure<ProcessError>, List<ExecutedCommand>> pushSettingUpstream(Path workTree, String name,
                                                                                   RemoteBranch upstream) {
        return GitProcess.log(workTree, git("push", "--set-upstream", upstream.remote(), refspec(name, upstream)));
    }

    /**
     * Runs {@code git pull --no-edit}: pulls the checked out branch from its upstream, merging or rebasing as
     * git's {@code pull.rebase} and {@code pull.ff} say. A merge commit gets git's own message instead of an
     * editor, and with {@code pull.rebase=interactive} the todo list is taken as it is. A pull that stops on
     * conflicts is undone again, so the repository is left as it was.
     */
    public static Either<GitFailure<ProcessError>, List<ExecutedCommand>> pull(Path workTree) {
        return GitProcess.log(workTree, git("pull", "--no-edit")
                        .withEnvironment(HashMap.of("GIT_SEQUENCE_EDITOR", ":"))
                        .withTimeout(PULL_TIMEOUT))
                .mapLeft(failure -> CliMerge.conflicted(workTree).getOrElse(true)
                        ? failure.followedBy(GitProcess.log(workTree, rebasing(workTree) ? REBASE_ABORT : MERGE_ABORT))
                        : failure);
    }

    /**
     * Whether a rebase stopped on conflicts, rather than a merge. Only asked, so not logged.
     */
    private static boolean rebasing(Path workTree) {
        // rev-parse --verify --quiet exits with 1, saying nothing, when the ref does not exist.
        return GitProcess.ask(workTree, git("rev-parse", "--verify", "--quiet", "REBASE_HEAD"))
                .map(Option::isDefined)
                .getOrElse(false);
    }

    /**
     * Runs {@code git fetch <remote> refs/heads/<upstream>:refs/heads/<name>}: fast-forwards {@code name}, which
     * is not checked out, to {@code upstream}, the branch it tracks. Git refuses when that is not a fast-forward.
     */
    public static Either<GitFailure<ProcessError>, List<ExecutedCommand>> fastForward(Path workTree, String name,
                                                                               RemoteBranch upstream) {
        return GitProcess.log(workTree,
                git("fetch", upstream.remote(), Refs.head(upstream.branch()) + ":" + Refs.head(name))
                        .withTimeout(PULL_TIMEOUT));
    }

    /**
     * What to push {@code name} as: just the name when the remote branch has it too, so the command reads as
     * one would type it.
     */
    private static String refspec(String name, RemoteBranch target) {
        return target.branch().equals(name) ? name : name + ":" + Refs.head(target.branch());
    }
}
