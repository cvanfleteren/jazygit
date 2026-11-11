package net.vanfleteren.jazygit.feature.worktree.open;

import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.Worktree;
import net.vanfleteren.jazygit.settings.EditorProgram;
import net.vanfleteren.jazygit.settings.Editors;
import net.vanfleteren.jazygit.state.AppError;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * Opening a worktree in an editor the user set (ADR 110): at once with only one set, else after choosing it in a
 * menu of them. Where there is nothing to open, it says why.
 */
public final class OpenInEditorUpdate {

    private OpenInEditorUpdate() {}

    public static Next update(Model model, OpenInEditorMsg msg) {
        return switch (msg) {
            case OpenInEditorMsg.WorktreeRequested(String location) ->
                model.repo()
                        .worktree(location)
                        .map(worktree -> worktree(model, worktree))
                        .getOrElse(() -> Next.of(model));
            case OpenInEditorMsg.BranchRequested(BranchName branch) -> branch(model, branch);
            case OpenInEditorMsg.Chosen(EditorProgram editor) ->
                model.popup(OpenInEditorPopup.class)
                        .map(popup -> open(model.withoutPopup(OpenInEditorPopup.class), editor, popup.worktree()))
                        .getOrElse(() -> Next.of(model));
        };
    }

    /**
     * Opens the worktree that works on {@code branch}, the one shown or another: that has it checked out, or rebases
     * or bisects it (ADR 110). It is in none, which is said; nothing is done before the worktrees are read.
     */
    private static Next branch(Model model, BranchName branch) {
        return model.repo()
                .worktrees()
                .toOption()
                .map(worktrees -> worktrees
                        .find(worktree -> worktree.workingOn().contains(branch))
                        .map(worktree -> worktree(model, worktree))
                        .getOrElse(() -> Next.of(model.fail(new AppError.NotInWorktree(branch)))))
                .getOrElse(() -> Next.of(model));
    }

    /**
     * Opens {@code worktree}, unless its directory is gone, which is said.
     */
    private static Next worktree(Model model, Worktree worktree) {
        return worktree.prunable()
                ? Next.of(model.fail(new AppError.WorktreeMissing(worktree.name())))
                : editors(model, worktree.location());
    }

    /**
     * Opens the worktree at {@code location} in the editor set, or asks which one when there are more; none set is
     * said, with how to set one.
     */
    private static Next editors(Model model, String location) {
        Editors editors = model.preferences().settings().editors();
        if (editors.isEmpty()) {
            return Next.of(model.fail(new AppError.NoEditorSet()));
        }
        return editors.all().size() == 1
                ? open(model, editors.all().head(), location)
                : Next.of(model.clearError().openPopup(new OpenInEditorPopup(location, editors.lettered())));
    }

    private static Next open(Model model, EditorProgram editor, String location) {
        return Update.start(model.clearError(), new OpenInEditorCmd(editor, location));
    }
}
