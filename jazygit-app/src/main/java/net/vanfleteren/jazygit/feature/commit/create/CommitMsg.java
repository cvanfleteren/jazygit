package net.vanfleteren.jazygit.feature.commit.create;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;

public sealed interface CommitMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return CommitUpdate.update(model, this);
    }

    /**
     * The user asked to commit: the message is asked for, after staging everything if nothing is
     * staged yet.
     */
    record Requested() implements CommitMsg {}

    record StageAllConfirmed() implements CommitMsg {}

    /**
     * Everything was staged for a commit.
     */
    record StagedForCommit(CreateCommitCmd.StageForCommit cmd, List<ExecutedCommand> commands) implements CommitMsg {}

    /**
     * The user confirmed the message typed in the popup.
     */
    record Confirmed() implements CommitMsg {}
}
