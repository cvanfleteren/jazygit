package net.vanfleteren.jazygit.features.github.browse;

import net.vanfleteren.jazygit.core.gh.model.PullRequestSummary;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.Update;

public sealed interface BrowseMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return BrowseUpdate.update(model, this);
    }

    /**
     * The user asked to open a local branch on GitHub, in the browser.
     */
    record Requested(BranchName branch) implements BrowseMsg {}

    /**
     * The user asked to open a remote branch on GitHub, in the browser.
     */
    record RemoteRequested(RemoteBranch branch) implements BrowseMsg {}

    /**
     * The user asked to open a pull request, of any repository, in the browser.
     */
    record PullRequestRequested(PullRequestSummary pullRequest) implements BrowseMsg {}
}
