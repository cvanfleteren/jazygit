package net.vanfleteren.jazygit.features.branches.branch.pullrequest;

import net.vanfleteren.jazygit.core.repo.Branch;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Update.Next;

/**
 * What a forge does once a branch is on its remote with all its commits, before its pull request is created (ADR 25):
 * asking what the pull request says. A forge gives it as a record of its own, so the popup and the push that carry it
 * stay values.
 */
public interface AfterPush {

    /**
     * Goes on with {@code branch}, which is on its remote with all its commits now, in {@code model}.
     */
    Next next(Model model, Branch branch);
}
