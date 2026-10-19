package net.vanfleteren.jazygit.feature.branch.newbranch;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.state.TextPopup;

/**
 * Asking for the name of a new branch, which starts at {@code base}, shown to the user as {@code label}.
 *
 * @param name      the name as far as it was typed; it starts out empty, or as the remote branch is named
 * @param following the remote branch the new branch is to follow, if any
 */
public record NewBranchPopup(String base, String label, String name, Option<RemoteBranch> following)
        implements TextPopup {

    public NewBranchPopup(String base, String label) {
        this(base, label, "", Option.none());
    }

    public NewBranchPopup(String base) {
        this(base, base);
    }

    @Override
    public String text() {
        return name;
    }

    @Override
    public NewBranchPopup withText(String text) {
        return new NewBranchPopup(base, label, text, following);
    }

    /**
     * Asking for the name of a new branch that follows {@code remote}, starting out with the name it has there.
     */
    public static NewBranchPopup following(RemoteBranch remote) {
        return new NewBranchPopup(
                remote.trackingRef(), remote.shortName(), remote.branch().value(), Option.of(remote));
    }
}
