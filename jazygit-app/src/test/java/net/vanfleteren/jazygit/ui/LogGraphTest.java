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
                        List.of("| * first", "|/  second", "|   third"),
                        List.of("* first", "| second", "| third"),
                        List.of("* first", "| second", "| third")),
                nodes.map(node -> LogGraph.gutter(node).draw(lines, "Merge")).toList());
    }

    @Test
    void aMergeWithoutLinesDrawsNone() {
        CommitGraph.Node merge = new CommitGraph.Node(commit("m", "b", "f"), 0, false);

        assertEquals(List.empty(), LogGraph.gutter(merge).draw(List.empty(), "Merge"));
    }
}
