package net.vanfleteren.jazygit.features.midway.resolve;

import io.vavr.collection.List;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.core.git.model.Conflict;
import net.vanfleteren.jazygit.core.git.model.MarkedFile;
import net.vanfleteren.jazygit.features.changes.stage.MarkersLeftPopup;
import net.vanfleteren.jazygit.features.changes.stage.StageMsg;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.ui.PopupSlot;
import net.vanfleteren.jazygit.kernel.ui.files.FilesPanel;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;
import net.vanfleteren.jazygit.kernel.ui.widgets.ChoiceDialog;
import net.vanfleteren.jazygit.kernel.ui.widgets.ChoiceDialog.Choice;

/**
 * The choices for resolving a file in conflict, and the question staging asks of files with conflict markers left in
 * them (ADR 198).
 */
public final class ResolveDialogs {

    /**
     * How to resolve the file of the popup: keep one side, or edit it.
     */
    public static final ChoiceDialog.Spec<ResolvePopup> RESOLVE = new ChoiceDialog.Spec<>(
            "resolve",
            ResolvePopup.class,
            popup -> Messages.get("midway.dialog.resolve.title", popup.file().path()),
            List.of(
                    new Choice<ResolvePopup>(
                                    'o',
                                    popup -> keepLabel(
                                            popup.sides().ours(),
                                            popup.file().conflict().get(),
                                            Conflict.Version.OURS),
                                    () -> new ResolveMsg.Chosen(Conflict.Version.OURS))
                            .withDetails(popup -> keepDetails(
                                    popup.sides(), popup.file().conflict().get(), Conflict.Version.OURS)),
                    new Choice<ResolvePopup>(
                                    't',
                                    popup -> keepLabel(
                                            popup.sides().theirs(),
                                            popup.file().conflict().get(),
                                            Conflict.Version.THEIRS),
                                    () -> new ResolveMsg.Chosen(Conflict.Version.THEIRS))
                            .withDetails(popup -> keepDetails(
                                    popup.sides(), popup.file().conflict().get(), Conflict.Version.THEIRS)),
                    new Choice<ResolvePopup>('e', "midway.dialog.resolve.edit", ResolveMsg.EditChosen::new)
                            .withDetails("midway.dialog.resolve.edit.details")));

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
                PopupSlot.returningTo(FilesPanel.PANE, new ChoiceDialog<>(MARKERS_LEFT, dispatch)));
    }
}
