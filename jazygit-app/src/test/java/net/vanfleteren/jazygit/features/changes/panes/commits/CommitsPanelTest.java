package net.vanfleteren.jazygit.features.changes.panes.commits;

import static org.junit.jupiter.api.Assertions.*;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.MouseButton;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import net.vanfleteren.jazygit.app.keys.Features;
import net.vanfleteren.jazygit.core.git.model.CommitQuery;
import net.vanfleteren.jazygit.core.git.model.Fixtures;
import net.vanfleteren.jazygit.core.git.model.RepoStatus;
import net.vanfleteren.jazygit.core.git.model.SampleData;
import net.vanfleteren.jazygit.core.repo.Ahead;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.core.repo.Commit;
import net.vanfleteren.jazygit.core.repo.CommitGraph;
import net.vanfleteren.jazygit.core.repo.CommitMessage;
import net.vanfleteren.jazygit.core.repo.Head;
import net.vanfleteren.jazygit.core.repo.InProgress;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.core.repo.RemoteRef;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.core.repo.Tag;
import net.vanfleteren.jazygit.core.repo.Upstream;
import net.vanfleteren.jazygit.features.changes.commit.Unpushed;
import net.vanfleteren.jazygit.features.changes.commit.search.CommitSearch;
import net.vanfleteren.jazygit.features.changes.commit.search.CommitSearchMsg;
import net.vanfleteren.jazygit.features.changes.commit.search.CommitSearchView;
import net.vanfleteren.jazygit.features.changes.commit.search.SearchCommits;
import net.vanfleteren.jazygit.kernel.state.Loadable;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.PaneId;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.state.Update;
import net.vanfleteren.jazygit.kernel.state.load.SelectionMsg;
import net.vanfleteren.jazygit.kernel.state.view.FilesLock;
import net.vanfleteren.jazygit.kernel.ui.RenderedText;
import net.vanfleteren.jazygit.kernel.ui.SettledPilot;
import net.vanfleteren.jazygit.kernel.ui.commits.AheadView;
import net.vanfleteren.jazygit.kernel.ui.commits.CommitRows;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;
import net.vanfleteren.jazygit.kernel.ui.theme.Theme;
import net.vanfleteren.jazygit.kernel.ui.widgets.LoadableList;
import org.junit.jupiter.api.Test;

class CommitsPanelTest {

    // A log without a base: no commit is dimmed or marked.
    private static final AheadView NO_BASE = new AheadView(Option.none(), Option.none());

    /**
     * The rows of {@code log} as the pane draws them.
     */
    private static List<LoadableList.Row> drawn(List<Commit> log) {
        return CommitRows.graphed(log, Option.none(), NO_BASE).map(LoadableList.Item::row);
    }

    private static Commit commit(String sha, String... parents) {
        return Fixtures.commit(
                new Sha(sha + "0000"),
                "Ada",
                "ada@example.com",
                Instant.EPOCH,
                "msg " + sha,
                "",
                List.of(parents).map(p -> new Sha(p + "0000")));
    }

    @Test
    void mergesAreFollowedByTheirBranchWithRails() {
        List<Commit> log = CommitGraph.arrange(List.of(
                commit("mmmmmmm", "bbbbbbb", "fffff02"),
                commit("fffff02", "fffff01"),
                commit("bbbbbbb", "aaaaaaa"),
                commit("fffff01", "aaaaaaa"),
                commit("aaaaaaa")));

        List<LoadableList.Row> rows = drawn(log);

        assertEquals(
                List.of(
                        "mmmmmmm Ad ◎─╮ msg mmmmmmm",
                        "fffff02 Ad │ ○ msg fffff02",
                        "fffff01 Ad │ ○ msg fffff01",
                        "bbbbbbb Ad ○ │ msg bbbbbbb",
                        "aaaaaaa Ad ○─╯ msg aaaaaaa"),
                rows.map(LoadableList.Row::text).toList());
    }

    @Test
    void eachBranchOfAMergeTrainJoinsTheStartOfTheBranchItWasMergedInto() {
        // Each train starts from the start of the one before, which goes on with work of its own; each train is
        // merged into the one before, the first one into main.
        List<Commit> log = CommitGraph.arrange(List.of(
                commit("mmmmmm1", "rrrrrrr", "mmmmmm2"),
                commit("mmmmmm2", "wwwwww1", "mmmmmm3"),
                commit("mmmmmm3", "wwwwww2", "ssssss3"),
                commit("ssssss3", "ssssss2"),
                commit("wwwwww2", "ssssss2"),
                commit("ssssss2", "ssssss1"),
                commit("wwwwww1", "ssssss1"),
                commit("ssssss1", "aaaaaaa"),
                commit("rrrrrrr", "aaaaaaa"),
                commit("aaaaaaa")));

        List<LoadableList.Row> rows = drawn(log);

        assertEquals(
                List.of(
                        "mmmmmm1 Ad ◎─╮ msg mmmmmm1",
                        "mmmmmm2 Ad │ ◎─╮ msg mmmmmm2",
                        "mmmmmm3 Ad │ │ ◎─╮ msg mmmmmm3",
                        "ssssss3 Ad │ │ │ ○ msg ssssss3",
                        "wwwwww2 Ad │ │ ○ │ msg wwwwww2",
                        "ssssss2 Ad │ │ ○─╯ msg ssssss2",
                        "wwwwww1 Ad │ ○ │ msg wwwwww1",
                        "ssssss1 Ad │ ○─╯ msg ssssss1",
                        "rrrrrrr Ad ○ │ msg rrrrrrr",
                        "aaaaaaa Ad ○─╯ msg aaaaaaa"),
                rows.map(LoadableList.Row::text).toList());
    }

