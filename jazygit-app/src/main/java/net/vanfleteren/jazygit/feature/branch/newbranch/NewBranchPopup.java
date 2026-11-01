package net.vanfleteren.jazygit.feature.branch.newbranch;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.branch.checkout.CheckoutRequest;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.git.model.Revision;
import net.vanfleteren.jazygit.state.TextPopup;

/**
 * Asking for the name of a new branch, which starts at {@code base}. Confirming it checks the branch out where the user
 * says, in a linked worktree (ADR 68).
 *
 * @param name      the name as far as it was typed; it starts out empty, or as the remote branch is named
 * @param following the remote branch the new branch is to follow, if any
 * @param parent    the branch the new branch is made from, if it is made from one
 */
public record NewBranchPopup(Revision base, String name, Option<RemoteBranch> following, Option<BranchName> parent)
        implements TextPopup {

    public NewBranchPopup(Revision base, Option<BranchName> parent) {
        this(base, "", Option.none(), parent);
    }

    /**
     * Asking for the name of a new branch made from the local branch {@code base}.
     */
    public NewBranchPopup(BranchName base) {
        this(new BranchRef.Local(base), Option.of(base));
    }

    /**
     * How where it starts is shown to the user.
     */
    public String label() {
        return base.label();
    }

    @Override
    public String text() {
        return name;
    }

    @Override
    public NewBranchPopup withText(String text) {
        return new NewBranchPopup(base, text, following, parent);
    }

    /**
     * Asking for the name of a new branch that follows {@code remote}, starting out with the name it has there. It
     * records no parent: it is the same branch as {@code remote}, not one made from it.
     */
    public static NewBranchPopup following(RemoteBranch remote) {
        return new NewBranchPopup(remote.tracking(), remote.branch().value(), Option.of(remote), Option.none());
    }

    /**
     * The checkout of the new branch, as typed: none for a name that is no branch name, like a blank one, which keeps
     * the dialog open.
     */
    public Option<CheckoutRequest> request() {
        return BranchName.parse(name)
                .map(branch -> following
                        .<CheckoutRequest>map(remote -> new CheckoutRequest.Remote(branch, remote))
                        .getOrElse(() -> new CheckoutRequest.NewBranch(branch, base, parent)));
    }
}
