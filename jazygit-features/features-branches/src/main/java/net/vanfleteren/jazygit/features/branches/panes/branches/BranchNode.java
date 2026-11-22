package net.vanfleteren.jazygit.features.branches.panes.branches;

import io.vavr.collection.List;
import io.vavr.collection.Set;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.repo.Branch;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.features.branches.panes.branches.BranchGroups.Entry;
import net.vanfleteren.jazygit.features.branches.panes.branches.BranchGroups.Group;

/**
 * A row of the local branches (ADR 72): a branch, the header of a group the features make of branches (ADR 167), what
 * belongs to such a group but is no local branch here, or a detached HEAD, listed first.
 */
sealed interface BranchNode {

    /**
     * What the node is known by, so that the highlight follows it when the list changes.
     */
    String key();

    /**
     * The nodes of {@code branches}, in their order (ADR 41): a branch in a group brings in its whole group, a header
     * and, when the group is open, what is under it; at the first of its branches, so a group sits where the one of its
     * branches that saw activity last does, and the checked out branch's group comes first. Unless {@code grouped},
     * every branch is a node of its own, also one in a group (ADR 72).
     *
     * @param groups  the groups the features make of {@code branches}
     * @param grouped whether the groups show
     * @param closed  the keys of the groups shown closed
     */
    static List<BranchNode> of(List<Branch> branches, List<Group> groups, boolean grouped, Set<String> closed) {
        if (!grouped) {
            return branches.map(branch -> new Leaf(branch, false));
        }
        List<Option<Group>> of =
                branches.map(branch -> groups.find(group -> group.members().contains(branch.name())));
        return branches.zip(of)
                .zipWithIndex()
                .flatMap(indexed -> indexed._1()
                        ._2()
                        .fold(
                                () -> List.<BranchNode>of(new Leaf(indexed._1()._1(), false)),
                                group -> of.take(indexed._2())
                                                .exists(earlier -> earlier.exists(
                                                        seen -> seen.key().equals(group.key())))
                                        ? List.<BranchNode>empty()
                                        : group(group, branches, closed)));
    }

    /**
     * The header of {@code group} and, when it is open, what is under it.
     */
    private static List<BranchNode> group(Group group, List<Branch> branches, Set<String> closed) {
        List<Branch> members = branches.filter(branch -> group.members().contains(branch.name()));
        List<BranchNode> under = group.entries().flatMap(entry -> switch (entry) {
            case Entry.Member member ->
                members.find(branch -> branch.name().equals(member.branch()))
                        .<BranchNode>map(branch -> new Leaf(branch, true))
                        .toList();
            case Entry.Elsewhere elsewhere -> List.<BranchNode>of(new Other(group.key(), elsewhere));
        });
        boolean open = !closed.contains(group.key());
        return List.<BranchNode>of(
                        new Header(group, open, members, group.members().lastOption()))
                .appendAll(open ? under : List.empty());
    }

    /**
     * A local branch.
     *
     * @param inGroup whether it is listed under the header of its group
     */
    record Leaf(Branch branch, boolean inGroup) implements BranchNode {

        @Override
        public String key() {
            return "branch:" + branch.name().value();
        }
    }

    /**
     * The header of a group.
     *
     * @param group    the group
     * @param open     whether what is in it is listed under it
     * @param branches its local branches
     * @param top      the last local branch of it, whose log the header shows
     */
    record Header(Group group, boolean open, List<Branch> branches, Option<BranchName> top) implements BranchNode {

        @Override
        public String key() {
            return group.key();
        }
    }

    /**
     * HEAD, detached at {@code at}, listed first, as lazygit does: no branch is checked out. It has no log of its own,
     * as the commits pane shows it, and the keys of a branch do nothing on it.
     */
    record Detached(Sha at) implements BranchNode {

        @Override
        public String key() {
            return "detached";
        }
    }

    /**
     * What belongs to a group but is no local branch here.
     *
     * @param group the key of its group
     */
    record Other(String group, Entry.Elsewhere entry) implements BranchNode {

        @Override
        public String key() {
            return entry.key();
        }
    }
}
