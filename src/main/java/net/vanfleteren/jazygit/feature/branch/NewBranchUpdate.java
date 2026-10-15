package net.vanfleteren.jazygit.feature.branch;

import net.vanfleteren.jazygit.feature.branch.BranchCmd.CheckoutRemote;
import net.vanfleteren.jazygit.feature.branch.BranchCmd.CreateBranch;
import net.vanfleteren.jazygit.feature.branch.NewBranchMsg.Cancelled;
import net.vanfleteren.jazygit.feature.branch.NewBranchMsg.Confirmed;
import net.vanfleteren.jazygit.feature.branch.NewBranchMsg.Requested;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * Creating a branch.
 */
public final class NewBranchUpdate {

    private NewBranchUpdate() {
    }

    public static Next update(Model model, NewBranchMsg msg) {
        return switch (msg) {
            case Requested(Branch.Name base) ->
                    Update.ask(model, model.idleBranch(base).map(known -> new NewBranchPopup(base.value())));
            case NewBranchMsg.RequestedAtCommit(var sha) -> atCommit(model, sha);
            case NewBranchMsg.RequestedAtRemote(var remote) -> Update.ask(model, model.remoteBranch(remote)
                    .map(known -> new NewBranchPopup(remote.trackingRef(), remote.shortName())));
            case Cancelled() -> Next.of(model.withoutPopup(NewBranchPopup.class));
            case Confirmed(String name) -> confirmed(model, name);
        };
    }

    private static Next atCommit(Model model, Sha sha) {
        return Update.ask(model, model.commit(sha).map(commit -> new NewBranchPopup(commit.sha().value(), commit.shortSha())));
    }

    private static Next confirmed(Model model, String name) {
        // A name that is no branch name, like a blank one, keeps the dialog open.
        return Update.confirm(model, NewBranchPopup.class, popup -> Branch.Name.parse(name)
                .map(branch -> popup.following()
                        .<BranchCmd>map(remote -> new CheckoutRemote(branch, remote))
                        .getOrElse(() -> new CreateBranch(branch, popup.base()))));
    }
}