    @Test
    void aStackOfMergedBranchesJoinsWhereEachForked() {
        // x is merged into y, y's branch into w and w's branch into main: all three branches end at x.
        List<Commit> log = CommitGraph.arrange(List.of(
                commit("mmmmmmm", "ccccccc", "vvvvvvv"),
                commit("vvvvvvv", "wwwwwww"),
                commit("wwwwwww", "bbbbbbb", "zzzzzzz"),
                commit("zzzzzzz", "yyyyyyy"),
                commit("yyyyyyy", "aaaaaaa", "xxxxxxx"),
                commit("xxxxxxx", "aaaaaaa"),
                commit("ccccccc", "bbbbbbb"),
                commit("bbbbbbb", "aaaaaaa"),
                commit("aaaaaaa")));

        List<LoadableList.Row> rows = drawn(log);

        assertEquals(
                List.of(
                        "mmmmmmm Ad ◎─╮ msg mmmmmmm",
                        "vvvvvvv Ad │ ○ msg vvvvvvv",
                        "wwwwwww Ad │ ◎─╮ msg wwwwwww",
                        "zzzzzzz Ad │ │ ○ msg zzzzzzz",
                        "yyyyyyy Ad │ │ ◎─╮ msg yyyyyyy",
                        "xxxxxxx Ad │ │ │ ○ msg xxxxxxx",
                        "ccccccc Ad ○ │ │ │ msg ccccccc",
                        "bbbbbbb Ad ○─╯ │ │ msg bbbbbbb",
                        "aaaaaaa Ad ○───┴─╯ msg aaaaaaa"),
                rows.map(LoadableList.Row::text).toList());
    }

    @Test
    void aCommitWhereTheInnerBranchesOfAStackEndJoinsThemOnTheOuterRail() {
        // x is merged into y, y's branch into w, both onto u; w's branch, which goes on to u, into main.
        List<Commit> log = CommitGraph.arrange(List.of(
                commit("mmmmmmm", "ccccccc", "vvvvvvv"),
                commit("vvvvvvv", "wwwwwww"),
                commit("wwwwwww", "uuuuuuu", "zzzzzzz"),
                commit("zzzzzzz", "yyyyyyy"),
                commit("yyyyyyy", "uuuuuuu", "xxxxxxx"),
                commit("xxxxxxx", "uuuuuuu"),
                commit("uuuuuuu", "aaaaaaa"),
                commit("ccccccc", "bbbbbbb"),
                commit("bbbbbbb", "aaaaaaa"),
                commit("aaaaaaa")));

        List<LoadableList.Row> rows = drawn(log);

        assertEquals(
                List.of(
                        "mmmmmmm Ad ◎─╮ msg mmmmmmm",
                        "vvvvvvv Ad │ ○ msg vvvvvvv",
                        "wwwwwww Ad │ ◎─╮ msg wwwwwww",
                        "zzzzzzz Ad │ │ ○ msg zzzzzzz",
                        "yyyyyyy Ad │ │ ◎─╮ msg yyyyyyy",
                        "xxxxxxx Ad │ │ │ ○ msg xxxxxxx",
                        "uuuuuuu Ad │ ○─┴─╯ msg uuuuuuu",
                        "ccccccc Ad ○ │ msg ccccccc",
                        "bbbbbbb Ad ○ │ msg bbbbbbb",
                        "aaaaaaa Ad ○─╯ msg aaaaaaa"),
                rows.map(LoadableList.Row::text).toList());
    }

    @Test
    void aMergeWhereAMergedBranchForkedJoinsItWhereItOpensItsOwn() {
        // f forked from n and is merged into m; g is merged into n.
        List<Commit> log = CommitGraph.arrange(List.of(
                commit("mmmmmmm", "nnnnnnn", "fffffff"),
                commit("fffffff", "nnnnnnn"),
                commit("nnnnnnn", "bbbbbbb", "ggggggg"),
                commit("ggggggg", "bbbbbbb"),
                commit("bbbbbbb")));

        List<LoadableList.Row> rows = drawn(log);

        assertEquals(
                List.of(
                        "mmmmmmm Ad ◎─╮ msg mmmmmmm",
                        "fffffff Ad │ ○ msg fffffff",
                        "nnnnnnn Ad ◎─┤ msg nnnnnnn",
                        "ggggggg Ad │ ○ msg ggggggg",
                        "bbbbbbb Ad ○─╯ msg bbbbbbb"),
                rows.map(LoadableList.Row::text).toList());
    }

