package net.vanfleteren.jazygit.state;

import net.vanfleteren.jazygit.model.Commit;

import java.util.List;

/**
 * The log of the branch highlighted in the branches pane.
 */
public record BranchLog(String branch, Loadable<List<Commit>> commits) {

    /**
     * Like {@link Loadable#reload}, returns this instance when the commits are unchanged.
     */
    BranchLog reload(List<Commit> commits) {
        Loadable<List<Commit>> reloaded = this.commits.reload(commits);
        return reloaded == this.commits ? this : new BranchLog(branch, reloaded);
    }
}
