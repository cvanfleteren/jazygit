package net.vanfleteren.jazygit.changes.edit;

import static net.vanfleteren.jazygit.state.TestModels.loaded;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.Tuple;
import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.function.Supplier;
import net.vanfleteren.jazygit.gh.StubGitHub;
import net.vanfleteren.jazygit.gh.model.ReviewThread;
import net.vanfleteren.jazygit.gh.model.ReviewThread.Side;
import net.vanfleteren.jazygit.git.Git;
import net.vanfleteren.jazygit.git.StubGit;
import net.vanfleteren.jazygit.git.files.StubEditor;
import net.vanfleteren.jazygit.git.model.DiffText;
import net.vanfleteren.jazygit.git.model.HiddenHunks.Ranges;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.process.GitFailure;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.settings.EditorProgram;
import net.vanfleteren.jazygit.settings.Editors;
import net.vanfleteren.jazygit.settings.RecentRepositories;
import net.vanfleteren.jazygit.settings.SettingsStore;
import net.vanfleteren.jazygit.state.AreaId;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Repositories;
import net.vanfleteren.jazygit.state.Terminal;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;
import net.vanfleteren.jazygit.state.diff.Anchor;
import net.vanfleteren.jazygit.state.diff.DiffCursorAt;
import net.vanfleteren.jazygit.state.diff.Scrolled;
import net.vanfleteren.jazygit.state.diff.ScrolledContent;
import net.vanfleteren.jazygit.state.operation.OperationMsg;
import org.junit.jupiter.api.Test;

/**
 * Opening a file in an editor the user set to open files, at a line (ADR 145).
 */
class OpenFileUpdateTest {

    private static final EditorProgram CODE = new EditorProgram(
            "Code",
            Option.of(List.of("code", EditorProgram.DIR)),
            Option.of(List.of("code", "-g", "{file}:{line}")),
            false,
            Option.of('c'));
    private static final EditorProgram NVIM =
            EditorProgram.ofFile("Neovim", List.of("nvim", "+{line}", "{file}"), true, Option.none());
    private static final EditorProgram IDEA =
            EditorProgram.ofDir("IntelliJ", List.of("idea", EditorProgram.DIR), false, Option.none());

    private static final AreaId AREA = new AreaId("stash-changes");
    /**
     * Line 10 of a file of 30 changed, as git shows it with three lines of context.
     */
    private static final DiffText CHANGES = DiffText.of("diff --git a/a.txt b/a.txt\n--- a/a.txt\n+++ b/a.txt\n"
            + "@@ -7,7 +7,7 @@\n"
            + List.rangeClosed(7, 13)
                    .map(number -> number == 10 ? "-line10\n+changed\n" : " line" + number + "\n")
                    .mkString());

    private static final ScrolledContent CONTENT = new ScrolledContent.StashChanges(new Sha("5555"), CHANGES);

    private static Model model(EditorProgram... editors) {
        return loaded().mapPreferences(preferences ->
                preferences.withSettings(preferences.settings().withEditors(new Editors(List.of(editors)))));
    }

    /**
     * What {@code e} on {@code cursor} in the changes of the stash runs, with Neovim the one editor that opens files.
     */
    private static List<?> opened(Anchor cursor) {
        return Update.update(model(NVIM), new EditMsg.AtCursor(at(cursor))).cmds();
    }

    private static DiffCursorAt at(Anchor cursor) {
        return at(CONTENT, cursor);
    }

    private static DiffCursorAt at(ScrolledContent content, Anchor cursor) {
        return new DiffCursorAt(
                AREA,
                new Scrolled(content, 0, 0, Option.of(cursor), Option.none()),
                cursor,
                Option.none(),
                Option.none(),
                Option.none());
    }

    @Test
    void withoutAnEditorSetToOpenFilesTheFileOpensInGitsEditor() {
        Next next = Update.update(model(IDEA), new EditMsg.Requested("src/A.java"));

        assertEquals(List.of(new EditCmd(List.of("src/A.java"))), next.cmds());
    }

    @Test
    void withOneEditorSetToOpenFilesTheFileOpensInItAtItsStart() {
        Next next = Update.update(model(IDEA, NVIM), new EditMsg.Requested("src/A.java"));

        assertEquals(List.of(new OpenFileCmd(NVIM, "src/A.java", 1)), next.cmds());
    }

    @Test
    void withMoreTheEditorsThatOpenFilesAreOfferedWithTheirLetters() {
        Next next = Update.update(model(IDEA, CODE, NVIM), new EditMsg.Requested("src/A.java"));

        assertEquals(List.empty(), next.cmds());
        assertEquals(
                Option.of(new EditPopup("src/A.java", 1, List.of(Tuple.of('c', CODE), Tuple.of('1', NVIM)))),
                next.model().popup());
    }

    @Test
    void choosingAnEditorClosesTheMenuAndOpensTheFileInIt() {
        Model offered = Update.update(model(CODE, NVIM), new EditMsg.AtCursor(at(right(12))))
                .model();

        Next chosen = Update.update(offered, new EditMsg.Chosen(CODE));

        assertEquals(List.of(new OpenFileCmd(CODE, "a.txt", 12)), chosen.cmds());
        assertEquals(Option.none(), chosen.model().popup());
    }

    @Test
    void fromTheDiffTheFileOpensAtTheLineOfTheCursor() {
        assertEquals(
                List.of(new OpenFileCmd(NVIM, "a.txt", 12)),
                Update.update(model(NVIM), new EditMsg.AtCursor(at(right(12)))).cmds());
    }

    @Test
    void aRemovedLineOpensWhereItWasInTheNewFile() {
        Anchor.OnLine removed = new Anchor.OnLine("a.txt", Side.LEFT, 10);

        assertEquals(
                List.of(new OpenFileCmd(NVIM, "a.txt", 10)),
                Update.update(model(NVIM), new EditMsg.AtCursor(at(removed))).cmds());
    }

