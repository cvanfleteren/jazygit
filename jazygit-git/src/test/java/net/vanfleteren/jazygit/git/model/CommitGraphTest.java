package net.vanfleteren.jazygit.git.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class CommitGraphTest {

    private static Commit commit(String sha, String... parents) {
        return Fixtures.commit(
                new Sha(sha),
                "Ada",
                "ada@example.com",
                Instant.EPOCH,
                sha,
                "",
                List.of(parents).map(Sha::new));
    }

    private static List<String> shas(List<Commit> commits) {
        return commits.map(c -> c.sha().value()).toList();
    }

    private static CommitGraph.Node node(Commit commit, int column, List<Integer> passing) {
        return node(commit, column, passing, List.empty(), List.empty());
    }

    private static CommitGraph.Node node(
            Commit commit, int column, List<Integer> passing, List<Integer> joins, List<Integer> opens) {
        return new CommitGraph.Node(commit, column, passing, joins, opens);
    }

    @Test
    void aLinearLogKeepsItsOrderOnOneRail() {
        Commit c = commit("c", "b");
        Commit b = commit("b", "a");
        Commit a = commit("a");
        List<Commit> log = List.of(c, b, a);

        assertEquals(log, CommitGraph.arrange(log));
        assertEquals(
                List.of(node(c, 0, List.empty()), node(b, 0, List.empty()), node(a, 0, List.empty())),
                CommitGraph.layout(log));
    }

    @Test
    void theCommitsAMergeBroughtInFollowItOnARailThatJoinsWhereTheirBranchForked() {
        // main: a - b - m (merges f2), feature: a - f1 - f2; by date the feature commits interleave.
        Commit m = commit("m", "b", "f2");
        Commit f2 = commit("f2", "f1");
        Commit b = commit("b", "a");
        Commit f1 = commit("f1", "a");
        Commit a = commit("a");

        List<Commit> arranged = CommitGraph.arrange(List.of(m, f2, b, f1, a));

        assertEquals(List.of("m", "f2", "f1", "b", "a"), shas(arranged));
        assertEquals(
                List.of(
                        node(m, 0, List.empty(), List.empty(), List.of(1)),
                        node(f2, 1, List.of(0)),
                        node(f1, 1, List.of(0)),
                        node(b, 0, List.of(1)),
                        node(a, 0, List.empty(), List.of(1), List.empty())),
                CommitGraph.layout(arranged));
    }

    @Test
    void eachBranchOfAMergeTrainJoinsTheStartOfTheBranchItWasMergedInto() {
        // Each train starts from the start of the one before, which goes on with work of its own; each train is
        // merged into the one before, the first one into main.
        Commit m1 = commit("m1", "r", "m2");
        Commit m2 = commit("m2", "w1", "m3");
        Commit m3 = commit("m3", "w2", "s3");
        Commit s3 = commit("s3", "s2");
        Commit w2 = commit("w2", "s2");
        Commit s2 = commit("s2", "s1");
        Commit w1 = commit("w1", "s1");
        Commit s1 = commit("s1", "a");
        Commit r = commit("r", "a");
        Commit a = commit("a");

        List<Commit> arranged = CommitGraph.arrange(List.of(m1, m2, m3, s3, w2, s2, w1, s1, r, a));

        assertEquals(List.of("m1", "m2", "m3", "s3", "w2", "s2", "w1", "s1", "r", "a"), shas(arranged));
        assertEquals(
                List.of(
                        node(m1, 0, List.empty(), List.empty(), List.of(1)),
                        node(m2, 1, List.of(0), List.empty(), List.of(2)),
                        node(m3, 2, List.of(0, 1), List.empty(), List.of(3)),
                        node(s3, 3, List.of(0, 1, 2)),
                        node(w2, 2, List.of(0, 1, 3)),
                        node(s2, 2, List.of(0, 1), List.of(3), List.empty()),
                        node(w1, 1, List.of(0, 2)),
                        node(s1, 1, List.of(0), List.of(2), List.empty()),
                        node(r, 0, List.of(1)),
                        node(a, 0, List.empty(), List.of(1), List.empty())),
                CommitGraph.layout(arranged));
    }

    @Test
    void mergesInsideABranchAreNested() {
        // inner merges g into f; outer merges f into main.
        Commit outer = commit("m", "b", "f");
        Commit f = commit("f", "e", "g");
        Commit g = commit("g", "a");
        Commit e = commit("e", "a");
        Commit b = commit("b", "a");
        Commit a = commit("a");

        List<Commit> arranged = CommitGraph.arrange(List.of(outer, f, g, e, b, a));

        assertEquals(List.of("m", "f", "g", "e", "b", "a"), shas(arranged));
        assertEquals(
                List.of(
                        node(outer, 0, List.empty(), List.empty(), List.of(1)),
                        node(f, 1, List.of(0), List.empty(), List.of(2)),
                        node(g, 2, List.of(0, 1)),
                        node(e, 1, List.of(0, 2)),
                        node(b, 0, List.of(1, 2)),
                        node(a, 0, List.empty(), List.of(1, 2), List.empty())),
                CommitGraph.layout(arranged));
    }

    @Test
    void aMergeWhileARailPassesOpensOneFurtherOut() {
        // f is merged into m, g into n; both forked from b.
        Commit m = commit("m", "n", "f");
        Commit f = commit("f", "b");
        Commit n = commit("n", "b", "g");
        Commit g = commit("g", "b");
        Commit b = commit("b");

        List<Commit> arranged = CommitGraph.arrange(List.of(m, f, n, g, b));

        assertEquals(
                List.of(
                        node(m, 0, List.empty(), List.empty(), List.of(1)),
                        node(f, 1, List.of(0)),
                        node(n, 0, List.of(1), List.empty(), List.of(2)),
                        node(g, 2, List.of(0, 1)),
                        node(b, 0, List.empty(), List.of(1, 2), List.empty())),
                CommitGraph.layout(arranged));
    }

    @Test
    void aMergeWhereABranchForkedOpensOnTheRailThatJoinsIt() {
        // f forked from n and is merged into m; n merges g.
        Commit m = commit("m", "n", "f");
        Commit f = commit("f", "n");
        Commit n = commit("n", "b", "g");
        Commit g = commit("g", "b");
        Commit b = commit("b");

        List<Commit> arranged = CommitGraph.arrange(List.of(m, f, n, g, b));

        assertEquals(
                List.of(
                        node(m, 0, List.empty(), List.empty(), List.of(1)),
                        node(f, 1, List.of(0)),
                        node(n, 0, List.empty(), List.of(1), List.of(1)),
                        node(g, 1, List.of(0)),
                        node(b, 0, List.empty(), List.of(1), List.empty())),
                CommitGraph.layout(arranged));
    }

    @Test
    void aMergeOfACommitARailWaitsForAlreadyOpensOnThatRail() {
        // m merges k, which is on p; c, on m's own line, merges p too.
        Commit m = commit("m", "c", "k");
        Commit k = commit("k", "p");
        Commit c = commit("c", "q", "p");
        Commit p = commit("p", "q");
        Commit q = commit("q");

        assertEquals(
                List.of(
                        node(m, 0, List.empty(), List.empty(), List.of(1)),
                        node(k, 1, List.of(0)),
                        node(c, 0, List.of(1), List.empty(), List.of(1)),
                        node(p, 1, List.of(0)),
                        node(q, 0, List.empty(), List.of(1), List.empty())),
                CommitGraph.layout(List.of(m, k, c, p, q)));
    }
}
