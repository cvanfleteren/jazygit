package net.vanfleteren.jazygit.feature.branch.movecommits;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.git.model.CommitRange;
import net.vanfleteren.jazygit.git.model.RemoteBranch;

/**
 * Moving the commits of the checked out branch {@code from}, from {@code oldest} up to its tip, to a new branch.
 *
 * @param fromIsDefault whether {@code from} is the main branch
 * @param upstream      the remote branch {@code from} follows, if any
 * @param range         what moving the commits takes away from {@code from}
 * @param bases         where the new branch can start, the parent of {@code oldest} first
 */
public record MoveRequest(
        Branch.Name from,
        boolean fromIsDefault,
        Option<RemoteBranch> upstream,
        Commit oldest,
        CommitRange range,
        List<MoveBase> bases) {

    /**
     * Whether some of the commits are on the upstream of {@code from} already, which a pull would bring back.
     */
    public boolean pushed() {
        return upstream.isDefined() && range.pushed() > 0;
    }
}
