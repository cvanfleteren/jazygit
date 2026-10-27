package net.vanfleteren.jazygit.feature.commit.revert;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.state.ConfirmablePopup;

/**
 * Asking to confirm reverting {@code commit}.
 */
public record RevertPopup(Commit commit) implements ConfirmablePopup {
    @Override
    public Option<RevertCmd> confirm() {
        return Option.of(new RevertCmd(commit));
    }
}
