package net.vanfleteren.jazygit.feature.branch.stack;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.gh.model.GhBranch;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.popup.PopupMsg;
import net.vanfleteren.jazygit.ui.BranchesPanel;
import net.vanfleteren.jazygit.ui.PopupSlot;
import net.vanfleteren.jazygit.ui.TextWidth;
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
                            .shownWhen((model, menu) -> menu.canMake())
                            .withDetails("dialog.stack.make.details"),
                    new Choice<StackPopups.Menu>('o', "dialog.stack.stackOn", StackMsg.StackOnChosen::new)
                            .shownWhen((model, menu) -> menu.canStackOn())
                            .withDetails("dialog.stack.stackOn.details"),
                    new Choice<StackPopups.Menu>('a', "dialog.stack.add", StackMsg.AddChosen::new)
                            .shownWhen((model, menu) -> menu.canAdd())
                            .withDetails("dialog.stack.add.details"),
                    // Disabled, with why, when there is nothing to check out or update (ADR 99).
                    new Choice<StackPopups.Menu>(
                                    'c',
                                    StackDialogs::checkoutLabel,
                                    StackMsg.CheckoutChosen::new,
                                    (model, menu) -> menu.canCheckOut())
                            .shownWhen((model, menu) -> menu.canUnstack())
                            .withDetails(StackDialogs::checkoutDetails),
                    new Choice<StackPopups.Menu>('r', "dialog.stack.rebase", StackMsg.RebaseChosen::new)
                            .shownWhen((model, menu) -> menu.canUnstack())
                            .withDetails("dialog.stack.rebase.details"),
                    new Choice<StackPopups.Menu>('p', "dialog.stack.push", StackMsg.PushChosen::new)
                            .shownWhen((model, menu) -> menu.canUnstack())
                            .withDetails("dialog.stack.push.details"),
                    new Choice<StackPopups.Menu>('u', "dialog.stack.unstack", StackMsg.UnstackChosen::new)
                            .shownWhen((model, menu) -> menu.canUnstack())
                            .withDetails("dialog.stack.unstack.details")));

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
            () -> new PopupMsg.Confirmed(StackPopups.Unstack.class));

    /**
     * Asks before rebasing a stack and pushing it: its branches, what onto, and the merges it drops (ADR 74). Enter
     * rebases and pushes, {@code l} only rebases.
     */
    static final KeyedConfirmDialog.Spec<StackPopups.RebaseConfirm> REBASE = new KeyedConfirmDialog.Spec<>(
            "stack-rebase",
            StackPopups.RebaseConfirm.class,
            popup -> Messages.get(
                    "dialog.rebaseStack.title",
                    String.valueOf(popup.cmd().rebase().stack())),
            StackDialogs::rebased,
            'l',
            popup -> true,
            StackMsg.RebaseLocallyChosen::new,
            "dialog.rebaseStack.hint");

    /**
     * Asks before linking branches as a stack; when a branch is not on the one below, {@code r} links them and rebases
     * the stack once GitHub lists it (ADR 73, ADR 74).
     */
    static final KeyedConfirmDialog.Spec<StackPopups.Confirm> LINK = new KeyedConfirmDialog.Spec<>(
            "stack-confirm",
            StackPopups.Confirm.class,
            confirm -> Messages.get(
                    "dialog.makeStack.confirmTitle",
                    confirm.cmd().pullRequests().map(number -> "#" + number).mkString(" ")),
            StackDialogs::changes,
            'r',
            confirm -> !confirm.diverged().isEmpty(),
            StackMsg.LinkAndRebaseChosen::new,
            "dialog.makeStack.hintRebase");

    private StackDialogs() {}

    // The menu, and what it asks for a stack, open from the pull requests screen too (ADR 99): they give the focus back
    // to where it was. Making a stack and stacking on start from a branch.
    public static List<PopupSlot> popups(Consumer<Msg> dispatch) {
        return List.of(
                PopupSlot.returningToFocus(new ChoiceDialog<>(MENU, dispatch)),
                PopupSlot.returningTo(BranchesPanel.ID, new StackPickDialog(dispatch)),
                PopupSlot.returningTo(BranchesPanel.ID, new KeyedConfirmDialog<>(LINK, dispatch)),
                PopupSlot.returningTo(BranchesPanel.ID, new PickDialog<>(STACK_ON, dispatch)),
                PopupSlot.returningToFocus(new ConfirmDialog<>(UNSTACK, dispatch)),
                PopupSlot.returningToFocus(new KeyedConfirmDialog<>(REBASE, dispatch)),
                PopupSlot.returningToFocus(new StackCheckoutDialog(dispatch)));
    }

    /**
     * The label of checking out a stack in its menu: checking it out while a pull request of it has no branch here,
     * else updating its branches (ADR 99).
     */
    static String checkoutLabel(StackPopups.Menu menu) {
        return Messages.get(
                menu.checkout() == StackCheckout.Status.CHECK_OUT || menu.checkout() == StackCheckout.Status.NOTHING
                        ? "dialog.stack.checkout"
                        : "dialog.stack.update");
    }

    /**
     * What checking out a stack does, or why it is disabled.
     */
    static String checkoutDetails(StackPopups.Menu menu) {
        return Messages.get(
                switch (menu.checkout()) {
                    case CHECK_OUT -> "dialog.stack.checkout.details";
                    case UPDATE -> "dialog.stack.update.details";
                    case UP_TO_DATE -> "dialog.stack.update.upToDate";
                    case NOTHING -> "dialog.stack.checkout.nothing";
                });
    }

    /**
     * What checking out a stack does to the branch of each of its open pull requests, one per line, the bottom first;
     * the pull requests left out; then which branch can be checked out, the one Enter checks out marked.
     */
    static String checkedOut(StackCheckout checkout) {
        int width = checkout.lines()
                .map(line -> TextWidth.of(line.branch().value()))
                .max()
                .getOrElse(0);
        int numbers = checkout.lines()
                .map(line -> String.valueOf(line.number()).length() + 1)
                .max()
                .getOrElse(0);
        List<String> lines = checkout.lines()
                .map(line -> pad("#" + line.number(), numbers) + "  "
                        + pad(line.branch().value(), width) + "  "
                        + Messages.get(
                                "dialog.checkoutStack." + kindKey(line.kind()),
                                line.commits(),
                                line.upstream().shortName()));
        List<String> skipped = Option.when(
                        !checkout.skipped().isEmpty(),
                        () -> Messages.get(
                                "dialog.checkoutStack.skipped",
                                checkout.skipped().map(number -> "#" + number).mkString(", ")))
                .toList();
        return List.of(lines.appendAll(skipped).mkString("\n"), landings(checkout))
                .mkString("\n\n");
    }

    /**
     * Which branch can be checked out once done, each after its key, the one Enter checks out marked; or why nothing
     * is done.
     */
    private static String landings(StackCheckout checkout) {
        if (!checkout.startable()) {
            return Messages.get("dialog.checkoutStack.refused");
        }
        StackCheckout.Landing preferred = checkout.preferred();
        List<String> options = checkout.top()
                .map(top -> marked(
                        Messages.get("dialog.checkoutStack.top", top.value()), preferred == StackCheckout.Landing.TOP))
                .toList()
                .appendAll(checkout.asked()
                        .map(asked -> marked(
                                Messages.get(
                                        "dialog.checkoutStack.asked",
                                        String.valueOf(checkout.on().get()),
                                        asked.value()),
                                preferred == StackCheckout.Landing.ASKED)))
                .append(Messages.get("dialog.checkoutStack.stay"));
        return Messages.get("dialog.checkoutStack.then") + "\n" + options.mkString("\n");
    }

    private static String marked(String option, boolean preferred) {
        return preferred ? option + "  " + Messages.get("dialog.checkoutStack.enter") : option;
    }

    private static String pad(String text, int width) {
        return text + " ".repeat(Math.max(0, width - TextWidth.of(text)));
    }

    private static String kindKey(StackCheckout.Kind kind) {
        return switch (kind) {
            case MADE -> "made";
            case FAST_FORWARDED -> "fastForwarded";
            case UP_TO_DATE -> "upToDate";
            case OWN_COMMITS -> "ownCommits";
            case CHECKED_OUT_HERE -> "checkedOutHere";
            case OTHER_WORKTREE -> "otherWorktree";
            case FORK -> "fork";
            case NO_UPSTREAM -> "noUpstream";
            case OTHER_UPSTREAM -> "otherUpstream";
        };
    }

    /**
     * What a rebase of a stack does: its branches from the bottom up, what onto, and the merges it drops, one per line.
     */
    static String rebased(StackPopups.RebaseConfirm popup) {
        StackRebaseCmd.RebaseStack cmd = popup.cmd();
        String branches = Messages.get(
                "dialog.rebaseStack.message",
                cmd.rebase().branches().map(branch -> branch.value()).mkString(", "),
                cmd.rebase().base().label());
        List<String> dropped = cmd.commits()
                .flatMap(layer -> layer.merges()
                        .map(merge -> Messages.get(
                                "dialog.rebaseStack.dropped", layer.branch().value(), merge.subject())));
        return dropped.isEmpty() ? branches : branches + "\n\n" + dropped.mkString("\n");
    }

    /**
     * What stays of a stack taken apart: its open pull requests, unlinked, and its merged ones, in it on GitHub.
     */
    static String unstacked(StackPopups.Unstack popup) {
        String open = Messages.get("dialog.unstack.message", popup.open());
        return popup.staying() == 0 ? open : open + "\n" + Messages.get("dialog.unstack.staying", popup.staying());
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
                        change.from().map(GhBranch::name).getOrElse("?"),
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
