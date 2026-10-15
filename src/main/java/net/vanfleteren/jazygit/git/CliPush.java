package net.vanfleteren.jazygit.git;

import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.process.ExecutedCommand;

import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.model.PushMode;
import net.vanfleteren.jazygit.git.model.RemoteBranch;

import java.nio.file.Path;

import static net.vanfleteren.jazygit.git.GitCommand.git;

/**
 * Pushes branches through {@code git push}, so the remote is reached with the user's own configuration and
 * credentials.
 */
public final class CliPush {

    private CliPush() {
    }

    /**
     * Pushes {@code name} to {@code upstream}, the branch it tracks.
     */
    public static Either<GitFailure<ProcessError>, List<ExecutedCommand>> push(Path workTree, Branch.Name name,
                                                                        RemoteBranch upstream, PushMode mode,
                                                                        Map<String, String> credentials) {
        return CliRemote.run(workTree, git(List.of("push")
                .appendAll(mode == PushMode.FORCE_WITH_LEASE ? List.of("--force-with-lease") : List.empty())
                .append(upstream.remote())
                .append(refspec(name, upstream))), credentials);
    }

    /**
     * Runs {@code git push --set-upstream <remote> <name>:<branch>}: pushes {@code name} to {@code upstream},
     * which it then tracks. Git only sets the upstream when the push succeeds.
     */
    public static Either<GitFailure<ProcessError>, List<ExecutedCommand>> pushSettingUpstream(Path workTree, Branch.Name name,
                                                                                   RemoteBranch upstream,
                                                                                   Map<String, String> credentials) {
        return CliRemote.run(workTree, git("push", "--set-upstream", upstream.remote(), refspec(name, upstream)),
                credentials);
    }

    /**
     * What to push {@code name} as: just the name when the remote branch has it too, so the command reads as
     * one would type it.
     */
    private static String refspec(Branch.Name name, RemoteBranch target) {
        return target.branch().equals(name) ? name.value() : name.value() + ":" + Refs.head(target.branch());
    }
}
