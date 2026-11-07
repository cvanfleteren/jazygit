package net.vanfleteren.jazygit.feature.branch.rename;

import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.state.Popup;

/**
 * Asking whether renaming {@code branch} to {@code newName} renames {@code upstream}, the branch of the same name on
 * a remote it follows, too.
 */
public record RenameUpstreamPopup(BranchName branch, BranchName newName, RemoteBranch upstream) implements Popup {

    /**
     * The branch {@code upstream} becomes when it is renamed too.
     */
    public RemoteBranch renamed() {
        return new RemoteBranch(upstream.remote(), newName);
    }
}
