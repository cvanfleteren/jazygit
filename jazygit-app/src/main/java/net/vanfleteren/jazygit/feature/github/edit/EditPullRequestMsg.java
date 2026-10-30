package net.vanfleteren.jazygit.feature.github.edit;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.ListCursor;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;

/**
 * Editing the pull request that is shown: its title, its body, its base and whether it is a draft (ADR 59).
 */
public sealed interface EditPullRequestMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return EditPullRequestUpdate.update(model, this);
    }

    /**
     * The user asked to edit the pull request that is shown.
     */
    record Requested() implements EditPullRequestMsg {}

    /**
     * The user moved in the list of bases, to {@code cursor}: the highlighted base, if the filter shows any, is the
     * one the pull request merges into.
     */
    record BaseMoved(ListCursor cursor, Option<RemoteBranch> base) implements EditPullRequestMsg {}

    /**
     * The user changed the title, the body or whether it is a draft.
     */
    record Edited(String title, String body, boolean draft) implements EditPullRequestMsg {}

    /**
     * The user asked to save what was changed.
     */
    record Confirmed() implements EditPullRequestMsg {}

    record Cancelled() implements EditPullRequestMsg {}

    /**
     * The user asked to write the body in the editor.
     */
    record EditorRequested() implements EditPullRequestMsg {}

    /**
     * The editor ran {@code commands}, and saved the body as {@code text}.
     */
    record EditorClosed(String text, List<ExecutedCommand> commands) implements EditPullRequestMsg {}

    record EditorFailed(Failure failure) implements EditPullRequestMsg {}

    /**
     * What was changed was saved by running {@code commands}.
     */
    record Saved(EditPullRequestCmd.SavePullRequest cmd, List<ExecutedCommand> commands)
            implements EditPullRequestMsg {}

    /**
     * Saving failed.
     *
     * @param edited whether the title, body and base were saved, and only making it a draft or ready failed after
     */
    record SaveFailed(EditPullRequestCmd.SavePullRequest cmd, Failure failure, boolean edited)
            implements EditPullRequestMsg {}
}
