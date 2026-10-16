package net.vanfleteren.jazygit.feature.commit;

import static net.vanfleteren.jazygit.state.TestModels.loaded;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.util.ArrayList;
import java.util.function.Supplier;
import net.vanfleteren.jazygit.gh.StubGitHub;
import net.vanfleteren.jazygit.git.GitError;
import net.vanfleteren.jazygit.git.GitFailure;
import net.vanfleteren.jazygit.git.StubGitProvider;
import net.vanfleteren.jazygit.git.model.CommitMessage;
import net.vanfleteren.jazygit.model.SampleData;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.settings.RecentRepositories;
import net.vanfleteren.jazygit.settings.SettingsStore;
import net.vanfleteren.jazygit.state.AppError;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Operation;
import net.vanfleteren.jazygit.state.Repositories;
import net.vanfleteren.jazygit.state.Terminal;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;
import org.junit.jupiter.api.Test;

/**
 * Editing the message of the commit dialog in the editor.
 */
class MessageEditorUpdateTest {

    private static final CommitMessage TYPED = new CommitMessage("fix", "");
    private static final CommitMessage EDITED = new CommitMessage("Fix it", "Because.");
    private static final ExecutedCommand NVIM = ExecutedCommand.of("nvim", "COMMIT_EDITMSG");

    private static Model committing() {
        return loaded().openPopup(new CommitPopup());
    }

    @Test
    void theTypedMessageIsOpenedInTheEditor() {
        Next next = Update.update(committing(), new MessageEditorMsg.Requested(TYPED));

        assertEquals(List.of(new MessageEditorCmd(TYPED)), next.cmds());
        assertEquals(Option.of(new CommitPopup(Option.of(TYPED))), next.model().popup());
    }

    @Test
    void withoutCommitDialogNothingIsOpened() {
        Next next = Update.update(loaded(), new MessageEditorMsg.Requested(TYPED));

        assertEquals(List.empty(), next.cmds());
    }

    @Test
    void theEditedMessageIsShownInTheDialog() {
        Model editing = Update.update(committing(), new MessageEditorMsg.Requested(TYPED))
                .model();

        Model edited = Update.update(editing, new MessageEditorMsg.Edited(EDITED, List.of(NVIM)))
                .model();

        assertEquals(Option.of(new CommitPopup(Option.of(EDITED))), edited.popup());
        assertEquals(Operation.EDIT_MESSAGE, edited.commandLog().last().operation());
    }

    @Test
    void theRewordDialogShowsTheEditedMessageToo() {
        RewordPopup rewording = new RewordPopup(new SampleData().commits().get().head());

        Model edited = Update.update(loaded().openPopup(rewording), new MessageEditorMsg.Edited(EDITED, List.of(NVIM)))
                .model();

        assertEquals(Option.of(rewording.withDraft(EDITED)), edited.popup());
        assertEquals(EDITED, rewording.withDraft(EDITED).message());
    }

    @Test
    void aDialogThatIsGoneStaysGone() {
        Model edited = Update.update(loaded(), new MessageEditorMsg.Edited(EDITED, List.of(NVIM)))
                .model();

        assertEquals(Option.none(), edited.popup());
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
            public Either<GitFailure<GitError>, Tuple2<CommitMessage, List<ExecutedCommand>>> editMessage(
                    CommitMessage message) {
                events.add("edit " + message.summary());
                return Either.right(Tuple.of(EDITED, List.of(NVIM)));
            }
        };

        assertEquals(
                new MessageEditorMsg.Edited(EDITED, List.of(NVIM)),
                new MessageEditorCmd(TYPED).run(env(git, terminal)));
        assertEquals(java.util.List.of("leave", "edit fix", "come back"), events);
    }

    @Test
    void anEditorThatFailsKeepsTheDialogAndShowsWhy() {
        StubGitProvider git = new StubGitProvider() {
            @Override
            public Either<GitFailure<GitError>, Tuple2<CommitMessage, List<ExecutedCommand>>> editMessage(
                    CommitMessage message) {
                return Either.left(new GitFailure<>(new GitError.EditorFailed("nvim", 1), List.of()));
            }
        };

        Model failed = Update.update(committing(), new MessageEditorCmd(TYPED).run(env(git, Terminal.NONE)))
                .model();

        assertEquals(Option.of(new CommitPopup()), failed.popup());
        AppError.OperationFailed error =
                assertInstanceOf(AppError.OperationFailed.class, failed.error().get());
        assertEquals(Operation.EDIT_MESSAGE, error.operation());
    }

    private static Env env(StubGitProvider git, Terminal terminal) {
        return new Env(
                git, StubGitHub.empty(), SettingsStore.NONE, RecentRepositories.NONE, Repositories.NONE, terminal);
    }
}
