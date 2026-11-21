package net.vanfleteren.jazygit.kernel.state;

import io.vavr.Tuple;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Set;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.repo.Branch;
import net.vanfleteren.jazygit.core.repo.BranchName;

/**
 * What the features state the branches are built on (ADR 72, 169), each in a part it keeps: the kernel composes what
 * they state without naming any. A branch is a root when one of them says so; the base of a branch is the one the
 * first of them that states one states, in the order of the names of their parts.
 *
 * @param stated what each feature states, leaving out those that state nothing
 */
public record BranchBases(List<StatedBases> stated) {

    /**
     * Nothing stated, as before any feature read anything.
     */
    public static final BranchBases NONE = new BranchBases(List.empty());

    /**
     * What the parts {@code model} keeps state.
     */
    public static BranchBases in(Model model) {
        return new BranchBases(model.parts().stated());
    }

    /**
     * Whether {@code branch} is a root: the main branch, {@code main}, or one a feature states is a root, as a
     * branch protected on a forge. A root is never built on another branch.
     */
    public boolean root(BranchName branch, Option<BranchName> main) {
        return main.contains(branch) || stated.exists(bases -> bases.roots().contains(branch));
    }

    /**
     * What is stated of {@code branch}: the branch it is built on, and what says so; none when no feature states it.
     * It comes before what is recorded for the branch, which so cannot set it (ADR 143).
     */
    public Option<StatedBases.Stated> of(Branch branch) {
        return stated.toStream().flatMap(bases -> bases.of(branch)).headOption();
    }

    /**
     * The parent of each of {@code local} that is not a root and has one (ADR 72): the first of the branch it is built
     * on as stated and the one recorded in {@code recorded}, {@code branch.<name>.gh-merge-base}, and only when
     * that is another of {@code local}, or a branch on {@code origin} (ADR 143). What is stated comes first, also when
     * it names a branch that is in neither: then the branch has no parent.
     *
     * @param onOrigin the branches on {@code origin}, by their name there
     * @param main     the main branch, a root
     */
    public Map<BranchName, BranchName> parents(
            List<Branch> local,
            Set<BranchName> onOrigin,
            Option<BranchName> main,
            Map<BranchName, BranchName> recorded) {
        return local.filter(branch -> !root(branch.name(), main))
                .flatMap(branch -> of(branch)
                        .map(StatedBases.Stated::base)
                        .fold(
                                () -> recorded.get(branch.name())
                                        .filter(parent -> isLocal(local, parent) || onOrigin.contains(parent)),
                                onRemote -> localOf(local, onRemote)
                                        .orElse(() -> Option.of(onRemote).filter(onOrigin::contains)))
                        .filter(parent -> !parent.equals(branch.name()))
                        .map(parent -> Tuple.of(branch.name(), parent)))
                .toMap(pair -> pair);
    }

    /**
     * The local branch that is {@code onRemote} on the remote: the one that follows it, else the one of the same name.
     */
    private static Option<BranchName> localOf(List<Branch> local, BranchName onRemote) {
        return local.find(branch -> branch.remoteUpstream()
                        .exists(upstream -> upstream.branch().equals(onRemote)))
                .map(Branch::name)
                .orElse(() -> Option.of(onRemote).filter(name -> isLocal(local, name)));
    }

    private static boolean isLocal(List<Branch> local, BranchName name) {
        return local.exists(branch -> branch.name().equals(name));
    }
}
