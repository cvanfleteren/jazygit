package net.vanfleteren.jazygit.feature.branch.stack;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.ui.BranchesPanel;
import net.vanfleteren.jazygit.ui.PopupSlot;
import net.vanfleteren.jazygit.ui.Theme;
import net.vanfleteren.jazygit.ui.widgets.ChoiceDialog;
import net.vanfleteren.jazygit.ui.widgets.ChoiceDialog.Choice;
import net.vanfleteren.jazygit.ui.widgets.ConfirmDialog;
import net.vanfleteren.jazygit.ui.widgets.PickDialog;

/**
 * The popups of making and unmaking a stack (ADR 73).
 */
public final class StackDialogs {

    /**
     * What can be done with the stack of the highlighted branch, or with the highlighted stack: only what applies.
     */
    public static final ChoiceDialog.Spec<StackPopups.Menu> MENU = new ChoiceDialog.Spec<>(
            "stack-menu",
            StackPopups.Menu.class,
            menu -> menu.stack()
                    .map(stack -> Messages.get("dialog.stack.title", String.valueOf(stack.number())))
                    .getOrElse(() -> Messages.get(
                            "dialog.stack.titleBranch",
                            menu.branch().map(branch -> branch.name().value()).getOrElse(""))),
            List.of(
                    new Choice<StackPopups.Menu>('m', "dialog.stack.make", StackMsg.MakeChosen::new)
                            .shownWhen((model, menu) -> menu.canMake()),
                    new Choice<StackPopups.Menu>('o', "dialog.stack.stackOn", StackMsg.StackOnChosen::new)
                            .shownWhen((model, menu) -> menu.canStackOn()),
                    new Choice<StackPopups.Menu>('a', "dialog.stack.add", StackMsg.AddChosen::new)
                            .shownWhen((model, menu) -> menu.canAdd()),
                    new Choice<StackPopups.Menu>('u', "dialog.stack.unstack", StackMsg.UnstackChosen::new)
                            .shownWhen((model, menu) -> menu.canUnstack())));

    /**
     * The pull requests the one of the branch can be stacked on: in no stack, or on the top of one.
     */
    public static final PickDialog.Spec<StackPopups.StackOn, StackPopups.Target> STACK_ON = new PickDialog.Spec<>(
            "stack-on",
            StackPopups.StackOn.class,
            popup -> Messages.get(
                    "dialog.stackOn.title",
                    String.valueOf(popup.pullRequest().number()),
                    popup.branch().name().value()),
            popup -> popup.targets()
                    .map(target -> new PickDialog.Row<>(
                            target,
                            List.of(
                                    PickDialog.Cell.of(target.branch().name().value()),
                                    PickDialog.Cell.of(
                                            "#" + target.pullRequest().number()),
                                    PickDialog.Cell.styled(
                                            target.pullRequest()
                                                    .stack()
                                                    .map(stack -> Messages.get(
                                                            "dialog.stackOn.top", String.valueOf(stack.number())))
                                                    .getOrElse(""),
                                            Theme.Classes.DIM)))),
            StackMsg.TargetChosen::new);

    /**
     * Asks before taking a stack apart on GitHub.
     */
    public static final ConfirmDialog.Spec<StackPopups.Unstack> UNSTACK = new ConfirmDialog.Spec<>(
            "stack-unstack",
            StackPopups.Unstack.class,
            popup -> Messages.get(
                    "dialog.unstack.title", String.valueOf(popup.stack().number())),
            StackDialogs::unstacked,
            () -> new Msg.PopupConfirmed(StackPopups.Unstack.class));

    /**
     * Asks before changing the bases of pull requests on GitHub to make a stack of them.
     */
    public static final ConfirmDialog.Spec<StackPopups.Confirm> CONFIRM = new ConfirmDialog.Spec<>(
            "stack-confirm",
            StackPopups.Confirm.class,
            confirm -> Messages.get(
                    "dialog.makeStack.confirmTitle",
                    confirm.cmd().pullRequests().map(number -> "#" + number).mkString(" ")),
            StackDialogs::changes,
            () -> new Msg.PopupConfirmed(StackPopups.Confirm.class));

    private StackDialogs() {}

    public static List<PopupSlot> popups(Consumer<Msg> dispatch) {
        return List.of(
                PopupSlot.returningTo(BranchesPanel.ID, new ChoiceDialog<>(MENU, dispatch)),
                PopupSlot.returningTo(BranchesPanel.ID, new StackPickDialog(dispatch)),
                PopupSlot.returningTo(BranchesPanel.ID, new ConfirmDialog<>(CONFIRM, dispatch)),
                PopupSlot.returningTo(BranchesPanel.ID, new PickDialog<>(STACK_ON, dispatch)),
                PopupSlot.returningTo(BranchesPanel.ID, new ConfirmDialog<>(UNSTACK, dispatch)));
    }

    /**
     * What stays of a stack taken apart: its open pull requests, unlinked, and its merged ones, in it on GitHub.
     */
    static String unstacked(StackPopups.Unstack popup) {
        String open = Messages.get("dialog.unstack.message", String.valueOf(popup.open()));
        return popup.merged() == 0
                ? open
                : open + "\n" + Messages.get("dialog.unstack.merged", String.valueOf(popup.merged()));
    }

    /**
     * The bases that change, one per line: {@code #97: develop → feat/a}.
     */
    static String changes(StackPopups.Confirm confirm) {
        List<String> changes = confirm.cmd()
                .changes()
                .map(change -> Messages.get(
                        "dialog.makeStack.change",
                        String.valueOf(change.number()),
                        change.from(),
                        change.to().name()));
        List<String> diverged = confirm.diverged()
                .map(branch -> Messages.get(
                        "dialog.makeStack.behind",
                        branch.branch().value(),
                        String.valueOf(branch.behind()),
                        branch.below().value()));
        return List.of(
                        Option.when(
                                !changes.isEmpty(),
                                () -> Messages.get("dialog.makeStack.confirmMessage", changes.mkString("\n"))),
                        Option.when(
                                !diverged.isEmpty(),
                                () -> Messages.get("dialog.makeStack.divergedMessage", diverged.mkString("\n"))))
                .flatMap(section -> section)
                .mkString("\n\n");
    }
}
