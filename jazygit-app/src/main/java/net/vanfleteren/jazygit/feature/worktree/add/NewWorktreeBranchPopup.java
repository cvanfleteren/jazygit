package net.vanfleteren.jazygit.feature.worktree.add;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.NewBranchStart;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.git.model.Revision;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.state.TextPopup;

/**
 * Asking for the name of a new branch to check out in a new worktree, which starts where {@code from} says, shown
 * to the user as {@code label}.
 *
 * @param name    the name as far as it was typed; it starts out empty, or as the remote branch is named
 * @param ifBlank what the worktree checks out when the name is left blank; without it, a blank name is no name
 */
public record NewWorktreeBranchPopup(String label, String name, NewBranchStart from, Option<WorktreeSource> ifBlank)
        implements TextPopup {

    /**
     * Asking for the name of a new branch, which has to be given.
     */
    public NewWorktreeBranchPopup(String label, String name, NewBranchStart from) {
        this(label, name, from, Option.none());
    }

    /**
     * Asking for the name of a new branch made from the local branch {@code base}, recorded as its parent.
     */
    static NewWorktreeBranchPopup fromLocal(BranchName base, Option<WorktreeSource> ifBlank) {
        return new NewWorktreeBranchPopup(
                base.value(), "", new NewBranchStart.From(new BranchRef.Local(base), Option.of(base)), ifBlank);
    }

    /**
     * Asking for the name of a new branch that follows {@code remote}, starting out named as it.
     */
    static NewWorktreeBranchPopup followingRemote(RemoteBranch remote, Option<WorktreeSource> ifBlank) {
        return new NewWorktreeBranchPopup(
                remote.shortName(), remote.branch().value(), new NewBranchStart.Following(remote), ifBlank);
    }

    /**
     * Asking for the name of a new branch starting at {@code commit}, which the user typed as {@code typed}; left
     * blank, the worktree checks out {@code commit} on no branch.
     */
    static NewWorktreeBranchPopup fromCommit(String typed, Sha commit) {
        Revision start = new Revision.Commit(commit);
        return new NewWorktreeBranchPopup(
                typed,
                "",
                new NewBranchStart.From(start, Option.none()),
                Option.of(new WorktreeSource.Detached(start, typed)));
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
