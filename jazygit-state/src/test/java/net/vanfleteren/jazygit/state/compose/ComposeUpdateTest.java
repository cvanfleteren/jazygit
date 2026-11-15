package net.vanfleteren.jazygit.state.compose;

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
import lombok.With;
import net.vanfleteren.jazygit.gh.StubGitHub;
import net.vanfleteren.jazygit.git.Git;
import net.vanfleteren.jazygit.git.StubGit;
import net.vanfleteren.jazygit.git.files.StubEditor;
import net.vanfleteren.jazygit.git.process.GitError;
import net.vanfleteren.jazygit.git.process.GitFailure;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.settings.RecentRepositories;
import net.vanfleteren.jazygit.settings.SettingsStore;
import net.vanfleteren.jazygit.state.AppError;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Repositories;
import net.vanfleteren.jazygit.state.Terminal;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;
import net.vanfleteren.jazygit.state.compose.ComposeCmd.OpenInEditor;
import net.vanfleteren.jazygit.state.github.PullRequestsError;
import net.vanfleteren.jazygit.state.operation.Operation;
import net.vanfleteren.jazygit.state.popup.ErrorPopup;
import org.junit.jupiter.api.Test;

/**
 * Writing text in a popup, in the popup or in the editor, sending it and leaving it (ADR 115), for any popup that
 * composes text: what differs, the popup says.
 */
class ComposeUpdateTest {

    private static final Operation EDIT_NOTE = Operation.of("log.title.editComment", "error.editComment.failed");
    private static final EditorFile NOTE_FILE = new EditorFile("NOTE.md", EDIT_NOTE);
    private static final ExecutedCommand NVIM = ExecutedCommand.of("nvim", "NOTE.md");
    private static final Model SENT = loaded().withoutPopup(Note.class);

    /**
     * A popup that composes a note: sending it opens an error popup saying it was sent, as a stand-in for what a
     * feature starts; leaving it closes it.
     */
    @With
    record Note(String text, boolean sending) implements ComposePopup {

        @Override
        public EditorFile editorFile() {
            return NOTE_FILE;
        }

        @Override
        public Next send(Model model) {
            return Next.of(model.openPopup(withSending(true)));
        }

        @Override
        public Model back(Model model) {
            return model.withoutPopup(Note.class);
        }
    }

    private static Model writing(String text) {
        return loaded().openPopup(new Note(text, false));
    }

    private static Model sending(String text) {
        return loaded().openPopup(new Note(text, true));
    }

    private static Env env(Git git, Terminal terminal) {
        return new Env(git, StubGitHub.of(), SettingsStore.NONE, RecentRepositories.NONE, Repositories.NONE, terminal);
    }

    @Test
    void whatIsTypedIsKeptInThePopup() {
        Next typed = Update.update(writing(""), new ComposeMsg.Edited("Looks good"));

        assertEquals(Option.of(new Note("Looks good", false)), typed.model().popup());
        assertEquals(List.empty(), typed.cmds());
    }

    @Test
    void sendingAndLeavingAreWhatThePopupSays() {
        assertEquals(
                Option.of(new Note("Looks good", true)),
                Update.update(writing("Looks good"), new ComposeMsg.Confirmed())
                        .model()
                        .popup());
        assertEquals(
                Option.none(),
                Update.update(writing("Looks good"), new ComposeMsg.Cancelled())
                        .model()
                        .popup());
    }

    @Test
    void whileItIsSentTheTextCannotBeChangedSentAgainLeftOrOpenedInTheEditor() {
        Model sending = sending("Looks good");

        assertEquals(
                sending,
                Update.update(sending, new ComposeMsg.Edited("Looks bad")).model());
        assertEquals(Next.of(sending), Update.update(sending, new ComposeMsg.Confirmed()));
        assertEquals(sending, Update.update(sending, new ComposeMsg.Cancelled()).model());
        assertEquals(Next.of(sending), Update.update(sending, new ComposeMsg.EditorRequested()));
    }

    @Test
    void withoutAPopupThatComposesNothingHappens() {
        Model other = loaded().openPopup(new ErrorPopup(new PullRequestsError.NotRead()));

        assertEquals(Next.of(other), Update.update(other, new ComposeMsg.Edited("Looks good")));
        assertEquals(Next.of(other), Update.update(other, new ComposeMsg.Confirmed()));
        assertEquals(Next.of(other), Update.update(other, new ComposeMsg.Cancelled()));
        assertEquals(Next.of(other), Update.update(other, new ComposeMsg.EditorRequested()));
    }

    @Test
    void theTextIsOpenedInTheEditorAsTheFileThePopupNames() {
        Next next = Update.update(writing("draft"), new ComposeMsg.EditorRequested());

        assertEquals(List.of(new OpenInEditor(NOTE_FILE, "draft")), next.cmds());
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
        Git git = StubGit.of().withEditor(new StubEditor() {
            @Override
            public Either<GitFailure, Tuple2<String, List<ExecutedCommand>>> editText(String fileName, String text) {
                events.add("edit " + fileName + " " + text);
                return Either.right(Tuple.of("Looks good", List.of(NVIM)));
            }
        });

        assertEquals(
                new ComposeMsg.EditorClosed(NOTE_FILE, "Looks good", List.of(NVIM)),
                new OpenInEditor(NOTE_FILE, "draft").run(env(git, terminal)));
        assertEquals(java.util.List.of("leave", "edit NOTE.md draft", "come back"), events);
    }

    @Test
    void whatTheEditorSavedIsShownInThePopupAndItsCommandsLogged() {
        Next edited =
                Update.update(writing("draft"), new ComposeMsg.EditorClosed(NOTE_FILE, "Looks good", List.of(NVIM)));

        assertEquals(Option.of(new Note("Looks good", false)), edited.model().popup());
        assertEquals(List.empty(), edited.cmds());
        assertEquals(EDIT_NOTE, edited.model().outcome().commandLog().last().operation());
    }

    @Test
    void whatTheEditorSavedAfterThePopupWasLeftIsOnlyLogged() {
        Model edited = Update.update(SENT, new ComposeMsg.EditorClosed(NOTE_FILE, "Looks good", List.of(NVIM)))
                .model();

        assertEquals(Option.none(), edited.popup());
        assertEquals(EDIT_NOTE, edited.outcome().commandLog().last().operation());
    }

    @Test
    void anEditorThatFailsKeepsTheTextAndShowsWhy() {
        Git git = StubGit.of().withEditor(new StubEditor() {
            @Override
            public Either<GitFailure, Tuple2<String, List<ExecutedCommand>>> editText(String fileName, String text) {
                return Either.left(new GitFailure(new GitError.EditorFailed("nvim", 1), List.of()));
            }
        });

        Model failed = Update.update(
                        writing("draft"), new OpenInEditor(NOTE_FILE, "draft").run(env(git, Terminal.NONE)))
                .model();

        assertEquals(Option.of(new Note("draft", false)), failed.popup());
        AppError.OperationFailed error = assertInstanceOf(
                AppError.OperationFailed.class, failed.outcome().error().get());
        assertEquals(EDIT_NOTE, error.operation());
    }

    @Test
    void aFailedSendOpensTheTextForANewTry() {
        assertEquals(
                Option.of(new Note("Looks good", false)),
                ComposeUpdate.unsent(sending("Looks good"), Note.class).popup());
        assertEquals(SENT, ComposeUpdate.unsent(SENT, Note.class));
    }
}
