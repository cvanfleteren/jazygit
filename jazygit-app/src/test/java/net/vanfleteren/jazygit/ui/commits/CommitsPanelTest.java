package net.vanfleteren.jazygit.ui.commits;

import static org.junit.jupiter.api.Assertions.*;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.MouseButton;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import net.vanfleteren.jazygit.git.history.History;
import net.vanfleteren.jazygit.git.model.Ahead;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.git.model.CommitGraph;
import net.vanfleteren.jazygit.git.model.CommitQuery;
import net.vanfleteren.jazygit.git.model.Fixtures;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.model.Tag;
import net.vanfleteren.jazygit.model.SampleData;
import net.vanfleteren.jazygit.state.Loadable;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.load.CommitSearchMsg;
import net.vanfleteren.jazygit.state.load.LoadMsg;
import net.vanfleteren.jazygit.state.view.CommitSearch;
import net.vanfleteren.jazygit.ui.RenderedText;
import net.vanfleteren.jazygit.ui.SettledPilot;
import net.vanfleteren.jazygit.ui.theme.Theme;
import net.vanfleteren.jazygit.ui.widgets.LoadableList;
import org.junit.jupiter.api.Test;

class CommitsPanelTest {

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

        List<LoadableList.Row> rows = CommitsPanel.rows(CommitGraph.layout(log));

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

        List<LoadableList.Row> rows = CommitsPanel.rows(CommitGraph.layout(log));

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

        List<LoadableList.Row> rows = CommitsPanel.rows(CommitGraph.layout(log));

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

        List<LoadableList.Row> rows = CommitsPanel.rows(CommitGraph.layout(log));

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

        List<LoadableList.Row> rows = CommitsPanel.rows(CommitGraph.layout(log));

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

        List<LoadableList.Row> rows = CommitsPanel.rows(CommitGraph.layout(log));

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

