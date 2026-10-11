package net.vanfleteren.jazygit.git.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
        Map<String, Commit> bySha = index(commits);
        List<Commit> arranged = new ArrayList<>(commits.size());
        Set<String> placed = new HashSet<>();
        for (Commit commit : commits) {
            place(commit, commits, bySha, placed, arranged);
        }
        return arranged;
    }

    private static void place(Commit commit, List<Commit> commits, Map<String, Commit> bySha, Set<String> placed,
                              List<Commit> arranged) {
        if (!placed.add(commit.sha())) {
            return;
        }
        arranged.add(commit);
        if (commit.isMerge()) {
            Set<String> brought = broughtIn(commit, bySha);
            for (Commit candidate : commits) {
                if (brought.contains(candidate.sha())) {
                    place(candidate, commits, bySha, placed, arranged);
                }
            }
        }
    }

    /**
     * The nodes for commits in the order of {@link #arrange}.
     */
    public static List<Node> layout(List<Commit> arranged) {
        Map<String, Commit> bySha = index(arranged);
        int[] depth = new int[arranged.size()];
        boolean[] closes = new boolean[arranged.size()];
        Map<String, Integer> position = new HashMap<>();
        for (int i = 0; i < arranged.size(); i++) {
            position.put(arranged.get(i).sha(), i);
        }
        for (Commit merge : arranged) {
            if (!merge.isMerge()) {
                continue;
            }
            int last = -1;
            for (String sha : broughtIn(merge, bySha)) {
                int at = position.get(sha);
                depth[at]++;
                last = Math.max(last, at);
            }
            if (last >= 0) {
                closes[last] = true;
            }
        }
        List<Node> nodes = new ArrayList<>(arranged.size());
        for (int i = 0; i < arranged.size(); i++) {
            nodes.add(new Node(arranged.get(i), depth[i], closes[i]));
        }
        return nodes;
    }

    private static Map<String, Commit> index(List<Commit> commits) {
        Map<String, Commit> bySha = new HashMap<>();
        commits.forEach(c -> bySha.put(c.sha(), c));
        return bySha;
    }

    /**
     * The shas of the commits in the list reachable from the other parents of {@code merge}, but not from
     * its first parent.
     */
    private static Set<String> broughtIn(Commit merge, Map<String, Commit> bySha) {
        Set<String> main = reachable(merge.parents().subList(0, 1), bySha);
        Set<String> brought = reachable(merge.parents().subList(1, merge.parents().size()), bySha);
        brought.removeAll(main);
        return brought;
    }

    private static Set<String> reachable(List<String> from, Map<String, Commit> bySha) {
        Set<String> seen = new HashSet<>();
        ArrayDeque<String> todo = new ArrayDeque<>(from);
        while (!todo.isEmpty()) {
            String sha = todo.pop();
            Commit commit = bySha.get(sha);
            if (commit != null && seen.add(sha)) {
                todo.addAll(commit.parents());
            }
        }
        return seen;
    }
}
