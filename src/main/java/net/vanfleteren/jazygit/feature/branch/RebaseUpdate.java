package net.vanfleteren.jazygit.feature.branch;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.branch.BranchCmd.Rebase;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.CommandLog;
import net.vanfleteren.jazygit.state.Loadable.Loaded;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update.Next;
import net.vanfleteren.jazygit.state.Update;

/**
 * Rebasing the checked out branch onto another one.
 */
public final class RebaseUpdate {

    private RebaseUpdate() {
    }

    public static Next update(Model model, RebaseMsg msg) {
        return switch (msg) {
            case RebaseMsg.Requested(String onto) -> requested(model, onto);
            case RebaseMsg.Cancelled() -> Next.of(model.withoutPopup(RebasePopup.class));
            case RebaseMsg.Chosen() -> model.popup(RebasePopup.class).map(RebasePopup::onto)
                    .map(onto -> Next.of(model.withoutPopup(RebasePopup.class).withError(Option.none()),
                            new Rebase(onto)))
                    .getOrElse(() -> Next.of(model));
            case RebaseMsg.Done(String onto, List<String> commands) ->
                    Update.refresh(CommandLog.append(model.withError(Option.none()), "log.title.rebase", commands));
            case RebaseMsg.Failed(String onto, String message) ->
                    Update.refresh(model.withError(Option.of(Messages.get("error.rebase.failed", onto, message))));
        };
    }

    /**
     * Only a known branch other than the checked out one can be rebased onto.
     */
    private static Next requested(Model model, String onto) {
        if (!(model.branches() instanceof Loaded<List<Branch>>(List<Branch> branches))) {
            return Next.of(model);
        }
        Option<Branch> current = branches.find(Branch::current);
        boolean known = branches.exists(b -> b.name().equals(onto) && !b.current());
        return known && current.isDefined()
                ? Next.of(model.openPopup(new RebasePopup(current.get().name(), onto)))
                : Next.of(model);
    }
}
