package net.vanfleteren.jazygit.features.changes.stash;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.repo.Stash;
import net.vanfleteren.jazygit.kernel.state.popup.ConfirmablePopup;

/**
 * Asking to confirm the deletion of {@code stash}.
 */
public record StashDropPopup(Stash stash) implements ConfirmablePopup {
    @Override
    public Option<StashCmd.Drop> confirm() {
        return Option.of(new StashCmd.Drop(stash));
    }
}
