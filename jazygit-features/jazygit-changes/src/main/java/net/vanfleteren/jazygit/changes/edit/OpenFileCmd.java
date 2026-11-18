package net.vanfleteren.jazygit.changes.edit;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.nio.file.Path;
import net.vanfleteren.jazygit.git.process.GitFailure;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.settings.EditorProgram;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.operation.Operation;
import net.vanfleteren.jazygit.state.operation.OperationCmd;

/**
 * Opens the file at {@code path}, relative to the working tree, at {@code line} in {@code editor}, one the user set to
 * open files, which runs in the working tree (ADR 145): a terminal editor with the terminal handed over to it, after
 * which the repository is read again, like after any operation; any other started and left running, as one that
 * opens a worktree is (ADR 110).
 */
public record OpenFileCmd(EditorProgram editor, String path, int line) implements OperationCmd {

    @Override
    public Operation operation() {
        return EditCmd.EDIT;
    }

    @Override
    public Option<String> subject() {
        return Option.of(path);
    }

    @Override
    public Msg run(Env env) {
        Path worktree = env.git().worktree().toAbsolutePath();
        // Only an editor that opens files is offered for one.
        List<String> commandLine = editor.fileCommandLine(
                        worktree.toString(), worktree.resolve(path).toString(), line)
                .get();
        Either<GitFailure, List<ExecutedCommand>> opened = editor.terminal()
                ? env.terminal().handOver(() -> env.git().editor().open(worktree, commandLine))
                : env.git().editor().launch(worktree, commandLine);
        return opened.mapLeft(Failure::of).fold(this::failed, this::succeeded);
    }
}
