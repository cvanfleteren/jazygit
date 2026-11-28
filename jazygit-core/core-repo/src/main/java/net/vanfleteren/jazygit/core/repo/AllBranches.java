package net.vanfleteren.jazygit.core.repo;

import io.vavr.Tuple;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Set;
import io.vavr.control.Option;

/**
 * The branches of a repository, read at the same moment.
 *
 * @param local  the local branches: the checked out one first, then the most recently committed to
 * @param remote the remote-tracking branches, the most recently committed to first
 * @param setup  the remotes the branches go by
 */
public record AllBranches(List<Branch> local, List<RemoteRef> remote, RemoteSetup setup) {

    /**
     * Branches of a repository whose only remote setting is {@code origin}.
     */
    public AllBranches(List<Branch> local, List<RemoteRef> remote) {
        this(local, remote, RemoteSetup.ORIGIN);
    }

    /**
     * The commit each local branch and each branch on the base remote is at: those that can be
     * the base of a branch (ADR 20).
     */
    public Map<BranchRef, Sha> tips() {
        return HashMap.<BranchRef, Sha>ofEntries(
                        local.map(branch -> Tuple.of(new BranchRef.Local(branch.name()), branch.tip())))
                .merge(HashMap.ofEntries(
                        remote.filter(ref -> setup.base().contains(ref.branch().remote()))
                                .map(ref -> Tuple.of(new BranchRef.Remote(ref.branch()), ref.tip()))));
    }

    /**
     * The branches on the base remote, by their name there: those that can be a base without being checked out
     * (ADR 143).
     */
    public Set<BranchName> onBase() {
        return remote.filter(ref -> setup.base().contains(ref.branch().remote()))
                .map(ref -> ref.branch().branch())
                .toSet();
    }

    /**
     * The main branch, the base of the branches without a parent (ADR 46); none when no local branch is the default.
     */
    public Option<BranchName> main() {
        return main(local);
    }

    /**
     * The main branch among the local branches {@code local}: the one that is the default; none when none is. What
     * the commits ahead and the distances to the bases go by, so both have the same.
     */
    public static Option<BranchName> main(List<Branch> local) {
        return local.find(Branch::isDefault).map(Branch::name);
    }
}
