package net.vanfleteren.jazygit.feature.commit;

import java.util.List;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Model;

import net.vanfleteren.jazygit.state.Msg;

public sealed interface CommitMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return CommitUpdate.update(model, this);
    }

    /**
     * The user asked to commit: the message is asked for, after staging everything if nothing is
     * staged yet.
     */
    record Requested() implements CommitMsg {
    }

    record StageAllConfirmed() implements CommitMsg {
    }

    record StageAllCancelled() implements CommitMsg {
    }

    /**
     * Everything was staged for a commit.
     */
    record StagedForCommit(List<String> commands) implements CommitMsg {
    }

    record StageForCommitFailed(String message) implements CommitMsg {
    }

    record Cancelled() implements CommitMsg {
    }

    record Confirmed(String summary, String description) implements CommitMsg {
    }

    record Done(List<String> commands) implements CommitMsg {
    }

    record Failed(String message) implements CommitMsg {
    }
}