    @Test
    void aMergeWhileARailGoesOnCrossesItToOpenItsOwn() {
        // f is merged into m, g into n; both forked from b.
        List<Commit> log = CommitGraph.arrange(List.of(
                commit("mmmmmmm", "nnnnnnn", "fffffff"),
                commit("fffffff", "bbbbbbb"),
                commit("nnnnnnn", "bbbbbbb", "ggggggg"),
                commit("ggggggg", "bbbbbbb"),
                commit("bbbbbbb")));

        List<LoadableList.Row> rows = drawn(log);

        assertEquals(
                List.of(
                        "mmmmmmm Ad ◎─╮ msg mmmmmmm",
                        "fffffff Ad │ ○ msg fffffff",
                        "nnnnnnn Ad ◎─┼─╮ msg nnnnnnn",
                        "ggggggg Ad │ │ ○ msg ggggggg",
                        "bbbbbbb Ad ○─┴─╯ msg bbbbbbb"),
                rows.map(LoadableList.Row::text).toList());
    }

    @Test
    void aMergeWhileAStackOfMergedBranchesGoesOnCrossesThemAll() {
        // x is merged into w, w's branch into m, g into n: all forked from b.
        List<Commit> log = CommitGraph.arrange(List.of(
                commit("mmmmmmm", "nnnnnnn", "wwwwwww"),
                commit("wwwwwww", "bbbbbbb", "xxxxxxx"),
                commit("xxxxxxx", "bbbbbbb"),
                commit("nnnnnnn", "bbbbbbb", "ggggggg"),
                commit("ggggggg", "bbbbbbb"),
                commit("bbbbbbb", "aaaaaaa"),
                commit("aaaaaaa")));

        List<LoadableList.Row> rows = drawn(log);

        assertEquals(
                List.of(
                        "mmmmmmm Ad ◎─╮ msg mmmmmmm",
                        "wwwwwww Ad │ ◎─╮ msg wwwwwww",
                        "xxxxxxx Ad │ │ ○ msg xxxxxxx",
                        "nnnnnnn Ad ◎─┼─┼─╮ msg nnnnnnn",
                        "ggggggg Ad │ │ │ ○ msg ggggggg",
                        "bbbbbbb Ad ○─┴─┴─╯ msg bbbbbbb",
                        "aaaaaaa Ad ○ msg aaaaaaa"),
                rows.map(LoadableList.Row::text).toList());
    }

    @Test
    void anOctopusMergeOpensARailForEachOtherParent() {
        List<Commit> log = CommitGraph.arrange(List.of(
                commit("ooooooo", "aaaaaaa", "bbbbbbb", "ccccccc"),
                commit("bbbbbbb", "aaaaaaa"),
                commit("ccccccc", "aaaaaaa"),
                commit("aaaaaaa")));

        assertEquals(
                List.of(
                        "ooooooo Ad ◎─┬─╮ msg ooooooo",
                        "bbbbbbb Ad │ ○ │ msg bbbbbbb",
                        "ccccccc Ad │ │ ○ msg ccccccc",
                        "aaaaaaa Ad ○─┴─╯ msg aaaaaaa"),
                drawn(log).map(LoadableList.Row::text).toList());
    }

    @Test
    void aMergeOfACommitTheListLeavesOutHasNoRail() {
        // The commits of a branch only, which merged main's mm00001 to catch up; main is left out.
        List<Commit> log = CommitGraph.arrange(List.of(
                commit("ffff003", "ffff002"), commit("ffff002", "ffff001", "mm00001"), commit("ffff001", "bbbbbbb")));

        assertEquals(
                List.of("ffff003 Ad ○ msg ffff003", "ffff002 Ad ◎ msg ffff002", "ffff001 Ad ○ msg ffff001"),
                drawn(log).map(LoadableList.Row::text).toList());
    }

    @Test
    void tagsComeBeforeTheMessageInTheirOwnColour() {
        Commit tagged = new Commit(
                new Sha("abcdef0"),
                "Ada",
                "ada@example.com",
                Instant.EPOCH,
                new CommitMessage("Release", ""),
                List.empty(),
                List.of(new Tag("v1"), new Tag("v1.1")));

        LoadableList.Row row =
                CommitRows.row(new CommitGraph.Node(tagged, 0, List.empty(), List.empty(), List.empty(), List.empty()));

        assertEquals("abcdef0 Ad ○ [v1] [v1.1] Release", row.text());
        assertEquals(
                List.of(Option.none(), Option.of(Theme.Classes.TAG), Option.of(Theme.Classes.TAG), Option.none()),
                row.segs().map(LoadableList.Seg::cssClass));
    }

