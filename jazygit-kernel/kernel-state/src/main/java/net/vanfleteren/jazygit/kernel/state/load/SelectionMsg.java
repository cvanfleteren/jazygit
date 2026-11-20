package net.vanfleteren.jazygit.kernel.state.load;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.core.git.model.CommitFile;
import net.vanfleteren.jazygit.core.git.model.FileEntry;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.PaneId;
import net.vanfleteren.jazygit.kernel.state.Update;
import net.vanfleteren.jazygit.kernel.state.view.Followed;
import net.vanfleteren.jazygit.kernel.state.view.NoLog;

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
     * The user highlighted a row in the branches pane that has no log, as {@code noLog} says why: a pull request of a
     * stack whose branch is neither checked out nor fetched (ADR 72), or a detached HEAD.
     */
    record NoLogSelected(NoLog noLog) implements SelectionMsg {}

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
     * changes: of the working tree (ADR 108), or of the commit whose files it lists (ADR 128).
     */
    record UnchangedFileSelected(String path) implements SelectionMsg {}

    /**
     * The user highlighted the directory at {@code path}, the root when it is empty, in the files tree while it lists
     * all files, with no changes under it; it holds {@code files} files shown (ADR 108, 128).
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

    /**
     * The files pane, {@code listedIn}, is to list what the row highlighted in the pane {@code from} that has the focus
     * follows: its files, nothing, or the files of the working tree (ADR 141).
     */
    record FilesFollowed(Followed followed, PaneId from, PaneId listedIn) implements SelectionMsg {}

    /**
     * The user asked for the files that follow the highlight whole, which the pane counts in place of their tree as
     * there are many (ADR 141).
     */
    record WholeListed() implements SelectionMsg {}

    /**
     * The list the files pane is locked to follows {@code at}, which the files listed since the lock are for, whatever
     * they are (ADR 149).
     */
    record FilesLockedAt(Followed at) implements SelectionMsg {}

    /**
     * The files pane is no longer locked to a list: {@code Esc} let go of it, or the list was closed (ADR 149).
     */
    record FilesUnlocked() implements SelectionMsg {}
}
