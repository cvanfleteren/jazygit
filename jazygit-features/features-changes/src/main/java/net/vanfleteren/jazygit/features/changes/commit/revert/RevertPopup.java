package net.vanfleteren.jazygit.features.changes.commit.revert;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.repo.Commit;
import net.vanfleteren.jazygit.kernel.state.popup.ConfirmablePopup;

/**
 * Asking to confirm reverting {@code commit}.
 */
public record RevertPopup(Commit commit) implements ConfirmablePopup {
    @Override
    public Option<RevertCmd> confirm() {
        return Option.of(new RevertCmd(commit));
    }
}
