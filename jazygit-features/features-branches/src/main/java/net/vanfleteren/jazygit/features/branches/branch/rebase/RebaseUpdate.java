package net.vanfleteren.jazygit.features.branches.branch.rebase;

import io.vavr.collection.HashSet;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.repo.Branch;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.kernel.state.GitHubBases;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.github.GitHubState;
import net.vanfleteren.jazygit.kernel.state.load.AheadUpdate;
import net.vanfleteren.jazygit.kernel.state.popup.PopupUpdate;

/**
 * Rebasing the checked out branch onto another one.
 */
public final class RebaseUpdate {

    private RebaseUpdate() {}

    public static Next update(Model model, RebaseMsg msg) {
        return switch (msg) {
            case RebaseMsg.Requested(BranchName highlighted) -> requested(model, highlighted);
            case RebaseMsg.Chosen(RebaseMsg.Target target) ->
                PopupUpdate.confirm(
                        model,
                        RebasePopup.class,
                        popup -> onto(model, popup, target).map(RebaseCmd::new));
        };
    }

    /**
     * Asks where to rebase the checked out branch, when it and {@code highlighted} are known branches;
     * {@code highlighted} can be the checked out branch itself. An option that does not apply, also while an
     * operation runs on a branch it takes, is disabled rather than keeping the popup closed (ADR 91).
     */
    private static Next requested(Model model, BranchName highlighted) {
        return PopupUpdate.ask(
                model,
                model.repo()
                        .currentBranch()
                        .filter(current -> model.repo().branch(highlighted).isDefined())
                        .map(current -> new RebasePopup(current.name(), highlighted, base(model, current))));
    }

    /**
     * Where {@code target} of {@code popup} rebases onto, when it applies: there is such a branch, and no operation
     * runs on it nor on the checked out branch.
     */
    static Option<BranchName> onto(Model model, RebasePopup popup, RebaseMsg.Target target) {
        return popup.target(target)
                .filter(onto -> model.idleBranch(onto).isDefined()
                        && model.idleBranch(popup.branch()).isDefined());
    }

    /**
     * The base branch of {@code branch} (ADR 91): its parent (ADR 72), else the main branch; none for a root.
     */
    static Option<BranchName> base(Model model, Branch branch) {
        Option<BranchName> main = AheadUpdate.main(model);
        if (GitHubBases.root(
                branch.name(),
                main,
                model.part(GitHubState.PART).protectedBranches().getOrElse(HashSet.empty()))) {
            return Option.none();
        }
        return model.repo()
                .parents()
                .get(branch.name())
                .orElse(main)
                .filter(base ->
                        !base.equals(branch.name()) && model.repo().branch(base).isDefined());
    }
}
