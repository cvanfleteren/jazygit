package net.vanfleteren.jazygit.feature.branch.nuke;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.branch.nuke.NukeBranchMsg.Confirmed;
import net.vanfleteren.jazygit.feature.branch.nuke.NukeBranchMsg.NotAllNuked;
import net.vanfleteren.jazygit.feature.branch.nuke.NukeBranchMsg.Requested;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.git.model.Worktree;
import net.vanfleteren.jazygit.state.AppError;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.OperationUpdate;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * Nuking a branch (ADR 43): deleting it, the other worktree that has it checked out and its remote branch, after one
 * confirmation.
 */
public final class NukeBranchUpdate {

    private NukeBranchUpdate() {}

    public static Next update(Model model, NukeBranchMsg msg) {
        return switch (msg) {
            case Requested(BranchName branch) -> requested(model, branch);
            // Nothing starts when the branch, its worktree or its remote branch changed since the dialog named them.
            case Confirmed() ->
                Update.confirm(
                        model,
                        NukeBranchPopup.class,
                        popup -> popup(model, popup.branch())
                                .map(NukeBranchPopup::cmd)
                                .filter(popup.cmd()::equals));
            case NotAllNuked(NukeBranchCmd cmd, var commands, var left, var failures) ->
                Update.refresh(OperationUpdate.done(model, cmd, commands)
                        .fail(new AppError.NotAllNuked(cmd.branch(), left, failures)));
        };
    }

    /**
     * A branch that can never be nuked is refused with why; one that can, but not now, as an operation runs on it or
     * on its remote branch, is not asked about.
     */
    private static Next requested(Model model, BranchName name) {
        return model.repo()
                .branch(name)
                .flatMap(branch -> refused(model, branch))
                .map(error -> Next.of(model.fail(error)))
                .getOrElse(() -> Update.ask(model, popup(model, name)));
    }

    /**
     * Why {@code branch} cannot be nuked, if it cannot: it is the default branch, or it is checked out in the
     * worktree shown or in the main worktree, which cannot be removed.
     */
    private static Option<AppError> refused(Model model, Branch branch) {
        return Option.<AppError>when(
                        branch.isDefault(),
                        () -> new AppError.DefaultBranch(branch.name().value()))
                .orElse(() -> Option.when(branch.current(), () -> model.repo().currentWorktree())
                        .flatMap(shown -> shown)
                        .map(shown -> new AppError.ShownWorktreeNotRemovable(shown.name())))
                .orElse(() -> worktree(model, branch)
                        .flatMap(other -> other)
                        .filter(Worktree::main)
                        .map(main -> new AppError.MainWorktreeNotRemovable(main.name())));
    }

    /**
     * What nuking {@code name} would delete, as the dialog asks about it: none when it cannot be nuked now. That is
     * a known branch that is not checked out here, nor the default one, that no operation runs on, nor on its remote
     * branch, and whose other worktree, if it has one, is known and not the main one.
     */
    private static Option<NukeBranchPopup> popup(Model model, BranchName name) {
        return model.idleBranch(name)
                .filter(branch -> !branch.current() && refused(model, branch).isEmpty())
                .filter(branch ->
                        remote(branch).forAll(target -> !model.jobs().busy().containsKey(new BranchRef.Remote(target))))
                .flatMap(branch ->
                        worktree(model, branch).map(worktree -> new NukeBranchPopup(name, remote(branch), worktree)));
    }

    /**
     * The other worktree that has {@code branch} checked out: none when that is not known, and else none inside when
     * no other worktree has it.
     */
    private static Option<Option<Worktree>> worktree(Model model, Branch branch) {
        return branch.worktreeLocation()
                .fold(
                        () -> Option.of(Option.none()),
                        location -> model.repo().worktree(location).map(Option::of));
    }

    /**
     * The remote branch that goes with {@code branch}: where it is pushed, when that is on {@code origin}, has its
     * own name and exists. Another one is left alone: one of another name, like the {@code origin/main} a branch was
     * made from, or one on another remote, which may be that remote's default branch.
     */
    private static Option<RemoteBranch> remote(Branch branch) {
        return Option.when(branch.hasRemote(), branch::pushTarget)
                .filter(target -> target.remote().equals(RemoteBranch.DEFAULT_REMOTE)
                        && target.branch().equals(branch.name()));
    }
}
