package net.vanfleteren.jazygit.feature.github;

import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;

public sealed interface BrowseMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return BrowseUpdate.update(model, this);
    }

    /**
     * The user asked to open a local branch on GitHub, in the browser.
     */
    record Requested(Branch.Name branch) implements BrowseMsg {
    }

    /**
     * The user asked to open a remote branch on GitHub, in the browser.
     */
    record RemoteRequested(RemoteBranch branch) implements BrowseMsg {
    }
}
