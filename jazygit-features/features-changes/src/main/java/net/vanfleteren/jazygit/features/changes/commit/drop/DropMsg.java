package net.vanfleteren.jazygit.features.changes.commit.drop;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.features.changes.commit.PushedCheckCmd;
import net.vanfleteren.jazygit.features.changes.commit.PushedWarning;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.Update;

public sealed interface DropMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return DropUpdate.update(model, this);
    }

    /**
     * The user asked to drop the commit {@code sha} from the history of the checked out branch.
     */
    record Requested(Sha sha) implements DropMsg, PushedCheckCmd.Then {

        @Override
        public Msg checked(Option<PushedWarning> warning) {
            return new Checked(sha, warning);
        }
    }

    /**
     * It was read whether the commit {@code sha} to drop is pushed: {@code warning} when it is.
     */
    record Checked(Sha sha, Option<PushedWarning> warning) implements DropMsg {}
}
