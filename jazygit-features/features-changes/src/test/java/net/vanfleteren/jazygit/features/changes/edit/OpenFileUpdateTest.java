package net.vanfleteren.jazygit.features.changes.edit;

import static net.vanfleteren.jazygit.kernel.state.TestModels.loaded;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.Tuple;
import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.nio.file.Path;
import java.util.ArrayList;
import net.vanfleteren.jazygit.core.git.StubGit;
import net.vanfleteren.jazygit.core.git.model.Conflict;
import net.vanfleteren.jazygit.core.git.model.DiffText;
import net.vanfleteren.jazygit.core.git.model.FileEntry;
import net.vanfleteren.jazygit.core.git.model.HiddenHunks.Ranges;
import net.vanfleteren.jazygit.core.git.model.RepoStatus;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.Head;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.kernel.settings.EditorProgram;
import net.vanfleteren.jazygit.kernel.settings.Editors;
import net.vanfleteren.jazygit.kernel.settings.Settings;
import net.vanfleteren.jazygit.kernel.state.AreaId;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Events;
import net.vanfleteren.jazygit.kernel.state.KernelEvent;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Phrase;
import net.vanfleteren.jazygit.kernel.state.Reactions;
import net.vanfleteren.jazygit.kernel.state.Update;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.diff.Anchor;
import net.vanfleteren.jazygit.kernel.state.diff.DiffCursorAt;
import net.vanfleteren.jazygit.kernel.state.diff.DiffSide;
import net.vanfleteren.jazygit.kernel.state.diff.Remark;
import net.vanfleteren.jazygit.kernel.state.diff.RemarkThread;
import net.vanfleteren.jazygit.kernel.state.diff.Scrolled;
import net.vanfleteren.jazygit.kernel.state.diff.ScrolledContent;
import net.vanfleteren.jazygit.kernel.state.launch.ProgramFailure;
import net.vanfleteren.jazygit.kernel.state.launch.ProgramLauncher;
import net.vanfleteren.jazygit.kernel.state.load.LoadMsg;
import net.vanfleteren.jazygit.kernel.state.operation.OperationMsg;
import net.vanfleteren.jazygit.kernel.state.popup.ListCursor;
import net.vanfleteren.jazygit.kernel.state.popup.Popup;
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
                preferences.withSettings(preferences.settings().with(Settings.EDITORS, new Editors(List.of(editors)))));
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
    void aFileInConflictIsReadForItsFirstConflictBeforeItOpens() {
        Next next = Update.update(inConflict(model(NVIM)), new EditMsg.Requested("a.txt"));

        assertEquals(List.of(new ReadFirstConflictCmd("a.txt")), next.cmds());
    }

    @Test
    void aFileInConflictOpensAtItsFirstConflict() {
        Next next = Update.update(model(NVIM), new EditMsg.FirstConflictRead("a.txt", Option.of(7)));

        assertEquals(List.of(new OpenFileCmd(NVIM, "a.txt", 7)), next.cmds());
    }

    @Test
    void aFileInConflictWithoutMarkersLeftOpensAtItsStart() {
        Next next = Update.update(model(NVIM), new EditMsg.FirstConflictRead("a.txt", Option.none()));

        assertEquals(List.of(new OpenFileCmd(NVIM, "a.txt", 1)), next.cmds());
    }

    @Test
    void gitsEditorOpensAFileInConflictAtItsStartAsItTakesNoLine() {
        Next next = Update.update(model(), new EditMsg.FirstConflictRead("a.txt", Option.of(7)));

        assertEquals(List.of(new EditCmd(List.of("a.txt"))), next.cmds());
    }

    @Test
    void aFileAnotherFeatureAsksForOpensAtTheLineItAsks() {
        Next next = asked(model(NVIM), KernelEvent.FileToOpen.at("a.txt", 9));

        assertEquals(List.of(new OpenFileCmd(NVIM, "a.txt", 9)), next.cmds());
    }

    @Test
    void aFileAnotherFeatureAsksForWithoutALineOpensAsEOpensIt() {
        Next next = asked(inConflict(model(NVIM)), KernelEvent.FileToOpen.of("a.txt"));

        assertEquals(List.of(new ReadFirstConflictCmd("a.txt")), next.cmds());
    }

    @Test
    void theMenuGoesOverACoverablePopupWithTheTitleAskedAndBackToItOnceAnEditorIsChosen() {
        Model over = model(CODE, NVIM).openPopup(new Shown(true)).withListCursor(ListCursor.START.at(4));

        Next asked = asked(over, KernelEvent.FileToOpen.at("a.txt", 9).titled(Phrase.verbatim("the log")));

        assertEquals(List.empty(), asked.cmds());
        assertEquals(
                Option.of(new EditPopup(
                        "a.txt",
                        9,
                        List.of(Tuple.of('c', CODE), Tuple.of('1', NVIM)),
                        Option.of(Phrase.verbatim("the log")),
                        Option.of(new Shown(true)),
                        ListCursor.START.at(4))),
                asked.model().popup());

        Next chosen = Update.update(asked.model(), new EditMsg.Chosen(NVIM));

        assertEquals(List.of(new OpenFileCmd(NVIM, "a.txt", 9)), chosen.cmds());
        assertEquals(Option.of(new Shown(true)), chosen.model().popup());
        assertEquals(ListCursor.START.at(4), chosen.model().listCursor());
    }

    @Test
    void escapeOnTheMenuGoesBackToThePopupUnderIt() {
        Model asked = asked(model(CODE, NVIM).openPopup(new Shown(true)), KernelEvent.FileToOpen.at("a.txt", 9))
                .model();

        Next cancelled = Update.update(asked, new EditMsg.Cancelled());

        assertEquals(List.empty(), cancelled.cmds());
        assertEquals(Option.of(new Shown(true)), cancelled.model().popup());
    }

    @Test
    void escapeOnTheMenuOverNoPopupClosesIt() {
        Model offered =
                Update.update(model(CODE, NVIM), new EditMsg.Requested("a.txt")).model();

        assertEquals(
                Option.none(),
                Update.update(offered, new EditMsg.Cancelled()).model().popup());
    }

    @Test
    void overAPopupThatCannotBeCoveredTheFirstEditorOpensTheFile() {
        Next next = asked(model(CODE, NVIM).openPopup(new Shown(false)), KernelEvent.FileToOpen.at("a.txt", 9));

        assertEquals(List.of(new OpenFileCmd(CODE, "a.txt", 9)), next.cmds());
        assertEquals(Option.of(new Shown(false)), next.model().popup());
    }

    @Test
    void theFeatureAnswersTheFilesAskedForInTheKernel() {
        Model answering = model(NVIM).withReactions(new Reactions(EditFeature.FEATURE.reactions()));

        Next next = Events.happened(Next.of(answering), KernelEvent.FileToOpen.at("a.txt", 9));

        assertEquals(List.of(new OpenFileCmd(NVIM, "a.txt", 9)), next.cmds());
    }

    private static Next asked(Model model, KernelEvent.FileToOpen event) {
        return EditUpdate.reacting(model, event);
    }

    /**
     * {@code model} with {@code a.txt} in conflict.
     */
    private static Model inConflict(Model model) {
        RepoStatus status = new RepoStatus(
                Head.onBranch(new BranchName("main"), new Sha("aaaa")),
                List.of(FileEntry.conflicted("a.txt", new Conflict(Conflict.Side.UPDATED, Conflict.Side.UPDATED))));
        return Update.update(model, new LoadMsg.StatusLoaded(status)).model();
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
        Anchor.OnLine removed = new Anchor.OnLine("a.txt", DiffSide.OLD, 10);

        assertEquals(
                List.of(new OpenFileCmd(NVIM, "a.txt", 10)),
                Update.update(model(NVIM), new EditMsg.AtCursor(at(removed))).cmds());
    }

    @Test
    void aContextLineOnTheLeftOfTheSplitViewOpensAtItsOwnLineOfTheNewFile() {
        // A context line is a stop on the left by its number in the old file (ADR 139); here the same as in the new.
        assertEquals(List.of(new OpenFileCmd(NVIM, "a.txt", 9)), opened(new Anchor.OnLine("a.txt", DiffSide.OLD, 9)));
    }

    @Test
    void aRemovedLineAtTheEndOfTheFileOpensAtTheLastLineBeforeIt() {
        DiffText removedLast = DiffText.of(
                "diff --git a/a.txt b/a.txt\n--- a/a.txt\n+++ b/a.txt\n@@ -1,3 +1,2 @@\n one\n two\n-three\n");
        Anchor.OnLine three = new Anchor.OnLine("a.txt", DiffSide.OLD, 3);

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
        Anchor.Thread thread = new Anchor.Thread(new RemarkThread.Id("t1"), Option.of(right(12)));
        Anchor.Remark comment = new Anchor.Remark(new Remark.Id("c2"), thread);

        assertEquals(List.of(new OpenFileCmd(NVIM, "a.txt", 12)), opened(thread));
        assertEquals(List.of(new OpenFileCmd(NVIM, "a.txt", 12)), opened(comment));
    }

    @Test
    void aThreadOnNoLineHasNoFileToOpen() {
        Anchor.Thread thread = new Anchor.Thread(new RemarkThread.Id("t1"), Option.none());

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
    void theEditorRunsOnTheTerminalInTheWorktreeOnTheFileByItsWholePath() {
        java.util.List<String> events = new ArrayList<>();
        OpenFileCmd cmd = new OpenFileCmd(NVIM, "src/A.java", 3);

        assertEquals(
                new OperationMsg.Succeeded(cmd, List.of(ExecutedCommand.of("nvim", "+3", "/repos/app/src/A.java"))),
                cmd.run(env(recording(events))));
        assertEquals(java.util.List.of("run in /repos/app: nvim +3 /repos/app/src/A.java"), events);
    }

    @Test
    void aGuiEditorIsStartedAndLeftRunning() {
        java.util.List<String> events = new ArrayList<>();

        new OpenFileCmd(CODE, "src/A.java", 7).run(env(recording(events)));

        assertEquals(java.util.List.of("start in /repos/app: code -g /repos/app/src/A.java:7"), events);
    }

    private static Anchor.OnLine right(int number) {
        return new Anchor.OnLine("a.txt", DiffSide.NEW, number);
    }

    private static Env env(ProgramLauncher launcher) {
        return new Env(StubGit.of(Path.of("/repos/app"))).with(launcher);
    }

    /**
     * A launcher that records what it was asked to run or start, and where.
     */
    private static ProgramLauncher recording(java.util.List<String> events) {
        return new ProgramLauncher() {
            @Override
            public Either<ProgramFailure, ExecutedCommand> run(Path directory, List<String> commandLine) {
                events.add("run in " + directory + ": " + commandLine.mkString(" "));
                return Either.right(new ExecutedCommand(commandLine));
            }

            @Override
            public Either<ProgramFailure, ExecutedCommand> start(Path directory, List<String> commandLine) {
                events.add("start in " + directory + ": " + commandLine.mkString(" "));
                return Either.right(new ExecutedCommand(commandLine));
            }
        };
    }

    /**
     * A popup of another feature, which may go under the menu when {@code coverable}.
     */
    private record Shown(boolean coverable) implements Popup {}
}
