package net.vanfleteren.jazygit.ui;

import static org.junit.jupiter.api.Assertions.*;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.Conflict;
import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.git.model.Head;
import net.vanfleteren.jazygit.git.model.InProgress;
import net.vanfleteren.jazygit.git.model.RepoStatus;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.model.SampleData;
import net.vanfleteren.jazygit.state.LoadMsg;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Screen;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import org.junit.jupiter.api.Test;

/**
 * The status panel shows the repository name and the current branch.
 */
class StatusPanelTest {

    @Test
    void aClickOnTheNameOfAScreenInTheTitleTellsWhichOne() {
        int repository = Messages.get("screen.repository").length();
        int pullRequests = Messages.get("screen.pullRequests").length();

        assertEquals(Option.of(Screen.REPOSITORY), StatusPanel.screenAt(0, 0));
        assertEquals(Option.of(Screen.REPOSITORY), StatusPanel.screenAt(repository - 1, 0));
        assertEquals(Option.none(), StatusPanel.screenAt(repository + 1, 0));
        assertEquals(Option.of(Screen.PULL_REQUESTS), StatusPanel.screenAt(repository + 3, 0));
        assertEquals(Option.of(Screen.PULL_REQUESTS), StatusPanel.screenAt(repository + 3 + pullRequests - 1, 0));
        assertEquals(Option.none(), StatusPanel.screenAt(repository + 3 + pullRequests, 0));
        assertEquals(Option.none(), StatusPanel.screenAt(-1, 0));
    }

    @Test
    void theNewPullRequestsAreCountedAfterTheNameOfTheirScreenAndAClickOnTheCountShowsIt() {
        String pullRequests = Messages.get("screen.pullRequests");
        int start = Messages.get("screen.repository").length() + 3;

        assertEquals(
                Messages.get("screen.repository") + " │ " + pullRequests + " ●2",
                StatusPanel.title(Screen.REPOSITORY, 2, Theme.SpanStyles.NONE).rawContent());
        assertEquals(
                Messages.get("screen.repository") + " │ " + pullRequests,
                StatusPanel.title(Screen.REPOSITORY, 0, Theme.SpanStyles.NONE).rawContent());
        assertEquals(Option.of(Screen.PULL_REQUESTS), StatusPanel.screenAt(start + pullRequests.length() + 2, 2));
        assertEquals(Option.none(), StatusPanel.screenAt(start + pullRequests.length() + 3, 2));
    }

    @Test
    void showsRepositoryNameAndCurrentBranch() throws Exception {
        Model model = TestModels.loaded(new SampleData());

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> StatusPanel.render(model))) {
            String screen = RenderedText.of(testRunner, () -> StatusPanel.render(model));
            assertTrue(screen.contains("Repo │ PRs"), screen);
            assertTrue(screen.contains("jazygit → main"), screen);

            String loading = RenderedText.of(testRunner, () -> StatusPanel.render(TestModels.loading("jazygit")));
            assertTrue(loading.contains("jazygit → " + Placeholders.loading()), loading);

            SettledPilot.of(testRunner).quit();
        }
    }

    @Test
    void aDetachedHeadIsDescribedByItsCommit() {
        assertEquals("main", StatusPanel.branchLabel(Head.onBranch(new BranchName("main"), new Sha("0123456789"))));
        assertEquals("HEAD detached at 0123456", StatusPanel.branchLabel(new Head.Detached(new Sha("0123456789"))));
    }

    @Test
    void doesNotShowTheLastOperationError() throws Exception {
        Model model = TestModels.withCheckoutError(TestModels.loaded(new SampleData()), "foo", "boom");

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> StatusPanel.render(model))) {
            String screen = RenderedText.of(testRunner, () -> StatusPanel.render(model));
            assertTrue(screen.contains("jazygit → main"), screen);
            assertFalse(screen.contains("Checkout of foo failed: boom"), screen);

            SettledPilot.of(testRunner).quit();
        }
    }

    @Test
    void showsWhatTheRepositoryIsInTheMiddleOfAndItsConflicts() throws Exception {
        FileEntry conflicted =
                FileEntry.conflicted("a.txt", new Conflict(Conflict.Side.UPDATED, Conflict.Side.UPDATED));
        FileEntry alsoConflicted =
                FileEntry.conflicted("b.txt", new Conflict(Conflict.Side.UPDATED, Conflict.Side.DELETED));
        Head main = Head.onBranch(new BranchName("main"), new Sha("aaaa"));

        assertEquals(
                "repo → REBASING feature 1/2 onto main · 2 conflicts",
                statusLine(new RepoStatus(
                        new Head.Detached(new Sha("bbbb")),
                        List.of(conflicted, alsoConflicted),
                        Option.of(new InProgress.Rebasing(
                                Option.of(new BranchName("feature")), new Sha("aaaa"), 1, 2)))));
        assertEquals(
                "repo → REBASING 1/2 onto 0123456",
                statusLine(new RepoStatus(
                        new Head.Detached(new Sha("bbbb")),
                        List.of(),
                        Option.of(new InProgress.Rebasing(Option.none(), new Sha("0123456789"), 1, 2)))));
        assertEquals(
                "repo → main · MERGING feature · 1 conflict",
                statusLine(
                        new RepoStatus(main, List.of(conflicted), Option.of(new InProgress.Merging(new Sha("ffff"))))));
        assertEquals(
                "repo → main · CHERRY-PICKING 0123456",
                statusLine(new RepoStatus(
                        main, List.of(), Option.of(new InProgress.CherryPicking(new Sha("0123456789"))))));
        assertEquals(
                "repo → main · REVERTING 0123456",
                statusLine(
                        new RepoStatus(main, List.of(), Option.of(new InProgress.Reverting(new Sha("0123456789"))))));
        assertEquals("repo → main · CONFLICTS (1)", statusLine(new RepoStatus(main, List.of(conflicted))));
    }

    /**
     * The line the status panel shows for {@code status}, with the sample branches main and feature.
     */
    private static String statusLine(RepoStatus status) throws Exception {
        Model model = Update.update(TestModels.loaded(), new LoadMsg.StatusLoaded(status))
                .model();
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> StatusPanel.render(model))) {
            String screen = RenderedText.ofLarge(testRunner, () -> StatusPanel.render(model));
            SettledPilot.of(testRunner).quit();
            return List.of(screen.split("\n"))
                    .map(line -> line.replaceAll("^[│ ]+|[│ ]+$", ""))
                    .find(line -> line.startsWith("repo →"))
                    .getOrElse(screen);
        }
    }
}
