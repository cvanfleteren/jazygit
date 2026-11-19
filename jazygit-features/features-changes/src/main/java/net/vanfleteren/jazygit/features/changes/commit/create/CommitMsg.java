package net.vanfleteren.jazygit.features.changes.commit.create;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.Update;

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
}
