package net.vanfleteren.jazygit.feature.branch.rebase;

import io.vavr.collection.HashSet;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.state.GitHubBases;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;
import net.vanfleteren.jazygit.state.load.AheadUpdate;

/**
 * Rebasing the checked out branch onto another one.
 */
public final class RebaseUpdate {

    private RebaseUpdate() {}

    public static Next update(Model model, RebaseMsg msg) {
        return switch (msg) {
            case RebaseMsg.Requested(BranchName highlighted) -> requested(model, highlighted);
            case RebaseMsg.Chosen(RebaseMsg.Target target) ->
                Update.confirm(
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
        return Update.ask(
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
                branch.name(), main, model.gitHub().protectedBranches().getOrElse(HashSet.empty()))) {
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
