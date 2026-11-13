package net.vanfleteren.jazygit.feature.worktree.open;

import static net.vanfleteren.jazygit.state.TestModels.errorText;
import static net.vanfleteren.jazygit.state.TestModels.loaded;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import io.vavr.Tuple;
import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.function.Supplier;
import net.vanfleteren.jazygit.feature.worktree.WorktreeError;
import net.vanfleteren.jazygit.gh.StubGitHub;
import net.vanfleteren.jazygit.git.GitError;
import net.vanfleteren.jazygit.git.GitFailure;
import net.vanfleteren.jazygit.git.StubEditor;
import net.vanfleteren.jazygit.git.StubGit;
import net.vanfleteren.jazygit.git.model.*;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.settings.EditorProgram;
import net.vanfleteren.jazygit.settings.Editors;
import net.vanfleteren.jazygit.state.*;
import net.vanfleteren.jazygit.state.Update.Next;
import net.vanfleteren.jazygit.state.load.LoadMsg;
import net.vanfleteren.jazygit.state.operation.OperationMsg;
import net.vanfleteren.jazygit.state.popup.PopupMsg;
import org.junit.jupiter.api.Test;

/**
 * Opening a worktree in an editor the user set, from the worktrees or the branches (ADR 110).
 */
class OpenInEditorUpdateTest {

    private static final BranchName MAIN_BRANCH = new BranchName("main");
    private static final BranchName FEATURE = new BranchName("feature");
    private static final BranchName TAKEN = new BranchName("taken");
    private static final BranchName GONE = new BranchName("gone");
    private static final Path MAIN_PATH = Path.of("/repos/app");
    private static final Path LINKED_PATH = Path.of("/repos/app-taken");
    private static final Path GONE_PATH = Path.of("/repos/app-gone");
    private static final Worktree MAIN = new Worktree(
            MAIN_PATH,
            Option.of(new Head.OnBranch(MAIN_BRANCH, Option.of(new Sha("aaaa")))),
            true,
            true,
            Option.none(),
            false);
    private static final Worktree LINKED = new Worktree(
            LINKED_PATH,
            Option.of(new Head.OnBranch(TAKEN, Option.of(new Sha("1111")))),
            false,
            false,
            Option.none(),
            false);
    private static final Worktree MISSING = new Worktree(
            GONE_PATH,
            Option.of(new Head.OnBranch(GONE, Option.of(new Sha("2222")))),
            false,
            false,
            Option.none(),
            true);
    private static final EditorProgram IDEA =
            new EditorProgram("IntelliJ", List.of("idea", EditorProgram.DIR), false, Option.of('i'));
    private static final EditorProgram NVIM = new EditorProgram("Neovim", List.of("nvim"), true, Option.none());

    /**
     * {@code main} is checked out in the main worktree, which is shown; {@code taken} in a linked one;
     * {@code gone} in one whose directory is gone; {@code feature} in none; with {@code editors} set.
     */
    private static Model model(List<EditorProgram> editors) {
        Model branches = TestModels.localBranchesRead(
                        loaded(),
                        List.of(
                                Fixtures.branch(MAIN_BRANCH, true, new Sha("aaaa")),
                                Fixtures.branch(FEATURE, false, new Sha("ffff")),
                                elsewhere(TAKEN, LINKED_PATH),
                                elsewhere(GONE, GONE_PATH)))
                .model();
        return Update.update(branches, new LoadMsg.WorktreesLoaded(List.of(MAIN, LINKED, MISSING)))
                .model()
                .mapPreferences(preferences ->
                        preferences.withSettings(preferences.settings().withEditors(new Editors(editors))));
    }

    private static Branch elsewhere(BranchName name, Path worktree) {
        return new Branch(
                name,
                false,
                new Sha("1111"),
                Instant.EPOCH,
                Option.none(),
                false,
                false,
                new Tracking.None(),
                Option.of(worktree));
    }

    @Test
    void withOneEditorTheWorktreeOpensInItAtOnce() {
        Next next = Update.update(model(List.of(IDEA)), new OpenInEditorMsg.WorktreeRequested(LINKED.location()));

        assertEquals(List.of(new OpenInEditorCmd(IDEA, LINKED_PATH.toString())), next.cmds());
        assertEquals(Option.none(), next.model().popup());
    }

