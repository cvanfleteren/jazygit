package net.vanfleteren.jazygit.ui.commits;

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
import net.vanfleteren.jazygit.content.ContentText;
import net.vanfleteren.jazygit.git.model.*;
import net.vanfleteren.jazygit.model.SampleData;
import net.vanfleteren.jazygit.state.Loadable;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.ui.RenderedText;
import net.vanfleteren.jazygit.ui.SettledPilot;
import net.vanfleteren.jazygit.ui.theme.Theme;
import net.vanfleteren.jazygit.ui.widgets.LoadableList;
import org.junit.jupiter.api.Test;

/**
 * How a log shows the commits that are not on the base of its branch.
 */
class AheadViewTest {

    private static final BranchRef MAIN =
            new BranchRef.Remote(new RemoteBranch(RemoteBranch.DEFAULT_REMOTE, new BranchName("main")));
    private static final Commit TIP = commit("c");
    private static final Commit MIDDLE = commit("b");
    private static final Commit FORK = commit("a");
    private static final Commit OLD = commit("0");
    private static final List<Commit> LOG = List.of(TIP, MIDDLE, FORK, OLD);
    private static final Ahead AHEAD = new Ahead(TIP.sha(), MAIN, List.of(TIP, MIDDLE), false, Option.of(FORK));
    // More commits are not on the base than were read.
    private static final Ahead MORE = new Ahead(TIP.sha(), MAIN, List.of(TIP, MIDDLE), true, Option.of(FORK));

    private static Commit commit(String sha) {
        return Fixtures.commit(new Sha(sha.repeat(7)), "Ada", "ada@example.com", Instant.EPOCH, "msg " + sha, "");
    }

    @Test
    void theTitleCountsTheCommitsNotOnTheBase() {
        assertEquals(
                "Commits · 2 ahead of origin/main", new AheadView(Option.of(AHEAD), Option.none()).title("Commits"));
        assertEquals(
                "Commits · 3000+ ahead of origin/main", new AheadView(Option.of(MORE), Option.none()).title("Commits"));
        assertEquals("Commits", new AheadView(Option.none(), Option.none()).title("Commits"));
    }

    @Test
    void theTitleCountsTheCommitsAheadAndBehindOnceThoseAreRead() {
        assertEquals("Commits · 1 ahead, 6 behind origin/main", title(1, 6));
        assertEquals("Commits · 301 ahead of origin/main", title(301, 0));
        assertEquals("Commits · 6 behind origin/main", title(0, 6));
        assertEquals("Commits · 0 ahead of origin/main", title(0, 0));
    }

    @Test
    void countsAgainstAnotherBaseAreNotTheTitles() {
        BaseDistance other = new BaseDistance(new BranchRef.Local(new BranchName("main")), TIP.sha(), 1, 6);

        assertEquals(
                "Commits · 2 ahead of origin/main", new AheadView(Option.of(AHEAD), Option.of(other)).title("Commits"));
    }

    private static String title(int ahead, int behind) {
        return new AheadView(Option.of(MORE), Option.of(new BaseDistance(MAIN, TIP.sha(), ahead, behind)))
                .title("Commits");
    }

    @Test
    void countsReadAtAnotherTipOfTheBranchAreNotTheTitles() {
        BaseDistance before = new BaseDistance(MAIN, MIDDLE.sha(), 1, 6);

        assertEquals(
                "Commits · 2 ahead of origin/main",
                new AheadView(Option.of(AHEAD), Option.of(before)).title("Commits"));
    }

    @Test
    void theCommitsOnTheBaseAreDimmed() {
        AheadView ahead = new AheadView(Option.of(AHEAD), Option.none());

        List<LoadableList.Row> rows =
                CommitsPanel.graphed(LOG, Option.none(), ahead).map(LoadableList.Item::row);

        assertEquals("aaaaaaa Ad ○ msg a", rows.get(2).text());
        assertTrue(rows.drop(2)
                .forAll(row -> row.segs().forAll(seg -> seg.cssClass().contains(Theme.Classes.DIM))));
        assertTrue(rows.head().segs().forAll(seg -> seg.cssClass().isEmpty()));
        assertFalse(new AheadView(Option.of(MORE), Option.none()).dimmed(OLD.sha()));
    }

    @Test
    void eachRowOfALogIsOneLineWithTheCopiedShaStandingOutAndTheCommitsOnTheBaseDimmed() {
        AheadView ahead = new AheadView(Option.of(AHEAD), Option.none());

        List<LoadableList.Item> items = CommitsPanel.graphed(LOG, Option.of(MIDDLE.sha()), ahead);

        assertTrue(items.forAll(item -> item.height() == 1 && item.row().height() == 1));
        assertEquals(
                List.of(
                        Option.none(),
                        Option.of(Theme.Classes.COPIED),
                        Option.of(Theme.Classes.DIM),
                        Option.of(Theme.Classes.DIM)),
                items.map(item -> item.row().segs().head().cssClass()));
    }

    @Test
    void theBranchLogDimsTheCommitsOnTheBase() {
        Style dimmed = Style.EMPTY.dim();
        Theme.SpanStyles styles = cssClass -> cssClass.equals(Theme.Classes.DIM) ? dimmed : Style.EMPTY;
        AheadView ahead = new AheadView(Option.of(AHEAD), Option.none());

        java.util.List<Line> lines =
                ContentText.logText(LOG, ZoneOffset.UTC, ahead, styles).lines();

        List<String> firstLines = List.ofAll(lines)
                .map(line -> line.spans().stream().map(Span::content).collect(Collectors.joining()))
                .filter(line -> line.startsWith("* "));
        assertEquals(
                List.of("* commit ccccccc", "* commit bbbbbbb", "* commit aaaaaaa", "* commit 0000000"), firstLines);
        List<Boolean> dim = List.ofAll(lines)
                .filter(line -> !line.spans().isEmpty())
                .map(line -> line.spans().stream().allMatch(span -> span.style().equals(dimmed)));
        // Only the blocks of the commits on the base, the last two, are dimmed.
        assertEquals(List.of(false, true), dim.distinct());
        assertTrue(dim.dropWhile(isDim -> !isDim).forAll(isDim -> isDim));
    }

    @Test
    void theCommitsPaneSaysHowManyCommitsAreNotOnTheBaseAndListsTheWholeLog() throws Exception {
        Model model = TestModels.loaded(SampleData.git())
                .mapRepo(repo -> repo.withCommits(new Loadable.Loaded<>(LOG))
                        .withAhead(new Loadable.Loaded<>(Option.of(AHEAD))));
        CommitsPanel panel = new CommitsPanel(msg -> {});

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(model, CommitsPanel.PANE))) {
            String shown = RenderedText.of(testRunner, () -> panel.render(model, CommitsPanel.PANE));

            // The title names the branch whose log it lists.
            assertTrue(shown.contains("Commits of main · 2 ahead of origin/ma"), shown);
            assertTrue(shown.contains("1/4"), shown);
            assertFalse(shown.contains("⑂"), shown);
            SettledPilot.of(testRunner).quit();
        }
    }
}
