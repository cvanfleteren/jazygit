package net.vanfleteren.jazygit.feature.branch;

import net.vanfleteren.jazygit.feature.branch.BranchCmd.Push;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Loadable.Loaded;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;

import java.util.List;
import java.util.Optional;

/**
 * Pushing a branch.
 */
public final class PushUpdate {

    private PushUpdate() {
    }

    public static Next update(Model model, PushMsg msg) {
        return switch (msg) {
            case PushMsg.Requested(String branch) -> requested(model, branch);
            // The branches then show the new ahead/behind counts.
            case PushMsg.Done(String branch) -> Update.refresh(model.withError(Optional.empty()));
            case PushMsg.Failed(String branch, String message) ->
                    Update.refresh(model.withError(Optional.of(Messages.get("error.push.failed", branch, message))));
        };
    }

    private static Next requested(Model model, String branch) {
        boolean known = model.branches() instanceof Loaded<List<Branch>>(List<Branch> branches)
                && branches.stream().anyMatch(b -> b.name().equals(branch));
        return known ? Next.of(model.withError(Optional.empty()), new Push(branch)) : Next.of(model);
    }
}
