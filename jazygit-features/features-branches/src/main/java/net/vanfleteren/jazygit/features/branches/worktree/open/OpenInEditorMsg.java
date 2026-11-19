package net.vanfleteren.jazygit.features.branches.worktree.open;

import net.vanfleteren.jazygit.core.git.model.BranchName;
import net.vanfleteren.jazygit.kernel.settings.EditorProgram;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.Update;

/**
 * Opening a worktree in an editor the user set (ADR 110).
 */
public sealed interface OpenInEditorMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return OpenInEditorUpdate.update(model, this);
    }

    /**
     * The user asked to open the worktree at {@code location} in an editor.
     */
    record WorktreeRequested(String location) implements OpenInEditorMsg {}

    /**
     * The user asked to open the worktree that has {@code branch} in an editor.
     */
    record BranchRequested(BranchName branch) implements OpenInEditorMsg {}

    /**
     * The user chose {@code editor} in the menu of the editors.
     */
    record Chosen(EditorProgram editor) implements OpenInEditorMsg {}
}
