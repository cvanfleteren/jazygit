package net.vanfleteren.jazygit.feature.branch.rebase;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.state.ConfirmablePopup;

/**
 * Asking how to rebase the checked out {@code branch} onto {@code onto}.
 */
public record RebasePopup(BranchName branch, BranchName onto) implements ConfirmablePopup {
    @Override
    public Option<RebaseCmd> confirm() {
        return Option.of(new RebaseCmd(onto));
    }
}
