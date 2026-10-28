package net.vanfleteren.jazygit.git;

import static net.vanfleteren.jazygit.git.GitCommand.git;

import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.nio.file.Path;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.GitError.PushRefused;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.PushDefault;
import net.vanfleteren.jazygit.git.model.PushMode;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.process.ExecutedCommand;

/**
 * Pushes branches through {@code git push}, so the remote is reached with the user's own configuration and
 * credentials.
 */
final class CliPush {

    private static final String PUSH_DEFAULT = "push.default";
    private static final String SIMPLE = "simple";

    private CliPush() {}

    /**
     * Pushes {@code name}, which tracks {@code upstream}, where {@code push.default} says: to {@code upstream}'s
     * remote, on the branch {@link #target} names.
     *
     * @return the command lines that were run, or why git failed; {@link PushRefused} when {@code push.default}
     *     pushes the branch nowhere, or has a value git does not know, before anything is pushed
     */
    static Either<GitFailure<GitError>, List<ExecutedCommand>> push(
            Path worktree, BranchName name, RemoteBranch upstream, PushMode mode, Map<String, String> credentials) {
        return CliConfig.effective(worktree, PUSH_DEFAULT)
                .mapLeft(error -> new GitFailure<GitError>(error, List.empty()))
                .map(setting -> setting.getOrElse(SIMPLE))
                .flatMap(setting -> PushDefault.parse(setting)
                        .flatMap(pushDefault -> target(pushDefault, name, upstream))
                        .toEither(() -> new GitFailure<GitError>(new PushRefused(setting, upstream), List.empty())))
                .flatMap(target -> GitFailure.widen(CliRemote.run(
                        worktree,
                        git(List.of("push")
                                .appendAll(
                                        mode == PushMode.FORCE_WITH_LEASE
                                                ? List.of("--force-with-lease")
                                                : List.empty())
                                .append(target.remote())
                                .append(refspec(name, target))),
                        credentials)));
    }

    /**
     * Where {@code push.default} pushes {@code name}, which tracks {@code upstream}, as {@code git push} would push
     * it when checked out: on {@code upstream}'s remote, to the branch of the same name, or to {@code upstream} for
     * {@code upstream}; none for {@code nothing}, and for {@code simple} when {@code upstream} has another name.
     * {@code matching}, which pushes every branch of the same name, pushes this one to its name.
     */
    static Option<RemoteBranch> target(PushDefault pushDefault, BranchName name, RemoteBranch upstream) {
        RemoteBranch sameName = new RemoteBranch(upstream.remote(), name);
        return switch (pushDefault) {
            case CURRENT, MATCHING -> Option.of(sameName);
            case UPSTREAM -> Option.of(upstream);
            case NOTHING -> Option.none();
            case SIMPLE -> Option.when(upstream.branch().equals(name), sameName);
        };
    }

    /**
     * Runs {@code git push --set-upstream <remote> <name>:<branch>}: pushes {@code name} to {@code upstream},
     * which it then tracks. Git only sets the upstream when the push succeeds.
     */
    static Either<GitFailure<ProcessError>, List<ExecutedCommand>> pushSettingUpstream(
            Path worktree, BranchName name, RemoteBranch upstream, Map<String, String> credentials) {
        return CliRemote.run(
                worktree, git("push", "--set-upstream", upstream.remote(), refspec(name, upstream)), credentials);
    }

    /**
     * What to push {@code name} as: just the name when the remote branch has it too, so the command reads as
     * one would type it.
     */
    private static String refspec(BranchName name, RemoteBranch target) {
        return target.branch().equals(name) ? name.value() : name.value() + ":" + Refs.head(target.branch());
    }
}
