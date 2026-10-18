package net.vanfleteren.jazygit.feature.commit.create;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.feature.commit.CommitCmd;
import net.vanfleteren.jazygit.git.model.CommitMessage;
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

    record StageAllCancelled() implements CommitMsg {}

    /**
     * Everything was staged for a commit.
     */
    record StagedForCommit(CommitCmd.StageForCommit cmd, List<ExecutedCommand> commands) implements CommitMsg {}

    record Cancelled() implements CommitMsg {}

    record Confirmed(CommitMessage message) implements CommitMsg {}
}
