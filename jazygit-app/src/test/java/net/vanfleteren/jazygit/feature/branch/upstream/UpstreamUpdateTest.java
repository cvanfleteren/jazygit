package net.vanfleteren.jazygit.feature.branch.upstream;

import static net.vanfleteren.jazygit.state.TestModels.branchesLoaded;
import static net.vanfleteren.jazygit.state.TestModels.errorText;
import static net.vanfleteren.jazygit.state.TestModels.loaded;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.feature.branch.tabs.BranchesTabMsg;
import net.vanfleteren.jazygit.feature.commit.reset.ResetCmd;
import net.vanfleteren.jazygit.feature.commit.reset.ResetMsg;
import net.vanfleteren.jazygit.feature.commit.reset.ResetPopup;
import net.vanfleteren.jazygit.git.model.*;
import net.vanfleteren.jazygit.state.BranchesTab;
import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.DivergenceView;
import net.vanfleteren.jazygit.state.DivergenceView.Against;
import net.vanfleteren.jazygit.state.LoadMsg;
import net.vanfleteren.jazygit.state.Loadable;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.SelectionMsg;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;
import org.junit.jupiter.api.Test;

/**
 * The options on the upstream: viewing how the highlighted branch diverged from its upstream or from the main
 * branch, and resetting the checked out branch onto its upstream.
 */
class UpstreamUpdateTest {

    private static final BranchName MAIN = new BranchName("main");
    private static final BranchName FEATURE = new BranchName("feature");
    private static final RemoteBranch ORIGIN_MAIN = new RemoteBranch("origin", MAIN);
    private static final RemoteBranch ORIGIN_FEATURE = new RemoteBranch("origin", FEATURE);
    private static final Branch TRACKING = Fixtures.branch(
            MAIN,
            true,
            new Sha("aaaa"),
            Instant.EPOCH,
            true,
            true,
            Option.of(new Upstream(new BranchRef.Remote(ORIGIN_MAIN), 2, 1)));
    private static final Branch FEATURE_BRANCH = Fixtures.branch(
            FEATURE,
            false,
            new Sha("ffff"),
            Instant.EPOCH,
            true,
            false,
            Option.of(new Upstream(new BranchRef.Remote(ORIGIN_FEATURE), 1, 0)));
    private static final List<RemoteRef> REMOTES = List.of(
            new RemoteRef(ORIGIN_MAIN, new Sha("a0a0"), Instant.EPOCH),
            new RemoteRef(ORIGIN_FEATURE, new Sha("f0f0"), Instant.EPOCH));
    private static final Commit AHEAD =
            Fixtures.commit(new Sha("ffff"), "Ada", "ada@example.com", Instant.EPOCH, "Local work", "");
    private static final Divergence DIVERGENCE =
            new Divergence(new Divergence.Side(List.of(AHEAD), 1), new Divergence.Side(List.of(), 0));

    private static Model with(Branch... branches) {
        return TestModels.localBranchesRead(loaded(), List.of(branches)).model();
    }

    private static Model withRemotes(Model model, List<RemoteRef> remotes) {
        return TestModels.remoteBranchesRead(model, remotes).model();
    }

    private static Model opened(Model model, BranchName highlighted) {
        return Update.update(model, new UpstreamMsg.Requested(Option.of(highlighted)))
                .model();
    }

    private static Model opened(Model model) {
        return opened(model, MAIN);
    }

    /**
     * The divergence of the feature branch from its upstream, read.
     */
    private static Model viewing() {
        Model asking = opened(withRemotes(with(TRACKING, FEATURE_BRANCH), REMOTES), FEATURE);
        Model open = Update.update(asking, new UpstreamMsg.UpstreamDivergenceChosen())
                .model();
        return Update.update(
                        open, new LoadMsg.DivergenceLoaded(FEATURE, new BranchRef.Remote(ORIGIN_FEATURE), DIVERGENCE))
                .model();
    }

    @Test
    void requestOpensTheOptionsForTheHighlightedBranch() {
        Model model = withRemotes(with(TRACKING, FEATURE_BRANCH), REMOTES);

        Next next = Update.update(model, new UpstreamMsg.Requested(Option.of(FEATURE)));

        assertEquals(
                Option.of(new UpstreamPopup(
                        Option.of(FEATURE_BRANCH), Option.of(new BranchRef.Remote(ORIGIN_MAIN)), Option.of(TRACKING))),
                next.model().popup(UpstreamPopup.class));
        assertEquals(List.of(), next.cmds());
    }

    @Test
    void withoutItsRemoteBranchTheBaseIsTheLocalMainBranch() {
        Model asking = opened(with(TRACKING, FEATURE_BRANCH), FEATURE);

        assertEquals(
                Option.of(new BranchRef.Local(MAIN)),
                asking.popup(UpstreamPopup.class).flatMap(UpstreamPopup::base));
    }

