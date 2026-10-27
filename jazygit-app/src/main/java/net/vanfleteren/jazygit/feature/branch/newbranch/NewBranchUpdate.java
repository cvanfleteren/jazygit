package net.vanfleteren.jazygit.feature.branch.newbranch;

import io.vavr.control.Option;
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
                                        remote.tracking().toArg(), remote.shortName(), Option.of(remote.branch()))));
        };
    }

    private static Next atCommit(Model model, Sha sha) {
        return Update.ask(
                model,
                model.commit(sha)
                        .map(commit -> new NewBranchPopup(commit.sha().value(), commit.shortSha(), Option.none())));
    }
}
