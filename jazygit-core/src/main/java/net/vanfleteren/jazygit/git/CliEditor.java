package net.vanfleteren.jazygit.git;

import io.vavr.collection.List;
import io.vavr.control.Either;
import java.nio.file.Path;
import java.util.regex.Pattern;
import net.vanfleteren.jazygit.git.GitError.EditorFailed;
import net.vanfleteren.jazygit.git.GitError.EditorNotStarted;
import net.vanfleteren.jazygit.git.GitError.Interrupted;
import net.vanfleteren.jazygit.git.GitError.UnexpectedOutput;
import net.vanfleteren.jazygit.process.Exec;
import net.vanfleteren.jazygit.process.ExecFailure;
import net.vanfleteren.jazygit.process.ExecutedCommand;

/**
 * Opens files in the editor git uses for commit messages, on the terminal.
 */
final class CliEditor {

    // The characters for which git hands the editor to the shell rather than running it itself, from run-command.c.
    private static final Pattern SHELL_CHARACTERS = Pattern.compile("[|&;<>()$`\\\\\"' \t\n*?\\[#~=%]");

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
        GitCommand editorCommand = GitCommand.reading("var", "GIT_EDITOR").withInheritedEnvironment("GIT_EDITOR");
        return GitProcess.read(workTree, editorCommand, CliEditor::editor)
                .<GitFailure<GitError>>mapLeft(error -> new GitFailure<>(error, List.of(editorCommand.executed())))
                .flatMap(editor -> open(workTree, editor, files));
    }

    /**
     * Opens {@code files} in {@code editor}, as git names it, and waits until it is closed.
     */
    static Either<GitFailure<GitError>, List<ExecutedCommand>> open(Path workTree, String editor, List<String> files) {
        return Exec.interactive(workTree, commandLine(editor, files))
                .<GitFailure<GitError>>mapLeft(failure -> new GitFailure<>(error(editor, failure), List.empty()))
                .map(List::of);
    }

    private static Either<UnexpectedOutput, String> editor(String raw) {
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
