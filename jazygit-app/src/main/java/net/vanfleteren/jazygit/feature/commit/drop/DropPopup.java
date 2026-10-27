package net.vanfleteren.jazygit.feature.commit.drop;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.state.ConfirmablePopup;

/**
 * Asking to confirm dropping {@code commit}.
 */
public record DropPopup(Commit commit) implements ConfirmablePopup {
    @Override
    public Option<DropCmd> confirm() {
        return Option.of(new DropCmd(commit.sha()));
    }
}
