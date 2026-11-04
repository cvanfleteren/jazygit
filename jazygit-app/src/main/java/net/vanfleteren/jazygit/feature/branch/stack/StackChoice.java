package net.vanfleteren.jazygit.feature.branch.stack;

import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Set;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.util.function.Function;
import java.util.function.Predicate;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.BranchName;

/**
 * The branches a stack can be made of from one branch, and the bottom and the top chosen among them (ADR 73): the
 * line of its parents down to the first root, which is the base of the stack and not listed; the branch; then the
 * branches built on it, and on those, as a tree.
 *
 * @param rows   the branches, the bottom of the line first, then the branch, then the tree above it depth first
 * @param branch the row of the branch it was opened on
 * @param bottom the row of the chosen bottom: the branch or one below it
 * @param top    the row of the chosen top: the branch or one above it
 * @param base   the base of the stack: the root the line stands on, if it stands on one
 */
public record StackChoice(List<StackChoice.Row> rows, int branch, int bottom, int top, Option<BranchName> base) {

    /**
     * A branch that can be in the stack.
     *
     * @param branch      the branch
     * @param depth       how far it is indented: 0 for the line and the branch, one more per branch above that
     * @param parent      the row of its parent, none for the lowest of the line
     * @param pullRequest its open pull request, if it has one
     */
    public record Row(Branch branch, int depth, Option<Integer> parent, Option<PullRequest> pullRequest) {}

    /**
     * A base of a pull request that has to change for the stack to be one line.
     *
     * @param number its pull request
     * @param from   the base it has, as GitHub names it
     * @param to     the head of the pull request below it
     */
    public record BaseChange(int number, String from, String to) {}

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
        List<Branch> line = below(branch, byName, parents, root);
        List<Row> lineRows = line.zipWithIndex()
                .map(at -> new Row(at._1(), 0, Option.when(at._2() > 0, at._2() - 1), pullRequest.apply(at._1())));
        int at = line.size();
        List<Row> rows = lineRows.append(new Row(branch, 0, Option.when(at > 0, at - 1), pullRequest.apply(branch)));
        rows = above(rows, at, local, parents, pullRequest);
        Option<BranchName> base =
                parents.get(line.headOption().getOrElse(branch).name()).filter(root);
        int bottom = at;
        while (bottom > 0 && rows.get(bottom - 1).pullRequest().isDefined()) {
            bottom--;
        }
        return new StackChoice(rows, at, bottom, at, base);
    }

    /**
     * The branches a stack can be added to its top from: the local branch of its top, {@code top}, as the fixed
     * bottom, then the branches built on it. The top chosen is the top of the stack until one is chosen above it.
     */
    public static StackChoice above(
            Branch top,
            List<Branch> local,
            Map<BranchName, BranchName> parents,
            Function<Branch, Option<PullRequest>> pullRequest) {
        List<Row> rows =
                above(List.of(new Row(top, 0, Option.none(), pullRequest.apply(top))), 0, local, parents, pullRequest);
        return new StackChoice(rows, 0, 0, 0, Option.none());
    }

    /**
     * The line of the parents of {@code branch} that are no roots, the lowest first.
     */
    private static List<Branch> below(
            Branch branch,
            Map<BranchName, Branch> byName,
            Map<BranchName, BranchName> parents,
            Predicate<BranchName> root) {
        List<Branch> line = List.empty();
        Set<BranchName> seen = HashSet.of(branch.name());
        Option<Branch> next = parents.get(branch.name()).filter(root.negate()).flatMap(byName::get);
        while (next.isDefined() && !seen.contains(next.get().name())) {
            Branch parent = next.get();
            line = line.prepend(parent);
            seen = seen.add(parent.name());
            next = parents.get(parent.name()).filter(root.negate()).flatMap(byName::get);
        }
        return line;
    }

    /**
     * {@code rows} with the branches built on the one at row {@code at} added after it, depth first, in the order of
     * {@code local}; none that is in the rows already, so a loop of parents ends.
     */
    private static List<Row> above(
            List<Row> rows,
            int at,
            List<Branch> local,
            Map<BranchName, BranchName> parents,
            Function<Branch, Option<PullRequest>> pullRequest) {
        BranchName name = rows.get(at).branch().name();
        List<Row> result = rows;
        for (Branch child : local.filter(b -> parents.get(b.name()).contains(name))) {
            if (result.exists(row -> row.branch().name().equals(child.name()))) {
                continue;
            }
            int index = result.size();
            result = above(
                    result.append(new Row(child, rows.get(at).depth() + 1, Option.of(at), pullRequest.apply(child))),
                    index,
                    local,
                    parents,
                    pullRequest);
        }
        return result;
    }

    /**
     * This with {@code row} as the bottom, when it is the branch or below it.
     */
    public Option<StackChoice> withBottom(int row) {
        return Option.when(row >= 0 && row <= branch, () -> new StackChoice(rows, branch, row, top, base));
    }

    /**
     * This with {@code row} as the top, when it is the branch or above it.
     */
    public Option<StackChoice> withTop(int row) {
        return Option.when(row >= branch && row < rows.size(), () -> new StackChoice(rows, branch, bottom, row, base));
    }

    /**
     * The rows from the bottom to the top, the bottom first: the one line between them, also where the tree forks.
     */
    public List<Row> chosen() {
        List<Row> path = List.empty();
        Option<Integer> at = Option.of(top);
        while (at.isDefined() && at.get() >= bottom) {
            Row row = rows.get(at.get());
            path = path.prepend(row);
            at = at.get() == bottom ? Option.none() : row.parent();
        }
        return path;
    }

    /**
     * Whether {@code row} is between the bottom and the top.
     */
    public boolean isChosen(int row) {
        return chosen().contains(rows.get(row));
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
                .filter(pair -> !pair._2().baseBranch().equals(pair._1().headBranch()))
                .map(pair -> new BaseChange(
                        pair._2().number(), pair._2().baseBranch(), pair._1().headBranch()));
        return Either.right(new Plan(pullRequests.drop(skip), changes));
    }
}
