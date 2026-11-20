package net.vanfleteren.jazygit.features.github.browse;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.repo.Branch;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.features.github.GitHubError;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.operation.OperationUpdate;

/**
 * Opening branches on GitHub, in the browser.
 */
public final class BrowseUpdate {

    private BrowseUpdate() {}

    public static Next update(Model model, BrowseMsg msg) {
        return switch (msg) {
            case BrowseMsg.Requested(BranchName name) ->
                model.repo()
                        .branch(name)
                        .map(branch -> onRemote(branch)
                                .map(remote -> OperationUpdate.start(model.clearError(), new BrowseCmd(remote)))
                                .getOrElse(() -> Next.of(model.fail(new GitHubError.NotOnRemote(name)))))
                        .getOrElse(() -> Next.of(model));
            case BrowseMsg.RemoteRequested(RemoteBranch branch) ->
                OperationUpdate.start(model.clearError(), new BrowseCmd(branch));
            case BrowseMsg.PullRequestRequested(var pullRequest) ->
                OperationUpdate.start(model.clearError(), new BrowsePullRequestCmd(pullRequest));
        };
    }

    /**
     * Where {@code branch} is on its remote: its upstream, or else the branch of the same name on {@code origin}
     * when that is there; none when it is on no remote, or its upstream is gone.
     */
    static Option<RemoteBranch> onRemote(Branch branch) {
        return branch.remoteUpstream()
                .orElse(() -> Option.when(
                        branch.hasRemote() && branch.goneUpstream().isEmpty(),
                        () -> new RemoteBranch(RemoteBranch.DEFAULT_REMOTE, branch.name())));
    }
}
