package net.vanfleteren.jazygit.feature.branch;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.branch.BranchCmd.Rebase;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * Rebasing the checked out branch onto another one.
 */
public final class RebaseUpdate {

    private RebaseUpdate() {}

    public static Next update(Model model, RebaseMsg msg) {
        return switch (msg) {
            case RebaseMsg.Requested(Branch.Name onto) -> requested(model, onto);
            case RebaseMsg.Cancelled() -> Next.of(model.withoutPopup(RebasePopup.class));
            case RebaseMsg.Chosen() ->
                Update.confirm(model, RebasePopup.class, popup -> Option.of(new Rebase(popup.onto())));
        };
    }

    /**
     * Only a known branch other than the checked out one can be rebased onto.
     */
    private static Next requested(Model model, Branch.Name onto) {
        return Update.ask(model, Branches.checkedOutAnd(model, onto).map(both -> new RebasePopup(both._1, both._2)));
    }
}
