package net.vanfleteren.jazygit.features.changes.commit.drop;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.model.Commit;
import net.vanfleteren.jazygit.kernel.state.popup.ConfirmablePopup;

/**
 * Asking to confirm dropping {@code commit}.
 */
public record DropPopup(Commit commit) implements ConfirmablePopup {
    @Override
    public Option<DropCmd> confirm() {
        return Option.of(new DropCmd(commit.sha()));
    }
}
