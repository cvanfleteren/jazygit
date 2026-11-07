package net.vanfleteren.jazygit.feature.branch.rebase;

import io.vavr.collection.HashSet;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.state.AheadUpdate;
import net.vanfleteren.jazygit.state.GitHubBases;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;

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
                        popup -> popup.target(target)
                                .filter(onto -> model.idleBranch(onto).isDefined())
                                .map(RebaseCmd::new));
        };
    }

    /**
     * Asks where to rebase the checked out branch, when it and {@code highlighted} are known branches no operation
     * runs on; {@code highlighted} can be the checked out branch itself (ADR 91).
     */
    private static Next requested(Model model, BranchName highlighted) {
        return Update.ask(
                model,
                model.repo()
                        .currentBranch()
                        .flatMap(current -> model.idleBranch(current.name()))
                        .filter(current -> model.idleBranch(highlighted).isDefined())
                        .map(current -> new RebasePopup(current.name(), highlighted, base(model, current))));
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
