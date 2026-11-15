package net.vanfleteren.jazygit.git.model;

import io.vavr.Tuple;
import io.vavr.collection.*;
import io.vavr.control.Option;
import java.util.Arrays;
import java.util.PriorityQueue;
import java.util.stream.IntStream;

/**
 * Lays out a commit log with its merges: the commits a merge brought in follow it, so they can be shown
 * as a branch that connects back to the main line.
 */
public final class CommitGraph {

    // What a commit is reached from in the walk down from a merge: its first parent, its other ones, or both.
    private static final byte FIRST = 1;
    private static final byte OTHER = 2;
    // The position of a parent the list leaves out.
    private static final int GONE = -1;

    private CommitGraph() {}

    /**
     * The commits with every merge directly followed by the commits it brought in, i.e. those reachable
     * from its other parents but not from its first one; the rest keeps its order. Only commits in the
     * list are considered.
     *
     * <p>A commit is known by its position in the list here, and the work is done on arrays local to the call, as
     * this runs on every log read: with persistent sets, walking all the commits below each merge cost seconds for
     * ten thousand commits of a repository with many merges. Nothing of it escapes the call.
     */
    public static List<Commit> arrange(List<Commit> commits) {
        Vector<Commit> listed = commits.toVector();
        int[][] parents = parentPositions(listed);
        int[] down = topologically(parents);
        int[] rank = new int[down.length];
        for (int at = 0; at < down.length; at++) {
            rank[down[at]] = at;
        }
        byte[] marks = new byte[listed.size()];
        boolean[] placed = new boolean[listed.size()];
        int[] order = new int[listed.size()];
        int count = 0;
        for (int at = 0; at < listed.size(); at++) {
            count = place(at, listed, parents, down, rank, marks, placed, order, count);
        }
        return List.ofAll(Arrays.stream(order, 0, count).mapToObj(listed::get));
    }

    /**
     * For each commit, the positions of its parents, in their order: {@link #GONE} for one the list leaves out.
     */
    private static int[][] parentPositions(Vector<Commit> listed) {
        Map<Sha, Integer> position =
                listed.zipWithIndex((commit, at) -> Tuple.of(commit.sha(), at)).toMap(entry -> entry);
        return listed.map(commit -> commit.parents()
                        .map(parent -> position.get(parent).getOrElse(GONE))
                        .toJavaStream()
                        .mapToInt(Integer::intValue)
                        .toArray())
                .toJavaArray(int[][]::new);
    }

    /**
     * The positions of the commits, each before its parents: in the order of the list, as {@code git log} lists them
     * so, but where clocks were off and it lists a parent before a child, the parent comes once all its children
     * did, as early as it can.
     */
    private static int[] topologically(int[][] parents) {
        int[] children = new int[parents.length];
        for (int[] own : parents) {
            for (int parent : own) {
                if (parent != GONE) {
                    children[parent]++;
                }
            }
        }
        PriorityQueue<Integer> ready = new PriorityQueue<>();
        for (int at = 0; at < parents.length; at++) {
            if (children[at] == 0) {
                ready.add(at);
            }
        }
        int[] order = new int[parents.length];
        int count = 0;
        while (!ready.isEmpty()) {
            int at = ready.poll();
            order[count++] = at;
            for (int parent : parents[at]) {
                if (parent != GONE && --children[parent] == 0) {
                    ready.add(parent);
                }
            }
        }
        return order;
    }

    /**
     * Places the commit at {@code at}, unless it is placed already, and when it is a merge the commits it brought
     * in after it.
     *
     * @return how many commits are placed now
     */
    private static int place(
            int at,
            Vector<Commit> listed,
            int[][] parents,
            int[] down,
            int[] rank,
            byte[] marks,
            boolean[] placed,
            int[] order,
            int count) {
        if (placed[at]) {
            return count;
        }
        placed[at] = true;
        order[count] = at;
        int next = count + 1;
        if (listed.get(at).isMerge()) {
            for (int brought : broughtIn(at, parents, down, rank, marks)) {
                next = place(brought, listed, parents, down, rank, marks, placed, order, next);
            }
        }
        return next;
    }

    /**
     * The positions of the commits the merge at {@code merge} brought in, in order: those reachable from its other
     * parents, but not from its first one. One pass down from the merge, each commit before its parents, marks each
     * commit with whether the first parent, the others or both reach it. A commit only the others reach is final once
     * the pass gets to it, as all that reach it came before it; the pass stops when none is waiting, much as git
     * finds a merge base. So a merge costs about as much as the branch it brought in, not as the whole list.
     *
     * @param down  the positions of the commits, each before its parents
     * @param rank  for each position, where it is in {@code down}
     * @param marks all zero, as they are left again
     */
    private static int[] broughtIn(int merge, int[][] parents, int[] down, int[] rank, byte[] marks) {
        int[] own = parents[merge];
        int waiting = 0;
        int from = marks.length;
        int to = rank[merge];
        for (int parent = 0; parent < own.length; parent++) {
            if (own[parent] != GONE) {
                waiting += mark(marks, own[parent], parent == 0 ? FIRST : OTHER);
                from = Math.min(from, rank[own[parent]]);
                to = Math.max(to, rank[own[parent]]);
            }
        }
        IntStream.Builder brought = IntStream.builder();
        for (int step = from; step < marks.length && waiting > 0; step++) {
            int at = down[step];
            byte mark = marks[at];
            if (mark == OTHER) {
                waiting--;
                brought.add(at);
            }
            if (mark != 0) {
                for (int parent : parents[at]) {
                    if (parent != GONE) {
                        waiting += mark(marks, parent, mark);
                        to = Math.max(to, rank[parent]);
                    }
                }
            }
        }
        for (int step = from; step <= to; step++) {
            marks[down[step]] = 0;
        }
        return brought.build().sorted().toArray();
    }

