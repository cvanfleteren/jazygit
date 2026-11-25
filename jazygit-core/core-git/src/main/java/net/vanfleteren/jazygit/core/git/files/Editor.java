package net.vanfleteren.jazygit.core.git.files;

import io.vavr.Tuple2;
import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.core.git.model.ConflictTools;
import net.vanfleteren.jazygit.core.git.process.GitError;
import net.vanfleteren.jazygit.core.git.process.GitFailure;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.core.repo.CommitMessage;

/**
 * Editing files, as git edits commit messages (ADR 69), in the editor git names. A program the user set, as an editor
 * that opens a worktree, is not git's: a feature starts it with the kernel's {@code ProgramLauncher} (ADR 183).
 */
public interface Editor {

    /**
     * Opens {@code files}, relative to the working tree, in the editor git uses for commit messages, and waits until
     * it is closed. It runs on the terminal, which the caller must have handed over. Fails with a
     * {@link GitError.EditorNotStarted} or {@link GitError.EditorFailed}, or a {@link GitError} when git names no
     * editor.
     */
    Either<GitFailure, List<ExecutedCommand>> edit(List<String> files);

    /**
     * Opens {@code message} in the editor git uses for commit messages, and gives the message it was saved as once
     * the editor is closed, with the command that ran. It runs on the terminal, which the caller must have handed
     * over. Fails as {@link #edit} does, or with a {@link GitError.TempFileFailed}.
     */
    Either<GitFailure, Tuple2<CommitMessage, List<ExecutedCommand>>> editMessage(CommitMessage message);

    /**
     * Opens {@code text} in the editor git uses, in a temporary file called {@code fileName}, which tells the editor
     * what it is, and gives the text it was saved as once the editor is closed, with the command that ran. It runs on
     * the terminal, which the caller must have handed over. Fails as {@link #editMessage} does.
     */
    Either<GitFailure, Tuple2<String, List<ExecutedCommand>>> editText(String fileName, String text);

    /**
     * Hands the file in conflict at {@code path} to git's merge tool, {@code git mergetool}, as {@code tools} says to
     * run it (ADR 201), and waits until it is done. It runs on the terminal, which the caller must have handed over.
     * Git stages the file when it takes it as resolved. Fails with a {@link GitError.ProgramFailed} when the merge
     * failed or was not confirmed.
     */
    Either<GitFailure, List<ExecutedCommand>> mergeTool(String path, ConflictTools tools);
}
