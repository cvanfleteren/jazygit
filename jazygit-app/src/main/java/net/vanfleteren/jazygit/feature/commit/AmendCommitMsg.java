package net.vanfleteren.jazygit.feature.commit;

import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;

public sealed interface AmendCommitMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return AmendCommitUpdate.update(model, this);
    }

    /**
     * The user asked to amend the commit {@code sha} with the staged changes; confirmation is still to be given.
     */
    record Requested(Sha sha) implements AmendCommitMsg {}

    /**
     * The user cancelled either question, which leaves everything as it was.
     */
    record Cancelled() implements AmendCommitMsg {}

    record Confirmed() implements AmendCommitMsg {}

    /**
     * The user agreed to amend with all files, as none was staged.
     */
    record StageAllConfirmed() implements AmendCommitMsg {}
}
