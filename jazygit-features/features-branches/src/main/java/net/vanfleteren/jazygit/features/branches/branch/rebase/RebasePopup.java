package net.vanfleteren.jazygit.features.branches.branch.rebase;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.kernel.state.popup.Popup;

/**
 * Asking where to rebase the checked out {@code branch}: onto the highlighted branch, {@code highlighted}, or on its
 * base branch, {@code base}, none when it has none (ADR 91).
 */
public record RebasePopup(BranchName branch, BranchRef highlighted, Option<BranchRef> base) implements Popup {

    /**
     * The highlighted branch to rebase onto; none when that is the checked out branch itself.
     */
    public Option<BranchRef> onto() {
        return Option.of(highlighted).filter(ref -> !ref.equals(new BranchRef.Local(branch)));
    }

    /**
     * Where to rebase onto for {@code target}, if anywhere.
     */
    public Option<BranchRef> target(RebaseMsg.Target target) {
        return switch (target) {
            case ONTO -> onto();
            case BASE -> base;
        };
    }
}
