package net.vanfleteren.jazygit.feature.edit;

import static net.vanfleteren.jazygit.state.TestModels.loaded;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.List;
import io.vavr.control.Either;
import java.util.ArrayList;
import java.util.function.Supplier;
import net.vanfleteren.jazygit.gh.StubGitHub;
import net.vanfleteren.jazygit.git.GitError;
import net.vanfleteren.jazygit.git.GitFailure;
import net.vanfleteren.jazygit.git.StubGitProvider;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.settings.RecentRepositories;
import net.vanfleteren.jazygit.settings.SettingsStore;
import net.vanfleteren.jazygit.state.AppError;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Operation;
import net.vanfleteren.jazygit.state.OperationMsg;
import net.vanfleteren.jazygit.state.Repositories;
import net.vanfleteren.jazygit.state.Terminal;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;
import org.junit.jupiter.api.Test;

/**
 * Opening files in the editor.
 */
class EditUpdateTest {

    private static final ExecutedCommand NVIM = ExecutedCommand.of("nvim", "src/A.java");

    @Test
    void aFileIsOpenedInTheEditor() {
        Next next = Update.update(loaded(), new EditMsg.Requested("src/A.java"));

        assertEquals(List.of(new EditCmd(List.of("src/A.java"))), next.cmds());
    }

    @Test
    void theEditorRunsWithTheTerminalHandedOver() {
        java.util.List<String> events = new ArrayList<>();
        Terminal terminal = new Terminal() {
            @Override
            public <T> T handOver(Supplier<T> program) {
                events.add("leave");
                T result = program.get();
                events.add("come back");
                return result;
            }
        };
        StubGitProvider git = new StubGitProvider() {
            @Override
            public Either<GitFailure<GitError>, List<ExecutedCommand>> edit(List<String> files) {
                events.add("edit " + files.mkString(" "));
                return Either.right(List.of(NVIM));
            }
        };

        Msg done = new EditCmd(List.of("src/A.java")).run(env(git, terminal));

        assertEquals(java.util.List.of("leave", "edit src/A.java", "come back"), events);
        assertEquals(new OperationMsg.Succeeded(new EditCmd(List.of("src/A.java")), List.of(NVIM)), done);
    }

    @Test
    void anEditorThatFailsIsReported() {
        StubGitProvider git = new StubGitProvider() {
            @Override
            public Either<GitFailure<GitError>, List<ExecutedCommand>> edit(List<String> files) {
                return Either.left(new GitFailure<>(new GitError.EditorFailed("nvim", 1), List.of(NVIM)));
            }
        };
        EditCmd cmd = new EditCmd(List.of("src/A.java"));

        Next next = Update.update(loaded(), cmd.run(env(git, Terminal.NONE)));

        AppError.OperationFailed failed = assertInstanceOf(
                AppError.OperationFailed.class, next.model().outcome().error().get());
        assertEquals(Operation.EDIT, failed.operation());
        assertTrue(next.model().outcome().commandLog().nonEmpty());
    }

    private static Env env(StubGitProvider git, Terminal terminal) {
        return new Env(
                git, StubGitHub.empty(), SettingsStore.NONE, RecentRepositories.NONE, Repositories.NONE, terminal);
    }
}
