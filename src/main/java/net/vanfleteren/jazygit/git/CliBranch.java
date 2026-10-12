package net.vanfleteren.jazygit.git;

import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.model.DeleteScope;
import net.vanfleteren.jazygit.git.model.PushMode;
import net.vanfleteren.jazygit.git.model.RemoteBranch;

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
     * Deletes the branch {@code name} where {@code scope} says. With both, the remote one goes first, so a
     * failure leaves the local branch in place.
     */
    public static Either<GitFailure<ProcessError>, List<String>> delete(Path workTree, String name, DeleteScope scope) {
        return GitFailure.sequence(
                () -> scope.remote() ? deleteRemote(workTree, name) : GitFailure.nothing(),
                () -> scope.local() ? deleteLocal(workTree, name) : GitFailure.nothing());
    }

    /**
     * Runs {@code git branch -D <name>} in {@code workTree}.
     */
    public static Either<GitFailure<ProcessError>, List<String>> deleteLocal(Path workTree, String name) {
        return GitProcess.log(workTree, git("branch", "--delete", "--force", name));
    }

    /**
     * Runs {@code git push <remote> --delete <branch>} in {@code workTree}: deletes the branch {@code name}
     * tracks, or the one of the same name on {@code origin}.
     */
    public static Either<GitFailure<ProcessError>, List<String>> deleteRemote(Path workTree, String name) {
        return tracking(workTree, name).flatMap(tracking -> GitProcess.log(workTree,
                git("push", tracking.target().remote(), "--delete", tracking.target().branch())));
    }

    /**
     * Pushes {@code name} to the branch it tracks. A branch without an upstream is pushed to the branch of the
     * same name on {@code origin}, which becomes its upstream, as with {@code --set-upstream}.
     */
    public static Either<GitFailure<ProcessError>, List<String>> push(Path workTree, String name, PushMode mode) {
        return tracking(workTree, name).flatMap(tracking -> GitProcess.log(workTree, git(List.of("push")
                .appendAll(!tracking.hasUpstream() ? List.of("--set-upstream")
                        : mode == PushMode.FORCE_WITH_LEASE ? List.of("--force-with-lease")
                        : List.empty())
                .append(tracking.target().remote())
                .append(refspec(name, tracking.target())))));
    }

    /**
     * Runs {@code git push --set-upstream <remote> <name>:<branch>}: pushes {@code name} to {@code upstream},
     * which it then tracks. Git only sets the upstream when the push succeeds.
     */
    public static Either<GitFailure<ProcessError>, List<String>> pushSettingUpstream(Path workTree, String name,
                                                                                   RemoteBranch upstream) {
        return GitProcess.log(workTree, git("push", "--set-upstream", upstream.remote(), refspec(name, upstream)));
    }

    /**
     * What to push {@code name} as: just the name when the remote branch has it too, so the command reads as
     * one would type it.
     */
    private static String refspec(String name, RemoteBranch target) {
        return target.branch().equals(name) ? name : name + ":" + Refs.head(target.branch());
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
        GitCommand lookup = git("for-each-ref",
                "--format=%(upstream:remotename)" + GitOutput.REF_FIELD_SEPARATOR + "%(upstream:remoteref)",
                Refs.head(name));
        return GitProcess.read(workTree, lookup, raw -> Either.right(tracking(raw, name)))
                .mapLeft(error -> new GitFailure<>(error, List.of(lookup.line())));
    }

    /**
     * Parses {@code <remote name><separator><remote ref>}, which is just the separator without an upstream.
     */
    private static Tracking tracking(String raw, String name) {
        // Not stripped: the separator is whitespace too.
        return GitOutput.lines(raw).headOption()
                .flatMap(line -> GitOutput.fields(line, 2).toOption())
                .filter(fields -> !fields.head().isEmpty())
                .flatMap(fields -> GitOutput.after(fields.last(), Refs.HEADS)
                        .map(branch -> new RemoteBranch(fields.head(), branch)))
                .map(upstream -> new Tracking(true, upstream))
                .getOrElse(() -> new Tracking(false, new RemoteBranch(Refs.DEFAULT_REMOTE, name)));
    }
}
