package net.vanfleteren.jazygit.features.changes.commit.fixup;

import net.vanfleteren.jazygit.core.git.model.Sha;
import net.vanfleteren.jazygit.core.git.model.SquashMode;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.Update;

public sealed interface FixupMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return FixupUpdate.update(model, this);
    }

    /**
     * The user asked to fix up the commit {@code sha} into the one below it; which message to keep is still
     * to be chosen.
     */
    record Requested(Sha sha) implements FixupMsg {}

    /**
     * The user chose which message to keep: {@link SquashMode#FIXUP} or {@link SquashMode#FIXUP_KEEP_MESSAGE}.
     */
    record Chosen(SquashMode mode) implements FixupMsg {}
}
