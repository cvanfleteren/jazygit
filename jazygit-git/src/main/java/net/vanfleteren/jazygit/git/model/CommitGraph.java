package net.vanfleteren.jazygit.git.model;

import io.vavr.collection.*;
import io.vavr.control.Option;

/**
 * Lays out a commit log with its merges: the commits a merge brought in follow it, so they can be shown
 * as a branch that connects back to the main line.
 */
public final class CommitGraph {

    private CommitGraph() {}

    /**
     * The commits with every merge directly followed by the commits it brought in, i.e. those reachable
     * from its other parents but not from its first one; the rest keeps its order. Only commits in the
     * list are considered.
     */
    public static List<Commit> arrange(List<Commit> commits) {
        Map<Sha, Commit> bySha = index(commits);
        return commits.foldLeft(
                        new Placed(HashSet.empty(), List.empty()),
                        (state, commit) -> place(commit, commits, bySha, state))
                .latestFirst()
                .reverse();
    }

    private static Placed place(Commit commit, List<Commit> commits, Map<Sha, Commit> bySha, Placed state) {
        Placed placed =
                new Placed(state.shas().add(commit.sha()), state.latestFirst().prepend(commit));
        return state.shas().contains(commit.sha())
                ? state
                : !commit.isMerge() ? placed : placeBroughtIn(commit, commits, bySha, placed);
    }

    /**
     * Places the commits the merge {@code merge} brought in.
     */
    private static Placed placeBroughtIn(Commit merge, List<Commit> commits, Map<Sha, Commit> bySha, Placed state) {
        Set<Sha> brought = broughtIn(merge, bySha);
        return commits.filter(candidate -> brought.contains(candidate.sha()))
                .foldLeft(state, (next, candidate) -> place(candidate, commits, bySha, next));
    }

    /**
     * The nodes for commits in the order of {@link #arrange}. Each rail waits for a commit: the parent of a commit
     * above. A commit is on the leftmost rail waiting for it, else on the first free one, and the other rails
     * waiting for it join it there. Its rail then waits for its first parent; a merge puts each other parent on a
     * rail to its right: one already waiting for that parent, else the first free one.
     */
    public static List<Node> layout(List<Commit> arranged) {
        return arranged.foldLeft(new Laid(List.empty(), List.empty()), Laid::next)
                .latestFirst()
                .reverse();
    }

    /**
     * The first column from {@code from} on without a rail.
     */
    private static int free(List<Option<Sha>> rails, int from) {
        return List.range(from, rails.size())
                .find(at -> rails.get(at).isEmpty())
                .getOrElse(Math.max(from, rails.size()));
    }

    private static Map<Sha, Commit> index(List<Commit> commits) {
        return commits.toMap(Commit::sha, commit -> commit);
    }

    /**
     * The shas of the commits in the list reachable from the other parents of {@code merge}, but not from
     * its first parent.
     */
    private static Set<Sha> broughtIn(Commit merge, Map<Sha, Commit> bySha) {
        return reachable(merge.parents().drop(1), bySha)
                .removeAll(reachable(merge.parents().take(1), bySha));
    }

    private static Set<Sha> reachable(List<Sha> from, Map<Sha, Commit> bySha) {
        return Iterator.iterate(new Walk(from, HashSet.empty()), walk -> walk.step(bySha))
                .find(walk -> walk.todo().isEmpty())
                .map(Walk::seen)
                .getOrElse(HashSet.empty());
    }

    /**
     * A commit, and the rails on its row, by their column.
     *
     * @param column  the rail the commit is on
     * @param passing the other rails that go on past the commit, waiting for other commits
     * @param joins   the rails that end at the commit, as they waited for it too
     * @param opens   the rails that go on below to the other parents of a merge: a {@code passing} one already
     *                waiting for that parent, or a free one, which can be one of the {@code joins}
     * @param meets   the rails that from this commit on wait for the same commit as a rail to their left, which
     *                {@code git log --graph} ends right away rather than where they join
     */
    public record Node(
            Commit commit,
            int column,
            List<Integer> passing,
            List<Integer> joins,
            List<Integer> opens,
            List<Meet> meets) {}

    /**
     * A rail that waits for the same commit as the rail {@code into}, the leftmost one waiting for it.
     */
    public record Meet(int rail, int into) {}

    /**
     * The rails below the nodes laid out so far, by column, with the commit each waits for, and those nodes, last
     * laid out first.
     */
    private record Laid(List<Option<Sha>> rails, List<Node> latestFirst) {

        Laid next(Commit commit) {
            Option<Sha> sha = Option.of(commit.sha());
            int column = rails.contains(sha) ? rails.indexOf(sha) : free(rails, 0);
            List<Integer> joins = List.range(column + 1, rails.size())
                    .filter(at -> rails.get(at).equals(sha));
            List<Integer> passing = List.range(0, rails.size())
                    .filter(at -> at != column && rails.get(at).isDefined() && !joins.contains(at));
            List<Option<Sha>> kept = List.range(0, Math.max(rails.size(), column + 1))
                    .map(at -> at == column
                            ? commit.parents().headOption()
                            : joins.contains(at) ? Option.<Sha>none() : rails.get(at));
            Opened opened = commit.parents()
                    .drop(1)
                    .foldLeft(new Opened(kept, List.empty()), (state, parent) -> state.open(parent, column));
            List<Option<Sha>> below =
                    opened.rails().reverse().dropWhile(Option::isEmpty).reverse();
            List<Meet> meets = List.range(0, below.size())
                    .filter(at -> doubled(below, at)
                            && !(doubled(rails, at) && rails.get(at).equals(below.get(at))))
                    .map(at -> new Meet(at, below.indexOf(below.get(at))));
            return new Laid(
                    below, latestFirst.prepend(new Node(commit, column, passing, joins, opened.opens(), meets)));
        }

        /**
         * Whether the rail {@code at} waits for the same commit as a rail to its left.
         */
        private static boolean doubled(List<Option<Sha>> rails, int at) {
            return at < rails.size() && rails.get(at).isDefined() && rails.indexOf(rails.get(at)) < at;
        }
    }

    /**
     * The rails while the other parents of a merge get theirs, and the columns they got so far.
     */
    private record Opened(List<Option<Sha>> rails, List<Integer> opens) {

        Opened open(Sha parent, int column) {
            int at = List.range(column + 1, rails.size())
                    .find(waiting -> rails.get(waiting).contains(parent) && !opens.contains(waiting))
                    .getOrElse(() -> free(rails, column + 1));
            return new Opened(rails.padTo(at + 1, Option.none()).update(at, Option.of(parent)), opens.append(at));
        }
    }

    /**
     * The shas placed so far, and the commits in the order they were placed, last placed first.
     */
    private record Placed(Set<Sha> shas, List<Commit> latestFirst) {}

    /**
     * A depth-first walk through the parents: the shas still to visit, and those visited.
     */
    private record Walk(List<Sha> todo, Set<Sha> seen) {

        Walk step(Map<Sha, Commit> bySha) {
            return todo.pop2Option()
                    .map(next -> bySha.get(next._1)
                            .filter(commit -> !seen.contains(next._1))
                            .map(commit -> new Walk(next._2.prependAll(commit.parents()), seen.add(next._1)))
                            .getOrElse(() -> new Walk(next._2, seen)))
                    .getOrElse(this);
        }
    }
}
