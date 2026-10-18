package net.vanfleteren.jazygit.feature.commit.amend;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;

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

    /**
     * The user cancelled either question, which leaves everything as it was.
     */
    record Cancelled() implements AmendMsg {}

    record Confirmed() implements AmendMsg {}

    /**
     * The user agreed to amend with all files, as none was staged.
     */
    record StageAllConfirmed() implements AmendMsg {}
}