        List<LoadableList.Row> rows = CommitsPanel.rows(CommitGraph.layout(log));

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
                CommitsPanel.rows(CommitGraph.layout(log))
                        .map(LoadableList.Row::text)
                        .toList());
    }

    @Test
    void aMergeOfACommitTheListLeavesOutHasNoRail() {
        // The commits of a branch only, which merged main's mm00001 to catch up; main is left out.
        List<Commit> log = CommitGraph.arrange(List.of(
                commit("ffff003", "ffff002"), commit("ffff002", "ffff001", "mm00001"), commit("ffff001", "bbbbbbb")));

        assertEquals(
                List.of("ffff003 Ad ○ msg ffff003", "ffff002 Ad ◎ msg ffff002", "ffff001 Ad ○ msg ffff001"),
                CommitsPanel.rows(CommitGraph.layout(log))
                        .map(LoadableList.Row::text)
                        .toList());
    }

    @Test
    void tagsComeBeforeTheMessageInTheirOwnColour() {
        Commit tagged = new Commit(
                new Sha("abcdef0"),
                "Ada",
                "ada@example.com",
                Instant.EPOCH,
                "Release",
                "",
                List.empty(),
                List.of(new Tag("v1"), new Tag("v1.1")));

        LoadableList.Row row = CommitsPanel.row(
                new CommitGraph.Node(tagged, 0, List.empty(), List.empty(), List.empty(), List.empty()));

        assertEquals("abcdef0 Ad ○ [v1] [v1.1] Release", row.text());
        assertEquals(
                List.of(Option.none(), Option.of(Theme.Classes.TAG), Option.of(Theme.Classes.TAG), Option.none()),
                row.segs().map(LoadableList.Seg::cssClass));
    }

    @Test
    void initialsAreTheCapitalizedFirstLettersOfTheName() {
        assertEquals("CV", CommitsPanel.initials("christophe vanfleteren"));
        assertEquals("AL", CommitsPanel.initials("  Ada   Lovelace "));
        assertEquals("re", CommitsPanel.initials("renovate"));
        assertEquals("cl", CommitsPanel.initials("claude[bot]"));
        assertEquals("JF", CommitsPanel.initials("John F Kennedy"));
        assertEquals("x", CommitsPanel.initials("x"));
        assertEquals("", CommitsPanel.initials(""));
    }

    @Test
    void initialsTakeNoMoreThanTwoColumnsAndKeepLettersWhole() {
        assertEquals("张", CommitsPanel.initials("张 伟"));
        assertEquals("张", CommitsPanel.initials("张伟"));
        assertEquals("🇧🇪", CommitsPanel.initials("🇧🇪 fan"));
        assertEquals("👨‍👩‍👧", CommitsPanel.initials("👨‍👩‍👧family"));
        // A letter with a combining accent keeps its accent.
        assertEquals("E\u0301L", CommitsPanel.initials("e\u0301mile lambert"));
    }

    @Test
    void theSelectionFollowsItsCommitWhenANewOneComesOnTop() throws Exception {
        List<Commit> log = List.of(commit("s1"), commit("s0"));
        java.util.concurrent.atomic.AtomicReference<Model> model = new java.util.concurrent.atomic.AtomicReference<>(
                TestModels.loaded(SampleData.git()).mapRepo(repo -> repo.withCommits(new Loadable.Loaded<>(log))));
        CommitsPanel panel = new CommitsPanel(msg -> {}, () -> {});

        try (ToolkitTestRunner testRunner =
                ToolkitTestRunner.runTest(() -> panel.render(model.get(), CommitsPanel.ID))) {
            SettledPilot.focus(testRunner, CommitsPanel.ID);
            SettledPilot.of(testRunner).pause();
            SettledPilot.of(testRunner).press(KeyCode.DOWN);
            SettledPilot.of(testRunner).pause();
            model.set(model.get().mapRepo(repo -> repo.withCommits(new Loadable.Loaded<>(log.prepend(commit("s2"))))));
            RenderedText.ofLarge(testRunner, () -> panel.render(model.get(), CommitsPanel.ID));

            assertEquals(commit("s0").sha(), panel.selectedCommit().get().sha());

            SettledPilot.of(testRunner).quit();
        }
    }

    @Test
    void theNewestCommitStaysSelectedWhenANewOneComesOnTop() throws Exception {
        List<Commit> log = List.of(commit("s1"), commit("s0"));
        java.util.concurrent.atomic.AtomicReference<Model> model = new java.util.concurrent.atomic.AtomicReference<>(
                TestModels.loaded(SampleData.git()).mapRepo(repo -> repo.withCommits(new Loadable.Loaded<>(log))));
        CommitsPanel panel = new CommitsPanel(msg -> {}, () -> {});

        try (ToolkitTestRunner testRunner =
                ToolkitTestRunner.runTest(() -> panel.render(model.get(), CommitsPanel.ID))) {
            SettledPilot.focus(testRunner, CommitsPanel.ID);
            SettledPilot.of(testRunner).pause();
            model.set(model.get().mapRepo(repo -> repo.withCommits(new Loadable.Loaded<>(log.prepend(commit("s2"))))));
            RenderedText.ofLarge(testRunner, () -> panel.render(model.get(), CommitsPanel.ID));

            assertEquals(commit("s2").sha(), panel.selectedCommit().get().sha());

            SettledPilot.of(testRunner).quit();
        }
    }

    @Test
    void theListScrollsAlongWithTheSelection() throws Exception {
        List<Commit> log = List.range(0, 80).map(i -> commit("s" + i));
        Model model = TestModels.loaded(SampleData.git()).mapRepo(repo -> repo.withCommits(new Loadable.Loaded<>(log)));
        CommitsPanel panel = new CommitsPanel(msg -> {}, () -> {});

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(model, CommitsPanel.ID))) {
            SettledPilot.focus(testRunner, CommitsPanel.ID);
            SettledPilot.of(testRunner).pause();
            for (int i = 0; i < 45; i++) {
                SettledPilot.of(testRunner).press(KeyCode.DOWN);
            }
            SettledPilot.of(testRunner).pause();

            String screen = RenderedText.ofLarge(testRunner, () -> panel.render(model, CommitsPanel.ID));
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
        CommitsPanel panel = new CommitsPanel(msg -> {}, () -> {});

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(model, CommitsPanel.ID))) {
            // The border, s1, then s0.
            SettledPilot.of(testRunner).mousePress(MouseButton.LEFT, 5, 2);

            assertEquals(commit("s0").sha(), panel.selectedCommit().get().sha());
            assertEquals(CommitsPanel.ID, testRunner.runner().focusManager().focusedId());

            SettledPilot.of(testRunner).quit();
        }
    }

    @Test
    void theCommitsFoundAreListedWithoutRails() {
        List<LoadableList.Row> rows = CommitsPanel.foundRows(
                List.of(commit("mmmmmmm", "bbbbbbb", "fffff02"), commit("aaaaaaa")), Option.none());

        assertEquals(
                List.of("mmmmmmm Ad ◎ msg mmmmmmm", "aaaaaaa Ad ○ msg aaaaaaa"),
                rows.map(LoadableList.Row::text).toList());
    }

    @Test
    void theTitleSaysWhatWasSearchedAndWhenThereWereMoreThanItLists() {
        CommitSearch search = CommitSearch.loading("fix", Option.none());

        assertEquals("Commits: /fix", CommitsPanel.title(search));
        assertEquals(
                "Commits: /fix (first 300)",
                CommitsPanel.title(search.withResults(
                        new Loadable.Loaded<>(List.range(0, History.MAX_COMMITS).map(i -> commit("s" + i))))));
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
                () -> {});

        try (ToolkitTestRunner testRunner =
                ToolkitTestRunner.runTest(() -> panel.render(model.get(), CommitsPanel.ID))) {
            SettledPilot.focus(testRunner, CommitsPanel.ID);
            SettledPilot.of(testRunner).pause();
            SettledPilot.of(testRunner).press('/');
            SettledPilot.of(testRunner).pause();
            // The keys of the commits, like d to drop, are letters of the search meanwhile.
            SettledPilot.of(testRunner).press('d');
            SettledPilot.of(testRunner).pause();
            assertTrue(RenderedText.of(testRunner, () -> panel.render(model.get(), CommitsPanel.ID))
                    .contains("/ d"));
            SettledPilot.of(testRunner).press(KeyCode.ENTER);
            SettledPilot.of(testRunner).pause();

            assertEquals(
                    List.of(
                            new CommitSearchMsg.Opened(),
                            new CommitSearchMsg.Edited("d"),
                            new CommitSearchMsg.Submitted()),
                    List.ofAll(sent));
            assertEquals(Option.of("d"), model.get().repoView().commitSearch().map(CommitSearch::text));
            model.set(Update.update(
                            model.get(),
                            new LoadMsg.CommitSearchLoaded(
                                    CommitQuery.parse("d"), Option.none(), List.of(commit("s0"))))
                    .model());
            String shown = RenderedText.of(testRunner, () -> panel.render(model.get(), CommitsPanel.ID));
            assertTrue(shown.contains("Commits: /d"), shown);
            assertTrue(shown.contains("msg s0"), shown);
            assertFalse(shown.contains("msg s1"), shown);

            SettledPilot.of(testRunner).press(KeyCode.ESCAPE);
            SettledPilot.of(testRunner).pause();

            assertEquals(Option.none(), model.get().repoView().commitSearch());
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
                () -> {});

        try (ToolkitTestRunner testRunner =
                ToolkitTestRunner.runTest(() -> panel.render(model.get(), CommitsPanel.ID))) {
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
                .mapRepoView(
                        repoView -> repoView.withCommitSearch(Option.of(CommitSearch.loading("nothing", Option.none())
                                .withResults(new Loadable.Loaded<>(List.empty())))));
        CommitsPanel panel = new CommitsPanel(msg -> {}, () -> {});

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(model, CommitsPanel.ID))) {
            assertTrue(RenderedText.of(testRunner, () -> panel.render(model, CommitsPanel.ID))
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
        CommitsPanel panel = new CommitsPanel(msg -> {}, () -> {});

        panel.render(model, CommitsPanel.ID);
        List<Option<String>> before = shaClasses(panel);
        panel.render(model.mapRepoView(repoView -> repoView.withCherryPick(Option.of(commit("s0")))), CommitsPanel.ID);

        assertEquals(List.of(Option.none(), Option.none()), before);
        assertEquals(List.of(Option.none(), Option.of(Theme.Classes.COPIED)), shaClasses(panel));
    }

    /**
     * The class of the first piece of each row the pane shows, the one with the sha.
     */
    private static List<Option<String>> shaClasses(CommitsPanel panel) {
        return panel.shownRows().map(row -> row.segs().head().cssClass());
    }

    @Test
    void onlyTheCommitsAheadAreListedAndAllAgainAsThatIsToggled() {
        Commit tip = commit("c2", "c1");
        Commit middle = commit("c1", "f0");
        Commit fork = commit("f0", "o0");
        BranchRef main = new BranchRef.Remote(new RemoteBranch(RemoteBranch.DEFAULT_REMOTE, new BranchName("main")));
        Model all = TestModels.loaded(SampleData.git())
                .mapRepo(repo -> repo.withCommits(new Loadable.Loaded<>(List.of(tip, middle, fork, commit("o0"))))
                        .withAhead(new Loadable.Loaded<>(
                                Option.of(new Ahead(tip.sha(), main, List.of(tip, middle), false, Option.of(fork))))));
        Model only = all.mapRepoView(repoView -> repoView.withOnlyAhead(true));
        CommitsPanel panel = new CommitsPanel(msg -> {}, () -> {});

        panel.render(all, CommitsPanel.ID);
        List<String> first = panel.shownRows().map(LoadableList.Row::text);
        panel.render(only, CommitsPanel.ID);
        List<String> ahead = panel.shownRows().map(LoadableList.Row::text);
        panel.render(all, CommitsPanel.ID);

        assertEquals(4, first.size());
        assertEquals(List.of("msg c2", "msg c1", "⑂ origin/main msg f0"), ahead.map(CommitsPanelTest::afterGraph));
        assertEquals(first, panel.shownRows().map(LoadableList.Row::text));
    }

    /**
     * The text of a row after the sha, the initials and the graph.
     */
    private static String afterGraph(String row) {
        return row.substring(row.indexOf("○ ") + 2);
    }

    @Test
    void aSearchThatFindsTheWholeLogListsItWithoutRailsAndClearedWithThem() {
        Loadable<List<Commit>> log = new Loadable.Loaded<>(
                List.of(commit("mmmmmmm", "bbbbbbb", "aaaaaaa"), commit("bbbbbbb", "aaaaaaa"), commit("aaaaaaa")));
        Model listed = TestModels.loaded(SampleData.git()).mapRepo(repo -> repo.withCommits(log));
        Model found = listed.mapRepoView(repoView -> repoView.withCommitSearch(
                Option.of(CommitSearch.loading("a", Option.none()).withResults(log))));
        CommitsPanel panel = new CommitsPanel(msg -> {}, () -> {});

        panel.render(listed, CommitsPanel.ID);
        String first = panel.shownRows().head().text();
        panel.render(found, CommitsPanel.ID);
        String searched = panel.shownRows().head().text();
        panel.render(listed, CommitsPanel.ID);

        assertEquals("mmmmmmm Ad ◎─╮ msg mmmmmmm", first);
        assertEquals("mmmmmmm Ad ◎ msg mmmmmmm", searched);
        assertEquals(first, panel.shownRows().head().text());
    }
}
