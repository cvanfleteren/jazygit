package net.vanfleteren.jazygit.feature.branch.checkout;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.Worktree;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.ui.BranchesPanel;
import net.vanfleteren.jazygit.ui.PopupSlot;
import net.vanfleteren.jazygit.ui.widgets.ChoiceDialog;
import net.vanfleteren.jazygit.ui.widgets.ChoiceDialog.Choice;
import net.vanfleteren.jazygit.ui.widgets.ConfirmDialog;

/**
 * The popups of checking out a branch.
 */
public final class CheckoutDialogs {

    /**
     * Asks to take the uncommitted changes along to the branch being checked out, through a stash.
     */
    public static final ConfirmDialog.Spec<StashCheckoutPopup> STASH_CHECKOUT = ConfirmDialog.Spec.confirming(
            "stash-checkout",
            StashCheckoutPopup.class,
            popup -> Messages.get("dialog.stashCheckout.title", popup.branch().value()),
            popup -> Messages.get("dialog.stashCheckout.message"));

    /**
     * Tells that the branch to check out is checked out in another worktree, and asks to switch to that one.
     */
    public static final ConfirmDialog.Spec<SwitchWorktreePopup> SWITCH_WORKTREE = new ConfirmDialog.Spec<>(
            "switch-worktree",
            SwitchWorktreePopup.class,
            popup -> Messages.get("dialog.switchWorktree.title", popup.branch().value()),
            popup ->
                    Messages.get("dialog.switchWorktree.message", popup.branch().value(), popup.worktree()),
            CheckoutMsg.SwitchWorktreeConfirmed::new);

    /**
     * Asks where to check out, in a linked worktree: here, in the main worktree or in a new one (ADR 68). It opens
     * on the main worktree, or on a new one without, so that Enter pressed out of habit leaves the worktree shown as
     * it is.
     */
    public static final ChoiceDialog.Spec<CheckoutWherePopup> CHECKOUT_WHERE = new ChoiceDialog.Spec<>(
                    "checkout-where",
                    CheckoutWherePopup.class,
                    popup -> Messages.get(
                            "dialog.checkoutWhere.title", popup.request().label()),
                    List.of(
                            new Choice<CheckoutWherePopup>(
                                    'h',
                                    CheckoutDialogs::hereLabel,
                                    CheckoutMsg.HereChosen::new,
                                    (model, popup) -> !popup.here().isLocked()),
                            new Choice<CheckoutWherePopup>('m', CheckoutDialogs::mainLabel, CheckoutMsg.MainChosen::new)
                                    .shownWhen((model, popup) -> popup.main().isDefined()),
                            new Choice<CheckoutWherePopup>(
                                    'n', "dialog.checkoutWhere.newWorktree", CheckoutMsg.NewWorktreeChosen::new)))
            .openingAt(popup -> Option.of(popup.main().isDefined() ? 'm' : 'n'));

    private CheckoutDialogs() {}

    /**
     * The worktree shown, and the branch it has checked out; why it is locked instead, if it is.
     */
    private static String hereLabel(CheckoutWherePopup popup) {
        Worktree here = popup.here();
        return here.locked()
                .map(reason -> reason.isBlank()
                        ? Messages.get("dialog.checkoutWhere.hereLocked", here.name())
                        : Messages.get("dialog.checkoutWhere.hereLockedBecause", here.name(), reason))
                .getOrElse(() -> Messages.get("dialog.checkoutWhere.here", here.name(), checkedOut(here)));
    }

    /**
     * The main worktree, the branch it has checked out, and whether it has changes.
     */
    private static String mainLabel(CheckoutWherePopup popup) {
        return popup.main()
                .map(main -> Messages.get(
                        popup.mainChanged().getOrElse(false)
                                ? "dialog.checkoutWhere.mainChanged"
                                : "dialog.checkoutWhere.main",
                        main.name(),
                        checkedOut(main)))
                .getOrElse("");
    }

    private static String checkedOut(Worktree worktree) {
        return worktree.branch().map(BranchName::value).getOrElse(() -> Messages.get("dialog.checkoutWhere.noBranch"));
    }

    public static List<PopupSlot> popups(Consumer<Msg> dispatch) {
        return List.of(
                PopupSlot.returningTo(BranchesPanel.ID, new ConfirmDialog<>(STASH_CHECKOUT, dispatch)),
                // Checking out a pull request, on its own screen, offers it too.
                PopupSlot.returningToFocus(new ConfirmDialog<>(SWITCH_WORKTREE, dispatch)),
                // So does asking where to check out.
                PopupSlot.returningToFocus(new ChoiceDialog<>(CHECKOUT_WHERE, dispatch)));
    }
}
