package net.vanfleteren.jazygit.kernel.state;

import io.vavr.collection.HashMap;
import io.vavr.collection.HashSet;
import io.vavr.collection.Map;
import io.vavr.collection.Set;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.repo.Branch;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.RemoteName;

/**
 * What one feature states the branches are built on (ADR 72, 169), as a part it keeps says it: by the branch, as the
 * remotes name it, the branch it is built on, and the branches that are roots. A forge states it from its pull
 * requests and its protected branches; the kernel names none. All are branches as the remotes name them, so that this
 * changes only when what the feature read does.
 *
 * @param byHead  by the branch on the remotes, the branch it is built on, and what says so
 * @param roots   the branches that are roots, as the main branch is: they are built on no other branch
 * @param remotes the remotes the names are of; none while that is not known, when any of them may be
 */
public record StatedBases(Map<BranchName, Stated> byHead, Set<BranchName> roots, Option<Set<RemoteName>> remotes) {

    /**
     * Nothing stated.
     */
    public static final StatedBases NONE = new StatedBases(HashMap.empty(), HashSet.empty(), Option.none());

    /**
     * What is stated of {@code branch}, by the name it has on the remotes: that of the branch it follows there, else
     * its own; nothing when it follows a branch on a remote other than {@link #remotes}.
     */
    public Option<Stated> of(Branch branch) {
        return branch.remoteUpstream()
                .<Option<BranchName>>fold(
                        () -> Option.of(branch.name()),
                        upstream -> remotes.forAll(known -> known.contains(upstream.remote()))
                                ? Option.of(upstream.branch())
                                : Option.none())
                .flatMap(byHead::get);
    }

    /**
     * The base {@code base} of a branch, and what states it, for the user: {@code by}, as "its pull request #12 on
     * GitHub".
     */
    public record Stated(BranchName base, Phrase by) {}
}
