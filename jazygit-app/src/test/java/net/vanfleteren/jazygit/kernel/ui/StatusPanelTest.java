package net.vanfleteren.jazygit.kernel.ui;

import static org.junit.jupiter.api.Assertions.*;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.model.Conflict;
import net.vanfleteren.jazygit.core.git.model.FileEntry;
import net.vanfleteren.jazygit.core.git.model.RepoStatus;
import net.vanfleteren.jazygit.core.git.model.SampleData;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.Head;
import net.vanfleteren.jazygit.core.repo.InProgress;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.features.branches.branch.checkout.CheckoutModels;
import net.vanfleteren.jazygit.features.github.prs.PrOverview;
import net.vanfleteren.jazygit.kernel.settings.UnseenPullRequests;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.state.Update;
import net.vanfleteren.jazygit.kernel.state.load.LoadMsg;
import net.vanfleteren.jazygit.kernel.state.preferences.Screen;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;
import net.vanfleteren.jazygit.kernel.ui.screen.Screens;
import net.vanfleteren.jazygit.kernel.ui.text.Placeholders;
import net.vanfleteren.jazygit.kernel.ui.theme.Theme;
import org.junit.jupiter.api.Test;

/**
 * The status panel shows the repository name and the current branch.
 */
class StatusPanelTest {

    /**
     * The names of the app's screens in the title, while {@code unseen} pull requests of the repository are new.
     */
    private static io.vavr.collection.List<StatusPanel.Name> names(int unseen) {
        Model model = TestModels.loaded()
                .withPart(
                        PrOverview.PART,
                        PrOverview.INITIAL.withUnseen(Option.of(new UnseenPullRequests(
                                unseen,
                                io.vavr.collection.TreeSet.ofAll(io.vavr.collection.List.rangeClosed(1, unseen))))));
        return StatusPanel.names(model, Screens.standard());
    }

    @Test
    void aClickOnTheNameOfAScreenInTheTitleTellsWhichOne() {
        int repository = Messages.get("screen.repository").length();
        int pullRequests = Messages.get("github.screen.pullRequests").length();

        assertEquals(Option.of(Screen.REPOSITORY), StatusPanel.screenAt(0, names(0)));
        assertEquals(Option.of(Screen.REPOSITORY), StatusPanel.screenAt(repository - 1, names(0)));
        assertEquals(Option.none(), StatusPanel.screenAt(repository + 1, names(0)));
        assertEquals(Option.of(PrOverview.SCREEN), StatusPanel.screenAt(repository + 3, names(0)));
        assertEquals(Option.of(PrOverview.SCREEN), StatusPanel.screenAt(repository + 3 + pullRequests - 1, names(0)));
        assertEquals(Option.none(), StatusPanel.screenAt(repository + 3 + pullRequests, names(0)));
        assertEquals(Option.none(), StatusPanel.screenAt(-1, names(0)));
    }

    @Test
    void theNewPullRequestsAreCountedAfterTheNameOfTheirScreenAndAClickOnTheCountShowsIt() {
        String pullRequests = Messages.get("github.screen.pullRequests");
        int start = Messages.get("screen.repository").length() + 3;

        assertEquals(
                Messages.get("screen.repository") + " │ " + pullRequests + " ●2",
                StatusPanel.title(Screen.REPOSITORY, names(2), Theme.SpanStyles.NONE)
                        .rawContent());
        assertEquals(
                Messages.get("screen.repository") + " │ " + pullRequests,
                StatusPanel.title(Screen.REPOSITORY, names(0), Theme.SpanStyles.NONE)
                        .rawContent());
        assertEquals(Option.of(PrOverview.SCREEN), StatusPanel.screenAt(start + pullRequests.length() + 2, names(2)));
        assertEquals(Option.none(), StatusPanel.screenAt(start + pullRequests.length() + 3, names(2)));
    }

    @Test
    void showsRepositoryNameAndCurrentBranch() throws Exception {
        Model model = TestModels.loaded(SampleData.git());

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
        Model model = CheckoutModels.withCheckoutError(TestModels.loaded(SampleData.git()), "foo", "boom");

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
