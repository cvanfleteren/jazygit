package net.vanfleteren.jazygit.features.branches.branch.upstream;

import net.vanfleteren.jazygit.core.repo.Branch;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.kernel.state.popup.TextPopup;

/**
 * Asking which remote branch {@code branch} is to follow.
 *
 * @param upstream the upstream as far as it was typed, as {@code <remote> <branchname>}
 */
public record ChangeUpstreamPopup(BranchName branch, String upstream) implements TextPopup {

    /**
     * Asking for {@code branch}, starting out with the branch of its own name on the remote it follows, or on the
     * default one when it follows none.
     */
    public static ChangeUpstreamPopup of(Branch branch) {
        return new ChangeUpstreamPopup(
                branch.name(), new RemoteBranch(branch.pushTarget().remote(), branch.name()).text());
    }

    @Override
    public String text() {
        return upstream;
    }

    @Override
    public ChangeUpstreamPopup withText(String text) {
        return new ChangeUpstreamPopup(branch, text);
    }
}
