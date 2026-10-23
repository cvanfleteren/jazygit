package net.vanfleteren.jazygit.feature.branch.push;

import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.state.TextPopup;

/**
 * Asking where to push {@code branch}, which has no upstream yet.
 *
 * @param upstream the upstream as far as it was typed; it starts out as the branch of the same name on the default
 *                 remote
 */
public record SetUpstreamPopup(BranchName branch, String upstream) implements TextPopup {

    public SetUpstreamPopup(BranchName branch) {
        this(branch, new RemoteBranch(RemoteBranch.DEFAULT_REMOTE, branch).text());
    }

    @Override
    public String text() {
        return upstream;
    }

    @Override
    public SetUpstreamPopup withText(String text) {
        return new SetUpstreamPopup(branch, text);
    }
}
