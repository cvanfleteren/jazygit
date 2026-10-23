package net.vanfleteren.jazygit.ui;

import static org.junit.jupiter.api.Assertions.*;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.MouseButton;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import net.vanfleteren.jazygit.git.GitInfoProvider;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.git.model.CommitGraph;
import net.vanfleteren.jazygit.git.model.CommitQuery;
import net.vanfleteren.jazygit.git.model.Fixtures;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.model.Tag;
import net.vanfleteren.jazygit.model.SampleData;
import net.vanfleteren.jazygit.state.CommitSearch;
import net.vanfleteren.jazygit.state.CommitSearchMsg;
import net.vanfleteren.jazygit.state.LoadMsg;
import net.vanfleteren.jazygit.state.Loadable;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
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
                        "bbbbbbb Ad ○─╯ msg bbbbbbb",
                        "aaaaaaa Ad ○ msg aaaaaaa"),
                rows.map(LoadableList.Row::text).toList());
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

        LoadableList.Row row = CommitsPanel.row(new CommitGraph.Node(tagged, 0, false), false);

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
                TestModels.loaded(new SampleData()).mapRepo(repo -> repo.withCommits(new Loadable.Loaded<>(log))));
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
                TestModels.loaded(new SampleData()).mapRepo(repo -> repo.withCommits(new Loadable.Loaded<>(log))));
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
        Model model = TestModels.loaded(new SampleData()).mapRepo(repo -> repo.withCommits(new Loadable.Loaded<>(log)));
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
        Model model = TestModels.loaded(new SampleData()).mapRepo(repo -> repo.withCommits(new Loadable.Loaded<>(log)));
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
                CommitsPanel.title(search.withResults(new Loadable.Loaded<>(
                        List.range(0, GitInfoProvider.MAX_COMMITS).map(i -> commit("s" + i))))));
    }

    @Test
    void theSearchIsTypedAboveTheListRunWithEnterAndClearedWithEscape() throws Exception {
        AtomicReference<Model> model = new AtomicReference<>(TestModels.loaded(new SampleData())
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
    void aSearchThatFindsNothingSaysSo() throws Exception {
        Model model = TestModels.loaded(new SampleData())
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
}
