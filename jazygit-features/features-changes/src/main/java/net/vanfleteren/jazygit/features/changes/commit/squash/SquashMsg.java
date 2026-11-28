package net.vanfleteren.jazygit.features.changes.commit.squash;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.features.changes.commit.PushedCheckCmd;
import net.vanfleteren.jazygit.features.changes.commit.PushedWarning;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.Update;

public sealed interface SquashMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return SquashUpdate.update(model, this);
    }

    /**
     * The user asked to squash the commit {@code sha} into the one below it.
     */
    record Requested(Sha sha) implements SquashMsg, PushedCheckCmd.Then {

        @Override
        public Msg checked(Option<PushedWarning> warning) {
            return new Checked(sha, warning);
        }
    }

    /**
     * It was read whether the commit {@code sha} to squash is pushed: {@code warning} when it is.
     */
    record Checked(Sha sha, Option<PushedWarning> warning) implements SquashMsg {}
}
