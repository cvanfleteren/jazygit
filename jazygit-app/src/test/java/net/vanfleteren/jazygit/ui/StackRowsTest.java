package net.vanfleteren.jazygit.ui;

import static org.junit.jupiter.api.Assertions.*;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.tui.event.KeyCode;
import io.vavr.collection.HashMap;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.feature.branch.stack.StackMsg;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.State;
import net.vanfleteren.jazygit.gh.model.PullRequestStack;
import net.vanfleteren.jazygit.git.model.*;
import net.vanfleteren.jazygit.model.SampleData;
import net.vanfleteren.jazygit.state.*;
import net.vanfleteren.jazygit.state.load.SelectionMsg;
import net.vanfleteren.jazygit.state.view.*;
import org.junit.jupiter.api.Test;

/**
 * The rows of a stack in the branches pane (ADR 72): which log each shows, where the highlight goes, and what the keys
 * do on its header.
 */
class StackRowsTest {

    private static final BranchName MAIN = new BranchName("main");
    private static final BranchName FEAT_A = new BranchName("feat/a");
    private static final BranchName FEAT_B = new BranchName("feat/b");
    private static final RemoteBranch ORIGIN_FEAT_C = new RemoteBranch("origin", new BranchName("feat/c"));

    // #95 is merged; #96 and #97 are checked out; #98 is only fetched.
    private static final List<PullRequestStack.Entry> ENTRIES = List.of(
            new PullRequestStack.Entry(1, 95, State.MERGED, "feat/0", "main"),
            new PullRequestStack.Entry(2, 96, State.OPEN, "feat/a", "main"),
            new PullRequestStack.Entry(3, 97, State.OPEN, "feat/b", "feat/a"),
            new PullRequestStack.Entry(4, 98, State.OPEN, "feat/c", "feat/b"));

    private static PullRequest stacked(int number, String head, int position) {
        return new PullRequest(
                number,
                head,
                "",
                false,
                Checks.NONE,
                Review.NONE,
                Option.of(new PullRequestStack(12, position, ENTRIES.size(), ENTRIES)),
                Option.none());
    }

    /**
     * {@code current} checked out, the others not; listed as given, the current one first.
     */
    private static Model model(BranchName current, List<BranchName> branches) {
        return TestModels.localBranchesRead(
                        TestModels.loaded(SampleData.git()),
                        branches.map(name -> Fixtures.branch(name, name.equals(current), new Sha(name.value()))))
                .model()
                .mapRepo(repo -> repo.withRemoteBranches(
                        new Loadable.Loaded<>(List.of(new RemoteRef(ORIGIN_FEAT_C, new Sha("cccc"), Instant.EPOCH)))))
                .mapGitHub(gitHub -> gitHub.withPullRequests(Option.of(HashMap.of(
                        FEAT_A, stacked(96, "feat/a", 2),
                        FEAT_B, stacked(97, "feat/b", 3)))));
    }

    // main; then ▾ stack #12, #95 ◆ merged, feat/a, feat/b, #98 not checked out.
    private static final Model OPEN = model(MAIN, List.of(MAIN, FEAT_A, FEAT_B));

    private static Model closed(Model model) {
        return model.mapRepoView(repoView -> repoView.withClosedStacks(HashSet.of(12)));
    }

    private static Option<Msg> selecting(BranchRef branch) {
        return Option.of(new SelectionMsg.BranchSelected(branch));
    }

