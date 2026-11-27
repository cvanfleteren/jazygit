package net.vanfleteren.jazygit.features.github.stack;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Set;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.util.function.Function;
import java.util.function.Predicate;
import net.vanfleteren.jazygit.core.gh.model.PullRequest;
import net.vanfleteren.jazygit.core.repo.Branch;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.ForgeBranch;
import net.vanfleteren.jazygit.core.repo.PrNumber;

/**
 * The branches a stack can be made of from one branch, and the bottom and the top chosen among them (ADR 73): the
 * line of its parents down to the first root, which is the base of the stack and not listed; the branch; then the
 * branches built on it, and on those, as a tree. The line is the trunk of that tree, each branch of it the one child
 * of the one below, so the rows from the bottom to the top are a path from the root of the tree.
 *
 * <p>Made by {@link #of} and {@link #above}, and changed by {@link #withBottom} and {@link #withTop}, which keep
 * {@code rows} and {@code chosen} those of the tree.
 *
 * @param tree   the branches, the lowest of the line at its root
 * @param branch the row of the branch it was opened on
 * @param bottom the row of the chosen bottom: the branch or one below it
 * @param top    the row of the chosen top: the branch or one above it
 * @param base   the base of the stack: the root the line stands on, if it stands on one
 * @param rows   the rows of {@code tree}, depth first: the bottom of the line first, then the branch, then the tree
 *               above it
 * @param chosen the rows from the bottom to the top, the bottom first: the one line between them, also where the tree
 *               forks
 */