    @Test
    void aContextLineOnTheLeftOfTheSplitViewOpensAtItsOwnLineOfTheNewFile() {
        // A context line is a stop on the left by its number in the old file (ADR 139); here the same as in the new.
        assertEquals(List.of(new OpenFileCmd(NVIM, "a.txt", 9)), opened(new Anchor.OnLine("a.txt", Side.LEFT, 9)));
    }

    @Test
    void aRemovedLineAtTheEndOfTheFileOpensAtTheLastLineBeforeIt() {
        DiffText removedLast = DiffText.of(
                "diff --git a/a.txt b/a.txt\n--- a/a.txt\n+++ b/a.txt\n@@ -1,3 +1,2 @@\n one\n two\n-three\n");
        Anchor.OnLine three = new Anchor.OnLine("a.txt", Side.LEFT, 3);

        assertEquals(
                List.of(new OpenFileCmd(NVIM, "a.txt", 2)),
                Update.update(
                                model(NVIM),
                                new EditMsg.AtCursor(
                                        at(new ScrolledContent.StashChanges(new Sha("6666"), removedLast), three)))
                        .cmds());
    }

    @Test
    void onAMarkerOfFilteredHunksItOpensAtTheFirstLineOfItsFirstHunk() {
        Anchor.Filtered filtered =
                new Anchor.Filtered("a.txt", List.of(new Ranges(20, 2, 21, 3), new Ranges(40, 1, 42, 1)), false);

        assertEquals(List.of(new OpenFileCmd(NVIM, "a.txt", 21)), opened(filtered));
    }

    @Test
    void onARowOfJsonRenderedItOpensAtTheLineItWasDrawnFrom() {
        Anchor.Node node = new Anchor.Node("a.txt", "/name", right(11));

        assertEquals(List.of(new OpenFileCmd(NVIM, "a.txt", 11)), opened(node));
    }

    @Test
    void onAThreadOrACommentItOpensAtTheLineOfTheThread() {
        Anchor.Thread thread = new Anchor.Thread(new ReviewThread.Id("t1"), Option.of(right(12)));
        Anchor.Comment comment = new Anchor.Comment(new ReviewThread.Comment.Id("c2"), thread);

        assertEquals(List.of(new OpenFileCmd(NVIM, "a.txt", 12)), opened(thread));
        assertEquals(List.of(new OpenFileCmd(NVIM, "a.txt", 12)), opened(comment));
    }

    @Test
    void aThreadOnNoLineHasNoFileToOpen() {
        Anchor.Thread thread = new Anchor.Thread(new ReviewThread.Id("t1"), Option.none());

        assertEquals(Option.none(), EditUpdate.file(thread));
        assertEquals(List.empty(), opened(thread));
    }

    @Test
    void onAMarkerItOpensAtTheFirstLineItLeavesOutAndOnTheBarOfAFileAtItsStart() {
        Anchor.Gap below = new Anchor.Gap("a.txt", 14, Option.none());

        assertEquals(
                List.of(new OpenFileCmd(NVIM, "a.txt", 14)),
                Update.update(model(NVIM), new EditMsg.AtCursor(at(below))).cmds());
        assertEquals(
                List.of(new OpenFileCmd(NVIM, "a.txt", 1)),
                Update.update(model(NVIM), new EditMsg.AtCursor(at(new Anchor.File("a.txt"))))
                        .cmds());
    }

    @Test
    void beforeTheFirstFileThereIsNothingToOpen() {
        assertEquals(Option.none(), EditUpdate.file(new Anchor.Lead()));
        assertEquals(
                List.empty(),
                Update.update(model(NVIM), new EditMsg.AtCursor(at(new Anchor.Lead())))
                        .cmds());
    }

    @Test
    void theEditorRunsInTheWorktreeOnTheFileByItsWholePath() {
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
        ExecutedCommand ran = ExecutedCommand.of("nvim", "+3", "/repos/app/src/A.java");
        Git git = StubGit.of(Path.of("/repos/app")).withEditor(new StubEditor() {
            @Override
            public Either<GitFailure, List<ExecutedCommand>> open(Path directory, List<String> commandLine) {
                events.add(directory + ": " + commandLine.mkString(" "));
                return Either.right(List.of(ran));
            }
        });
        OpenFileCmd cmd = new OpenFileCmd(NVIM, "src/A.java", 3);

        assertEquals(new OperationMsg.Succeeded(cmd, List.of(ran)), cmd.run(env(git, terminal)));
        assertEquals(java.util.List.of("leave", "/repos/app: nvim +3 /repos/app/src/A.java", "come back"), events);
    }

    @Test
    void aGuiEditorIsStartedAndLeftRunning() {
        java.util.List<String> launched = new ArrayList<>();
        Git git = StubGit.of(Path.of("/repos/app")).withEditor(new StubEditor() {
            @Override
            public Either<GitFailure, List<ExecutedCommand>> launch(Path directory, List<String> commandLine) {
                launched.add(commandLine.mkString(" "));
                return Either.right(List.empty());
            }
        });

        new OpenFileCmd(CODE, "src/A.java", 7).run(env(git, Terminal.NONE));

        assertEquals(java.util.List.of("code -g /repos/app/src/A.java:7"), launched);
    }

    private static Anchor.OnLine right(int number) {
        return new Anchor.OnLine("a.txt", Side.RIGHT, number);
    }

    private static Env env(Git git, Terminal terminal) {
        return new Env(git, StubGitHub.of(), SettingsStore.NONE, RecentRepositories.NONE, Repositories.NONE, terminal);
    }
}