    @Test
    void viewingTheDivergenceFromTheUpstreamReadsIt() {
        Model asking = opened(withRemotes(with(TRACKING, FEATURE_BRANCH), REMOTES), FEATURE);

        Next next = Update.update(asking, new UpstreamMsg.UpstreamDivergenceChosen());

        DivergenceView view = DivergenceView.loading(FEATURE, new BranchRef.Remote(ORIGIN_FEATURE), Against.UPSTREAM);
        assertEquals(Option.none(), next.model().popup());
        assertEquals(Option.of(view), next.model().repoView().divergence());
        assertEquals(List.of(new Cmd.LoadDivergence(FEATURE, new BranchRef.Remote(ORIGIN_FEATURE))), next.cmds());
    }

    @Test
    void viewingTheDivergenceFromTheBaseComparesWithTheMainBranch() {
        Model asking = opened(withRemotes(with(TRACKING, FEATURE_BRANCH), REMOTES), FEATURE);

        Next next = Update.update(asking, new UpstreamMsg.BaseDivergenceChosen());

        assertEquals(
                Option.of(DivergenceView.loading(FEATURE, new BranchRef.Remote(ORIGIN_MAIN), Against.BASE)),
                next.model().repoView().divergence());
        assertEquals(List.of(new Cmd.LoadDivergence(FEATURE, new BranchRef.Remote(ORIGIN_MAIN))), next.cmds());
    }

    @Test
    void theMainBranchHasNoDivergenceFromTheBase() {
        Next next = Update.update(opened(with(TRACKING, FEATURE_BRANCH)), new UpstreamMsg.BaseDivergenceChosen());

        assertEquals(Option.none(), next.model().repoView().divergence());
        assertEquals(Option.of("Branch main is the base branch itself"), errorText(next.model()));
    }

    @Test
    void withoutAMainBranchThereIsNoDivergenceFromTheBase() {
        Next next = Update.update(opened(with(FEATURE_BRANCH), FEATURE), new UpstreamMsg.BaseDivergenceChosen());

        assertEquals(Option.none(), next.model().repoView().divergence());
        assertEquals(
                Option.of("Branch feature has no base branch to compare with: there is no main or master branch"),
                errorText(next.model()));
    }

    @Test
    void aBranchWithoutUpstreamHasNoDivergenceFromIt() {
        Next next = Update.update(opened(loaded()), new UpstreamMsg.UpstreamDivergenceChosen());

        assertEquals(Option.none(), next.model().repoView().divergence());
        assertEquals(Option.of("Branch main has no upstream"), errorText(next.model()));
    }

    @Test
    void theDivergenceReadIsShown() {
        assertEquals(
                Option.of(new Loadable.Loaded<>(DIVERGENCE)),
                viewing().repoView().divergence().map(DivergenceView::commits));
    }

    @Test
    void aHighlightedCommitOfTheDivergenceShowsWhatItChanged() {
        Next next = Update.update(viewing(), new SelectionMsg.DivergenceCommitSelected(AHEAD.sha()));
        assertEquals(
                List.of(new Cmd.LoadCommitDetail(AHEAD.sha(), DiffOptions.DEFAULT, Cmd.Slot.DIVERGENCE_DETAIL)),
                next.cmds());

        Model shown = Update.update(
                        next.model(),
                        new LoadMsg.CommitDetailLoaded(
                                AHEAD.sha(), DiffOptions.DEFAULT, DiffText.of("diff"), Cmd.Slot.DIVERGENCE_DETAIL))
                .model();

        assertEquals(
                Option.of(new Loadable.Loaded<>(DiffText.of("diff"))),
                shown.repoView().divergence().flatMap(DivergenceView::detail).map(detail -> detail.value()));
        assertEquals(Option.none(), shown.repoView().commitDetail());
    }

    @Test
    void closingShowsTheBranchesAgain() {
        Next next = Update.update(viewing(), new UpstreamMsg.DivergenceClosed());

        assertEquals(Option.none(), next.model().repoView().divergence());
    }

    @Test
    void switchingTabsClosesIt() {
        Next next = Update.update(viewing(), new BranchesTabMsg.Switched(1));

        assertEquals(Option.none(), next.model().repoView().divergence());
        assertEquals(BranchesTab.REMOTE, next.model().repoView().branchesTab());
    }

    @Test
    void itIsReadAgainWhenTheUpstreamMoves() {
        List<RemoteRef> fetched = REMOTES.update(1, new RemoteRef(ORIGIN_FEATURE, new Sha("f1f1"), Instant.EPOCH));

        Next next = TestModels.remoteBranchesRead(viewing(), fetched);

        assertEquals(
                List.of(new Cmd.LoadDivergence(FEATURE, new BranchRef.Remote(ORIGIN_FEATURE))),
                next.cmds().filter(Cmd.LoadDivergence.class::isInstance));
    }

    @Test
    void itIsReadAgainOnceWhenTheBranchAndItsUpstreamBothMove() {
        Branch committed = Fixtures.branch(
                FEATURE,
                false,
                new Sha("f2f2"),
                Instant.EPOCH,
                true,
                false,
                Option.of(new Upstream(new BranchRef.Remote(ORIGIN_FEATURE), 2, 0)));
        List<RemoteRef> fetched = REMOTES.update(1, new RemoteRef(ORIGIN_FEATURE, new Sha("f1f1"), Instant.EPOCH));

        Next next = Update.update(viewing(), branchesLoaded(new AllBranches(List.of(TRACKING, committed), fetched)));

        assertEquals(
                List.of(new Cmd.LoadDivergence(FEATURE, new BranchRef.Remote(ORIGIN_FEATURE))),
                next.cmds().filter(Cmd.LoadDivergence.class::isInstance));
    }

