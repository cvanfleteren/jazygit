package net.vanfleteren.jazygit.feature.commit.reset;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.ResetMode;
import net.vanfleteren.jazygit.git.model.ResetTarget;
import net.vanfleteren.jazygit.state.ConfirmablePopup;

/**
 * Asking to confirm a hard reset to {@code target}, which discards the uncommitted changes.
 *
 * @param files how many files have changes that are discarded; none when the status is not known
 */
public record HardResetPopup(ResetTarget target, Option<Integer> files) implements ConfirmablePopup {
    @Override
    public Option<ResetCmd> confirm() {
        return Option.of(new ResetCmd(target, ResetMode.HARD));
    }
}
