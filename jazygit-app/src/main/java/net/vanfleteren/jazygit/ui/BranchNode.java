package net.vanfleteren.jazygit.ui;

import io.vavr.Tuple2;
import io.vavr.collection.List;
import io.vavr.collection.Set;
import io.vavr.control.Option;
import java.util.function.Function;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequestStack;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.BranchName;

/**
 * A row of the local branches (ADR 72): a branch, the header of a stack of pull requests on GitHub that has a local
 * branch, or a pull request of such a stack whose branch is not checked out here.
 */
sealed interface BranchNode {

    /**
     * What the node is known by, so that the highlight follows it when the list changes.
     */
    String key();

    /**
     * The nodes of {@code branches}, in their order (ADR 41): a branch in a stack on GitHub brings in its whole stack,
     * a header and, when the stack is open, its pull requests under it, the bottom one first; at the first of its
     * branches, so a stack sits where the one of its branches that saw activity last does, and the checked out
     * branch's stack comes first.
     *
     * @param pullRequest the open pull request of a branch, if it has one
     * @param closed      the numbers of the stacks shown closed
     */
    static List<BranchNode> of(
            List<Branch> branches, Function<Branch, Option<PullRequest>> pullRequest, Set<Integer> closed) {
        List<Option<PullRequestStack>> stacks =
                branches.map(branch -> pullRequest.apply(branch).flatMap(PullRequest::stack));
        return branches.zip(stacks)
                .zipWithIndex()
                .flatMap(indexed -> indexed._1()
                        ._2()
                        .fold(
                                () -> List.<BranchNode>of(new Leaf(indexed._1()._1(), false)),
                                stack -> stacks.take(indexed._2())
                                                .exists(earlier ->
                                                        earlier.exists(seen -> seen.number() == stack.number()))
                                        ? List.<BranchNode>empty()
                                        : stack(stack.number(), branches.zip(stacks), pullRequest, closed)));
    }

    /**
     * The header of the stack numbered {@code number} and, when it is open, its pull requests under it.
     */
    private static List<BranchNode> stack(
            int number,
            List<Tuple2<Branch, Option<PullRequestStack>>> branches,
            Function<Branch, Option<PullRequest>> pullRequest,
            Set<Integer> closed) {
        List<Tuple2<Branch, PullRequestStack>> members = branches.flatMap(
                branch -> branch._2().filter(stack -> stack.number() == number).map(stack -> branch.map2(s -> stack)));
        // The stacks of its pull requests are read with each, and the one that lists the most of them says most.
        PullRequestStack stack =
                members.map(Tuple2::_2).maxBy(read -> read.entries().size()).get();
        List<BranchNode> leaves = stack.entries()
                .sortBy(PullRequestStack.Entry::position)
                .map(entry -> members.find(
                                member -> pullRequest.apply(member._1()).exists(pr -> pr.number() == entry.number()))
                        .<BranchNode>map(member -> new Leaf(member._1(), true))
                        .getOrElse(() -> new Other(number, entry)));
        // A branch whose pull request the stack it was read with does not list goes after the ones it does.
        List<BranchNode> unlisted = members.filter(member -> !leaves.exists(
                        leaf -> leaf instanceof Leaf listed && listed.branch().equals(member._1())))
                .sortBy(member -> member._2().position())
                .map(member -> new Leaf(member._1(), true));
        List<BranchNode> under = leaves.appendAll(unlisted);
        boolean open = !closed.contains(number);
        return List.<BranchNode>of(new Header(
                        stack,
                        open,
                        members.map(Tuple2::_1),
                        under.filter(Leaf.class::isInstance)
                                .map(leaf -> ((Leaf) leaf).branch().name())
                                .lastOption()))
                .appendAll(open ? under : List.empty());
    }

    /**
     * A local branch.
     *
     * @param inStack whether it is listed under the header of its stack
     */
    record Leaf(Branch branch, boolean inStack) implements BranchNode {

        @Override
        public String key() {
            return "branch:" + branch.name().value();
        }
    }

    /**
     * The header of a stack on GitHub that has a local branch.
     *
     * @param stack    the stack, as the pull request that lists the most of it has it
     * @param open     whether its pull requests are listed under it
     * @param branches its local branches
     * @param top      the local branch of it that is the highest in it, whose log shows the whole stack
     */
    record Header(PullRequestStack stack, boolean open, List<Branch> branches, Option<BranchName> top)
            implements BranchNode {

        @Override
        public String key() {
            return "stack:" + stack.number();
        }
    }

    /**
     * A pull request of a stack whose branch is not checked out here, or that is merged or closed.
     *
     * @param stack the number of its stack
     */
    record Other(int stack, PullRequestStack.Entry entry) implements BranchNode {

        @Override
        public String key() {
            return "pullRequest:" + entry.number();
        }
    }
}
