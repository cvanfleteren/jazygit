package net.vanfleteren.jazygit.state.load;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.CommitFile;
import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;

/**
 * The user moved the highlight in one of the panes.
 */
public sealed interface SelectionMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return SelectionUpdate.update(model, this);
    }

    /**
     * The user highlighted a branch, local or remote, in the branches pane.
     */
    record BranchSelected(BranchRef branch) implements SelectionMsg {}

    /**
     * The user highlighted a pull request of a stack in the branches pane whose branch is neither checked out nor
     * fetched, so that it has no log (ADR 72).
     */
    record UnfetchedSelected(int pullRequest) implements SelectionMsg {}

    /**
     * The user highlighted a commit in the commits pane.
     */
    record CommitSelected(Sha sha) implements SelectionMsg {}

    /**
     * The user highlighted a commit in the divergence the branches pane lists.
     */
    record DivergenceCommitSelected(Sha sha) implements SelectionMsg {}

    /**
     * The user highlighted a commit in the commits of a branch the branches pane lists.
     */
    record BranchCommitSelected(Sha sha) implements SelectionMsg {}

    /**
     * The user highlighted a node of the files tree; {@code files} are the changed files under it.
     */
    record FilesSelected(List<FileEntry> files) implements SelectionMsg {}

    /**
     * The user highlighted the file at {@code path} in the files tree while it lists all files, a file without
     * changes (ADR 108).
     */
    record UnchangedFileSelected(String path) implements SelectionMsg {}

    /**
     * The user highlighted the directory at {@code path}, the root when it is empty, in the files tree while it lists
     * all files, with no changes under it; it holds {@code files} files shown (ADR 108).
     */
    record UnchangedDirectorySelected(String path, int files) implements SelectionMsg {}

    /**
     * The user highlighted a node of the tree of the files of a commit or a stash; {@code files} are the files
     * directly under it.
     */
    record CommitFilesSelected(List<CommitFile> files) implements SelectionMsg {}

    /**
     * The user highlighted the stash {@code sha} in the stash pane.
     */
    record StashSelected(Sha sha) implements SelectionMsg {}
}