    @Test
    void withMoreTheEditorsAreOfferedWithTheirLetters() {
        Next next = Update.update(model(List.of(IDEA, NVIM)), new OpenInEditorMsg.WorktreeRequested(LINKED.location()));

        assertEquals(List.empty(), next.cmds());
        assertEquals(
                Option.of(new OpenInEditorPopup(
                        LINKED_PATH.toString(), List.of(Tuple.of('i', IDEA), Tuple.of('1', NVIM)))),
                next.model().popup());
    }

    @Test
    void choosingAnEditorClosesTheMenuAndOpensTheWorktreeInIt() {
        Model offered = Update.update(
                        model(List.of(IDEA, NVIM)), new OpenInEditorMsg.WorktreeRequested(LINKED.location()))
                .model();

        Next chosen = Update.update(offered, new OpenInEditorMsg.Chosen(NVIM));

        assertEquals(List.of(new OpenInEditorCmd(NVIM, LINKED_PATH.toString())), chosen.cmds());
        assertEquals(Option.none(), chosen.model().popup());
    }

    @Test
    void escapeClosesTheMenuWithoutOpeningAnything() {
        Model offered = Update.update(
                        model(List.of(IDEA, NVIM)), new OpenInEditorMsg.WorktreeRequested(LINKED.location()))
                .model();

        Next dismissed = Update.update(offered, new PopupMsg.Dismissed(OpenInEditorPopup.class));

        assertEquals(List.empty(), dismissed.cmds());
        assertEquals(Option.none(), dismissed.model().popup());
    }

    @Test
    void withoutAnEditorSetItSaysHowToSetOne() {
        Next next = Update.update(model(List.empty()), new OpenInEditorMsg.WorktreeRequested(LINKED.location()));

        assertEquals(List.empty(), next.cmds());
        assertEquals(
                Option.of("No editor is set to open a worktree in. Add one to the settings file, config.toml, such as:"
                        + "\n\n[[editors]]\nname = \"IntelliJ\"\ncommand = [\"idea\", \"{dir}\"]"),
                errorText(next.model()));
    }

    @Test
    void aWorktreeWhoseDirectoryIsGoneIsNotOpened() {
        Next next = Update.update(model(List.of(IDEA)), new OpenInEditorMsg.WorktreeRequested(MISSING.location()));

        assertEquals(List.empty(), next.cmds());
        assertInstanceOf(
                WorktreeError.WorktreeMissing.class,
                next.model().outcome().error().get());
    }

    @Test
    void theCheckedOutBranchOpensTheWorktreeShown() {
        Next next = Update.update(model(List.of(IDEA)), new OpenInEditorMsg.BranchRequested(MAIN_BRANCH));

        assertEquals(List.of(new OpenInEditorCmd(IDEA, MAIN_PATH.toString())), next.cmds());
    }

    @Test
    void aBranchInAnotherWorktreeOpensThatOne() {
        Next next = Update.update(model(List.of(IDEA)), new OpenInEditorMsg.BranchRequested(TAKEN));

        assertEquals(List.of(new OpenInEditorCmd(IDEA, LINKED_PATH.toString())), next.cmds());
    }

    @Test
    void aBranchInAWorktreeWhoseDirectoryIsGoneIsNotOpened() {
        Next next = Update.update(model(List.of(IDEA)), new OpenInEditorMsg.BranchRequested(GONE));

        assertEquals(List.empty(), next.cmds());
        assertInstanceOf(
                WorktreeError.WorktreeMissing.class,
                next.model().outcome().error().get());
    }

    @Test
    void aBranchNoWorktreeHasSaysSo() {
        Next next = Update.update(model(List.of(IDEA)), new OpenInEditorMsg.BranchRequested(FEATURE));

        assertEquals(List.empty(), next.cmds());
        assertEquals(Option.of("Branch feature is not checked out in a worktree"), errorText(next.model()));
    }

