package net.vanfleteren.jazygit.features.midway.resolve;

import static net.vanfleteren.jazygit.kernel.state.TestModels.loaded;
import static net.vanfleteren.jazygit.kernel.state.TestModels.localBranchesRead;
import static net.vanfleteren.jazygit.kernel.ui.text.ErrorTexts.errorText;
import static net.vanfleteren.jazygit.kernel.ui.text.NoticeTexts.noticeText;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.model.ChangeType;
import net.vanfleteren.jazygit.core.git.model.Conflict;
import net.vanfleteren.jazygit.core.git.model.ConflictFile;
import net.vanfleteren.jazygit.core.git.model.ConflictTools;
import net.vanfleteren.jazygit.core.git.model.FileEntry;
import net.vanfleteren.jazygit.core.git.model.Fixtures;
import net.vanfleteren.jazygit.core.git.model.MarkedFile;
import net.vanfleteren.jazygit.core.git.model.RepoStatus;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.Head;
import net.vanfleteren.jazygit.core.repo.InProgress;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.features.changes.edit.EditMsg;
import net.vanfleteren.jazygit.features.changes.edit.EditorSettings;
import net.vanfleteren.jazygit.features.changes.edit.FileEditor;
import net.vanfleteren.jazygit.features.changes.edit.ReadFirstConflictCmd;
import net.vanfleteren.jazygit.features.changes.stage.MarkersLeftPopup;
import net.vanfleteren.jazygit.features.changes.stage.StageMsg;
import net.vanfleteren.jazygit.features.midway.resolve.ResolveCmd.Keep;
import net.vanfleteren.jazygit.features.midway.resolve.ResolveCmd.Restore;
import net.vanfleteren.jazygit.kernel.settings.EditorProgram;
import net.vanfleteren.jazygit.kernel.settings.Editors;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Update;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.load.LoadMsg;
import net.vanfleteren.jazygit.kernel.state.popup.PopupMsg;
import org.junit.jupiter.api.Test;

/**
 * Resolving a file in conflict by keeping one side of it, and bringing a conflict back (ADR 198).
 */
class ResolveUpdateTest {

    private static final Sha ONTO = new Sha("1111111111");
    private static final Sha MERGED = new Sha("2222222222");
    private static final Conflict BOTH = new Conflict(Conflict.Side.UPDATED, Conflict.Side.UPDATED);
    private static final FileEntry CONFLICTED = FileEntry.conflicted("a.txt", BOTH);
    private static final FileEntry MODIFIED = Fixtures.fileEntry("b.txt", ChangeType.MODIFIED);
    private static final InProgress REBASING =
            new InProgress.Rebasing(Option.of(new BranchName("feature")), ONTO, 2, 3);

    private static Model stopped(InProgress inProgress, Head head, List<String> resolved) {
        Model branches = localBranchesRead(loaded(), List.of(Fixtures.branch(new BranchName("main"), false, ONTO)))
                .model();
        RepoStatus status = new RepoStatus(head, List.of(CONFLICTED, MODIFIED), Option.of(inProgress), resolved);
        return Update.update(branches, new LoadMsg.StatusLoaded(status)).model();
    }

    private static Model rebasing() {
        return stopped(REBASING, new Head.Detached(ONTO), List.empty());
    }

    private static final ConflictTools ALL = new ConflictTools(true, false, true);

    /**
     * {@link #rebasing()} with the dialog open on {@code a.txt}, once what it can be handed to and its first conflict
     * were read.
     */
    private static Model asked() {
        return asked(ConflictFile.AS_WRITTEN);
    }

    /**
     * {@link #rebasing()} with the dialog open on {@code a.txt}, standing as {@code written} against git's conflict.
     */
    private static Model asked(ConflictFile written) {
        Model opened =
                Update.update(rebasing(), new ResolveMsg.Requested(CONFLICTED)).model();
        return Update.update(opened, new ResolveMsg.ToolsRead("a.txt", ALL, Option.of(4), written))
                .model();
    }

    @Test
    void openingTheDialogReadsWhatTheFileCanBeHandedToAndItsFirstConflict() {
        Next next = Update.update(rebasing(), new ResolveMsg.Requested(CONFLICTED));

        assertEquals(List.of(new ReadToolsCmd("a.txt")), next.cmds());
    }