    /**
     * Adds {@code mark} to the marks of the commit at {@code at}.
     *
     * @return by how much that changes the number of commits that only the other parents reach
     */
    private static int mark(byte[] marks, int at, byte mark) {
        byte before = marks[at];
        byte after = (byte) (before | mark);
        marks[at] = after;
        return (after == OTHER ? 1 : 0) - (before == OTHER ? 1 : 0);
    }

    /**
     * The nodes for commits in the order of {@link #arrange}. Each rail waits for a commit: the parent of a commit
     * above. A commit is on the leftmost rail waiting for it, else on the first free one, and the other rails
     * waiting for it join it there. Its rail then waits for its first parent; a merge puts each other parent on a
     * rail to its right: one already waiting for that parent, else the first free one. Only parents in the list get
     * a rail, so a list of part of the history, as a range, has no rails waiting for commits it does not show.
     */
    public static List<Node> layout(List<Commit> arranged) {
        Set<Sha> listed = arranged.map(Commit::sha).toSet();
        return arranged.foldLeft(
                        new Laid(Array.empty(), List.empty()),
                        (laid, commit) -> laid.next(commit, commit.parents().filter(listed::contains)))
                .latestFirst()
                .reverse();
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
     * laid out first. The rails are an {@link Array}, as they are looked up by column; a row is worked out on arrays
     * of its own, as a graph can be hundreds of rails wide and each row looks at all of them.
     */
    private record Laid(Array<Option<Sha>> rails, List<Node> latestFirst) {

        /**
         * @param parents the parents of {@code commit} that get a rail
         */
        Laid next(Commit commit, List<Sha> parents) {
            Option<Sha> sha = Option.of(commit.sha());
            String own = commit.sha().value();
            int waiting = rails.indexOf(sha);
            int column = waiting >= 0 ? waiting : free(rails.toJavaArray(), 0, rails.size());
            int width = Math.max(rails.size(), column + 1);
            @SuppressWarnings("unchecked")
            Option<Sha>[] below = (Option<Sha>[]) new Option<?>[width + parents.size()];
            Arrays.fill(below, Option.none());
            IntStream.Builder joins = IntStream.builder();
            IntStream.Builder passing = IntStream.builder();
            for (int at = 0; at < rails.size(); at++) {
                Option<Sha> rail = rails.get(at);
                // The rails that wait for the commit are its own and those that join it; all others pass it. The
                // shas are compared by their text, which is quicker than as records.
                if (rail.isDefined() && !rail.get().value().equals(own)) {
                    passing.add(at);
                    below[at] = rail;
                } else if (rail.isDefined() && at != column) {
                    joins.add(at);
                }
            }
            below[column] = parents.headOption();
            boolean[] opened = new boolean[below.length];
            IntStream.Builder opens = IntStream.builder();
            for (Sha parent : parents.drop(1)) {
                int at = opening(below, opened, parent, column, width);
                below[at] = Option.of(parent);
                opened[at] = true;
                opens.add(at);
                width = Math.max(width, at + 1);
            }
            int size = width;
            while (size > 0 && below[size - 1].isEmpty()) {
                size--;
            }
            Array<Option<Sha>> next = Array.of(Arrays.copyOf(below, size));
            int[] leftAbove = leftmost(rails);
            int[] leftBelow = leftmost(next);
            List<Meet> meets = List.ofAll(IntStream.range(0, next.size())
                    .filter(at -> leftBelow[at] < at
                            && !(at < rails.size()
                                    && leftAbove[at] < at
                                    && rails.get(at).equals(next.get(at))))
                    .mapToObj(at -> new Meet(at, leftBelow[at]))
                    .toList());
            return new Laid(
                    next,
                    latestFirst.prepend(new Node(
                            commit,
                            column,
                            List.ofAll(passing.build().toArray()),
                            List.ofAll(joins.build().toArray()),
                            List.ofAll(opens.build().toArray()),
                            meets)));
        }

        /**
         * The column that the rail to {@code parent}, another parent of the merge in {@code column}, goes on in: one
         * to its right already waiting for that parent, and not opened for it, else the first free one to its right.
         *
         * @param width how many of {@code rails} are in use
         */
        private static int opening(Option<Sha>[] rails, boolean[] opened, Sha parent, int column, int width) {
            return IntStream.range(column + 1, width)
                    .filter(at ->
                            !opened[at] && rails[at].exists(rail -> rail.value().equals(parent.value())))
                    .findFirst()
                    .orElseGet(() -> free(rails, column + 1, width));
        }

        /**
         * The first column from {@code from} on without a rail, of the first {@code width} of {@code rails}.
         */
        private static int free(Object[] rails, int from, int width) {
            return IntStream.range(from, width)
                    .filter(at -> ((Option<?>) rails[at]).isEmpty())
                    .findFirst()
                    .orElse(Math.max(from, width));
        }

        /**
         * For each column, the leftmost one whose rail waits for the same commit, worked out once for a row, as looking
         * it up for each rail made a row cost the square of its rails. A column without a rail is its own.
         */
        private static int[] leftmost(Array<Option<Sha>> rails) {
            var first = new java.util.HashMap<Sha, Integer>();
            return IntStream.range(0, rails.size())
                    .map(at -> rails.get(at)
                            .map(sha -> first.computeIfAbsent(sha, key -> at))
                            .getOrElse(at))
                    .toArray();
        }
    }
}
