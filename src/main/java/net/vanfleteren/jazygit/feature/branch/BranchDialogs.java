package net.vanfleteren.jazygit.feature.branch;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.DeleteScope;
import net.vanfleteren.jazygit.git.model.MergeMode;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.ui.widgets.ChoiceDialog;
import net.vanfleteren.jazygit.ui.widgets.ChoiceDialog.Choice;
import net.vanfleteren.jazygit.ui.widgets.ConfirmDialog;
import net.vanfleteren.jazygit.ui.widgets.TextInputDialog;

/**
 * The popups of the branch operations.
 */
public final class BranchDialogs {

    /**
     * Asks for the name of a new branch. A blank name keeps it open.
     */
    public static final TextInputDialog.Spec<NewBranchPopup> NEW_BRANCH = new TextInputDialog.Spec<>(
            "new-branch-name",
            NewBranchPopup.class,
            popup -> Messages.get("dialog.newBranch.title", popup.label()),
            Option.of(Messages.get("dialog.newBranch.placeholder")),
            NewBranchPopup::name,
            NewBranchMsg.Confirmed::new,
            NewBranchMsg.Cancelled::new);

    /**
     * Asks for the new name of a branch, starting out with the one it has. A blank name keeps it open.
     */
    public static final TextInputDialog.Spec<RenameBranchPopup> RENAME_BRANCH = new TextInputDialog.Spec<>(
            "rename-branch",
            RenameBranchPopup.class,
            popup -> Messages.get("dialog.renameBranch.title", popup.branch().value()),
            Option.of(Messages.get("dialog.renameBranch.placeholder")),
            popup -> popup.branch().value(),
            RenameBranchMsg.Confirmed::new,
            RenameBranchMsg.Cancelled::new);

    /**
     * Asks where to push a branch without an upstream, as {@code <remote> <branch>}; it starts out with the
     * branch of the same name on {@code origin}. What cannot be read that way keeps it open.
     */
    public static final TextInputDialog.Spec<SetUpstreamPopup> SET_UPSTREAM = new TextInputDialog.Spec<>(
            "set-upstream",
            SetUpstreamPopup.class,
            popup -> Messages.get("dialog.setUpstream.title"),
            Option.none(),
            popup -> new RemoteBranch(RemoteBranch.DEFAULT_REMOTE, popup.branch()).text(),
            PushMsg.UpstreamConfirmed::new,
            PushMsg.UpstreamCancelled::new);

    /**
     * Warns that a branch has diverged from its remote branch, and asks to force push it.
     */
    public static final ConfirmDialog.Spec<ForcePushPopup> FORCE_PUSH = new ConfirmDialog.Spec<>(
            "force-push",
            ForcePushPopup.class,
            popup -> Messages.get("dialog.forcePush.title", popup.branch().value()),
            popup -> Messages.get("dialog.forcePush.message"),
            PushMsg.ForceConfirmed::new,
            PushMsg.ForceCancelled::new);

    /**
     * Asks to take the uncommitted changes along to the branch being checked out, through a stash.
     */
    public static final ConfirmDialog.Spec<StashCheckoutPopup> STASH_CHECKOUT = new ConfirmDialog.Spec<>(
            "stash-checkout",
            StashCheckoutPopup.class,
            popup -> Messages.get("dialog.stashCheckout.title", popup.branch().value()),
            popup -> Messages.get("dialog.stashCheckout.message"),
            CheckoutMsg.StashConfirmed::new,
            CheckoutMsg.StashCancelled::new);

    /**
     * Tells that the branch to check out is checked out in another worktree, and asks to switch to that one.
     */
    public static final ConfirmDialog.Spec<SwitchWorktreePopup> SWITCH_WORKTREE = new ConfirmDialog.Spec<>(
            "switch-worktree",
            SwitchWorktreePopup.class,
            popup -> Messages.get("dialog.switchWorktree.title", popup.branch().value()),
            popup ->
                    Messages.get("dialog.switchWorktree.message", popup.branch().value(), popup.worktree()),
            CheckoutMsg.SwitchWorktreeConfirmed::new,
            CheckoutMsg.SwitchWorktreeCancelled::new);

