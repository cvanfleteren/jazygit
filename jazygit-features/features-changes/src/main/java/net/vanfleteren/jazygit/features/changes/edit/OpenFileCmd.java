package net.vanfleteren.jazygit.features.changes.edit;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.nio.file.Path;
import net.vanfleteren.jazygit.kernel.settings.EditorProgram;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.edit.EditedFile;
import net.vanfleteren.jazygit.kernel.state.operation.Operation;
import net.vanfleteren.jazygit.kernel.state.operation.OperationCmd;

/**
 * Opens {@code file}, of the working tree or elsewhere on disk, at {@code line} in {@code editor}, one the user set to
 * open files, which runs in the working tree (ADR 145): a terminal editor with the terminal handed over to it, after
 * which the repository is read again, like after any operation; any other started and left running, as one that
 * opens a worktree is (ADR 110).
 */
public record OpenFileCmd(EditorProgram editor, EditedFile file, int line) implements OperationCmd {

    @Override
    public Operation operation() {
        return EditCmd.EDIT;
    }

    @Override
    public Option<String> subject() {
        return Option.of(file.shown());
    }

    @Override
    public Msg run(Env env) {
        Path worktree = env.git().worktree().toAbsolutePath();
        // Only an editor that opens files is offered for one.
        List<String> commandLine = editor.fileCommandLine(
                        worktree.toString(), worktree.resolve(file.shown()).toString(), line)
                .get();
        return env.launcher()
                .launch(editor, worktree, commandLine)
                .fold(this::failed, opened -> succeeded(List.of(opened)));
    }
}
