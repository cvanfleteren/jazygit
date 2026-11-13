package net.vanfleteren.jazygit.feature.branch.rename;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.branch.BranchCmd;
import net.vanfleteren.jazygit.git.Git;
import net.vanfleteren.jazygit.git.model.*;
import net.vanfleteren.jazygit.git.process.GitFailure;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.operation.Operation;

/**
 * Renames {@code branch} to {@code newName}, and then {@code upstream} on its remote too, if given. The local branch
 * goes first: when renaming its upstream fails, it is renamed and still follows that, as when only it was renamed.
 */
public record RenameBranchCmd(BranchName branch, BranchName newName, Option<RemoteBranch> upstream)
        implements BranchCmd {

    public static final Operation RENAME_BRANCH = Operation.of("log.title.renameBranch", "error.renameBranch.failed");

    /**
     * Renames only the local branch {@code branch} to {@code newName}.
     */
    public RenameBranchCmd(BranchName branch, BranchName newName) {
        this(branch, newName, Option.none());
    }

    @Override
    public Operation operation() {
        return RENAME_BRANCH;
    }

    @Override
    public Option<Guard> guard() {
        return upstream.map(remote -> Guard.REMOTE_REFS);
    }

    @Override
    public boolean changesGitHub() {
        return upstream.isDefined();
    }

    @Override
    public Either<GitFailure, List<ExecutedCommand>> perform(Git git) {
        return GitFailure.sequence(
                () -> git.branches().rename(branch, newName),
                () -> upstream.map(remote -> git.remotes().renameUpstream(newName, remote))
                        .getOrElse(GitFailure::nothing));
    }
}
