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

    private static List<List<String>> drawn(List<Commit> commits, List<String> lines) {
        return LogGraph.gutters(CommitGraph.layout(CommitGraph.arrange(commits)))
                .map(gutter -> gutter.draw(lines, "Merge"))
                .toList();
    }

    @Test
    void theGutterDrawsRailsLikeGitLogGraph() {
        // main: a - b - m (merges f), feature: a - f.
        assertEquals(
                List.of(
                        List.of("*   first", "|\\  Merge", "| | second", "| | third"),
                        List.of("| * first", "| | second", "| | third"),
                        List.of("* | first", "|/  second", "|   third"),
                        List.of("* first", "| second", "| third")),
                drawn(
                        List.of(commit("m", "b", "f"), commit("f", "a"), commit("b", "a"), commit("a")),
                        List.of("first", "second", "third")));
    }

    @Test
    void aMergeWithoutLinesDrawsNone() {
        CommitGraph.Node merge =
                new CommitGraph.Node(commit("m", "b", "f"), 0, List.empty(), List.empty(), List.of(1), List.empty());

        assertEquals(List.empty(), LogGraph.gutter(merge, List.empty()).draw(List.empty(), "Merge"));
    }

    @Test
    void railsThatComeToWaitForTheSameCommitAsOneToTheirLeftEndRightAwayOneALine() {
        // x is merged into y, y's branch into w and w's branch into main; c and b are on main.
        assertEquals(
                List.of(
                        List.of("| | *   first", "| | |\\  Merge", "| | | | second"),
                        List.of("| | | * first", "| | |/  second"),
                        List.of("* | | first", "|/  | second"),
                        List.of("*   | first", "|__/  second"),
                        List.of("* first", "| second")),
                drawn(
                                List.of(
                                        commit("m", "c", "v"),
                                        commit("v", "w"),
                                        commit("w", "b", "z"),
                                        commit("z", "y"),
                                        commit("y", "a", "x"),
                                        commit("x", "a"),
                                        commit("c", "b"),
                                        commit("b", "a"),
                                        commit("a")),
                                List.of("first", "second"))
                        .drop(4));
    }

    @Test
    void aMergeOpensItsRailPastTheRailsThatGoOn() {
        // f is merged into m, g into n; both forked from b.
        assertEquals(
                List.of(
                        List.of("* |   first", "|-|\\  Merge", "|/  | second"),
                        List.of("|   * first", "|__/  second"),
                        List.of("* first", "| second")),
                drawn(
                                List.of(
                                        commit("m", "n", "f"),
                                        commit("f", "b"),
                                        commit("n", "b", "g"),
                                        commit("g", "b"),
                                        commit("b")),
                                List.of("first", "second"))
                        .drop(2));
    }

    @Test
    void aOneLineCommitAfterWhichARailEndsGetsABlankLineToEndItOn() {
        CommitGraph.Node meeting = new CommitGraph.Node(
                commit("f", "a"), 1, List.of(0), List.empty(), List.empty(), List.of(new CommitGraph.Meet(1, 0)));

        assertEquals(
                List.of("| * only", "|/  "),
                LogGraph.gutter(meeting, List.empty()).draw(List.of("only"), "Merge"));
    }

    @Test
    void aRailThatJoinsACommitWithoutHavingEndedEndsOnALineOfItsOwnBeforeIt() {
        CommitGraph.Node joined =
                new CommitGraph.Node(commit("a"), 0, List.empty(), List.of(1), List.empty(), List.empty());

        assertEquals(
                List.of("|/", "* only"), LogGraph.gutter(joined, List.empty()).draw(List.of("only"), "Merge"));
    }

    @Test
    void anOctopusMergeOpensARailForEachOtherParentOnOneLine() {
        assertEquals(
                List.of(
                        List.of("*     first", "|\\-\\  Merge", "| | | second"),
                        List.of("| * | first", "|/  | second"),
                        List.of("|   * first", "|__/  second"),
                        List.of("* first", "| second")),
                drawn(
                        List.of(commit("o", "a", "b", "c"), commit("b", "a"), commit("c", "a"), commit("a")),
                        List.of("first", "second")));
    }
}
