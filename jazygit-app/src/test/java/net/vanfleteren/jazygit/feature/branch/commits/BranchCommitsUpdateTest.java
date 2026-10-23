package net.vanfleteren.jazygit.feature.branch.commits;

import static net.vanfleteren.jazygit.state.TestModels.*;
import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.feature.branch.tabs.BranchesTabMsg;
import net.vanfleteren.jazygit.feature.diff.DiffOptionsMsg;
import net.vanfleteren.jazygit.git.model.*;
import net.vanfleteren.jazygit.state.*;
import org.junit.jupiter.api.Test;

/**
 * Listing the commits of a branch in place of the branches: opening and closing it, and what it shows as the
 * branches are read again.
 */
class BranchCommitsUpdateTest {

    private static final Branch.Name MAIN = new Branch.Name("main");
    private static final BranchRef FEATURE = new BranchRef.Local(new Branch.Name("feature"));
    private static final Sha FEATURE_TIP = new Sha("ffff");
    private static final DiffText CHANGES = DiffText.of("+added line");

    /**
     * The sample model with the feature branch highlighted and its log read.
     */
    private static Model featureLogRead() {
        Model selected = Update.update(loaded(), new SelectionMsg.BranchSelected(FEATURE))
                .model();
        return Update.update(selected, new LoadMsg.BranchLogLoaded(FEATURE, FEATURE_COMMITS))
                .model();
    }

    private static Model opened(Model model) {
        return Update.update(model, new BranchCommitsMsg.Opened(FEATURE)).model();
    }

    private static Option<BranchCommitsView> view(Model model) {
        return model.repoView().branchCommits();
    }

    @Test
    void openingListsTheLogOfTheHighlightedBranchWithoutReadingItAgain() {
        Model logRead = featureLogRead();

        Update.Next open = Update.update(logRead, new BranchCommitsMsg.Opened(FEATURE));

        assertEquals(Option.of(BranchCommitsView.of(FEATURE)), view(open.model()));
        assertEquals(List.empty(), open.cmds());
        assertEquals(
                new Loadable.Loaded<>(FEATURE_COMMITS), view(open.model()).get().commits(open.model()));
    }

    @Test
    void openingABranchNotHighlightedReadsItsLogAndShowsNoneOfAnotherMeanwhile() {
        Model logRead = featureLogRead();
        BranchRef main = new BranchRef.Local(MAIN);

        Update.Next open = Update.update(logRead, new BranchCommitsMsg.Opened(main));

        assertEquals(List.of(new Cmd.LoadBranchLog(main)), open.cmds());
        assertEquals(new Loadable.Loading<>(), view(open.model()).get().commits(open.model()));
    }

    @Test
    void openingClosesTheDivergence() {
        Model diverged = featureLogRead()
                .mapRepoView(repoView -> repoView.withDivergence(Option.of(DivergenceView.loading(
                        MAIN, new BranchRef.Local(new Branch.Name("feature")), DivergenceView.Against.BASE))));

        assertEquals(Option.none(), opened(diverged).repoView().divergence());
    }

    @Test
    void escapeAndSwitchingTabsGoBackToTheBranches() {
        Model open = opened(featureLogRead());

        assertEquals(
                Option.none(),
                view(Update.update(open, new BranchCommitsMsg.Closed()).model()));
        assertEquals(
                Option.none(),
                view(Update.update(open, new BranchesTabMsg.Switched(1)).model()));
        // The branch stays highlighted, with its log.
        assertEquals(
                Option.of(FEATURE),
                Update.update(open, new BranchCommitsMsg.Closed())
                        .model()
                        .repoView()
                        .branchLog()
                        .map(Preview::key));
    }

    @Test
    void highlightingACommitReadsWhatItChangedAndShowsIt() {
        Model open = opened(featureLogRead());

        Update.Next selected = Update.update(open, new SelectionMsg.BranchCommitSelected(FEATURE_TIP));
        Model read = Update.update(
                        selected.model(),
                        new LoadMsg.CommitDetailLoaded(
                                FEATURE_TIP, open.preferences().diffOptions(), CHANGES))
                .model();

        assertEquals(
                List.of(new Cmd.LoadCommitDetail(FEATURE_TIP, open.preferences().diffOptions())), selected.cmds());
        assertEquals(
                Option.of(new Loadable.Loaded<>(CHANGES)),
                view(read).flatMap(BranchCommitsView::detail).map(Preview::value));
        assertEquals(Option.of(FEATURE_COMMITS.head()), view(read).flatMap(v -> v.commit(read, FEATURE_TIP)));
    }

