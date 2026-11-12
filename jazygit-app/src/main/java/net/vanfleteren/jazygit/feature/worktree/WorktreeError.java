package net.vanfleteren.jazygit.feature.worktree;

import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.settings.EditorProgram;
import net.vanfleteren.jazygit.state.AppError;
import net.vanfleteren.jazygit.state.Phrase;
import net.vanfleteren.jazygit.state.operation.Operation;

/**
 * What went wrong with an operation on a worktree, or that a worktree kept from being done.
 */
public sealed interface WorktreeError extends AppError {

    /**
     * An operation was asked for on a branch that is checked out in another worktree, which keeps git from
     * doing it here.
     *
     * @param worktree the root of the worktree the branch is checked out in
     */
    record CheckedOutElsewhere(Operation operation, BranchName branch, String worktree) implements WorktreeError {

        @Override
        public Phrase phrase() {
            return Phrase.of(
                    operation.errorKey(),
                    Phrase.verbatim(branch.value()),
                    Phrase.of("error.checkedOutElsewhere", worktree));
        }
    }

    /**
     * The main worktree was asked to be removed, which holds the repository.
     *
     * @param worktree the name of its directory
     */
    record MainWorktreeNotRemovable(String worktree) implements WorktreeError {

        @Override
        public Phrase phrase() {
            return Phrase.of("error.removeWorktree.main", worktree);
        }
    }

    /**
     * The worktree shown was asked to be removed.
     *
     * @param worktree the name of its directory
     */
    record ShownWorktreeNotRemovable(String worktree) implements WorktreeError {

        @Override
        public Phrase phrase() {
            return Phrase.of("error.removeWorktree.shown", worktree);
        }
    }

    /**
     * A locked worktree was asked to be removed, which its lock keeps git from doing.
     *
     * @param worktree the name of its directory
     * @param reason   why it was locked; empty when no reason was given
     */
    record WorktreeLocked(String worktree, String reason) implements WorktreeError {

        @Override
        public Phrase phrase() {
            return reason.isBlank()
                    ? Phrase.of("error.removeWorktree.locked", worktree)
                    : Phrase.of("error.removeWorktree.lockedBecause", worktree, reason);
        }
    }

    /**
     * The worktree shown was asked to be switched to.
     *
     * @param worktree the name of its directory
     */
    record WorktreeShownAlready(String worktree) implements WorktreeError {

        @Override
        public Phrase phrase() {
            return Phrase.of("error.switchWorktree.shown", worktree);
        }
    }

    /**
     * A worktree whose directory is gone was asked to be switched to, or to be opened in an editor.
     *
     * @param worktree the name of its directory
     */
    record WorktreeMissing(String worktree) implements WorktreeError {

        @Override
        public Phrase phrase() {
            return Phrase.of("error.switchWorktree.missing", worktree);
        }
    }

    /**
     * The worktree of {@code branch} was asked to be opened in an editor, but no worktree has it checked out, nor
     * rebases or bisects it (ADR 110).
     */
    record NotInWorktree(BranchName branch) implements WorktreeError {

        @Override
        public Phrase phrase() {
            return Phrase.of("error.openInEditor.notInWorktree", branch.value());
        }
    }

    /**
     * A worktree was asked to be opened in an editor, but the settings file sets none (ADR 110).
     */
    record NoEditorSet() implements WorktreeError {

        @Override
        public Phrase phrase() {
            return Phrase.of(
                    "error.openInEditor.noEditor",
                    // On lines of their own, as TOML has them, so it can be pasted as it is.
                    "[[editors]]\nname = \"IntelliJ\"\ncommand = [\"idea\", \"" + EditorProgram.DIR + "\"]");
        }
    }

    /**
     * Pruning the worktrees was asked for, but none has its directory gone.
     */
    record NothingToPrune() implements WorktreeError {

        @Override
        public Phrase phrase() {
            return Phrase.of("error.pruneWorktrees.nothing");
        }
    }

    /**
     * The main worktree was asked to be locked or unlocked, which git refuses.
     */
    record MainWorktreeNotLockable() implements WorktreeError {

        @Override
        public Phrase phrase() {
            return Phrase.of("error.lockWorktree.main");
        }
    }

    /**
     * What the user typed as a revision to add a worktree at names no commit.
     */
    record UnknownRevision(String text) implements WorktreeError {

        @Override
        public Phrase phrase() {
            return Phrase.of("error.revision.unknown", text);
        }
    }
}