public record StackChoice(
        StackChoice.Node tree,
        int branch,
        int bottom,
        int top,
        Option<BranchName> base,
        List<StackChoice.Row> rows,
        List<StackChoice.Row> chosen) {

    /**
     * The branches a stack can be made of from {@code branch}, with the bottom as far down as the branches have open
     * pull requests and the top the branch itself.
     *
     * @param local       the local branches, in the order the list shows them
     * @param parents     the parent of each branch that has one (ADR 72)
     * @param root        whether a branch is a root, never in a stack
     * @param pullRequest the open pull request of a branch, if it has one
     */
    public static StackChoice of(
            Branch branch,
            List<Branch> local,
            Map<BranchName, BranchName> parents,
            Predicate<BranchName> root,
            Function<Branch, Option<PullRequest>> pullRequest) {
        Map<BranchName, Branch> byName = local.toMap(Branch::name, b -> b);
        List<Branch> line = below(branch.name(), HashSet.of(branch.name()), byName, parents, root);
        Node above = grown(
                        new Row(branch, 0, line.lastOption().map(Branch::name), pullRequest.apply(branch)),
                        line.map(Branch::name).toSet().add(branch.name()),
                        local,
                        parents,
                        pullRequest)
                ._1();
        // The trunk: each branch of the line is the one child of the one below it.
        List<Option<BranchName>> under = line.map(b -> Option.of(b.name())).prepend(Option.none());
        Node tree = line.zip(under)
                .foldRight(
                        above,
                        (at, child) ->
                                new Node(new Row(at._1(), 0, at._2(), pullRequest.apply(at._1())), List.of(child)));
        Option<BranchName> base =
                parents.get(line.headOption().getOrElse(branch).name()).filter(root);
        int at = line.size();
        // As far down as the line below the branch has open pull requests.
        int bottom = at
                - line.reverse()
                        .takeWhile(b -> pullRequest.apply(b).isDefined())
                        .size();
        return choice(tree, at, bottom, at, base);
    }

    /**
     * The branches a stack can be added to its top from: the local branch of its top, {@code top}, as the fixed
     * bottom, then the branches built on it. The top chosen at first is as far up as the branches above go in one
     * line while they have an open pull request in no stack; the first one above when it has none (ADR 148).
     */
    public static StackChoice above(
            Branch top,
            List<Branch> local,
            Map<BranchName, BranchName> parents,
            Function<Branch, Option<PullRequest>> pullRequest) {
        Node tree = grown(
                        new Row(top, 0, Option.none(), pullRequest.apply(top)),
                        HashSet.of(top.name()),
                        local,
                        parents,
                        pullRequest)
                ._1();
        return choice(tree, 0, 0, appended(tree), Option.none());
    }

    /**
     * The choice of {@code tree} from {@code bottom} to {@code top}, its rows and the chosen ones read once.
     */
    private static StackChoice choice(Node tree, int branch, int bottom, int top, Option<BranchName> base) {
        List<Row> rows = tree.rows();
        List<Row> chosen =
                tree.path(rows.get(top).branch().name()).getOrElse(List.empty()).drop(bottom);
        return new StackChoice(tree, branch, bottom, top, base, rows, chosen);
    }

    /**
     * The row the line above the root of {@code tree} ends at, as {@link #above} chooses it: the one child of a branch
     * is the row after it, so the row is how far the line goes.
     */
    private static int appended(Node tree) {
        int line = linked(tree);
        // The first branch above, without a pull request, is chosen to create it.
        return line == 0
                        && tree.only().exists(above -> above.row().pullRequest().isEmpty())
                ? 1
                : line;
    }

    /**
     * How far the line above {@code node} goes while its branches have an open pull request in no stack.
     */
    private static int linked(Node node) {
        return node.only()
                .filter(above ->
                        above.row().pullRequest().exists(pr -> pr.stack().isEmpty()))
                .map(above -> 1 + linked(above))
                .getOrElse(0);
    }

    /**
     * The parents, by their history, of the branches built on {@code top} (ADR 148): of those of {@code eligible} that
     * {@code containing} says have the tip of {@code top}, each on the nearest whose tip it has of the others read,
     * else on {@code top}. The others read are those found and the branches listed above the top by their parents, so
     * a branch made on one of those is placed on it. Two branches at the same commit are not built on each other.
     *
     * @param containing for {@code top}, for each branch found, and for each branch listed above the top by its parent,
     *                   the local branches that have its tip
     */
    public static Map<BranchName, BranchName> byHistory(
            BranchName top, Set<BranchName> eligible, Map<BranchName, List<BranchName>> containing) {
        List<BranchName> found = containing.get(top).getOrElse(List.empty()).filter(eligible::contains);
        Set<BranchName> read = containing.keySet().remove(top);
        Function<BranchName, List<BranchName>> having =
                name -> containing.get(name).getOrElse(List.empty());
        return found.toMap(
                branch -> branch,
                branch -> read.filter(other -> !other.equals(branch)
                                && having.apply(other).contains(branch)
                                && !having.apply(branch).contains(other))
                        .minBy(other -> having.apply(other).size())
                        .getOrElse(top));
    }

    /**
     * When adding to a stack, the one branch added if it has no open pull request: Enter asks to create it then
     * (ADR 148). With more branches added, one without a pull request is refused, as it is added one at a time.
     */
    public Option<Row> toCreate() {
        List<Row> added = chosen().drop(1);
        return Option.when(added.size() == 1, added::head)
                .filter(row -> row.pullRequest().isEmpty());
    }

    /**
     * The line of the parents of {@code name} that are no roots, the lowest first; none of {@code seen}, so a loop of
     * parents ends.
     */
    private static List<Branch> below(
            BranchName name,
            Set<BranchName> seen,
            Map<BranchName, Branch> byName,
            Map<BranchName, BranchName> parents,
            Predicate<BranchName> root) {
        return parents.get(name)
                .filter(root.negate())
                .flatMap(byName::get)
                .filter(parent -> !seen.contains(parent.name()))
                .map(parent -> below(parent.name(), seen.add(parent.name()), byName, parents, root)
                        .append(parent))
                .getOrElse(List.empty());
    }

    /**
     * The tree of {@code row} and the branches built on it, depth first, in the order of {@code local}, with the names
     * it holds, {@code taken} among them: none of {@code taken} is in it, so a loop of parents ends.
     */
    private static Tuple2<Node, Set<BranchName>> grown(
            Row row,
            Set<BranchName> taken,
            List<Branch> local,
            Map<BranchName, BranchName> parents,
            Function<Branch, Option<PullRequest>> pullRequest) {
        BranchName name = row.branch().name();
        Tuple2<List<Node>, Set<BranchName>> children = local.filter(
                        b -> parents.get(b.name()).contains(name))
                .foldLeft(
                        Tuple.of(List.<Node>empty(), taken),
                        (grown, child) -> grown._2().contains(child.name())
                                ? grown
                                : grown(
                                                new Row(
                                                        child,
                                                        row.depth() + 1,
                                                        Option.of(name),
                                                        pullRequest.apply(child)),
                                                grown._2().add(child.name()),
                                                local,
                                                parents,
                                                pullRequest)
                                        .map1(grown._1()::append));
        return children.map1(nodes -> new Node(row, nodes));
    }

    /**
     * This with {@code row} as the bottom, when it is the branch or below it.
     */
    public Option<StackChoice> withBottom(int row) {
        return Option.when(row >= 0 && row <= branch, () -> choice(tree, branch, row, top, base));
    }

    /**
     * This with {@code row} as the top, when it is the branch or above it.
     */
    public Option<StackChoice> withTop(int row) {
        return Option.when(row >= branch && row < rows.size(), () -> choice(tree, branch, bottom, row, base));
    }

    /**
     * Whether {@code row} is between the bottom and the top.
     */
    public boolean isChosen(int row) {
        return chosen.contains(rows.get(row));
    }

    /**
     * What making a stack of the chosen branches takes; or why they make none.
     *
     * @param least how many pull requests it takes: 2 to make a stack, 1 to add to one, whose bottom is on it already
     * @param skip  how many of the chosen are in the stack already, at its bottom: 0 to make one, 1 to add to one
     */
    public Either<Refusal, Plan> plan(int least, int skip) {
        List<Row> path = chosen();
        List<Row> added = path.drop(skip);
        List<BranchName> without = added.filter(row -> row.pullRequest().isEmpty())
                .map(row -> row.branch().name());
        if (!without.isEmpty()) {
            return Either.left(new Refusal.NoPullRequest(without));
        }
        List<BranchName> stacked = added.filter(
                        row -> row.pullRequest().exists(pr -> pr.stack().isDefined()))
                .map(row -> row.branch().name());
        if (!stacked.isEmpty()) {
            return Either.left(new Refusal.Stacked(stacked));
        }
        if (added.size() < least) {
            return Either.left(new Refusal.TooFew());
        }
        List<PullRequest> pullRequests = path.flatMap(Row::pullRequest);
        List<BaseChange> changes = pullRequests
                .zip(pullRequests.drop(1))
                .filter(pair -> !pair._2().baseBranch().contains(pair._1().headBranch()))
                .map(pair -> new BaseChange(
                        pair._2().number(), pair._2().baseBranch(), pair._1().headBranch()));
        return Either.right(new Plan(pullRequests.drop(skip), changes));
    }

    /**
     * A branch that can be in the stack.
     *
     * @param branch      the branch
     * @param depth       how far it is indented: 0 for the line and the branch, one more per branch above that
     * @param below       the branch under it in the tree, none for the lowest of the line
     * @param pullRequest its open pull request, if it has one
     */
    public record Row(Branch branch, int depth, Option<BranchName> below, Option<PullRequest> pullRequest) {}

    /**
     * A branch of the tree and the branches built on it.
     *
     * @param row      the branch
     * @param children the branches built on it, in the order of the local branches
     */
    public record Node(Row row, List<Node> children) {

        /**
         * The rows of this tree, depth first: this one, then each child's.
         */
        List<Row> rows() {
            return children.flatMap(Node::rows).prepend(row);
        }

        /**
         * The rows from this one up to the one of {@code name}, this one first; none when it is not in this tree.
         */
        Option<List<Row>> path(BranchName name) {
            return row.branch().name().equals(name)
                    ? Option.of(List.of(row))
                    : children.iterator()
                            .flatMap(child -> child.path(name))
                            .headOption()
                            .map(path -> path.prepend(row));
        }

        /**
         * The one branch built on this one; none where none is, or where the tree forks.
         */
        Option<Node> only() {
            return Option.when(children.size() == 1, children::head);
        }
    }

    /**
     * A base of a pull request that has to change for the stack to be one line.
     *
     * @param number its pull request
     * @param from   the base it has, as GitHub names it, if GitHub said
     * @param to     the head of the pull request below it
     */
    public record BaseChange(PrNumber number, Option<ForgeBranch> from, ForgeBranch to) {}

    /**
     * What making the stack takes: the pull requests from the bottom up, and the bases that change first.
     */
    public record Plan(List<PullRequest> pullRequests, List<BaseChange> changes) {}

    /**
     * Why the chosen branches do not make a stack.
     */
    public sealed interface Refusal {

        /** Branches between the bottom and the top without an open pull request. */
        record NoPullRequest(List<BranchName> branches) implements Refusal {}

        /** Branches between the bottom and the top whose pull request is in a stack already. */
        record Stacked(List<BranchName> branches) implements Refusal {}

        /** Only one pull request: a stack takes two. */
        record TooFew() implements Refusal {}
    }
}
