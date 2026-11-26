package net.vanfleteren.jazygit.features.branches.panes.branches;

import static org.junit.jupiter.api.Assertions.*;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.tui.event.KeyCode;
import io.vavr.collection.HashMap;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.core.gh.model.PullRequest;
import net.vanfleteren.jazygit.core.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.core.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.core.gh.model.PullRequestDetails.State;
import net.vanfleteren.jazygit.core.gh.model.PullRequestStack;
import net.vanfleteren.jazygit.core.gh.model.StackId;
import net.vanfleteren.jazygit.core.git.model.*;
import net.vanfleteren.jazygit.core.git.model.SampleData;
import net.vanfleteren.jazygit.core.repo.*;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.ForgeBranch;
import net.vanfleteren.jazygit.core.repo.PrNumber;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.features.github.stack.StackMsg;
import net.vanfleteren.jazygit.features.github.state.GitHubState;
import net.vanfleteren.jazygit.kernel.state.*;
import net.vanfleteren.jazygit.kernel.state.load.BranchFiles;
import net.vanfleteren.jazygit.kernel.state.load.SelectionMsg;
import net.vanfleteren.jazygit.kernel.state.view.*;
import net.vanfleteren.jazygit.kernel.ui.RenderedText;
import net.vanfleteren.jazygit.kernel.ui.SettledPilot;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;
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
            new PullRequestStack.Entry(
                    1, new PrNumber(95), State.MERGED, new ForgeBranch("feat/0"), new ForgeBranch("main")),
            new PullRequestStack.Entry(
                    2, new PrNumber(96), State.OPEN, new ForgeBranch("feat/a"), new ForgeBranch("main")),
            new PullRequestStack.Entry(
                    3, new PrNumber(97), State.OPEN, new ForgeBranch("feat/b"), new ForgeBranch("feat/a")),
            new PullRequestStack.Entry(
                    4, new PrNumber(98), State.OPEN, new ForgeBranch("feat/c"), new ForgeBranch("feat/b")));

    private static PullRequest stacked(int number, String head, int position) {
        return new PullRequest(
                new PrNumber(number),
                new ForgeBranch(head),
                Option.none(),
                false,
                Checks.NONE,
                Review.NONE,
                Option.of(new PullRequestStack(new StackId(12), position, ENTRIES.size(), ENTRIES)),
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
                .mapPart(
                        GitHubState.PART,
                        gitHub -> gitHub.withPullRequests(Option.of(HashMap.of(
                                FEAT_A, stacked(96, "feat/a", 2),
                                FEAT_B, stacked(97, "feat/b", 3)))));
    }

    // main; then ▾ stack #12, #95 ◆ merged, feat/a, feat/b, #98 not checked out.
    private static final Model OPEN = model(MAIN, List.of(MAIN, FEAT_A, FEAT_B));

    private static Model closed(Model model) {
        return model.mapRepoView(
                repoView -> repoView.mapBranches(branches -> branches.withClosedGroups(HashSet.of("stack:12"))));
    }

    private static Option<Msg> selecting(BranchRef branch) {
        return Option.of(new SelectionMsg.BranchSelected(branch));
    }

    @Test
    void eachRowShowsTheLogOfItsBranchAndAPullRequestWithNoneSaysSo() throws Exception {
        BranchesPanel panel = withFills(msg -> {});
        java.util.List<Option<Msg>> shown = new java.util.ArrayList<>();
        try (ToolkitTestRunner testRunner =
                ToolkitTestRunner.runTest(() -> panel.render(OPEN, Scopes.BRANCHES.pane()))) {
            SettledPilot.focus(testRunner, Scopes.BRANCHES.id());
            RenderedText.of(testRunner, () -> panel.render(OPEN, Scopes.BRANCHES.pane()));
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
                        Option.of(new SelectionMsg.NoLogSelected(new NoLog.Unfetched("#95"))),
                        selecting(new BranchRef.Local(FEAT_A)),
                        selecting(new BranchRef.Local(FEAT_B)),
                        selecting(new BranchRef.Remote(ORIGIN_FEAT_C))),
                shown);
    }

    @Test
    void eachRowSaysWhatTheFilesPaneListsForIt() throws Exception {
        // ADR 141: the branch checked out the working tree, a branch its files against its base, a stack and a pull
        // request of it not checked out here nothing.
        BranchesPanel panel = withFills(msg -> {});
        java.util.List<Option<Followed>> followed = new java.util.ArrayList<>();
        try (ToolkitTestRunner testRunner =
                ToolkitTestRunner.runTest(() -> panel.render(OPEN, Scopes.BRANCHES.pane()))) {
            SettledPilot.focus(testRunner, Scopes.BRANCHES.id());
            RenderedText.of(testRunner, () -> panel.render(OPEN, Scopes.BRANCHES.pane()));
            for (int row = 0; row < 6; row++) {
                followed.add(panel.followed(OPEN));
                SettledPilot.of(testRunner).press(KeyCode.DOWN);
            }
            SettledPilot.of(testRunner).quit();
        }

        Option<Followed> nothingHere = Option.of(new Followed.Nothing(new Unlisted.NotHere()));
        assertEquals(
                java.util.List.of(
                        Option.of(new Followed.WorkingTree()),
                        Option.of(new Followed.Nothing(new Unlisted.Group())),
                        nothingHere,
                        BranchFiles.of(OPEN, FEAT_A),
                        BranchFiles.of(OPEN, FEAT_B),
                        nothingHere),
                followed);
    }

    @Test
    void aPullRequestWithoutALogSaysSoOnceAndInPlaceOfTheLogBefore() {
        Model unfetched = Update.update(
                        Update.update(OPEN, new SelectionMsg.BranchSelected(new BranchRef.Local(MAIN)))
                                .model(),
                        new SelectionMsg.NoLogSelected(new NoLog.Unfetched("#95")))
                .model();

        assertEquals(Option.none(), unfetched.repoView().preview().branchLog());
        assertEquals(
                Option.of(new NoLog.Unfetched("#95")),
                unfetched.repoView().branches().noLog());
        assertEquals(
                Option.none(),
                Update.update(unfetched, new SelectionMsg.BranchSelected(new BranchRef.Local(MAIN)))
                        .model()
                        .repoView()
                        .branches()
                        .noLog());
    }

    @Test
    void enterOnAHeaderOpensOrClosesTheStackAndTheKeysOfABranchDoNothingThere() throws Exception {
        java.util.List<Msg> dispatched = new java.util.ArrayList<>();
        BranchesPanel panel = withFills(msg -> dispatched.add(net.vanfleteren.jazygit.app.GitHubKeys.unwrapped(msg)));
        try (ToolkitTestRunner testRunner =
                ToolkitTestRunner.runTest(() -> panel.render(OPEN, Scopes.BRANCHES.pane()))) {
            SettledPilot.focus(testRunner, Scopes.BRANCHES.id());
            RenderedText.of(testRunner, () -> panel.render(OPEN, Scopes.BRANCHES.pane()));
            SettledPilot.of(testRunner).press(KeyCode.DOWN);
            SettledPilot.of(testRunner).press(' ');
            SettledPilot.of(testRunner).press('P');
            SettledPilot.of(testRunner).press('d');
            SettledPilot.of(testRunner).press(KeyCode.ENTER);
            SettledPilot.of(testRunner).pause();
            SettledPilot.of(testRunner).quit();
        }

        assertEquals(java.util.List.of(new StackMsg.Toggled(new StackId(12))), dispatched);
    }

    @Test
    void aHighlightedHeaderStaysHighlightedAsTheStackClosesAndOpens() throws Exception {
        java.util.List<Msg> dispatched = new java.util.ArrayList<>();
        BranchesPanel panel = withFills(msg -> dispatched.add(net.vanfleteren.jazygit.app.GitHubKeys.unwrapped(msg)));
        try (ToolkitTestRunner testRunner =
                ToolkitTestRunner.runTest(() -> panel.render(OPEN, Scopes.BRANCHES.pane()))) {
            SettledPilot.focus(testRunner, Scopes.BRANCHES.id());
            RenderedText.of(testRunner, () -> panel.render(OPEN, Scopes.BRANCHES.pane()));
            SettledPilot.of(testRunner).press(KeyCode.DOWN);
            for (Model shown : List.of(closed(OPEN), OPEN)) {
                RenderedText.of(testRunner, () -> panel.render(shown, Scopes.BRANCHES.pane()));
                SettledPilot.of(testRunner).press(KeyCode.ENTER);
            }
            SettledPilot.of(testRunner).pause();
            SettledPilot.of(testRunner).quit();
        }

        assertEquals(
                java.util.List.of(new StackMsg.Toggled(new StackId(12)), new StackMsg.Toggled(new StackId(12))),
                dispatched);
    }

    @Test
    void afterACheckoutOfABranchInAClosedStackItsHeaderIsHighlighted() throws Exception {
        java.util.List<Msg> dispatched = new java.util.ArrayList<>();
        BranchesPanel panel = withFills(msg -> dispatched.add(net.vanfleteren.jazygit.app.GitHubKeys.unwrapped(msg)));
        Model before = closed(OPEN);
        Model after = closed(model(FEAT_A, List.of(FEAT_A, MAIN, FEAT_B)));
        Option<Msg> shown;
        try (ToolkitTestRunner testRunner =
                ToolkitTestRunner.runTest(() -> panel.render(before, Scopes.BRANCHES.pane()))) {
            SettledPilot.focus(testRunner, Scopes.BRANCHES.id());
            RenderedText.of(testRunner, () -> panel.render(before, Scopes.BRANCHES.pane()));
            // main is highlighted; feat/a, which the closed stack holds, is checked out.
            RenderedText.of(testRunner, () -> panel.render(after, Scopes.BRANCHES.pane()));
            shown = panel.selectionChange(after);
            SettledPilot.of(testRunner).press(KeyCode.ENTER);
            SettledPilot.of(testRunner).pause();
            SettledPilot.of(testRunner).quit();
        }

        assertEquals(selecting(new BranchRef.Local(FEAT_B)), shown);
        assertEquals(java.util.List.of(new StackMsg.Toggled(new StackId(12))), dispatched);
    }

    /**
     * The pane as the app builds it, with what its features give it, as GitHub's stacks (ADR 172).
     */
    private static BranchesPanel withFills(java.util.function.Consumer<Msg> dispatch) {
        return new BranchesPanel(
                dispatch,
                java.time.Clock.systemUTC(),
                net.vanfleteren.jazygit.kernel.ui.theme.Theme.Highlights.DEFAULT,
                net.vanfleteren.jazygit.app.keys.Features.FILLS);
    }
}
