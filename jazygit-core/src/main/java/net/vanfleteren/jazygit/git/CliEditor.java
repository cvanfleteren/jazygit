package net.vanfleteren.jazygit.git;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Try;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import net.vanfleteren.jazygit.git.GitError.EditorFailed;
import net.vanfleteren.jazygit.git.GitError.EditorNotStarted;
import net.vanfleteren.jazygit.git.GitError.Interrupted;
import net.vanfleteren.jazygit.git.GitError.TempFileFailed;
import net.vanfleteren.jazygit.git.GitError.UnexpectedOutput;
import net.vanfleteren.jazygit.git.model.CommitMessage;
import net.vanfleteren.jazygit.process.Exec;
import net.vanfleteren.jazygit.process.ExecFailure;
import net.vanfleteren.jazygit.process.ExecutedCommand;

/**
 * Opens files in the editor git uses for commit messages, on the terminal.
 */
final class CliEditor {

    // The characters for which git hands the editor to the shell rather than running it itself, from run-command.c.
    private static final Pattern SHELL_CHARACTERS = Pattern.compile("[|&;<>()$`\\\\\"' \t\n*?\\[#~=%]");

    // What git calls the file it edits a commit message in.
    private static final String MESSAGE_FILE = "COMMIT_EDITMSG";

    private CliEditor() {}

    /**
     * Opens {@code files}, relative to {@code workTree}, in git's editor, and waits until it is closed. That editor
     * is the one {@code git var GIT_EDITOR} names: {@code $GIT_EDITOR}, {@code core.editor}, {@code $VISUAL},
     * {@code $EDITOR}, else {@code vi}. It runs on the terminal, which the caller must have handed over.
     *
     * <p>Fails with a {@link EditorNotStarted} or {@link EditorFailed}, or a {@link GitError.ProcessError} when git
     * names no editor, as it does on a dumb terminal without {@code $EDITOR}.
     */
    static Either<GitFailure<GitError>, List<ExecutedCommand>> edit(Path workTree, List<String> files) {
        return editor(workTree).flatMap(editor -> open(workTree, editor, files));
    }

    /**
     * Opens {@code message} in git's editor, as {@link #edit} does, and gives the message it was saved as, with the
     * command that ran. The file is called {@code COMMIT_EDITMSG}, as git calls it, so editors treat it as a commit
     * message. Lines starting with {@code #} are kept, as no comments are added to it.
     *
     * <p>Fails as {@link #edit} does, or with a {@link TempFileFailed} when the file cannot be written or read.
     */
    static Either<GitFailure<GitError>, Tuple2<CommitMessage, List<ExecutedCommand>>> editMessage(
            Path workTree, CommitMessage message) {
        return editor(workTree).flatMap(editor -> editMessage(workTree, editor, message));
    }

    /**
     * Opens {@code message} in {@code editor}, as git names it, as {@link #editMessage(Path, CommitMessage)} does.
     */
    static Either<GitFailure<GitError>, Tuple2<CommitMessage, List<ExecutedCommand>>> editMessage(
            Path workTree, String editor, CommitMessage message) {
        return Try.of(() -> Files.createTempDirectory("jazygit"))
                .toEither()
                .<GitFailure<GitError>>mapLeft(CliEditor::tempFileFailed)
                .flatMap(directory -> {
                    Path file = directory.resolve(MESSAGE_FILE);
                    Either<GitFailure<GitError>, Tuple2<CommitMessage, List<ExecutedCommand>>> edited = Try.run(
                                    () -> Files.writeString(file, message.text()))
                            .toEither()
                            .<GitFailure<GitError>>mapLeft(CliEditor::tempFileFailed)
                            .flatMap(written -> open(workTree, editor, List.of(file.toString())))
                            .flatMap(commands -> Try.of(() -> Files.readString(file))
                                    .toEither()
                                    .<GitFailure<GitError>>mapLeft(
                                            e -> tempFileFailed(e).after(commands))
                                    .map(text -> Tuple.of(CommitMessage.parse(text), commands)));
                    // A leftover temporary file is harmless.
                    Try.run(() -> Files.deleteIfExists(file));
                    Try.run(() -> Files.deleteIfExists(directory));
                    return edited;
                });
    }

    private static GitFailure<GitError> tempFileFailed(Throwable cause) {
        return new GitFailure<>(new TempFileFailed(cause), List.empty());
    }

    /**
     * The editor git uses: {@code git var GIT_EDITOR}, as the user's environment has it rather than as jazygit sets
     * it for git's own commands.
     */
    private static Either<GitFailure<GitError>, String> editor(Path workTree) {
        GitCommand editorCommand = GitCommand.reading("var", "GIT_EDITOR").withInheritedEnvironment("GIT_EDITOR");
        return GitProcess.read(workTree, editorCommand, CliEditor::parseEditor)
                .mapLeft(error -> new GitFailure<>(error, List.of(editorCommand.executed())));
    }

    /**
     * Opens {@code files} in {@code editor}, as git names it, and waits until it is closed.
     */
    static Either<GitFailure<GitError>, List<ExecutedCommand>> open(Path workTree, String editor, List<String> files) {
        return Exec.interactive(workTree, commandLine(editor, files))
                .<GitFailure<GitError>>mapLeft(failure -> new GitFailure<>(error(editor, failure), List.empty()))
                .map(List::of);
    }

    private static Either<UnexpectedOutput, String> parseEditor(String raw) {
        String editor = raw.strip();
        return editor.isEmpty() ? Either.left(new UnexpectedOutput(raw)) : Either.right(editor);
    }

    /**
     * How git runs {@code editor} on {@code files}: through the shell when it is more than the name of a program,
     * like {@code code --wait}, so its arguments and quotes work as they do for git.
     */
    static List<String> commandLine(String editor, List<String> files) {
        return SHELL_CHARACTERS.matcher(editor).find()
                ? List.of("sh", "-c", editor + " \"$@\"", editor).appendAll(files)
                : files.prepend(editor);
    }

    private static GitError error(String editor, ExecFailure failure) {
        return switch (failure) {
            case ExecFailure.NotStarted(var cause) -> new EditorNotStarted(editor, cause);
            case ExecFailure.NonZero(int exitCode, var stderr, var stdout) -> new EditorFailed(editor, exitCode);
            case ExecFailure.Interrupted() -> new Interrupted(editor);
            // The editor gets as long as the user takes.
            case ExecFailure.TimedOut() -> new Interrupted(editor);
        };
    }
}
