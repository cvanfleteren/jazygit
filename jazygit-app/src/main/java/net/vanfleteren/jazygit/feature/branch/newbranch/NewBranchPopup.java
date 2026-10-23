package net.vanfleteren.jazygit.feature.branch.newbranch;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.state.TextPopup;

/**
 * Asking for the name of a new branch, which starts at {@code base}, shown to the user as {@code label}.
 *
 * @param name      the name as far as it was typed; it starts out empty, or as the remote branch is named
 * @param following the remote branch the new branch is to follow, if any
 * @param parent    the branch the new branch is made from, if it is made from one
 */
public record NewBranchPopup(
        String base, String label, String name, Option<RemoteBranch> following, Option<BranchName> parent)
        implements TextPopup {

    public NewBranchPopup(String base, String label, Option<BranchName> parent) {
        this(base, label, "", Option.none(), parent);
    }

    /**
     * Asking for the name of a new branch made from the local branch {@code base}.
     */
    public NewBranchPopup(BranchName base) {
        this(base.value(), base.value(), Option.of(base));
    }

    @Override
    public String text() {
        return name;
    }

    @Override
    public NewBranchPopup withText(String text) {
        return new NewBranchPopup(base, label, text, following, parent);
    }

    /**
     * Asking for the name of a new branch that follows {@code remote}, starting out with the name it has there. It
     * records no parent: it is the same branch as {@code remote}, not one made from it.
     */
    public static NewBranchPopup following(RemoteBranch remote) {
        return new NewBranchPopup(
                remote.trackingRef(), remote.shortName(), remote.branch().value(), Option.of(remote), Option.none());
    }
}
