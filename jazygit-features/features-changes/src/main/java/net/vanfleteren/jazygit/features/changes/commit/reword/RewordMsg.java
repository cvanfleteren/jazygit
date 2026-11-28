package net.vanfleteren.jazygit.features.changes.commit.reword;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.features.changes.commit.PushedCheckCmd;
import net.vanfleteren.jazygit.features.changes.commit.PushedWarning;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.Update;

public sealed interface RewordMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return RewordUpdate.update(model, this);
    }

    /**
     * The user asked to reword the commit {@code sha}; any commit can be.
     */
    record Requested(Sha sha) implements RewordMsg, PushedCheckCmd.Then {

        @Override
        public Msg checked(Option<PushedWarning> warning) {
            return new Checked(sha, warning);
        }
    }

    /**
     * It was read whether the commit {@code sha} to reword is pushed: {@code warning} when it is.
     */
    record Checked(Sha sha, Option<PushedWarning> warning) implements RewordMsg {}

    /**
     * The user wants to reword the commit, although it is pushed already.
     */
    record PushedConfirmed() implements RewordMsg {}
}
