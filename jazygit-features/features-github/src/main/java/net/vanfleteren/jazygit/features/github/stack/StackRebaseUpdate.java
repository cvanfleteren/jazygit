package net.vanfleteren.jazygit.features.github.stack;

import static net.vanfleteren.jazygit.features.github.stack.StackMenuUpdate.chosen;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.gh.model.PullRequestStack;
import net.vanfleteren.jazygit.core.gh.model.StackId;
import net.vanfleteren.jazygit.core.repo.Branch;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.core.repo.StackLayer;
import net.vanfleteren.jazygit.features.github.GitHubError;
import net.vanfleteren.jazygit.features.github.stack.StackMsg.LinkAndRebaseChosen;
import net.vanfleteren.jazygit.features.github.stack.StackMsg.PushChosen;
import net.vanfleteren.jazygit.features.github.stack.StackMsg.RebaseChosen;
import net.vanfleteren.jazygit.features.github.stack.StackMsg.RebaseLocallyChosen;
import net.vanfleteren.jazygit.features.github.stack.StackMsg.StackBehindRemote;
import net.vanfleteren.jazygit.features.github.stack.StackMsg.StackRead;
import net.vanfleteren.jazygit.features.github.stack.StackMsg.StackReadFailed;
import net.vanfleteren.jazygit.features.github.state.GitHubState;
import net.vanfleteren.jazygit.features.github.state.StackChange;
import net.vanfleteren.jazygit.kernel.state.AppError;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.operation.OperationUpdate;
import net.vanfleteren.jazygit.kernel.state.popup.PopupUpdate;

/**
 * Rebasing a stack and pushing it (ADR 74), from its menu, or once GitHub lists a stack linked to be rebased.
 */
final class StackRebaseUpdate {

    private StackRebaseUpdate() {}

    static Next update(Model model, StackMsg.Rebasing msg) {
        return switch (msg) {
            case LinkAndRebaseChosen() -> linkAndRebase(model);
            case RebaseChosen() -> rebaseChosen(model);
            case PushChosen() -> pushChosen(model);
            case StackRead(StackRebase rebase, var commits) -> stackRead(model, rebase, commits);
            case StackReadFailed(StackId stack, var failure) -> stackReadFailed(model, stack, failure);
            case StackBehindRemote(StackId stack, BranchName branch, var remote) ->
                behindRemote(model, stack, branch, remote);
            case RebaseLocallyChosen() -> rebaseLocally(model);
        };
    }

    /**
     * Rebases the stack that GitHub now lists {@code stacked} in, as was asked when linking it (ADR 74): its commits
     * are read, then asked about; or it says why it is not rebased.
     */
    static Next rebaseListed(Model model, StackChange.Stacked stacked) {
        return model.part(GitHubState.PART)
                .pullRequests()
                .flatMap(stacked::in)
                .map(stack -> StackRebase.of(model, stack)
                        .fold(
                                refused -> Next.of(model.fail(refused)),
                                rebase -> Next.of(model, new StackRebaseCmd.ReadStack(rebase))))
                .getOrElse(() -> Next.of(model));
    }

    /**
     * Linked as asked, and rebased once GitHub lists the stack (ADR 74).
     */
    private static Next linkAndRebase(Model model) {
        return model.popup(StackPopups.Confirm.class)
                .filter(confirm -> !confirm.diverged().isEmpty())
                .map(confirm -> OperationUpdate.start(
                        model.withoutPopup(StackPopups.Confirm.class).clearError(),
                        confirm.cmd().rebasing()))
                .getOrElse(() -> Next.of(model));
    }

    /**
     * Reads the commits of the stack of the menu, to ask about rebasing it; or says why it is not rebased.
     */
    private static Next rebaseChosen(Model model) {
        return chosen(
                model,
                menu -> menu.stack()
                        .map(stack -> StackRebase.of(model, stack)
                                .fold(
                                        refused -> Next.of(model.withoutPopup(StackPopups.Menu.class)
                                                .fail(refused)),
                                        rebase -> Next.of(
                                                model.withoutPopup(StackPopups.Menu.class)
                                                        .clearError(),
                                                new StackRebaseCmd.ReadStack(rebase))))
                        .getOrElse(() -> Next.of(model)));
    }

    /**
     * Pushes the stack of the menu.
     */
    private static Next pushChosen(Model model) {
        return chosen(
                model,
                menu -> menu.stack()
                        .map(stack -> pushed(model.withoutPopup(StackPopups.Menu.class), stack))
                        .getOrElse(() -> Next.of(model)));
    }

    /**
     * Pushed as well unless asked not to: GitHub sees the stack rebased only once it is (ADR 74). The menu is closed by
     * now, so a dialog that is open came up since, as one the user types in while a linked stack settles: it stays,
     * and the user is told to rebase from the menu.
     */
    private static Next stackRead(Model model, StackRebase rebase, List<StackLayer.Commits> commits) {
        return model.popup().isDefined()
                ? Next.of(model.inform(new StackNotice.NotAsked(rebase.stack())))
                : PopupUpdate.ask(
                        model, new StackPopups.RebaseConfirm(new StackRebaseCmd.RebaseStack(rebase, commits, true)));
    }

    /**
     * Says the commits of stack {@code stack} could not be read.
     */
    private static Next stackReadFailed(Model model, StackId stack, Failure failure) {
        return Next.of(model.fail(
                new AppError.OperationFailed(StackRebaseCmd.REBASE_STACK, Option.of("#" + stack.value()), failure)));
    }

    /**
     * Says {@code branch} is not rebased, as it lacks commits of {@code remote}. A pull brings in what is there only
     * when the branch follows it.
     */
    private static Next behindRemote(Model model, StackId stack, BranchName branch, RemoteBranch remote) {
        return Next.of(model.fail(new GitHubError.StackNotRebased(
                stack,
                new GitHubError.StackSubject.OfBranch(branch),
                model.repo().branch(branch).flatMap(Branch::remoteUpstream).contains(remote)
                        ? GitHubError.StackRefusal.BEHIND_REMOTE
                        : GitHubError.StackRefusal.BEHIND_UNFOLLOWED)));
    }

    /**
     * Rebases the stack asked about without pushing it.
     */
    private static Next rebaseLocally(Model model) {
        return PopupUpdate.confirm(
                model,
                StackPopups.RebaseConfirm.class,
                popup -> Option.of(popup.cmd().locally()));
    }

    /**
     * Pushes the open branches of {@code stack}, all of them or none (ADR 74); or says why not, as when one is not
     * checked out here.
     */
    private static Next pushed(Model model, PullRequestStack stack) {
        return StackPush.of(model, stack)
                .fold(
                        refused -> Next.of(model.fail(
                                new GitHubError.StackNotPushed(stack.number(), refused.branch(), refused.why()))),
                        push -> OperationUpdate.start(model.clearError(), new StackRebaseCmd.PushStack(push)));
    }
}
