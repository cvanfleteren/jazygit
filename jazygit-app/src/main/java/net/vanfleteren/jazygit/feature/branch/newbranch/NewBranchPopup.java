package net.vanfleteren.jazygit.feature.branch.newbranch;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.state.Popup;

/**
 * Asking for the name of a new branch, which starts at {@code base}, shown to the user as {@code label}.
 *
 * @param name      the name the dialog starts out with
 * @param following the remote branch the new branch is to follow, if any
 */
public record NewBranchPopup(String base, String label, String name, Option<RemoteBranch> following) implements Popup {

    public NewBranchPopup(String base, String label) {
        this(base, label, "", Option.none());
    }

    public NewBranchPopup(String base) {
        this(base, base);
    }

    /**
     * Asking for the name of a new branch that follows {@code remote}, starting out with the name it has there.
     */
    public static NewBranchPopup following(RemoteBranch remote) {
        return new NewBranchPopup(
                remote.trackingRef(), remote.shortName(), remote.branch().value(), Option.of(remote));
    }
}