    @Test
    void itIsNotReadAgainWhenNothingMoved() {
        Next next = TestModels.remoteBranchesRead(viewing(), REMOTES);

        assertEquals(List.of(), next.cmds().filter(Cmd.LoadDivergence.class::isInstance));
    }

    @Test
    void itClosesWhenTheUpstreamIsGone() {
        Branch gone = Fixtures.branch(
                FEATURE,
                false,
                new Sha("ffff"),
                Instant.EPOCH,
                false,
                false,
                new Tracking.Gone(new BranchRef.Remote(ORIGIN_FEATURE)));

        Next next = TestModels.localBranchesRead(viewing(), List.of(TRACKING, gone));

        assertEquals(Option.none(), next.model().repoView().divergence());
        assertEquals(
                Option.of("Branch feature has no upstream anymore: origin/feature was deleted on the remote"),
                errorText(next.model()));
    }

    @Test
    void itClosesQuietlyWhenTheBranchIsGone() {
        Next next = TestModels.localBranchesRead(viewing(), List.of(TRACKING));

        assertEquals(Option.none(), next.model().repoView().divergence());
        assertEquals(Option.none(), errorText(next.model()));
    }

    @Test
    void resettingAsksHowWarningAboutTheCommitsLeftBehind() {
        Next next = Update.update(opened(with(TRACKING)), new UpstreamMsg.ResetChosen());

        assertEquals(Option.none(), next.model().popup(UpstreamPopup.class));
        assertEquals(
                Option.of(new ResetPopup(new ResetTarget.ToUpstream(new BranchRef.Remote(ORIGIN_MAIN)), 2)),
                next.model().popup(ResetPopup.class));
        assertEquals(List.of(), next.cmds());
    }

    @Test
    void theResetIsOfTheCheckedOutBranchWhicheverIsHighlighted() {
        Next next = Update.update(opened(with(TRACKING, FEATURE_BRANCH), FEATURE), new UpstreamMsg.ResetChosen());

        assertEquals(
                Option.of(new ResetPopup(new ResetTarget.ToUpstream(new BranchRef.Remote(ORIGIN_MAIN)), 2)),
                next.model().popup(ResetPopup.class));
    }

    @Test
    void theResetGoesToTheUpstream() {
        Model asking = Update.update(opened(with(TRACKING)), new UpstreamMsg.ResetChosen())
                .model();

        Next next = Update.update(asking, new ResetMsg.Chosen(ResetMode.SOFT));

        assertEquals(
                List.of(new ResetCmd(new ResetTarget.ToUpstream(new BranchRef.Remote(ORIGIN_MAIN)), ResetMode.SOFT)),
                next.cmds());
        assertEquals(Option.none(), next.model().popup());
    }

    @Test
    void aBranchWithoutUpstreamCannotBeResetOntoIt() {
        Next next = Update.update(opened(loaded()), new UpstreamMsg.ResetChosen());

        assertEquals(Option.none(), next.model().popup(UpstreamPopup.class));
        assertEquals(Option.of("Branch main has no upstream"), errorText(next.model()));
    }

    @Test
    void aBranchWhoseUpstreamIsGoneCannotBeResetOntoIt() {
        Branch gone = Fixtures.branch(
                MAIN,
                true,
                new Sha("aaaa"),
                Instant.EPOCH,
                false,
                false,
                new Tracking.Gone(new BranchRef.Remote(ORIGIN_MAIN)));

        Next next = Update.update(opened(with(gone)), new UpstreamMsg.ResetChosen());

        assertEquals(Option.none(), next.model().popup(UpstreamPopup.class));
        assertEquals(
                Option.of("Branch main has no upstream anymore: origin/main was deleted on the remote"),
                errorText(next.model()));
    }

    @Test
    void aDetachedHeadHasNoBranchToReset() {
        Model detached = Update.update(
                        with(Fixtures.branch(MAIN, false, new Sha("aaaa"))),
                        new LoadMsg.StatusLoaded(new RepoStatus(new Head.Detached(new Sha("aaaa")), List.of())))
                .model();
        Model asking = opened(detached);
        assertEquals(Option.none(), asking.popup(UpstreamPopup.class).flatMap(UpstreamPopup::checkedOut));

        Next next = Update.update(asking, new UpstreamMsg.ResetChosen());

        assertEquals(Option.none(), next.model().popup(UpstreamPopup.class));
        assertEquals(Option.of("No branch is checked out"), errorText(next.model()));
    }

    @Test
    void cancellingClosesThePopup() {
        Next next = Update.update(opened(with(TRACKING)), new Msg.PopupDismissed(UpstreamPopup.class));

        assertEquals(Option.none(), next.model().popup());
        assertEquals(List.of(), next.cmds());
    }
}
