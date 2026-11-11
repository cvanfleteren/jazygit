package net.vanfleteren.jazygit.feature.worktree.open;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.nio.file.Path;
import net.vanfleteren.jazygit.git.GitFailure;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.settings.EditorProgram;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.operation.Operation;
import net.vanfleteren.jazygit.state.operation.OperationCmd;

/**
 * Opens the worktree at {@code worktree} in {@code editor}, which runs there (ADR 110): a terminal editor with the
 * terminal handed over to it, after which the repository is read again, like after any operation; any other started
 * and left running, as a browser is.
 */
public record OpenInEditorCmd(EditorProgram editor, String worktree) implements OperationCmd {

    public static final Operation OPEN_IN_EDITOR = Operation.of("log.title.openInEditor", "error.openInEditor.failed");

    @Override
    public Operation operation() {
        return OPEN_IN_EDITOR;
    }

    @Override
    public Option<String> subject() {
        return Option.of(worktree);
    }

    @Override
    public Msg run(Env env) {
        Path directory = Path.of(worktree);
        List<String> commandLine = editor.commandLine(worktree);
        Either<GitFailure, List<ExecutedCommand>> opened = editor.terminal()
                ? env.terminal().handOver(() -> env.git().editor().open(directory, commandLine))
                : env.git().editor().launch(directory, commandLine);
        return opened.mapLeft(Failure::of).fold(this::failed, this::succeeded);
    }
}
