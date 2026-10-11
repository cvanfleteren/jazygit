package net.vanfleteren.jazygit.git.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CommitGraphTest {

    private static Commit commit(String sha, String... parents) {
        return new Commit(sha, sha, "Ada", "ada@example.com", Instant.EPOCH, sha, "", List.of(parents));
    }

    private static List<String> shas(List<Commit> commits) {
        return commits.stream().map(Commit::sha).toList();
    }

    @Test
    void aLinearLogKeepsItsOrderAndHasNoBranches() {
        List<Commit> log = List.of(commit("c", "b"), commit("b", "a"), commit("a"));

        assertEquals(log, CommitGraph.arrange(log));
        assertEquals(List.of(0, 0, 0), CommitGraph.layout(log).stream().map(CommitGraph.Node::depth).toList());
    }

    @Test
    void theCommitsAMergeBroughtInFollowItAndTheLastOneClosesTheBranch() {
        // main: a - b - m (merges f2), feature: a - f1 - f2; by date the feature commits interleave.
        Commit m = commit("m", "b", "f2");
        Commit f2 = commit("f2", "f1");
        Commit b = commit("b", "a");
        Commit f1 = commit("f1", "a");
        Commit a = commit("a");

        List<Commit> arranged = CommitGraph.arrange(List.of(m, f2, b, f1, a));

        assertEquals(List.of("m", "f2", "f1", "b", "a"), shas(arranged));
        List<CommitGraph.Node> nodes = CommitGraph.layout(arranged);
        assertEquals(List.of(0, 1, 1, 0, 0), nodes.stream().map(CommitGraph.Node::depth).toList());
        assertEquals(List.of(false, false, true, false, false),
                nodes.stream().map(CommitGraph.Node::closesBranch).toList());
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
        assertEquals(List.of(0, 1, 2, 1, 0, 0),
                CommitGraph.layout(arranged).stream().map(CommitGraph.Node::depth).toList());
    }
}