    @Test
    void eachRowShowsTheLogOfItsBranchAndAPullRequestWithNoneSaysSo() throws Exception {
        BranchesPanel panel = new BranchesPanel(msg -> {}, tab -> {});
        java.util.List<Option<Msg>> shown = new java.util.ArrayList<>();
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(OPEN, BranchesPanel.ID))) {
            SettledPilot.focus(testRunner, BranchesPanel.ID);
            RenderedText.of(testRunner, () -> panel.render(OPEN, BranchesPanel.ID));
            for (int row = 0; row < 6; row++) {
                shown.add(panel.selectionChange(OPEN));
                SettledPilot.of(testRunner).press(KeyCode.DOWN);
            }
            SettledPilot.of(testRunner).quit();
        }

        assertEquals(
                java.util.List.of(
                        selecting(new BranchRef.Local(MAIN)),
                        // The header shows the highest branch of the stack, whose log holds all of it.
                        selecting(new BranchRef.Local(FEAT_B)),
                        Option.of(new SelectionMsg.UnfetchedSelected(95)),
                        selecting(new BranchRef.Local(FEAT_A)),
                        selecting(new BranchRef.Local(FEAT_B)),
                        selecting(new BranchRef.Remote(ORIGIN_FEAT_C))),
                shown);
    }

    @Test
    void aPullRequestWithoutALogSaysSoOnceAndInPlaceOfTheLogBefore() {
        Model unfetched = Update.update(
                        Update.update(OPEN, new SelectionMsg.BranchSelected(new BranchRef.Local(MAIN)))
                                .model(),
                        new SelectionMsg.UnfetchedSelected(95))
                .model();

        assertEquals(Option.none(), unfetched.repoView().branchLog());
        assertEquals(Option.of(95), unfetched.repoView().unfetched());
        assertEquals(
                Option.none(),
                Update.update(unfetched, new SelectionMsg.BranchSelected(new BranchRef.Local(MAIN)))
                        .model()
                        .repoView()
                        .unfetched());
    }

    @Test
    void enterOnAHeaderOpensOrClosesTheStackAndTheKeysOfABranchDoNothingThere() throws Exception {
        java.util.List<Msg> dispatched = new java.util.ArrayList<>();
        BranchesPanel panel = new BranchesPanel(dispatched::add, tab -> {});
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(OPEN, BranchesPanel.ID))) {
            SettledPilot.focus(testRunner, BranchesPanel.ID);
            RenderedText.of(testRunner, () -> panel.render(OPEN, BranchesPanel.ID));
            SettledPilot.of(testRunner).press(KeyCode.DOWN);
            SettledPilot.of(testRunner).press(' ');
            SettledPilot.of(testRunner).press('P');
            SettledPilot.of(testRunner).press('d');
            SettledPilot.of(testRunner).press(KeyCode.ENTER);
            SettledPilot.of(testRunner).pause();
            SettledPilot.of(testRunner).quit();
        }

        assertEquals(java.util.List.of(new StackMsg.Toggled(12)), dispatched);
    }

    @Test
    void aHighlightedHeaderStaysHighlightedAsTheStackClosesAndOpens() throws Exception {
        java.util.List<Msg> dispatched = new java.util.ArrayList<>();
        BranchesPanel panel = new BranchesPanel(dispatched::add, tab -> {});
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(OPEN, BranchesPanel.ID))) {
            SettledPilot.focus(testRunner, BranchesPanel.ID);
            RenderedText.of(testRunner, () -> panel.render(OPEN, BranchesPanel.ID));
            SettledPilot.of(testRunner).press(KeyCode.DOWN);
            for (Model shown : List.of(closed(OPEN), OPEN)) {
                RenderedText.of(testRunner, () -> panel.render(shown, BranchesPanel.ID));
                SettledPilot.of(testRunner).press(KeyCode.ENTER);
            }
            SettledPilot.of(testRunner).pause();
            SettledPilot.of(testRunner).quit();
        }

        assertEquals(java.util.List.of(new StackMsg.Toggled(12), new StackMsg.Toggled(12)), dispatched);
    }

    @Test
    void afterACheckoutOfABranchInAClosedStackItsHeaderIsHighlighted() throws Exception {
        java.util.List<Msg> dispatched = new java.util.ArrayList<>();
        BranchesPanel panel = new BranchesPanel(dispatched::add, tab -> {});
        Model before = closed(OPEN);
        Model after = closed(model(FEAT_A, List.of(FEAT_A, MAIN, FEAT_B)));
        Option<Msg> shown;
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(before, BranchesPanel.ID))) {
            SettledPilot.focus(testRunner, BranchesPanel.ID);
            RenderedText.of(testRunner, () -> panel.render(before, BranchesPanel.ID));
            // main is highlighted; feat/a, which the closed stack holds, is checked out.
            RenderedText.of(testRunner, () -> panel.render(after, BranchesPanel.ID));
            shown = panel.selectionChange(after);
            SettledPilot.of(testRunner).press(KeyCode.ENTER);
            SettledPilot.of(testRunner).pause();
            SettledPilot.of(testRunner).quit();
        }

        assertEquals(selecting(new BranchRef.Local(FEAT_B)), shown);
        assertEquals(java.util.List.of(new StackMsg.Toggled(12)), dispatched);
    }
}
