package net.vanfleteren.jazygit.features.changes.commit.fixup;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.model.SquashMode;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.features.changes.commit.PushedCheckCmd;
import net.vanfleteren.jazygit.features.changes.commit.PushedWarning;
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
    record Requested(Sha sha) implements FixupMsg, PushedCheckCmd.Then {

        @Override
        public Msg checked(Option<PushedWarning> warning) {
            return new Checked(sha, warning);
        }
    }

    /**
     * It was read whether the commit {@code sha} to fix up is pushed: {@code warning} when it is.
     */
    record Checked(Sha sha, Option<PushedWarning> warning) implements FixupMsg {}

    /**
     * The user wants to fix up the commit, although it is pushed already.
     */
    record PushedConfirmed() implements FixupMsg {}

    /**
     * The user chose which message to keep: {@link SquashMode#FIXUP} or {@link SquashMode#FIXUP_KEEP_MESSAGE}.
     */
    record Chosen(SquashMode mode) implements FixupMsg {}
}
