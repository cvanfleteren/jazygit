package net.vanfleteren.jazygit.git.model;

import io.vavr.Tuple;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Option;

/**
 * The branches of a repository, read at the same moment.
 *
 * @param local  the local branches: the checked out one first, then the most recently committed to
 * @param remote the remote-tracking branches, the most recently committed to first
 */
public record AllBranches(List<Branch> local, List<RemoteRef> remote) {

    /**
     * The commit each local branch and each branch on {@link RemoteBranch#DEFAULT_REMOTE} is at: those that can be
     * the base of a branch (ADR 20).
     */
    public Map<BranchRef, Sha> tips() {
        return HashMap.<BranchRef, Sha>ofEntries(
                        local.map(branch -> Tuple.of(new BranchRef.Local(branch.name()), branch.tip())))
                .merge(HashMap.ofEntries(
                        remote.filter(ref -> ref.branch().remote().equals(RemoteBranch.DEFAULT_REMOTE))
                                .map(ref -> Tuple.of(new BranchRef.Remote(ref.branch()), ref.tip()))));
    }

    /**
     * The main branch, the base of the branches without a parent (ADR 46); none when no local branch is the default.
     */
    public Option<BranchName> main() {
        return local.find(Branch::isDefault).map(Branch::name);
    }
}
