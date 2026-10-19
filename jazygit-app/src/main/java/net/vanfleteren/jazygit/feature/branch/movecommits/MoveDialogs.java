package net.vanfleteren.jazygit.feature.branch.movecommits;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.ui.CommitsPanel;
import net.vanfleteren.jazygit.ui.PopupSlot;
import net.vanfleteren.jazygit.ui.Theme;
import net.vanfleteren.jazygit.ui.widgets.ChoiceDialog;
import net.vanfleteren.jazygit.ui.widgets.ChoiceDialog.Choice;
import net.vanfleteren.jazygit.ui.widgets.PickDialog;
import net.vanfleteren.jazygit.ui.widgets.TextInputDialog;

/**
 * The popups of moving commits to another branch: which branch, or the name of a new one and where it starts,
 * and whether to force-push the branch they are taken off.
 */
public final class MoveDialogs {

    /**
     * Asks where the commits move to: a new branch, the first option, or one of the other local branches. One that
     * is checked out in another worktree is marked as such, as it cannot be checked out here.
     */
    public static final PickDialog.Spec<MoveTargetPopup, Option<Branch.Name>> TARGET = new PickDialog.Spec<>(
            "move-commits-target",
            MoveTargetPopup.class,
            popup -> Messages.get(
                    "dialog.moveCommitsTarget.title",
                    popup.request().range().commits(),
                    popup.request().from().value()),
            popup -> popup.branches()
                    .map(branch -> new PickDialog.Row<>(
                            Option.of(branch.name()),
                            List.of(
                                    PickDialog.Cell.of(branch.name().value()),
                                    PickDialog.Cell.styled(
                                            branch.checkedOutElsewhere() ? Messages.get("branches.worktree") : "",
                                            Theme.Classes.DIM))))
                    .prepend(new PickDialog.Row<>(
                            Option.none(),
                            List.of(
                                    PickDialog.Cell.of(Messages.get("dialog.moveCommitsTarget.newBranch")),
                                    PickDialog.Cell.of("")))),
            MoveMsg.TargetChosen::new,
            MoveMsg.TargetCancelled::new);

    /**
     * Asks for the name of the new branch. A blank name, or that of a branch that exists, keeps it open.
     */
    public static final TextInputDialog.Spec<MoveNamePopup> NAME = new TextInputDialog.Spec<>(
            "move-commits-name",
            MoveNamePopup.class,
            popup -> Messages.get(
                    "dialog.moveCommits.title",
                    popup.request().range().commits(),
                    popup.request().from().value()),
            Option.of(Messages.get("dialog.newBranch.placeholder")),
            MoveMsg.NameConfirmed::new,
            MoveMsg.NameCancelled::new);

    /**
     * Asks where the new branch starts: where the commits are, the first option, or a branch they are copied onto.
     */
    public static final PickDialog.Spec<MoveBasePopup, MoveBase> BASE = new PickDialog.Spec<>(
            "move-commits-base",
            MoveBasePopup.class,
            popup -> Messages.get("dialog.moveCommitsBase.title", popup.name().value()),
            popup -> popup.request()
                    .bases()
                    .map(base -> new PickDialog.Row<>(base, List.of(PickDialog.Cell.of(label(popup, base))))),
            MoveMsg.BaseChosen::new,
            MoveMsg.BaseCancelled::new);

    /**
     * Warns that commits are pushed already, and asks whether to force-push the branch they are taken off.
     */
    public static final ChoiceDialog.Spec<MovePushedPopup> PUSHED = new ChoiceDialog.Spec<>(
            "move-commits-pushed",
            MovePushedPopup.class,
            popup -> Messages.get(
                    "dialog.moveCommitsPushed.title",
                    popup.request().range().pushed(),
                    popup.request()
                            .upstream()
                            .map(upstream -> upstream.shortName())
                            .getOrElse("")),
            List.of(
                    new Choice<MovePushedPopup>(
                            'f',
                            popup -> Messages.get(
                                    popup.request().fromIsDefault()
                                            ? "dialog.moveCommitsPushed.forcePushDefault"
                                            : "dialog.moveCommitsPushed.forcePush",
                                    popup.request().from().value()),
                            () -> new MoveMsg.PushedConfirmed(true)),
                    new Choice<MovePushedPopup>(
                            'm',
                            popup -> Messages.get(
                                    "dialog.moveCommitsPushed.moveOnly",
                                    popup.request().from().value()),
                            () -> new MoveMsg.PushedConfirmed(false))),
            MoveMsg.PushedCancelled::new);

    private MoveDialogs() {}

    private static String label(MoveBasePopup popup, MoveBase base) {
        return switch (base) {
            case MoveBase.Parent() ->
                Messages.get(
                        "dialog.moveCommitsBase.parent",
                        popup.request().oldest().shortSha());
            case MoveBase.Onto(var branch) -> branch.name();
        };
    }

    public static List<PopupSlot> popups(Consumer<Msg> dispatch) {
        return List.of(
                PopupSlot.returningTo(CommitsPanel.ID, new PickDialog<>(TARGET, dispatch)),
                PopupSlot.returningTo(CommitsPanel.ID, new TextInputDialog<>(NAME, dispatch)),
                PopupSlot.returningTo(CommitsPanel.ID, new PickDialog<>(BASE, dispatch)),
                PopupSlot.returningTo(CommitsPanel.ID, new ChoiceDialog<>(PUSHED, dispatch)));
    }
}
