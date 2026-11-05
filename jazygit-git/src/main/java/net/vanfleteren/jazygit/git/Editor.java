package net.vanfleteren.jazygit.git;

import io.vavr.Tuple2;
import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.git.model.CommitMessage;
import net.vanfleteren.jazygit.process.ExecutedCommand;

/**
 * Editing files, as git edits commit messages (ADR 69).
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
}
