package net.vanfleteren.jazygit.features.midway.resolve;

import io.vavr.collection.List;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.core.git.model.Conflict;
import net.vanfleteren.jazygit.core.git.model.ConflictTools;
import net.vanfleteren.jazygit.core.git.model.MarkedFile;
import net.vanfleteren.jazygit.core.process.CommandLine;
import net.vanfleteren.jazygit.features.changes.stage.MarkersLeftPopup;
import net.vanfleteren.jazygit.features.changes.stage.StageMsg;
import net.vanfleteren.jazygit.kernel.settings.EditorProgram;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.edit.FileEditor;
import net.vanfleteren.jazygit.kernel.ui.PopupSlot;
import net.vanfleteren.jazygit.kernel.ui.files.FilesPanel;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;
import net.vanfleteren.jazygit.kernel.ui.widgets.ChoiceDialog;
import net.vanfleteren.jazygit.kernel.ui.widgets.ChoiceDialog.Choice;
import net.vanfleteren.jazygit.kernel.ui.widgets.ConfirmDialog;

/**
 * The choices for resolving a file in conflict, and the question staging asks of files with conflict markers left in
 * them (ADR 198).
 */
public final class ResolveDialogs {

    /**
     * How to resolve the file of the popup: keep one side, edit it, or hand it to git's merge tool or to mergiraf when
     * there are (ADR 201); each choice tells what it does and the command it runs (ADR 200).
     */
    public static final ChoiceDialog.Spec<ResolvePopup> RESOLVE = new ChoiceDialog.Spec<>(
                    "resolve",
                    ResolvePopup.class,
                    popup -> Messages.get(
                            "midway.dialog.resolve.title", popup.file().path()),
                    List.of(
                            new Choice<ResolvePopup>(
                                            'o',
                                            popup -> keepLabel(
                                                    popup.sides().ours(),
                                                    popup.file().conflict().get(),
                                                    Conflict.Version.OURS),
                                            () -> new ResolveMsg.Chosen(Conflict.Version.OURS))
                                    .withDetails(popup -> keepDetails(
                                            popup.sides(),
                                            popup.file().conflict().get(),
                                            Conflict.Version.OURS))
                                    .withCommand(popup -> keepCommand(popup, Conflict.Version.OURS)),
                            new Choice<ResolvePopup>(
                                            't',
                                            popup -> keepLabel(
                                                    popup.sides().theirs(),
                                                    popup.file().conflict().get(),
                                                    Conflict.Version.THEIRS),
                                            () -> new ResolveMsg.Chosen(Conflict.Version.THEIRS))
                                    .withDetails(popup -> keepDetails(
                                            popup.sides(),
                                            popup.file().conflict().get(),
                                            Conflict.Version.THEIRS))
                                    .withCommand(popup -> keepCommand(popup, Conflict.Version.THEIRS)),
                            new Choice<ResolvePopup>('e', "midway.dialog.resolve.edit", ResolveMsg.EditChosen::new)
                                    .withDetails(ResolveDialogs::editDetails)
                                    .withCommand(ResolveDialogs::editCommand),
                            new Choice<ResolvePopup>(
                                            'g', "midway.dialog.resolve.mergeTool", ResolveMsg.MergeToolChosen::new)
                                    .shownWhen((model, popup) -> popup.tools()
                                            .mergesWithTool(
                                                    popup.file().conflict().get()))
                                    .withDetails("midway.dialog.resolve.mergeTool.details")
                                    .withCommand(popup -> CommandLine.format(
                                            popup.tools().mergeTool(popup.file().path()))),
                            new Choice<ResolvePopup>(
                                            'm', "midway.dialog.resolve.mergiraf", ResolveMsg.MergirafChosen::new)
                                    .shownWhen((model, popup) -> popup.tools()
                                            .solvesWithMergiraf(
                                                    popup.file().conflict().get()))
                                    .withDetails(ResolveDialogs::mergirafDetails)
                                    .withCommand(popup -> ConflictTools.mergiraf(
                                                    popup.file().path(), popup.rewrites())
                                            .map(CommandLine::format)
                                            .mkString(" && "))),
                    // Wide enough for what a choice does to take two lines or so, and its command one.
                    72)
            .inline();

    /**
     * Asks whether to solve a file with mergiraf though it was edited since git wrote its conflict, which writing it
     * again loses (ADR 201).
     */
    public static final ConfirmDialog.Spec<SolveOverEditsPopup> SOLVE_OVER_EDITS = ConfirmDialog.Spec.confirming(
            "solve-over-edits",
            SolveOverEditsPopup.class,
            popup -> Messages.get("midway.dialog.solveOverEdits.title", popup.path()),
            popup -> Messages.get("midway.dialog.solveOverEdits.message", popup.path()));

    /**
     * What to do with files asked to be staged that still have conflict markers: resolve the one file, edit it, or
     * stage them anyway.
     */
    public static final ChoiceDialog.Spec<MarkersLeftPopup> MARKERS_LEFT = new ChoiceDialog.Spec<>(
            "markers-left",
            MarkersLeftPopup.class,
            popup -> popup.marked().size() == 1
                    ? Messages.get(
                            "midway.dialog.markersLeft.title",
                            popup.marked().head().path())
                    : Messages.get(
                            "midway.dialog.markersLeft.titleMany",
                            popup.marked().size()),
            List.of(
                    new Choice<MarkersLeftPopup>(
                                    'e',
                                    popup -> Messages.get(
                                            "midway.dialog.markersLeft.edit",
                                            popup.marked().head().path()),
                                    ResolveMsg.MarkedEditChosen::new)
                            .withDetails(popup -> Messages.get(
                                    "midway.dialog.markersLeft.edit.details",
                                    lines(popup.marked().head()))),
                    new Choice<MarkersLeftPopup>(
                            'o',
                            "midway.dialog.markersLeft.keepOurs",
                            () -> new ResolveMsg.MarkedKept(Conflict.Version.OURS),
                            (model, popup) -> ResolveUpdate.marked(popup).isDefined()),
                    new Choice<MarkersLeftPopup>(
                            't',
                            "midway.dialog.markersLeft.keepTheirs",
                            () -> new ResolveMsg.MarkedKept(Conflict.Version.THEIRS),
                            (model, popup) -> ResolveUpdate.marked(popup).isDefined()),
                    new Choice<MarkersLeftPopup>('s', "midway.dialog.markersLeft.stage", StageMsg.StagedAnyway::new)
                            .withDetails("midway.dialog.markersLeft.stage.details")));

