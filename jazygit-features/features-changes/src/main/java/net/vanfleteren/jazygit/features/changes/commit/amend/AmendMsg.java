package net.vanfleteren.jazygit.features.changes.commit.amend;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.features.changes.commit.PushedWarning;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.Update;

public sealed interface AmendMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return AmendUpdate.update(model, this);
    }

    /**
     * The user asked to amend the commit {@code sha} with the staged changes, or the last commit when none is
     * given; confirmation is still to be given.
     */
    record Requested(Option<Sha> sha) implements AmendMsg {

        /**
         * Amending the last commit.
         */
        public Requested() {
            this(Option.none());
        }
    }

    record Confirmed() implements AmendMsg {}

    /**
     * The user agreed to amend with all files, as none was staged.
     */
    record StageAllConfirmed() implements AmendMsg {}

    /**
     * It was read whether the commit {@code target} is pushed: {@code warning} when it is.
     */
    record Checked(AmendTarget target, Option<PushedWarning> warning) implements AmendMsg {}
}
