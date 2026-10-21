package net.vanfleteren.jazygit.feature.branch.delete;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.branch.Branches;
import net.vanfleteren.jazygit.feature.branch.delete.DeleteBranchCmd.DeleteBranch;
import net.vanfleteren.jazygit.feature.branch.delete.DeleteBranchCmd.DeleteRemoteBranch;
import net.vanfleteren.jazygit.feature.branch.delete.DeleteBranchMsg.Cancelled;
import net.vanfleteren.jazygit.feature.branch.delete.DeleteBranchMsg.Chosen;
import net.vanfleteren.jazygit.feature.branch.delete.DeleteBranchMsg.Requested;
import net.vanfleteren.jazygit.feature.worktree.remove.RemoveWorktreeCmd;
import net.vanfleteren.jazygit.feature.worktree.remove.RemoveWorktreeUpdate;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.DeleteScope;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.git.model.Worktree;
import net.vanfleteren.jazygit.state.AppError;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * Deleting a branch.
 */
public final class DeleteBranchUpdate {

    private DeleteBranchUpdate() {}

    public static Next update(Model model, DeleteBranchMsg msg) {
        return switch (msg) {
            case Requested(Branch.Name branch) -> requested(model, branch);
            case Cancelled() -> Next.of(model.withoutPopup(DeleteBranchPopup.class));
            case Chosen(DeleteScope scope) -> chosen(model, scope);
            case DeleteBranchMsg.WithWorktreeChosen() ->
                Update.confirm(
                        model,
                        DeleteBranchPopup.class,
                        popup -> removableWorktree(model, popup.branch())
                                .map(worktree ->
                                        new RemoveWorktreeCmd(worktree.location(), false, Option.of(popup.branch()))));
            case DeleteBranchMsg.RemoteRequested(RemoteBranch branch) -> remoteRequested(model, branch);
            case DeleteBranchMsg.RemoteCancelled() -> Next.of(model.withoutPopup(DeleteRemoteBranchPopup.class));
            case DeleteBranchMsg.RemoteConfirmed() ->
                Update.confirm(
                        model,
                        DeleteRemoteBranchPopup.class,
                        popup -> Option.when(
                                remoteDeletable(model, popup.branch()), () -> new DeleteRemoteBranch(popup.branch())));
        };
    }

    /**
     * Only a known branch that is not checked out can be deleted, and not while it is being pushed or deleted. One
     * that is checked out in another worktree can be deleted on its remote, or locally once that worktree is
     * removed, when it can be; when neither is possible, that is said.
     */
    private static Next requested(Model model, Branch.Name branch) {
        Option<Worktree> worktree = removableWorktree(model, branch);
        return model.repo()
                .branch(branch)
                .filter(known -> !known.remoteDeletable() && worktree.isEmpty())
                .flatMap(known -> Branches.checkedOutElsewhere(model, DeleteBranchCmd.DELETE_BRANCH, branch))
                .map(error -> Next.of(model.fail(error)))
                .getOrElse(() -> Update.ask(
                        model,
                        Option.when(
                                Branches.isOtherIdleBranch(model, branch),
                                () -> new DeleteBranchPopup(branch, worktree.map(Worktree::name)))));
    }

    /**
     * The other worktree that has {@code branch} checked out, if one does and it can be removed, so that the branch
     * can be deleted once it is gone.
     */
    public static Option<Worktree> removableWorktree(Model model, Branch.Name branch) {
        return model.repo()
                .branch(branch)
                .flatMap(Branch::worktreeLocation)
                .flatMap(location -> model.repo().worktree(location))
                .filter(worktree -> RemoveWorktreeUpdate.notRemovable(worktree).isEmpty());
    }

    private static Next chosen(Model model, DeleteScope scope) {
        // Deleting on the remote is not possible for a local-only branch, nor for the default one; deleting it
        // locally is not possible while another worktree has it checked out.
        return Update.confirm(
                model,
                DeleteBranchPopup.class,
                popup -> model.repo()
                        .branch(popup.branch())
                        .filter(branch -> scope == DeleteScope.LOCAL || branch.remoteDeletable())
                        .filter(branch -> scope == DeleteScope.REMOTE || !branch.checkedOutElsewhere())
                        .map(branch -> new DeleteBranch(branch.name(), branch.pushTarget(), scope)));
    }

    /**
     * Only a known remote branch can be deleted, and not while something runs on it. The default branch is
     * refused, as deleting it would break every clone.
     */
    private static Next remoteRequested(Model model, RemoteBranch branch) {
        return isDefault(model, branch)
                ? Next.of(model.fail(new AppError.DefaultBranch(branch.shortName())))
                : Update.ask(
                        model, Option.when(remoteDeletable(model, branch), () -> new DeleteRemoteBranchPopup(branch)));
    }

    private static boolean remoteDeletable(Model model, RemoteBranch branch) {
        return model.repo().remoteBranch(branch).isDefined()
                && !model.jobs().busy().containsKey(new BranchRef.Remote(branch))
                && !isDefault(model, branch);
    }

    public static boolean remoteDeletable(Model model, Branch.Name branch) {
        return model.repo().branch(branch).exists(Branch::remoteDeletable);
    }

    /**
     * Whether {@code branch} can be deleted here: not while another worktree has it checked out.
     */
    public static boolean localDeletable(Model model, Branch.Name branch) {
        return model.repo().branch(branch).exists(known -> !known.checkedOutElsewhere());
    }

    /**
     * Whether {@code branch} is the default branch of {@code origin}, as far as the local default branch tells.
     */
    private static boolean isDefault(Model model, RemoteBranch branch) {
        return branch.remote().equals(RemoteBranch.DEFAULT_REMOTE)
                && model.repo().branch(branch.branch()).exists(Branch::isDefault);
    }
}
