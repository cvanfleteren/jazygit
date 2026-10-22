package net.vanfleteren.jazygit.feature.worktree.add;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.state.TextPopup;

/**
 * Asking for the name of a new branch to check out in a new worktree, which starts where {@code from} says, shown
 * to the user as {@code label}.
 *
 * @param name    the name as far as it was typed; it starts out empty, or as the remote branch is named
 * @param ifBlank what the worktree checks out when the name is left blank; without it, a blank name is no name
 */
public record NewWorktreeBranchPopup(String label, String name, NewBranch from, Option<WorktreeSource> ifBlank)
        implements TextPopup {

    /**
     * Asking for the name of a new branch, which has to be given.
     */
    public NewWorktreeBranchPopup(String label, String name, NewBranch from) {
        this(label, name, from, Option.none());
    }

    /**
     * Asking for the name of a new branch made from the local branch {@code base}, recorded as its parent.
     */
    static NewWorktreeBranchPopup fromLocal(Branch.Name base, Option<WorktreeSource> ifBlank) {
        return new NewWorktreeBranchPopup(
                base.value(), "", new NewBranch(base.value(), Option.of(base), false), ifBlank);
    }

    /**
     * Asking for the name of a new branch that follows {@code remote}, starting out named as it.
     */
    static NewWorktreeBranchPopup followingRemote(RemoteBranch remote, Option<WorktreeSource> ifBlank) {
        return new NewWorktreeBranchPopup(
                remote.shortName(),
                remote.branch().value(),
                new NewBranch(remote.trackingRef(), Option.none(), true),
                ifBlank);
    }

    /**
     * Asking for the name of a new branch starting at {@code start}, any revision; left blank, the worktree checks
     * out {@code start} on no branch.
     */
    static NewWorktreeBranchPopup fromRevision(String start) {
        return new NewWorktreeBranchPopup(
                start, "", new NewBranch(start, Option.none(), false), Option.of(new WorktreeSource.Detached(start)));
    }

    @Override
    public String text() {
        return name;
    }

    @Override
    public NewWorktreeBranchPopup withText(String text) {
        return new NewWorktreeBranchPopup(label, text, from, ifBlank);
    }
}