    /**
     * Asks where to delete a branch. The options that act on the remote only apply when the branch has a
     * remote branch that can be deleted, the ones that act locally when no other worktree has it checked out.
     */
    public static final ChoiceDialog.Spec<DeleteBranchPopup> DELETE_BRANCH = new ChoiceDialog.Spec<>(
            "delete-branch",
            DeleteBranchPopup.class,
            popup -> Messages.get("dialog.deleteBranch.title", popup.branch().value()),
            List.of(
                    new Choice<>(
                            'd',
                            "dialog.deleteBranch.local",
                            () -> new DeleteBranchMsg.Chosen(DeleteScope.LOCAL),
                            BranchDialogs::localDeletable),
                    new Choice<>(
                            'r',
                            "dialog.deleteBranch.remote",
                            () -> new DeleteBranchMsg.Chosen(DeleteScope.REMOTE),
                            BranchDialogs::remoteDeletable),
                    new Choice<>(
                            'b',
                            "dialog.deleteBranch.both",
                            () -> new DeleteBranchMsg.Chosen(DeleteScope.BOTH),
                            (model, popup) -> localDeletable(model, popup) && remoteDeletable(model, popup))),
            DeleteBranchMsg.Cancelled::new);

    /**
     * Asks to confirm deleting a remote branch on its remote.
     */
    public static final ConfirmDialog.Spec<DeleteRemoteBranchPopup> DELETE_REMOTE_BRANCH = new ConfirmDialog.Spec<>(
            "delete-remote-branch",
            DeleteRemoteBranchPopup.class,
            popup -> Messages.get(
                    "dialog.deleteRemoteBranch.title", popup.branch().shortName()),
            popup -> Messages.get(
                    "dialog.deleteRemoteBranch.message", popup.branch().shortName()),
            DeleteBranchMsg.RemoteConfirmed::new,
            DeleteBranchMsg.RemoteCancelled::new);

    /**
     * Asks how to merge the highlighted branch into the checked out one.
     */
    public static final ChoiceDialog.Spec<MergePopup> MERGE = new ChoiceDialog.Spec<>(
            "merge",
            MergePopup.class,
            popup -> Messages.get(
                    "dialog.merge.title", popup.branch().value(), popup.into().value()),
            List.of(
                    new Choice<>('m', "dialog.merge.fastForward", () -> new MergeMsg.Chosen(MergeMode.FAST_FORWARD)),
                    new Choice<>('n', "dialog.merge.mergeCommit", () -> new MergeMsg.Chosen(MergeMode.MERGE_COMMIT)),
                    new Choice<>('s', "dialog.merge.squash", () -> new MergeMsg.Chosen(MergeMode.SQUASH)),
                    new Choice<>(
                            'S',
                            "dialog.merge.squashAndCommit",
                            () -> new MergeMsg.Chosen(MergeMode.SQUASH_AND_COMMIT))),
            MergeMsg.Cancelled::new);

    /**
     * Asks how to rebase the checked out branch onto the highlighted one.
     */
    public static final ChoiceDialog.Spec<RebasePopup> REBASE = new ChoiceDialog.Spec<>(
            "rebase",
            RebasePopup.class,
            popup -> Messages.get("dialog.rebase.title", popup.branch().value()),
            List.of(new Choice<RebasePopup>(
                    's',
                    popup -> Messages.get("dialog.rebase.simple", popup.onto().value()),
                    RebaseMsg.Chosen::new)),
            RebaseMsg.Cancelled::new);

    private BranchDialogs() {}

    private static boolean remoteDeletable(Model model, DeleteBranchPopup popup) {
        return DeleteBranchUpdate.remoteDeletable(model, popup.branch());
    }

    private static boolean localDeletable(Model model, DeleteBranchPopup popup) {
        return DeleteBranchUpdate.localDeletable(model, popup.branch());
    }
}