    @Test
    void whatWasReadShowsInTheOpenDialog() {
        assertEquals(
                Option.of(List.of(ALL, Option.of(4))),
                asked().popup(ResolvePopup.class).map(popup -> List.of(popup.tools(), popup.firstConflict())));
    }

    @Test
    void whatWasReadOfAnotherFileLeavesTheDialogAsItIs() {
        Model opened =
                Update.update(rebasing(), new ResolveMsg.Requested(CONFLICTED)).model();

        Model read = Update.update(
                        opened, new ResolveMsg.ToolsRead("other.txt", ALL, Option.of(4), ConflictFile.AS_WRITTEN))
                .model();

        assertEquals(opened.popup(ResolvePopup.class), read.popup(ResolvePopup.class));
    }

    @Test
    void theMergeToolIsHandedTheFileAndTheDialogCloses() {
        Next next = Update.update(asked(), new ResolveMsg.MergeToolChosen());

        assertEquals(Option.none(), next.model().popup(ResolvePopup.class));
        assertEquals(List.of(new ResolveCmd.MergeTool("a.txt", ALL)), next.cmds());
    }

    @Test
    void mergirafIsHandedTheFileAndTheDialogCloses() {
        Next next = Update.update(asked(), new ResolveMsg.MergirafChosen());

        assertEquals(Option.none(), next.model().popup(ResolvePopup.class));
        assertEquals(List.of(new ResolveCmd.Solve("a.txt", true)), next.cmds());
    }

    @Test
    void mergirafReadsAFileWithItsBasesAsItIs() {
        Next next = Update.update(asked(ConflictFile.WITH_BASE), new ResolveMsg.MergirafChosen());

        assertEquals(List.of(new ResolveCmd.Solve("a.txt", false)), next.cmds());
    }

    @Test
    void mergirafOnAnEditedFileAsksFirstAsItLosesTheEdits() {
        Next next = Update.update(asked(ConflictFile.EDITED), new ResolveMsg.MergirafChosen());

        assertEquals(List.empty(), next.cmds());
        assertEquals(Option.none(), next.model().popup(ResolvePopup.class));
        assertEquals(Option.of(new SolveOverEditsPopup("a.txt")), next.model().popup(SolveOverEditsPopup.class));
    }

    @Test
    void solvingOverTheEditsOnceConfirmedWritesTheConflictAgain() {
        Model asking = Update.update(asked(ConflictFile.EDITED), new ResolveMsg.MergirafChosen())
                .model();

        Next next = Update.update(asking, new PopupMsg.Confirmed(SolveOverEditsPopup.class));

        assertEquals(List.of(new ResolveCmd.Solve("a.txt", true)), next.cmds());
    }

    @Test
    void solvingOverTheEditsCancelledDoesNothing() {
        Model asking = Update.update(asked(ConflictFile.EDITED), new ResolveMsg.MergirafChosen())
                .model();

        Next next = Update.update(asking, new PopupMsg.Dismissed(SolveOverEditsPopup.class));

        assertEquals(List.empty(), next.cmds());
        assertEquals(Option.none(), next.model().popup());
    }

    @Test
    void editingOpensTheFileAtItsFirstConflict() {
        Next next = Update.update(asked(), new ResolveMsg.EditChosen());

        assertEquals(
                Update.update(rebasing(), new EditMsg.RequestedAt("a.txt", 4)).cmds(), next.cmds());
    }

    @Test
    void aFileHandedBackInConflictWithoutMarkersSaysSpaceStagesIt() {
        Next next = Update.update(
                rebasing(),
                new ResolveMsg.HandedBack(
                        new ResolveCmd.Solve("a.txt", true), List.empty(), "a.txt", true, Option.none()));

        assertEquals(Option.of("a.txt has no conflict markers left: space stages it"), noticeText(next.model()));
    }

    @Test
    void aFileHandedBackWithMarkersSaysWhereTheFirstIs() {
        Next next = Update.update(
                rebasing(),
                new ResolveMsg.HandedBack(
                        new ResolveCmd.Solve("a.txt", true),
                        List.empty(),
                        "a.txt",
                        true,
                        Option.of(new MarkedFile("a.txt", List.of(12, 30)))));

        assertEquals(Option.of("a.txt still has 2 conflicts, the first at line 12"), noticeText(next.model()));
    }