    private ResolveDialogs() {}

    /**
     * The commands keeping {@code version}'s side of the file of {@code popup} runs, as {@code CliIndex} runs them,
     * with the path quoted as a shell needs it: {@code git checkout --ours -- 'my notes.txt' && git add -- …}.
     */
    private static String keepCommand(ResolvePopup popup, Conflict.Version version) {
        return popup.file()
                .conflict()
                .get()
                .keeping(version)
                .map(arguments -> CommandLine.format(arguments
                        .prepend("git")
                        .append("--")
                        .append(popup.file().path())))
                .mkString(" && ");
    }

    /**
     * What solving the file of {@code popup} with mergiraf does: from the file as it is when it has its bases; from
     * the conflict written again otherwise, which loses what was edited of it, and says so (ADR 201).
     */
    private static String mergirafDetails(ResolvePopup popup) {
        return switch (popup.written()) {
            case WITH_BASE -> Messages.get("midway.dialog.resolve.mergiraf.details.withBase");
            case AS_WRITTEN -> Messages.get("midway.dialog.resolve.mergiraf.details");
            case EDITED -> Messages.get("midway.dialog.resolve.mergiraf.details.edited");
        };
    }

    /**
     * What editing the file of {@code popup} does: an editor set opens it at its first conflict, once that was read;
     * git's editor, which takes no line, at its start, and the dialog says where the conflict is (ADR 201).
     */
    private static String editDetails(ResolvePopup popup) {
        return popup.firstConflict()
                .map(line -> popup.editor() instanceof FileEditor.GitEditor
                        ? Messages.get("midway.dialog.resolve.edit.details.gitEditor", line)
                        : Messages.get("midway.dialog.resolve.edit.details.atConflict", line))
                .getOrElse(() -> Messages.get("midway.dialog.resolve.edit.details"));
    }

    /**
     * What editing the file of {@code popup} runs: the command of the one editor set, a choice of those set, or
     * git's editor, as {@code e} opens it (ADR 145).
     */
    private static String editCommand(ResolvePopup popup) {
        String path = CommandLine.format(List.of(popup.file().path()));
        return switch (popup.editor()) {
            // As e opens it, at its first conflict; the worktree is where the command runs.
            case FileEditor.One(EditorProgram editor) ->
                CommandLine.format(editor.fileCommandLine(
                                ".", popup.file().path(), popup.firstConflict().getOrElse(1))
                        .getOrElse(() -> List.of(editor.name(), popup.file().path())));
            case FileEditor.Several _ -> Messages.get("midway.dialog.resolve.edit.command.chosen", path);
            case FileEditor.GitEditor _ -> Messages.get("midway.dialog.resolve.edit.command", path);
        };
    }

    /**
     * Keeping a side, named by what it is; or, for a side that deleted the file, deleting it.
     */
    private static String keepLabel(SideName side, Conflict conflict, Conflict.Version version) {
        return conflict.did(version) == Conflict.Side.DELETED
                ? Messages.get("midway.dialog.resolve.delete", name(side))
                : Messages.get("midway.dialog.resolve.keep", name(side));
    }

    /**
     * What keeping a side drops: what the other side did to the file.
     */
    private static String keepDetails(Sides sides, Conflict conflict, Conflict.Version version) {
        SideName other = version == Conflict.Version.OURS ? sides.theirs() : sides.ours();
        Conflict.Version otherVersion =
                version == Conflict.Version.OURS ? Conflict.Version.THEIRS : Conflict.Version.OURS;
        return conflict.did(version) == Conflict.Side.DELETED
                ? Messages.get("midway.dialog.resolve.delete.details", name(other))
                : conflict.did(otherVersion) == Conflict.Side.DELETED
                        ? Messages.get("midway.dialog.resolve.keepDeleted.details", name(other))
                        : Messages.get("midway.dialog.resolve.keep.details", name(other));
    }

    /**
     * How a side is named: a branch by its name, a commit by its short sha.
     */
    static String name(SideName side) {
        return switch (side) {
            case SideName.Branch branch -> branch.name().value();
            case SideName.Commit commit -> commit.sha().shortSha();
            case SideName.Rebased rebased -> Messages.get("midway.side.rebased");
            case SideName.Incoming incoming -> Messages.get("midway.side.incoming");
        };
    }

    private static String lines(MarkedFile file) {
        return file.lines().map(String::valueOf).mkString(", ");
    }

    /**
     * The popups of this feature, which send what the user does to {@code dispatch}.
     */
    public static List<PopupSlot> popups(Consumer<Msg> dispatch) {
        return List.of(
                PopupSlot.returningTo(FilesPanel.PANE, new ChoiceDialog<>(RESOLVE, dispatch)),
                PopupSlot.returningTo(FilesPanel.PANE, new ChoiceDialog<>(MARKERS_LEFT, dispatch)),
                PopupSlot.returningTo(FilesPanel.PANE, new ConfirmDialog<>(SOLVE_OVER_EDITS, dispatch)));
    }
}