    @Test
    void theHighlightedCommitIsReadAgainWithOtherDiffOptions() {
        Model open = opened(featureLogRead());
        Model selected = Update.update(open, new SelectionMsg.BranchCommitSelected(FEATURE_TIP))
                .model();

        Update.Next ignoring = Update.update(selected, new DiffOptionsMsg.WhitespaceToggled());

        assertTrue(
                ignoring.cmds()
                        .contains(new Cmd.LoadCommitDetail(
                                FEATURE_TIP, ignoring.model().preferences().diffOptions())),
                ignoring.cmds().toString());
    }

    @Test
    void theLogIsReadAgainWhenTheBranchMovedAndClosedWhenItIsGone() {
        Model open = opened(featureLogRead());

        Update.Next moved = localBranchesRead(
                open,
                List.of(
                        new Branch(MAIN, true, new Sha("aaaa")),
                        new Branch(new Branch.Name("feature"), false, new Sha("eeee"))));
        Update.Next gone = localBranchesRead(open, List.of(new Branch(MAIN, true, new Sha("aaaa"))));

        assertTrue(
                moved.cmds().contains(new Cmd.LoadBranchLog(FEATURE)),
                moved.cmds().toString());
        assertEquals(Option.of(BranchCommitsView.of(FEATURE)), view(moved.model()));
        assertEquals(Option.none(), view(gone.model()));
    }

    @Test
    void aRemoteBranchThatIsGoneIsClosedToo() {
        BranchRef remote = new BranchRef.Remote(new RemoteBranch("origin", new Branch.Name("feature")));
        Model withRemote = remoteBranchesRead(
                        loaded(),
                        List.of(new RemoteRef(
                                new RemoteBranch("origin", new Branch.Name("feature")), FEATURE_TIP, Instant.EPOCH)))
                .model();
        Model open =
                Update.update(withRemote, new BranchCommitsMsg.Opened(remote)).model();

        Update.Next gone = remoteBranchesRead(open, List.empty());

        assertEquals(Option.of(remote), view(open).map(BranchCommitsView::branch));
        assertEquals(Option.none(), view(gone.model()));
    }

    @Test
    void whatAHighlightedCommitChangedThatFailedToBeReadIsReadAgainOnTheNextTick() {
        Model selected = Update.update(opened(featureLogRead()), new SelectionMsg.BranchCommitSelected(FEATURE_TIP))
                .model();
        Cmd.LoadCommitDetail load =
                new Cmd.LoadCommitDetail(FEATURE_TIP, selected.preferences().diffOptions());

        Model failed = Update.update(selected, new LoadMsg.Failed(load, refused("boom")))
                .model();

        assertTrue(
                view(failed).flatMap(BranchCommitsView::detail).exists(d -> d.value() instanceof Loadable.Failed),
                view(failed).toString());
        assertTrue(
                Update.tick(failed).cmds().contains(load),
                Update.tick(failed).cmds().toString());
    }

    @Test
    void theForkPointListedWithOnlyTheCommitsAheadIsFoundAlsoWhenTheLogStopsBeforeIt() {
        Commit fork = new Commit(new Sha("eeee"), "Ada", "ada@example.com", Instant.EPOCH, "Fork", "");
        BranchRef base = new BranchRef.Local(MAIN);
        Model open = opened(featureLogRead())
                .mapRepoView(repoView -> repoView.withOnlyAhead(true)
                        .withBranchAhead(Option.of(new Preview<>(
                                FEATURE,
                                new Loadable.Loaded<>(Option.of(
                                        new Ahead(FEATURE_TIP, base, FEATURE_COMMITS, false, Option.of(fork))))))));

        assertEquals(Option.of(fork), view(open).flatMap(v -> v.commit(open, fork.sha())));
    }
}
