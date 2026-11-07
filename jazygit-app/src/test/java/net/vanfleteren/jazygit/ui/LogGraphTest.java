package net.vanfleteren.jazygit.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import java.time.Instant;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.git.model.CommitGraph;
import net.vanfleteren.jazygit.git.model.Fixtures;
import net.vanfleteren.jazygit.git.model.Sha;
import org.junit.jupiter.api.Test;

class LogGraphTest {

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

    @Test
    void theGutterDrawsRailsLikeGitLogGraph() {
        // main: a - b - m (merges f), feature: a - f.
        List<CommitGraph.Node> nodes = CommitGraph.layout(
                CommitGraph.arrange(List.of(commit("m", "b", "f"), commit("f", "a"), commit("b", "a"), commit("a"))));
        List<String> lines = List.of("first", "second", "third");

        assertEquals(
                List.of(
                        List.of("*   first", "|\\  Merge", "| | second", "| | third"),
                        List.of("| * first", "| | second", "| | third"),
                        List.of("* | first", "| | second", "| | third"),
                        List.of("|/", "* first", "| second", "| third")),
                nodes.map(node -> LogGraph.gutter(node).draw(lines, "Merge")).toList());
    }

    @Test
    void aMergeWithoutLinesDrawsNone() {
        CommitGraph.Node merge = new CommitGraph.Node(commit("m", "b", "f"), 0, List.empty(), List.empty(), List.of(1));

        assertEquals(List.empty(), LogGraph.gutter(merge).draw(List.empty(), "Merge"));
    }

    @Test
    void eachRailThatJoinsACommitEndsOnALineOfItsOwnBeforeItTheOutermostFirst() {
        // x is merged into y, y's branch into w and w's branch into main; c and b are on main.
        List<CommitGraph.Node> nodes = CommitGraph.layout(CommitGraph.arrange(List.of(
                commit("m", "c", "v"),
                commit("v", "w"),
                commit("w", "b", "z"),
                commit("z", "y"),
                commit("y", "a", "x"),
                commit("x", "a"),
                commit("c", "b"),
                commit("b", "a"),
                commit("a"))));
        List<String> lines = List.of("first", "second");

        assertEquals(
                List.of(
                        List.of("| | *   first", "| | |\\  Merge", "| | | | second"),
                        List.of("| | | * first", "| | | | second"),
                        List.of("* | | | first", "| | | | second"),
                        List.of("|/  | |", "*   | | first", "|   | | second"),
                        List.of("|   |/", "|__/", "* first", "| second")),
                nodes.drop(4)
                        .map(node -> LogGraph.gutter(node).draw(lines, "Merge"))
                        .toList());
    }

    @Test
    void aMergeOpensItsRailPastTheRailsThatGoOn() {
        // f is merged into m, g into n; both forked from b.
        List<CommitGraph.Node> nodes = CommitGraph.layout(CommitGraph.arrange(List.of(
                commit("m", "n", "f"), commit("f", "b"), commit("n", "b", "g"), commit("g", "b"), commit("b"))));
        List<String> lines = List.of("first", "second");

        assertEquals(
                List.of(
                        List.of("* |   first", "|-|\\  Merge", "| | | second"),
                        List.of("| | * first", "| | | second"),
                        List.of("| |/", "|/", "* first", "| second")),
                nodes.drop(2)
                        .map(node -> LogGraph.gutter(node).draw(lines, "Merge"))
                        .toList());
    }
}
