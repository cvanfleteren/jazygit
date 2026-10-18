package net.vanfleteren.jazygit.feature.github.browse;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.git.model.Upstream;
import net.vanfleteren.jazygit.state.AppError;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * Opening branches on GitHub, in the browser.
 */
public final class BrowseUpdate {

    private BrowseUpdate() {}

    public static Next update(Model model, BrowseMsg msg) {
        return switch (msg) {
            case BrowseMsg.Requested(Branch.Name name) ->
                model.repo()
                        .branch(name)
                        .map(branch -> onRemote(branch)
                                .map(remote -> Update.start(model.clearError(), new BrowseCmd(remote)))
                                .getOrElse(() -> Next.of(model.fail(new AppError.NotOnRemote(name)))))
                        .getOrElse(() -> Next.of(model));
            case BrowseMsg.RemoteRequested(RemoteBranch branch) ->
                Update.start(model.clearError(), new BrowseCmd(branch));
            case BrowseMsg.PullRequestRequested(var pullRequest) ->
                Update.start(model.clearError(), new BrowsePullRequestCmd(pullRequest));
        };
    }

    /**
     * Where {@code branch} is on its remote: its upstream, or else the branch of the same name on {@code origin}
     * when that is there; none when it is on no remote, or its upstream is gone.
     */
    static Option<RemoteBranch> onRemote(Branch branch) {
        return branch.remoteUpstream()
                .map(Upstream::branch)
                .orElse(() -> Option.when(
                        branch.hasRemote() && branch.goneUpstream().isEmpty(),
                        () -> new RemoteBranch(RemoteBranch.DEFAULT_REMOTE, branch.name())));
    }
}
