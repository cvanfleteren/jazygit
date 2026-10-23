package net.vanfleteren.jazygit.feature.branch.newbranch;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.branch.BranchCmd;
import net.vanfleteren.jazygit.feature.branch.checkout.CheckoutCmd.CheckoutRemote;
import net.vanfleteren.jazygit.feature.branch.newbranch.NewBranchMsg.Cancelled;
import net.vanfleteren.jazygit.feature.branch.newbranch.NewBranchMsg.Confirmed;
import net.vanfleteren.jazygit.feature.branch.newbranch.NewBranchMsg.Requested;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * Creating a branch.
 */
public final class NewBranchUpdate {

    private NewBranchUpdate() {}

    public static Next update(Model model, NewBranchMsg msg) {
        return switch (msg) {
            case Requested(BranchName base) ->
                Update.ask(model, model.idleBranch(base).map(known -> new NewBranchPopup(base)));
            case NewBranchMsg.RequestedAtCommit(var sha) -> atCommit(model, sha);
            case NewBranchMsg.RequestedAtRemote(var remote) ->
                Update.ask(
                        model,
                        model.repo()
                                .remoteBranch(remote)
                                .map(known -> new NewBranchPopup(
                                        remote.trackingRef(), remote.shortName(), Option.of(remote.branch()))));
            case Cancelled() -> Next.of(model.withoutPopup(NewBranchPopup.class));
            case Confirmed() -> confirmed(model);
        };
    }

    private static Next atCommit(Model model, Sha sha) {
        return Update.ask(
                model,
                model.commit(sha)
                        .map(commit -> new NewBranchPopup(commit.sha().value(), commit.shortSha(), Option.none())));
    }

    private static Next confirmed(Model model) {
        // A name that is no branch name, like a blank one, keeps the dialog open.
        return Update.confirm(
                model,
                NewBranchPopup.class,
                popup -> BranchName.parse(popup.name())
                        .map(branch -> popup.following()
                                .<BranchCmd>map(remote -> new CheckoutRemote(branch, remote))
                                .getOrElse(() -> new NewBranchCmd(branch, popup.base(), popup.parent()))));
    }
}
