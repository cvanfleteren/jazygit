package net.vanfleteren.jazygit.feature.branch.newbranch;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.branch.checkout.CheckoutUpdate;
import net.vanfleteren.jazygit.feature.branch.newbranch.NewBranchMsg.Requested;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.Revision;
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
            // Checking it out may ask where, in a linked worktree (ADR 68).
            case NewBranchMsg.Confirmed() ->
                model.popup(NewBranchPopup.class)
                        .flatMap(NewBranchPopup::request)
                        .map(request -> CheckoutUpdate.place(model.withoutPopup(NewBranchPopup.class), request))
                        .getOrElse(() -> Next.of(model));
            case NewBranchMsg.RequestedAtRemote(var remote) ->
                Update.ask(
                        model,
                        model.repo()
                                .remoteBranch(remote)
                                .map(known -> new NewBranchPopup(remote.tracking(), Option.of(remote.branch()))));
        };
    }

    private static Next atCommit(Model model, Sha sha) {
        return Update.ask(
                model,
                model.commit(sha).map(commit -> new NewBranchPopup(new Revision.Commit(commit.sha()), Option.none())));
    }
}
