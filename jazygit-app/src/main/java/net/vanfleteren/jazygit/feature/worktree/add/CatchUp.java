package net.vanfleteren.jazygit.feature.worktree.add;

import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.RemoteBranch;

/**
 * What is done to a local branch that follows a remote one before a new worktree checks it out (ADR 109).
 */
public sealed interface CatchUp {

    /**
     * What is done to {@code branch}, as the branches read say it stands against the remote branch it follows.
     */
    static CatchUp of(Branch branch) {
        return branch.remoteUpstream()
                .<CatchUp>map(upstream -> branch.ahead() > 0
                        ? new Kept(upstream, branch.ahead())
                        : branch.behind() > 0 ? new FastForward(upstream) : new AsIs())
                .getOrElse(AsIs::new);
    }

    /**
     * Nothing: it is checked out as it is.
     */
    record AsIs() implements CatchUp {}

    /**
     * It is behind {@code upstream} and has no commit of its own, so it is fast-forwarded to it first.
     */
    record FastForward(RemoteBranch upstream) implements CatchUp {}

    /**
     * It has {@code ahead} commits that are not on {@code upstream}, so it is checked out as it is, which is said.
     */
    record Kept(RemoteBranch upstream, int ahead) implements CatchUp {}
}