    @Test
    void aBranchTheWorktreeShownBisectsWithItsHeadDetachedOpensIt() {
        Worktree bisecting = new Worktree(
                        MAIN_PATH, Option.of(new Head.Detached(new Sha("ffff"))), true, true, Option.none(), false)
                .withBusyWith(Option.of(FEATURE));
        Model model = Update.update(
                        model(List.of(IDEA)), new LoadMsg.WorktreesLoaded(List.of(bisecting, LINKED, MISSING)))
                .model();

        Next next = Update.update(model, new OpenInEditorMsg.BranchRequested(FEATURE));

        assertEquals(List.of(new OpenInEditorCmd(IDEA, MAIN_PATH.toString())), next.cmds());
    }

    @Test
    void aBranchAnotherWorktreeRebasesWithItsHeadDetachedOpensThatOne() {
        Worktree rebasing = new Worktree(
                        LINKED_PATH, Option.of(new Head.Detached(new Sha("1111"))), false, false, Option.none(), false)
                .withBusyWith(Option.of(FEATURE));
        Model model = Update.update(model(List.of(IDEA)), new LoadMsg.WorktreesLoaded(List.of(MAIN, rebasing, MISSING)))
                .model();

        Next next = Update.update(model, new OpenInEditorMsg.BranchRequested(FEATURE));

        assertEquals(List.of(new OpenInEditorCmd(IDEA, LINKED_PATH.toString())), next.cmds());
    }

    @Test
    void aTerminalEditorRunsWithTheTerminalHandedOverInTheWorktree() {
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
        Env env = new Env(StubGit.of().withEditor(recording(events)), StubGitHub.of())
                .withApp(Repositories.NONE, terminal);

        Msg result = new OpenInEditorCmd(NVIM, LINKED_PATH.toString()).run(env);

        assertEquals(java.util.List.of("leave", "open nvim /repos/app-taken in /repos/app-taken", "come back"), events);
        assertInstanceOf(OperationMsg.Succeeded.class, result);
    }

    @Test
    void aGuiEditorIsLeftRunningWithoutTheTerminal() {
        java.util.List<String> events = new ArrayList<>();
        Terminal terminal = new Terminal() {
            @Override
            public <T> T handOver(Supplier<T> program) {
                events.add("leave");
                return program.get();
            }
        };
        Env env = new Env(StubGit.of().withEditor(recording(events)), StubGitHub.of())
                .withApp(Repositories.NONE, terminal);

        Msg result = new OpenInEditorCmd(IDEA, LINKED_PATH.toString()).run(env);

        assertEquals(java.util.List.of("launch idea /repos/app-taken in /repos/app-taken"), events);
        assertInstanceOf(OperationMsg.Succeeded.class, result);
    }

    @Test
    void anEditorThatCannotStartIsReportedForTheWorktree() {
        Env env = new Env(
                StubGit.of().withEditor(new StubEditor() {
                    @Override
                    public Either<GitFailure, List<ExecutedCommand>> launch(Path directory, List<String> commandLine) {
                        return Either.left(new GitFailure(
                                new GitError.EditorNotStarted("idea", new java.io.IOException("not found")),
                                List.empty()));
                    }
                }),
                StubGitHub.of());
        OpenInEditorCmd cmd = new OpenInEditorCmd(IDEA, LINKED_PATH.toString());

        Model failed = Update.update(model(List.of(IDEA)), cmd.run(env)).model();

        assertEquals(
                Option.of("Opening /repos/app-taken in the editor failed: Could not start the editor \"idea\": "
                        + "not found"),
                errorText(failed));
    }

    /**
     * An editor that records what it was asked to open, and where.
     */
    private static StubEditor recording(java.util.List<String> events) {
        return new StubEditor() {
            @Override
            public Either<GitFailure, List<ExecutedCommand>> open(Path directory, List<String> commandLine) {
                events.add("open " + commandLine.mkString(" ") + " in " + directory);
                return Either.right(List.of(new ExecutedCommand(commandLine)));
            }

            @Override
            public Either<GitFailure, List<ExecutedCommand>> launch(Path directory, List<String> commandLine) {
                events.add("launch " + commandLine.mkString(" ") + " in " + directory);
                return Either.right(List.of(new ExecutedCommand(commandLine)));
            }
        };
    }
}