    @Test
    void aFileHandedBackResolvedSaysNothing() {
        Next next = Update.update(
                rebasing(),
                new ResolveMsg.HandedBack(
                        new ResolveCmd.MergeTool("a.txt", ALL), List.empty(), "a.txt", false, Option.none()));

        assertEquals(Option.none(), noticeText(next.model()));
    }

    @Test
    void keepingASideOfAFileInConflictKeepsIt() {
        Next next = Update.update(rebasing(), new ResolveMsg.Kept(CONFLICTED, Conflict.Version.THEIRS));

        assertEquals(List.of(new Keep("a.txt", BOTH, Conflict.Version.THEIRS)), next.cmds());
    }

    @Test
    void keepingASideOfAFileNotInConflictDoesNothing() {
        Model model = rebasing();

        assertEquals(
                List.empty(),
                Update.update(model, new ResolveMsg.Kept(MODIFIED, Conflict.Version.OURS))
                        .cmds());
    }

    @Test
    void theDialogNamesTheSidesOfARebaseByWhatTheyAre() {
        Next next = Update.update(rebasing(), new ResolveMsg.Requested(CONFLICTED));

        assertEquals(
                Option.of(new ResolvePopup(
                        CONFLICTED,
                        new Sides(new SideName.Branch(new BranchName("main")), new SideName.Rebased()),
                        new FileEditor.GitEditor(),
                        ConflictTools.NONE,
                        Option.none(),
                        ConflictFile.AS_WRITTEN)),
                next.model().popup(ResolvePopup.class));
    }

    @Test
    void theDialogNamesTheSideOfARebaseByTheCommitItStoppedAtOnceItsStepsAreKnown() {
        Sha stoppedAt = new Sha("4".repeat(40));
        Model branches = localBranchesRead(loaded(), List.of(Fixtures.branch(new BranchName("main"), false, ONTO)))
                .model();
        RepoStatus status = new RepoStatus(
                new Head.Detached(ONTO),
                List.of(CONFLICTED),
                Option.of(REBASING),
                List.empty(),
                Option.of(new net.vanfleteren.jazygit.core.repo.Plan(
                        ONTO,
                        net.vanfleteren.jazygit.core.repo.Plan.Step.pick(stoppedAt, "feat"),
                        List.empty(),
                        2,
                        3)));
        Model model = Update.update(branches, new LoadMsg.StatusLoaded(status)).model();

        assertEquals(
                Option.of(new Sides(new SideName.Branch(new BranchName("main")), new SideName.Commit(stoppedAt))),
                Update.update(model, new ResolveMsg.Requested(CONFLICTED))
                        .model()
                        .popup(ResolvePopup.class)
                        .map(ResolvePopup::sides));
    }

    @Test
    void theDialogNamesTheSidesOfAMergeAfterTheBranches() {
        Model model = stopped(
                new InProgress.Merging(MERGED),
                Head.onBranch(new BranchName("feature"), new Sha("3333333333")),
                List.empty());

        assertEquals(
                Option.of(new Sides(new SideName.Branch(new BranchName("feature")), new SideName.Commit(MERGED))),
                Update.update(model, new ResolveMsg.Requested(CONFLICTED))
                        .model()
                        .popup(ResolvePopup.class)
                        .map(ResolvePopup::sides));
    }

    @Test
    void theDialogOpensOnAConflictLeftWithNoOperationInProgress() {
        Model branches = localBranchesRead(loaded(), List.of(Fixtures.branch(new BranchName("main"), false, ONTO)))
                .model();
        RepoStatus status = new RepoStatus(
                Head.onBranch(new BranchName("feature"), new Sha("3333333333")),
                List.of(CONFLICTED),
                Option.none(),
                List.empty());
        Model model = Update.update(branches, new LoadMsg.StatusLoaded(status)).model();

        assertEquals(
                Option.of(new Sides(new SideName.Branch(new BranchName("feature")), new SideName.Incoming())),
                Update.update(model, new ResolveMsg.Requested(CONFLICTED))
                        .model()
                        .popup(ResolvePopup.class)
                        .map(ResolvePopup::sides));
    }

    @Test
    void theDialogSaysGitsEditorWithoutAnEditorSet() {
        assertEquals(
                Option.of(new FileEditor.GitEditor()),
                Update.update(rebasing(), new ResolveMsg.Requested(CONFLICTED))
                        .model()
                        .popup(ResolvePopup.class)
                        .map(ResolvePopup::editor));
    }

