package net.vanfleteren.jazygit.feature.inprogress;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.InProgress;
import net.vanfleteren.jazygit.git.model.InProgressAction;
import net.vanfleteren.jazygit.state.ConfirmablePopup;

/**
 * Asking to confirm {@code action}, an abort or a skip of {@code inProgress}, which throws work away.
 */
public record InProgressConfirmPopup(InProgress inProgress, InProgressAction action) implements ConfirmablePopup {
    @Override
    public Option<InProgressCmd> confirm() {
        return Option.of(new InProgressCmd(inProgress, action));
    }
}
