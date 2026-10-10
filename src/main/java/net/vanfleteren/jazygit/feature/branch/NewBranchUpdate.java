package net.vanfleteren.jazygit.feature.branch;

import java.util.List;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.feature.branch.NewBranchMsg.Requested;

import net.vanfleteren.jazygit.feature.branch.NewBranchMsg.Failed;

import net.vanfleteren.jazygit.feature.branch.NewBranchMsg.Created;

import net.vanfleteren.jazygit.feature.branch.NewBranchMsg.Confirmed;

import net.vanfleteren.jazygit.feature.branch.NewBranchMsg.Cancelled;

import net.vanfleteren.jazygit.feature.branch.BranchCmd.CreateBranch;

import net.vanfleteren.jazygit.state.CommandLog;
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
            case Requested(String base) -> Next.of(model.openPopup(new NewBranchPopup(base)));
            case Cancelled() -> Next.of(model.withoutPopup(NewBranchPopup.class));
            case Confirmed(String name) -> confirmed(model, name);
            // Like a checkout: status and branches show the new HEAD.
            case Created(String name, List<String> commands) ->
                    Update.refresh(CommandLog.append(model.withError(Optional.empty()), "log.title.newBranch", commands));
            case Failed(String name, String message) ->
                    Next.of(model.withError(Optional.of(Messages.get("error.newBranch.failed", name, message))));
        };
    }

    private static Next confirmed(Model model, String name) {
        String trimmed = name.strip();
        // A blank name keeps the dialog open.
        return model.popup(NewBranchPopup.class).map(NewBranchPopup::base)
                .filter(base -> !trimmed.isEmpty())
                .map(base -> Next.of(model.withoutPopup(NewBranchPopup.class).withError(Optional.empty()),
                        new CreateBranch(trimmed, base)))
                .orElseGet(() -> Next.of(model));
    }
}
