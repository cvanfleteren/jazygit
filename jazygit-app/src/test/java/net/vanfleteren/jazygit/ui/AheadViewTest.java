package net.vanfleteren.jazygit.ui;

import static org.junit.jupiter.api.Assertions.*;

import dev.tamboui.style.Style;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.toolkit.app.ToolkitTestRunner;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.stream.Collectors;
import net.vanfleteren.jazygit.git.model.*;
import net.vanfleteren.jazygit.model.SampleData;
import net.vanfleteren.jazygit.state.Loadable;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.TestModels;
import org.junit.jupiter.api.Test;

/**
 * How a log shows the commits that are not on the base of its branch.
 */
class AheadViewTest {

    private static final BranchRef MAIN =
            new BranchRef.Remote(new RemoteBranch(RemoteBranch.DEFAULT_REMOTE, new Branch.Name("main")));
    private static final Commit TIP = commit("c");
    private static final Commit MIDDLE = commit("b");
    private static final Commit FORK = commit("a");
    private static final Commit OLD = commit("0");
    private static final List<Commit> LOG = List.of(TIP, MIDDLE, FORK, OLD);
    private static final Ahead AHEAD = new Ahead(TIP.sha(), MAIN, List.of(TIP, MIDDLE), false, Option.of(FORK));
    // More commits are not on the base than were read.
    private static final Ahead MORE = new Ahead(TIP.sha(), MAIN, List.of(TIP, MIDDLE), true, Option.of(FORK));

    private static Commit commit(String sha) {
        return new Commit(new Sha(sha.repeat(7)), "Ada", "ada@example.com", Instant.EPOCH, "msg " + sha, "");
    }

    @Test
    void theTitleCountsTheCommitsNotOnTheBase() {
        assertEquals("Commits · 2 ahead of origin/main", new AheadView(Option.of(AHEAD), false).title("Commits"));
        assertEquals("Commits · only 2 ahead of origin/main", new AheadView(Option.of(AHEAD), true).title("Commits"));
        assertEquals("Commits · 300+ ahead of origin/main", new AheadView(Option.of(MORE), false).title("Commits"));
        assertEquals("Commits", new AheadView(Option.none(), true).title("Commits"));
    }

    @Test
    void onlyTheCommitsNotOnTheBaseAndTheForkPointAreListedWhenAsked() {
        assertEquals(LOG, new AheadView(Option.of(AHEAD), false).shown(LOG));
        assertEquals(List.of(TIP, MIDDLE, FORK), new AheadView(Option.of(AHEAD), true).shown(LOG));
        assertEquals(List.of(TIP, MIDDLE), new AheadView(Option.of(MORE), true).shown(LOG));
        assertEquals(LOG, new AheadView(Option.none(), true).shown(LOG));
    }

    @Test
    void theCommitsOnTheBaseAreDimmedAndTheForkPointMarked() {
        AheadView only = new AheadView(Option.of(AHEAD), true);

        List<LoadableList.Row> rows =
                CommitsPanel.marked(CommitsPanel.rows(CommitGraph.layout(only.shown(LOG))), only.shown(LOG), only);

        assertEquals("aaaaaaa Ad ○ ⑂ origin/main msg a", rows.last().text());
        assertTrue(rows.last().segs().forAll(seg -> seg.cssClass().contains(Theme.Classes.DIM)));
        assertTrue(rows.head().segs().forAll(seg -> seg.cssClass().isEmpty()));
        assertFalse(new AheadView(Option.of(MORE), false).dimmed(OLD.sha()));
    }

    @Test
    void theBranchLogDimsTheCommitsOnTheBaseAndMarksTheForkPointAfterItsSha() {
        Style dimmed = Style.EMPTY.dim();
        Theme.SpanStyles styles = cssClass -> cssClass.equals(Theme.Classes.DIM) ? dimmed : Style.EMPTY;
        AheadView only = new AheadView(Option.of(AHEAD), true);

        java.util.List<Line> lines = ContentPanel.logText(only.shown(LOG), ZoneOffset.UTC, only, styles)
                .lines();

        List<String> firstLines = List.ofAll(lines)
                .map(line -> line.spans().stream().map(Span::content).collect(Collectors.joining()))
                .filter(line -> line.startsWith("* "));
        assertEquals(List.of("* commit ccccccc", "* commit bbbbbbb", "* commit aaaaaaa  ⑂ origin/main"), firstLines);
        List<Boolean> dim = List.ofAll(lines)
                .filter(line -> !line.spans().isEmpty())
                .map(line -> line.spans().stream().allMatch(span -> span.style().equals(dimmed)));
        // Only the block of the fork point, the last commit, is dimmed.
        assertEquals(List.of(false, true), dim.distinct());
        assertTrue(dim.dropWhile(isDim -> !isDim).forAll(isDim -> isDim));
    }

    @Test
    void theCommitsPaneSaysHowManyCommitsAreNotOnTheBase() throws Exception {
        Model model = TestModels.loaded(new SampleData())
                .mapRepo(repo ->
                        repo.withCommits(new Loadable.Loaded<>(LOG)).withAhead(new Loadable.Loaded<>(Option.of(AHEAD))))
                .mapRepoView(repoView -> repoView.withOnlyAhead(true));
        CommitsPanel panel = new CommitsPanel(msg -> {}, () -> {});

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(model, CommitsPanel.ID))) {
            String shown = RenderedText.of(testRunner, () -> panel.render(model, CommitsPanel.ID));

            assertTrue(shown.contains("Commits · only 2 ahead of origin/main"), shown);
            assertTrue(shown.contains("⑂ origin/main msg a"), shown);
            assertFalse(shown.contains("msg 0"), shown);
            SettledPilot.of(testRunner).quit();
        }
    }
}
