package net.vanfleteren.jazygit.feature.stash;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.Stash;
import net.vanfleteren.jazygit.state.ConfirmablePopup;

/**
 * Asking to confirm the deletion of {@code stash}.
 */
public record StashDropPopup(Stash stash) implements ConfirmablePopup {
    @Override
    public Option<StashCmd.Drop> confirm() {
        return Option.of(new StashCmd.Drop(stash));
    }
}
