package net.vanfleteren.jazygit.feature.branch.rebase;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.state.Popup;

/**
 * Asking where to rebase the checked out {@code branch}: onto the highlighted branch, {@code highlighted}, or on its
 * base branch, {@code base}, none when it has none (ADR 91).
 */
public record RebasePopup(BranchName branch, BranchName highlighted, Option<BranchName> base) implements Popup {

    /**
     * The highlighted branch to rebase onto; none when that is the checked out branch itself.
     */
    public Option<BranchName> onto() {
        return Option.of(highlighted).filter(name -> !name.equals(branch));
    }

    /**
     * Where to rebase onto for {@code target}, if anywhere.
     */
    public Option<BranchName> target(RebaseMsg.Target target) {
        return switch (target) {
            case ONTO -> onto();
            case BASE -> base;
        };
    }
}
