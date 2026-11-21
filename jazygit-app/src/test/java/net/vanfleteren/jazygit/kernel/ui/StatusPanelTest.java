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
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.state.Update;
import net.vanfleteren.jazygit.kernel.state.load.LoadMsg;
import net.vanfleteren.jazygit.kernel.ui.text.Placeholders;
import org.junit.jupiter.api.Test;

/**
 * The status panel shows the repository name and the current branch.
 */
class StatusPanelTest {

    @Test
    void showsRepositoryNameAndCurrentBranch() throws Exception {
        Model model = TestModels.loaded(SampleData.git());

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> StatusPanel.render(model))) {
            String screen = RenderedText.of(testRunner, () -> StatusPanel.render(model));
            assertTrue(screen.contains("Repo"), screen);
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
