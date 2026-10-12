package net.vanfleteren.jazygit.feature.branch;

import net.vanfleteren.jazygit.feature.branch.BranchCmd.Rebase;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update.Next;

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
                    .map(onto -> Next.of(model.withoutPopup(RebasePopup.class).clearError(),
                            new Rebase(onto)))
                    .getOrElse(() -> Next.of(model));
        };
    }

    /**
     * Only a known branch other than the checked out one can be rebased onto.
     */
    private static Next requested(Model model, String onto) {
        return Branches.checkedOutAnd(model, onto)
                .map(both -> Next.of(model.openPopup(new RebasePopup(both._1, both._2)).clearError()))
                .getOrElse(() -> Next.of(model));
    }
}
