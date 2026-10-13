package net.vanfleteren.jazygit.git.model;

import io.vavr.collection.HashSet;
import io.vavr.collection.Iterator;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Set;

/**
 * Lays out a commit log with its merges: the commits a merge brought in follow it, so they can be shown
 * as a branch that connects back to the main line.
 */
public final class CommitGraph {

    private CommitGraph() {
    }

    /**
     * A commit, and where it stands in the branches of merges.
     *
     * @param depth        in how many merged branches the commit is
     * @param closesBranch whether it is the last commit of the (innermost) branch it is in
     */
    public record Node(Commit commit, int depth, boolean closesBranch) {
    }

    /**
     * The commits with every merge directly followed by the commits it brought in, i.e. those reachable
     * from its other parents but not from its first one; the rest keeps its order. Only commits in the
     * list are considered.
     */
    public static List<Commit> arrange(List<Commit> commits) {
        Map<Sha, Commit> bySha = index(commits);
        return commits
                .foldLeft(new Placed(HashSet.empty(), List.empty()),
                        (state, commit) -> place(commit, commits, bySha, state))
                .latestFirst()
                .reverse();
    }

    /**
     * The shas placed so far, and the commits in the order they were placed, last placed first.
     */
    private record Placed(Set<Sha> shas, List<Commit> latestFirst) {
    }

    private static Placed place(Commit commit, List<Commit> commits, Map<Sha, Commit> bySha, Placed state) {
        Placed placed = new Placed(state.shas().add(commit.sha()), state.latestFirst().prepend(commit));
        return state.shas().contains(commit.sha()) ? state
                : !commit.isMerge() ? placed
                : placeBroughtIn(commit, commits, bySha, placed);
    }

    /**
     * Places the commits the merge {@code merge} brought in.
     */
    private static Placed placeBroughtIn(Commit merge, List<Commit> commits, Map<Sha, Commit> bySha, Placed state) {
        Set<Sha> brought = broughtIn(merge, bySha);
        return commits
                .filter(candidate -> brought.contains(candidate.sha()))
                .foldLeft(state, (next, candidate) -> place(candidate, commits, bySha, next));
    }

    /**
     * The nodes for commits in the order of {@link #arrange}.
     */
    public static List<Node> layout(List<Commit> arranged) {
        Map<Sha, Commit> bySha = index(arranged);
        Map<Sha, Integer> position = arranged.zipWithIndex().toMap(entry -> entry._1.sha(), entry -> entry._2);
        List<Set<Sha>> brought = arranged.filter(Commit::isMerge).map(merge -> broughtIn(merge, bySha));
        // The last commit of each merged branch closes it.
        Set<Integer> closing = brought
                .flatMap(shas -> shas.flatMap(position::get).max())
                .toSet();
        return arranged.zipWithIndex().map(entry -> new Node(entry._1,
                brought.count(shas -> shas.contains(entry._1.sha())),
                closing.contains(entry._2)));
    }

    private static Map<Sha, Commit> index(List<Commit> commits) {
        return commits.toMap(Commit::sha, commit -> commit);
    }

    /**
     * The shas of the commits in the list reachable from the other parents of {@code merge}, but not from
     * its first parent.
     */
    private static Set<Sha> broughtIn(Commit merge, Map<Sha, Commit> bySha) {
        return reachable(merge.parents().drop(1), bySha).removeAll(reachable(merge.parents().take(1), bySha));
    }

    private static Set<Sha> reachable(List<Sha> from, Map<Sha, Commit> bySha) {
        return Iterator.iterate(new Walk(from, HashSet.empty()), walk -> walk.step(bySha))
                .find(walk -> walk.todo().isEmpty())
                .map(Walk::seen)
                .getOrElse(HashSet.empty());
    }

    /**
     * A depth-first walk through the parents: the shas still to visit, and those visited.
     */
    private record Walk(List<Sha> todo, Set<Sha> seen) {

        Walk step(Map<Sha, Commit> bySha) {
            return todo.pop2Option().map(next -> bySha.get(next._1)
                            .filter(commit -> !seen.contains(next._1))
                            .map(commit -> new Walk(next._2.prependAll(commit.parents()), seen.add(next._1)))
                            .getOrElse(() -> new Walk(next._2, seen)))
                    .getOrElse(this);
        }
    }
}