    @Test
    void initialsAreTheCapitalizedFirstLettersOfTheName() {
        assertEquals("CV", CommitRows.initials("christophe vanfleteren"));
        assertEquals("AL", CommitRows.initials("  Ada   Lovelace "));
        assertEquals("re", CommitRows.initials("renovate"));
        assertEquals("cl", CommitRows.initials("claude[bot]"));
        assertEquals("JF", CommitRows.initials("John F Kennedy"));
        assertEquals("x", CommitRows.initials("x"));
        assertEquals("", CommitRows.initials(""));
    }

    @Test
    void initialsTakeNoMoreThanTwoColumnsAndKeepLettersWhole() {
        assertEquals("张", CommitRows.initials("张 伟"));
        assertEquals("张", CommitRows.initials("张伟"));
        assertEquals("🇧🇪", CommitRows.initials("🇧🇪 fan"));
        assertEquals("👨‍👩‍👧", CommitRows.initials("👨‍👩‍👧family"));
        // A letter with a combining accent keeps its accent.
        assertEquals("E\u0301L", CommitRows.initials("e\u0301mile lambert"));
    }

    @Test
    void itSaysHowManyCommitsAreNotOnTheBaseAndListsTheWholeLog() throws Exception {
        List<Commit> log = List.of("c", "b", "a", "0")
                .map(sha -> Fixtures.commit(
                        new Sha(sha.repeat(7)), "Ada", "ada@example.com", Instant.EPOCH, "msg " + sha, ""));
        BranchRef main = new BranchRef.Remote(new RemoteBranch(RemoteBranch.DEFAULT_REMOTE, new BranchName("main")));
        Ahead ahead = new Ahead(log.get(0).sha(), main, log.take(2), false, Option.of(log.get(2)));
        Model model = TestModels.loaded(SampleData.git())
                .mapRepo(repo -> repo.withCommits(new Loadable.Loaded<>(log))
                        .withAhead(new Loadable.Loaded<>(Option.of(ahead))));
        CommitsPanel panel = new CommitsPanel(msg -> {}, Features.FILLS);

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(model, CommitsPanel.PANE))) {
            String shown = RenderedText.of(testRunner, () -> panel.render(model, CommitsPanel.PANE));

            // With the reflog's tab, the title names the tabs and a line at the bottom the branch whose log it lists.
            assertTrue(shown.contains("Commits │ Reflog"), shown);
            assertTrue(shown.contains("main · 2 ahead of origin/main"), shown);
            assertTrue(shown.contains("1/4"), shown);
            assertFalse(shown.contains("⑂"), shown);
            SettledPilot.of(testRunner).quit();
        }
    }

    @Test
    void itsBottomBorderShowsWhereTheSelectionIsAndFollowsIt() throws Exception {
        List<Commit> log = List.of(commit("s2"), commit("s1"), commit("s0"));
        Model model = TestModels.loaded(SampleData.git()).mapRepo(repo -> repo.withCommits(new Loadable.Loaded<>(log)));
        CommitsPanel panel = new CommitsPanel(msg -> {}, Features.FILLS);

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(model, CommitsPanel.PANE))) {
            SettledPilot.focus(testRunner, CommitsPanel.ID);
            SettledPilot.of(testRunner).pause();
            String initial = RenderedText.of(testRunner, () -> panel.render(model, CommitsPanel.PANE));
            assertTrue(
                    initial.lines().reduce((first, second) -> second).orElse("").contains("1/3"), initial);

            SettledPilot.of(testRunner).press(KeyCode.DOWN);
            SettledPilot.of(testRunner).pause();
            String moved = RenderedText.of(testRunner, () -> panel.render(model, CommitsPanel.PANE));
            assertTrue(
                    moved.lines().reduce((first, second) -> second).orElse("").contains("2/3"), moved);

            SettledPilot.of(testRunner).quit();
        }
    }

    @Test
    void theSelectionFollowsItsCommitWhenANewOneComesOnTop() throws Exception {
        List<Commit> log = List.of(commit("s1"), commit("s0"));
        java.util.concurrent.atomic.AtomicReference<Model> model = new java.util.concurrent.atomic.AtomicReference<>(
                TestModels.loaded(SampleData.git()).mapRepo(repo -> repo.withCommits(new Loadable.Loaded<>(log))));
        CommitsPanel panel = new CommitsPanel(msg -> {}, Features.FILLS);

        try (ToolkitTestRunner testRunner =
                ToolkitTestRunner.runTest(() -> panel.render(model.get(), CommitsPanel.PANE))) {
            SettledPilot.focus(testRunner, CommitsPanel.ID);
            SettledPilot.of(testRunner).pause();
            SettledPilot.of(testRunner).press(KeyCode.DOWN);
            SettledPilot.of(testRunner).pause();
            model.set(model.get().mapRepo(repo -> repo.withCommits(new Loadable.Loaded<>(log.prepend(commit("s2"))))));
            RenderedText.ofLarge(testRunner, () -> panel.render(model.get(), CommitsPanel.PANE));

            assertEquals(commit("s0").sha(), panel.selectedCommit().get().sha());

            SettledPilot.of(testRunner).quit();
        }
    }

    @Test
    void theNewestCommitStaysSelectedWhenANewOneComesOnTop() throws Exception {
        List<Commit> log = List.of(commit("s1"), commit("s0"));
        java.util.concurrent.atomic.AtomicReference<Model> model = new java.util.concurrent.atomic.AtomicReference<>(
                TestModels.loaded(SampleData.git()).mapRepo(repo -> repo.withCommits(new Loadable.Loaded<>(log))));
        CommitsPanel panel = new CommitsPanel(msg -> {}, Features.FILLS);

        try (ToolkitTestRunner testRunner =
                ToolkitTestRunner.runTest(() -> panel.render(model.get(), CommitsPanel.PANE))) {
            SettledPilot.focus(testRunner, CommitsPanel.ID);
            SettledPilot.of(testRunner).pause();
            model.set(model.get().mapRepo(repo -> repo.withCommits(new Loadable.Loaded<>(log.prepend(commit("s2"))))));
            RenderedText.ofLarge(testRunner, () -> panel.render(model.get(), CommitsPanel.PANE));

            assertEquals(commit("s2").sha(), panel.selectedCommit().get().sha());

            SettledPilot.of(testRunner).quit();
        }
    }

    @Test
    void theListScrollsAlongWithTheSelection() throws Exception {
        List<Commit> log = List.range(0, 80).map(i -> commit("s" + i));
        Model model = TestModels.loaded(SampleData.git()).mapRepo(repo -> repo.withCommits(new Loadable.Loaded<>(log)));
        CommitsPanel panel = new CommitsPanel(msg -> {}, Features.FILLS);

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(model, CommitsPanel.PANE))) {
            SettledPilot.focus(testRunner, CommitsPanel.ID);
            SettledPilot.of(testRunner).pause();
            for (int i = 0; i < 45; i++) {
                SettledPilot.of(testRunner).press(KeyCode.DOWN);
            }
            SettledPilot.of(testRunner).pause();

            String screen = RenderedText.ofLarge(testRunner, () -> panel.render(model, CommitsPanel.PANE));
            assertEquals(45, panel.selectedIndex());
            assertTrue(screen.contains("msg s45"), screen);
            assertFalse(screen.contains("msg s0 "), screen);

            SettledPilot.of(testRunner).quit();
        }
    }

    @Test
    void clickingACommitHighlightsItAndFocusesThePane() throws Exception {
        List<Commit> log = List.of(commit("s1"), commit("s0"));
        Model model = TestModels.loaded(SampleData.git()).mapRepo(repo -> repo.withCommits(new Loadable.Loaded<>(log)));
        CommitsPanel panel = new CommitsPanel(msg -> {}, Features.FILLS);

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(model, CommitsPanel.PANE))) {
            // The border, s1, then s0.
            SettledPilot.of(testRunner).mousePress(MouseButton.LEFT, 5, 2);

            assertEquals(commit("s0").sha(), panel.selectedCommit().get().sha());
            assertEquals(CommitsPanel.ID, testRunner.runner().focusManager().focusedId());

            SettledPilot.of(testRunner).quit();
        }
    }

    @Test
    void theCommitsFoundAreListedWithoutRails() {
        List<LoadableList.Row> rows = CommitsPanel.foundItems(
                        List.of(commit("mmmmmmm", "bbbbbbb", "fffff02"), commit("aaaaaaa")),
                        Option.none(),
                        NO_BASE,
                        io.vavr.collection.HashSet.empty())
                .map(LoadableList.Item::row);

        assertEquals(
                List.of("mmmmmmm Ad ◎ msg mmmmmmm", "aaaaaaa Ad ○ msg aaaaaaa"),
                rows.map(LoadableList.Row::text).toList());
    }

    @Test
    void theSearchIsTypedAboveTheListRunWithEnterAndClearedWithEscape() throws Exception {
        AtomicReference<Model> model = new AtomicReference<>(TestModels.loaded(SampleData.git())
                .mapRepo(repo -> repo.withCommits(new Loadable.Loaded<>(List.of(commit("s1"), commit("s0"))))));
        java.util.List<Msg> sent = new java.util.ArrayList<>();
        CommitsPanel panel = new CommitsPanel(
                msg -> {
                    sent.add(msg);
                    model.set(Update.update(model.get(), msg).model());
                },
                Features.FILLS);

        try (ToolkitTestRunner testRunner =
                ToolkitTestRunner.runTest(() -> panel.render(model.get(), CommitsPanel.PANE))) {
            SettledPilot.focus(testRunner, CommitsPanel.ID);
            SettledPilot.of(testRunner).pause();
            SettledPilot.of(testRunner).press('/');
            SettledPilot.of(testRunner).pause();
            // The keys of the commits, like d to drop, are letters of the search meanwhile.
            SettledPilot.of(testRunner).press('d');
            SettledPilot.of(testRunner).pause();
            assertTrue(RenderedText.of(testRunner, () -> panel.render(model.get(), CommitsPanel.PANE))
                    .contains("/ d"));
            SettledPilot.of(testRunner).press(KeyCode.ENTER);
            SettledPilot.of(testRunner).pause();

            assertEquals(
                    List.of(
                            new CommitSearchMsg.Opened(),
                            new CommitSearchMsg.Edited("d"),
                            new CommitSearchMsg.Submitted()),
                    List.ofAll(sent));
            assertEquals(
                    Option.of("d"),
                    model.get().part(CommitSearchView.PART).search().map(CommitSearch::text));
            model.set(Update.update(model.get(), new SearchCommits.Found(CommitQuery.parse("d"), List.of(commit("s0"))))
                    .model());
            String shown = RenderedText.of(testRunner, () -> panel.render(model.get(), CommitsPanel.PANE));
            assertTrue(shown.contains("Commits: /d"), shown);
            assertTrue(shown.contains("msg s0"), shown);
            assertFalse(shown.contains("msg s1"), shown);

            SettledPilot.of(testRunner).press(KeyCode.ESCAPE);
            SettledPilot.of(testRunner).pause();

            assertEquals(Option.none(), model.get().part(CommitSearchView.PART).search());
            SettledPilot.of(testRunner).quit();
        }
    }

    @Test
    void aPasteGoesInTheOpenSearchAtOnce() throws Exception {
        AtomicReference<Model> model = new AtomicReference<>(TestModels.loaded(SampleData.git())
                .mapRepo(repo -> repo.withCommits(new Loadable.Loaded<>(List.of(commit("s1"), commit("s0"))))));
        java.util.List<Msg> sent = new java.util.ArrayList<>();
        CommitsPanel panel = new CommitsPanel(
                msg -> {
                    sent.add(msg);
                    model.set(Update.update(model.get(), msg).model());
                },
                Features.FILLS);

        try (ToolkitTestRunner testRunner =
                ToolkitTestRunner.runTest(() -> panel.render(model.get(), CommitsPanel.PANE))) {
            SettledPilot.focus(testRunner, CommitsPanel.ID);
            // Without the search line, a paste does nothing: its letters are no keys of the commits.
            SettledPilot.paste(testRunner, "dd");
            SettledPilot.of(testRunner).press('/');
            SettledPilot.of(testRunner).pause();
            SettledPilot.paste(testRunner, "fix bug");

            assertEquals(
                    List.of(new CommitSearchMsg.Opened(), new CommitSearchMsg.Edited("fix bug")), List.ofAll(sent));
            SettledPilot.of(testRunner).quit();
        }
    }

    @Test
    void aSearchThatFindsNothingSaysSo() throws Exception {
        Model model = TestModels.loaded(SampleData.git())
                .mapPart(
                        CommitSearchView.PART,
                        view -> view.withSearch(Option.of(
                                CommitSearch.loading("nothing").withResults(new Loadable.Loaded<>(List.empty())))));
        CommitsPanel panel = new CommitsPanel(msg -> {}, Features.FILLS);

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(model, CommitsPanel.PANE))) {
            assertTrue(RenderedText.of(testRunner, () -> panel.render(model, CommitsPanel.PANE))
                    .contains("No commits match"));

            SettledPilot.of(testRunner).quit();
        }
    }

    // The pane keeps the rows it drew while what they are drawn from stays the same; each of these changes only one
    // thing they are drawn from, on the same pane, and the rows change with it.

    @Test
    void aCommitCopiedAfterThePaneWasDrawnStandsOut() {
        Model model = TestModels.loaded(SampleData.git())
                .mapRepo(repo -> repo.withCommits(new Loadable.Loaded<>(List.of(commit("s1"), commit("s0")))));
        CommitsPanel panel = new CommitsPanel(msg -> {}, Features.FILLS);

        panel.render(model, CommitsPanel.PANE);
        List<Option<String>> before = shaClasses(panel);
        panel.render(
                model.mapRepoView(
                        repoView -> repoView.mapCommits(commits -> commits.withCherryPick(Option.of(commit("s0"))))),
                CommitsPanel.PANE);

        assertEquals(List.of(Option.none(), Option.none()), before);
        assertEquals(List.of(Option.none(), Option.of(Theme.Classes.COPIED)), shaClasses(panel));
    }

    // #722: the commits not pushed to the upstream yet have their sha in bold, once they were read for where HEAD and
    // the upstream are; read for elsewhere, none is marked.
    @Test
    void theCommitsNotPushedYetStandOut() {
        RemoteBranch upstream = new RemoteBranch(RemoteBranch.DEFAULT_REMOTE, new BranchName("feature"));
        Model model = TestModels.localBranchesRead(
                        TestModels.loaded(SampleData.git())
                                .mapRepo(repo -> repo.withCommits(
                                                new Loadable.Loaded<>(List.of(commit("s1", "s0"), commit("s0"))))
                                        .withRemoteBranches(new Loadable.Loaded<>(List.of(new RemoteRef(
                                                upstream, commit("s0").sha(), Instant.EPOCH))))),
                        List.of(Fixtures.branch(
                                new BranchName("feature"),
                                true,
                                commit("s1").sha(),
                                Instant.EPOCH,
                                true,
                                false,
                                Option.of(new Upstream(new BranchRef.Remote(upstream), 1, 0)))))
                .model();
        Unpushed.Tips tips =
                new Unpushed.Tips(commit("s1").sha(), List.of(commit("s0").sha()));
        Unpushed read = new Unpushed(
                Option.of(tips),
                Option.of(tips),
                io.vavr.collection.HashSet.of(commit("s1").sha()));
        CommitsPanel panel = new CommitsPanel(msg -> {}, Features.FILLS);

        panel.render(model.withPart(Unpushed.PART, read), CommitsPanel.PANE);
        List<Option<String>> marked = shaClasses(panel);
        Unpushed.Tips elsewhere =
                new Unpushed.Tips(commit("s0").sha(), List.of(commit("s0").sha()));
        panel.render(
                model.withPart(
                        Unpushed.PART, read.withAsked(Option.of(elsewhere)).withRead(Option.of(elsewhere))),
                CommitsPanel.PANE);

        assertEquals(List.of(Option.of(Theme.Classes.UNPUSHED), Option.none()), marked);
        assertEquals(List.of(Option.none(), Option.none()), shaClasses(panel));
    }

    /**
     * The class of the first piece of each row the pane shows, the one with the sha.
     */
    private static List<Option<String>> shaClasses(CommitsPanel panel) {
        return panel.shownRows().map(row -> row.segs().head().cssClass());
    }

    @Test
    void aSearchThatFindsTheWholeLogListsItWithoutRailsAndClearedWithThem() {
        Loadable<List<Commit>> log = new Loadable.Loaded<>(
                List.of(commit("mmmmmmm", "bbbbbbb", "aaaaaaa"), commit("bbbbbbb", "aaaaaaa"), commit("aaaaaaa")));
        Model listed = TestModels.loaded(SampleData.git()).mapRepo(repo -> repo.withCommits(log));
        Model found = listed.mapPart(
                CommitSearchView.PART,
                view -> view.withSearch(Option.of(CommitSearch.loading("a").withResults(log))));
        CommitsPanel panel = new CommitsPanel(msg -> {}, Features.FILLS);

        panel.render(listed, CommitsPanel.PANE);
        String first = panel.shownRows().head().text();
        panel.render(found, CommitsPanel.PANE);
        String searched = panel.shownRows().head().text();
        panel.render(listed, CommitsPanel.PANE);

        assertEquals("mmmmmmm Ad ◎─╮ msg mmmmmmm", first);
        assertEquals("mmmmmmm Ad ◎ msg mmmmmmm", searched);
        assertEquals(first, panel.shownRows().head().text());
    }

    @Test
    void escapeLetsGoOfTheFilesLockedToTheCommits() throws Exception {
        // ADR 149: Escape in the list the files are locked to lets go of them.
        assertEquals(List.of(new SelectionMsg.FilesUnlocked()), escaped(lockedTo(withCommits(), CommitsPanel.PANE)));
    }

    @Test
    void escapeClearsTheSearchListedBeforeItLetsGoOfTheLock() throws Exception {
        Model searched = List.<Msg>of(
                        new CommitSearchMsg.Opened(),
                        new CommitSearchMsg.Edited("s0"),
                        new CommitSearchMsg.Submitted(),
                        new SearchCommits.Found(CommitQuery.parse("s0"), List.of(commit("s0"))))
                .foldLeft(
                        lockedTo(withCommits(), CommitsPanel.PANE),
                        (model, msg) -> Update.update(model, msg).model());

        assertEquals(List.of(new CommitSearchMsg.Cleared()), escaped(searched));
    }

    @Test
    void escapeLeavesTheFilesLockedToAnotherList() throws Exception {
        assertEquals(List.empty(), escaped(lockedTo(withCommits(), Scopes.STASH.pane())));
    }

    @Test
    void theTitleOfADetachedHeadSaysWhereItIs() {
        Model detached = withCommits()
                .mapRepo(repo -> repo.withStatus(
                        new Loadable.Loaded<>(new RepoStatus(new Head.Detached(new Sha("abcdef0123")), List.empty()))));

        assertEquals("Commits · detached at abcdef0", CommitsPanel.headTitle(detached));
        assertEquals("Commits of main", CommitsPanel.headTitle(withCommits()));
    }

    @Test
    void whileARebaseDetachesHeadTheLogIsNamedAfterTheBranchItRebases() {
        Sha onto = new Sha("abcdef0123");
        Model rebasing = withCommits()
                .mapRepo(repo -> repo.withStatus(new Loadable.Loaded<>(new RepoStatus(
                        new Head.Detached(onto),
                        List.empty(),
                        Option.of(new InProgress.Rebasing(Option.of(new BranchName("feature")), onto, 1, 2))))));
        Model detachedRebase = withCommits()
                .mapRepo(repo -> repo.withStatus(new Loadable.Loaded<>(new RepoStatus(
                        new Head.Detached(onto),
                        List.empty(),
                        Option.of(new InProgress.Rebasing(Option.none(), onto, 1, 2))))));

        assertEquals("Commits · rebasing feature", CommitsPanel.headTitle(rebasing));
        assertEquals("rebasing feature", CommitsPanel.headName(rebasing));
        assertEquals("Commits · detached at abcdef0", CommitsPanel.headTitle(detachedRebase));
    }

    @Test
    void theRowsAFeatureListsAboveTheLogComeFirstAndTheCommitsMadeSinceItsBaseAreDone() throws Exception {
        List<Commit> log = List.of(commit("s2", "s1"), commit("s1", "s0"), commit("s0"));
        Model model = TestModels.loaded(SampleData.git()).mapRepo(repo -> repo.withCommits(new Loadable.Loaded<>(log)));
        CommitsAbove above = new CommitsAbove(
                List.of(
                        new CommitsAbove.Row(LoadableList.Row.of("next step"), Option.of(new Sha("n0000")), "next"),
                        new CommitsAbove.Row(LoadableList.Row.of("stopped step"), Option.none(), "stopped")),
                Option.of(commit("s0").sha()),
                Option.of("2/3"));
        java.util.List<Msg> sent = new java.util.ArrayList<>();
        CommitsPanel panel = new CommitsPanel(
                sent::add,
                net.vanfleteren.jazygit.kernel.ui.Fills.NONE.with(CommitsPane.ABOVE, given -> Option.of(above)));

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(model, CommitsPanel.PANE))) {
            String shown = RenderedText.ofLarge(testRunner, () -> panel.render(model, CommitsPanel.PANE));

            assertEquals(
                    List.of(
                            "next step",
                            "stopped step",
                            "s20000 Ad ✓ msg s2",
                            "s10000 Ad ✓ msg s1",
                            "s00000 Ad ○ msg s0"),
                    panel.shownRows().map(LoadableList.Row::text));
            assertTrue(shown.contains("Commits of main · 2/3"), shown);
            // The top row is above the log: no commit of it, though the content panel shows its commit.
            assertEquals(Option.none(), panel.selectedCommit());
            assertEquals(Option.of(new SelectionMsg.CommitSelected(new Sha("n0000"))), panel.selectionChange(model));

            SettledPilot.focus(testRunner, CommitsPanel.ID);
            SettledPilot.of(testRunner).pause();
            SettledPilot.of(testRunner).press(KeyCode.DOWN);
            SettledPilot.of(testRunner).pause();
            RenderedText.ofLarge(testRunner, () -> panel.render(model, CommitsPanel.PANE));
            // A row without a commit leaves what the content panel shows.
            assertEquals(Option.none(), panel.selectionChange(model));

            SettledPilot.of(testRunner).press(KeyCode.DOWN);
            SettledPilot.of(testRunner).pause();
            RenderedText.ofLarge(testRunner, () -> panel.render(model, CommitsPanel.PANE));
            assertEquals(
                    Option.of(commit("s2", "s1").sha()), panel.selectedCommit().map(Commit::sha));
            SettledPilot.of(testRunner).quit();
        }
    }

    @Test
    void aBaseThatIsNotInTheLogMarksNoneDone() {
        List<Commit> log = List.of(commit("s1", "s0"), commit("s0"));

        assertEquals(
                List.of("s10000 Ad ○ msg s1", "s00000 Ad ○ msg s0"),
                CommitsPanel.done(CommitRows.graphed(log, Option.none(), NO_BASE), log, Option.of(new Sha("x0000")))
                        .map(item -> item.row().text()));
    }

    private static Model withCommits() {
        return TestModels.loaded(SampleData.git())
                .mapRepo(repo -> repo.withCommits(new Loadable.Loaded<>(List.of(commit("s1"), commit("s0")))));
    }

    private static Model lockedTo(Model model, PaneId pane) {
        return model.mapRepoView(repoView -> repoView.mapFiles(files -> files.lockedTo(pane, FilesLock.In.LIST)));
    }

    /**
     * What Escape in the commits of {@code model}, with the focus, sends.
     */
    private static List<Msg> escaped(Model model) throws Exception {
        java.util.List<Msg> sent = new java.util.ArrayList<>();
        CommitsPanel panel = new CommitsPanel(sent::add, Features.FILLS);
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(model, CommitsPanel.PANE))) {
            SettledPilot.focus(testRunner, CommitsPanel.ID);
            SettledPilot.of(testRunner).pause();
            SettledPilot.of(testRunner).press(KeyCode.ESCAPE);
            SettledPilot.of(testRunner).pause();
            SettledPilot.of(testRunner).quit();
        }
        return List.ofAll(sent).filter(msg -> !(msg instanceof SelectionMsg.CommitSelected));
    }
}
