package net.vanfleteren.jazygit.feature.branch;

import net.vanfleteren.jazygit.feature.branch.NewBranchMsg.Requested;

import net.vanfleteren.jazygit.feature.branch.NewBranchMsg.Failed;

import net.vanfleteren.jazygit.feature.branch.NewBranchMsg.Created;

import net.vanfleteren.jazygit.feature.branch.NewBranchMsg.Confirmed;

import net.vanfleteren.jazygit.feature.branch.NewBranchMsg.Cancelled;

import net.vanfleteren.jazygit.feature.branch.BranchCmd.CreateBranch;

import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;

import java.util.Optional;

/**
 * Creating a branch.
 */
public final class NewBranchUpdate {

    private NewBranchUpdate() {
    }

    public static Next update(Model model, NewBranchMsg msg) {
        return switch (msg) {
            case Requested(String base) -> Next.of(model.withNewBranchBase(Optional.of(base)));
            case Cancelled() -> Next.of(model.withNewBranchBase(Optional.empty()));
            case Confirmed(String name) -> confirmed(model, name);
            // Like a checkout: status and branches show the new HEAD.
            case Created(String name) -> Update.refresh(model.withError(Optional.empty()));
            case Failed(String name, String message) ->
                    Next.of(model.withError(Optional.of("Creating branch " + name + " failed: " + message)));
        };
    }

    private static Next confirmed(Model model, String name) {
        String trimmed = name.strip();
        // A blank name keeps the dialog open.
        return model.newBranchBase()
                .filter(base -> !trimmed.isEmpty())
                .map(base -> Next.of(model.withNewBranchBase(Optional.empty()).withError(Optional.empty()),
                        new CreateBranch(trimmed, base)))
                .orElseGet(() -> Next.of(model));
    }
}