    @Test
    void theDialogSaysTheCommandOfTheOneEditorSetToOpenFiles() {
        EditorProgram nvim = EditorProgram.ofFile("Neovim", List.of("nvim", "+{line}", "{file}"), true, Option.none());
        Model model = withEditors(rebasing(), nvim);

        assertEquals(
                Option.of(new FileEditor.One(nvim)),
                Update.update(model, new ResolveMsg.Requested(CONFLICTED))
                        .model()
                        .popup(ResolvePopup.class)
                        .map(ResolvePopup::editor));
    }

    @Test
    void theDialogSaysAnEditorIsChosenOfSeveralSet() {
        EditorProgram nvim = EditorProgram.ofFile("Neovim", List.of("nvim", "{file}"), true, Option.none());
        EditorProgram code = EditorProgram.ofFile("Code", List.of("code", "{file}"), false, Option.none());
        Model model = withEditors(rebasing(), nvim, code);

        assertEquals(
                Option.of(new FileEditor.Several(new Editors(List.of(nvim, code)))),
                Update.update(model, new ResolveMsg.Requested(CONFLICTED))
                        .model()
                        .popup(ResolvePopup.class)
                        .map(ResolvePopup::editor));
    }

    private static Model withEditors(Model model, EditorProgram... editors) {
        return model.mapPreferences(preferences -> preferences.withSettings(
                preferences.settings().with(EditorSettings.EDITORS, new Editors(List.of(editors)))));
    }

    @Test
    void choosingASideInTheDialogKeepsItAndClosesIt() {
        Model asked =
                Update.update(rebasing(), new ResolveMsg.Requested(CONFLICTED)).model();

        Next next = Update.update(asked, new ResolveMsg.Chosen(Conflict.Version.OURS));

        assertEquals(Option.none(), next.model().popup(ResolvePopup.class));
        assertEquals(List.of(new Keep("a.txt", BOTH, Conflict.Version.OURS)), next.cmds());
    }

    @Test
    void editingFromTheDialogBeforeItsFirstConflictIsReadReadsItFirst() {
        Model asked =
                Update.update(rebasing(), new ResolveMsg.Requested(CONFLICTED)).model();

        Next next = Update.update(asked, new ResolveMsg.EditChosen());

        assertEquals(Option.none(), next.model().popup(ResolvePopup.class));
        assertEquals(List.of(new ReadFirstConflictCmd("a.txt")), next.cmds());
    }

    @Test
    void aResolvedFileHasItsConflictBroughtBack() {
        Model model = stopped(REBASING, new Head.Detached(ONTO), List.of("c.txt"));

        assertEquals(
                List.of(new Restore("c.txt")),
                Update.update(model, new ResolveMsg.RestoreRequested("c.txt")).cmds());
    }

    @Test
    void aFileThatWasNotResolvedHasNoConflictToBringBack() {
        Next next = Update.update(rebasing(), new ResolveMsg.RestoreRequested("b.txt"));

        assertEquals(List.empty(), next.cmds());
        assertEquals(Option.of("b.txt has no conflict resolved since the operation stopped"), errorText(next.model()));
    }

    @Test
    void aFileWithMarkersLeftIsResolvedFromTheQuestionStagingAsks() {
        Model asked = Update.update(
                        rebasing(),
                        new StageMsg.MarkersRead(
                                List.of(CONFLICTED), Option.none(), List.of(new MarkedFile("a.txt", List.of(4)))))
                .model();

        Next next = Update.update(asked, new ResolveMsg.MarkedKept(Conflict.Version.THEIRS));

        assertEquals(Option.none(), next.model().popup(MarkersLeftPopup.class));
        assertEquals(List.of(new Keep("a.txt", BOTH, Conflict.Version.THEIRS)), next.cmds());
    }

    @Test
    void aFileWithMarkersLeftIsOpenedFromTheQuestionStagingAsks() {
        Model asked = Update.update(
                        rebasing(),
                        new StageMsg.MarkersRead(
                                List.of(CONFLICTED), Option.none(), List.of(new MarkedFile("a.txt", List.of(4)))))
                .model();

        Next next = Update.update(asked, new ResolveMsg.MarkedEditChosen());

        assertEquals(Option.none(), next.model().popup(MarkersLeftPopup.class));
        assertEquals(
                Update.update(rebasing(), new EditMsg.RequestedAt("a.txt", 4)).cmds(), next.cmds());
    }
}
