package net.vanfleteren.jazygit.state;

import static net.vanfleteren.jazygit.state.TestModels.*;
import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.*;
import org.junit.jupiter.api.Test;

/**
 * Which commits of HEAD and of the highlighted branch are not on their base: what the base is, and when that is read.
 */
class AheadUpdateTest {

    private static final Branch.Name MAIN = new Branch.Name("main");
    private static final Branch.Name FEATURE = new Branch.Name("feature");
    private static final BranchRef FEATURE_LOG = new BranchRef.Local(FEATURE);

    private static BranchRef origin(String name) {
        return new BranchRef.Remote(new RemoteBranch(RemoteBranch.DEFAULT_REMOTE, new Branch.Name(name)));
    }

    private static final Sha HEAD = new Sha("aaaa");
    private static final Sha FEATURE_TIP = new Sha("ffff");

    /**
     * The commits read from {@code tip} that are not on {@code base}: those of the feature branch.
     */
    private static Ahead ahead(Sha tip, BranchRef base) {
        return new Ahead(tip, base, FEATURE_COMMITS, false, Option.none());
    }

    /**
     * The sample model with the feature branch highlighted and its log read.
     */
    private static Model featureLogRead() {
        Model selected = Update.update(loaded(), new SelectionMsg.BranchSelected(FEATURE_LOG))
                .model();
        return Update.update(selected, TestModels.branchLogLoaded(FEATURE_LOG, FEATURE_COMMITS))
                .model();
    }

    @Test
    void theBaseIsTheParentElseTheMainBranchTheRemoteOneFirst() {
        Cmd.LoadAhead load = new Cmd.LoadAhead(Option.of(FEATURE_LOG), Option.of(FEATURE), Option.of(MAIN));

        assertEquals(
                List.of(
                        origin("parent"),
                        new BranchRef.Local(new Branch.Name("parent")),
                        origin("main"),
                        new BranchRef.Local(MAIN)),
                load.bases(Option.of(new Branch.Name("parent"))));
        assertEquals(List.of(origin("main"), new BranchRef.Local(MAIN)), load.bases(Option.none()));
    }

    @Test
    void aBranchIsNoBaseOfItself() {
        assertEquals(
                List.empty(),
                new Cmd.LoadAhead(Option.of(origin("main")), Option.of(MAIN), Option.of(MAIN)).bases(Option.none()));
    }

    @Test
    void aDetachedHeadIsComparedWithTheMainBranch() {
        assertEquals(
                List.of(origin("main"), new BranchRef.Local(MAIN)),
                new Cmd.LoadAhead(Option.none(), Option.none(), Option.of(MAIN)).bases(Option.none()));
    }

    @Test
    void theLogOfHeadIsShownWithItsCommitsNotOnItsBase() {
        Cmd.LoadAhead load = loadCommits().ahead();
        Update.Next next = Update.update(
                loaded(),
                new LoadMsg.CommitsLoaded(COMMITS, load, Either.right(Option.of(ahead(HEAD, origin("main"))))));

        // They were read with the log, so it is never shown without them.
        assertEquals(Option.of(ahead(HEAD, origin("main"))), AheadUpdate.head(next.model()));
        assertFalse(next.cmds().contains(load));
        assertEquals(FEATURE_COMMITS.headOption(), next.model().commit(new Sha("ffff")));
    }

    @Test
    void theLogOfHeadIsShownWhenItsCommitsNotOnItsBaseCannotBeRead() {
        Cmd.LoadAhead load = loadCommits().ahead();
        Model model = Update.update(loaded(), new LoadMsg.CommitsLoaded(COMMITS, load, Either.left(refused("boom"))))
                .model();

        assertEquals(Option.of(COMMITS), model.repo().commits().toOption());
        assertInstanceOf(Loadable.Failed.class, model.repo().ahead());
        assertTrue(Update.update(model, new Msg.Tick()).cmds().contains(load));
    }

    @Test
    void aRetryThatCameBackIsNoLongerRunning() {
        Cmd.LoadAhead load = AheadUpdate.ofHead(loaded());
        Model failed = Update.update(loaded(), new LoadMsg.Failed(load, refused("boom")))
                .model();
        Model retrying = Update.update(failed, new Msg.Tick()).model();
        assertTrue(retrying.jobs().refreshing().contains(load));

        Model retried = Update.update(retrying, new LoadMsg.AheadLoaded(load, Option.of(ahead(HEAD, origin("main")))))
                .model();
        assertFalse(retried.jobs().refreshing().contains(load));
    }

    @Test
    void whatIsReadForABranchNoLongerHighlightedIsDropped() {
        Model selected = Update.update(loaded(), new SelectionMsg.BranchSelected(FEATURE_LOG))
                .model();
        Cmd.LoadAhead load = loadBranchLog(FEATURE_LOG).ahead();
        Update.Next logRead = Update.update(
                selected,
                new LoadMsg.BranchLogLoaded(
                        FEATURE_LOG,
                        FEATURE_COMMITS,
                        load,
                        Either.right(Option.of(ahead(FEATURE_TIP, origin("main"))))));
        // They were read with the log, so it is never shown without them.
        assertEquals(List.empty(), logRead.cmds());
        assertEquals(Option.of(ahead(FEATURE_TIP, origin("main"))), AheadUpdate.branch(logRead.model(), FEATURE_LOG));

        // Read again as the base moved, they arrive after another branch is highlighted, and are dropped.
        BranchRef main = new BranchRef.Local(MAIN);
        Model other = Update.update(logRead.model(), new SelectionMsg.BranchSelected(main))
                .model();
        Model otherRead =
                Update.update(other, TestModels.branchLogLoaded(main, COMMITS)).model();
        Model late = Update.update(
                        otherRead, new LoadMsg.AheadLoaded(load, Option.of(ahead(FEATURE_TIP, origin("main")))))
                .model();
        assertEquals(Option.none(), AheadUpdate.branch(late, FEATURE_LOG));
        assertEquals(otherRead.repoView(), late.repoView());
    }

    @Test
    void aBranchIsNamedWithoutItsRemote() {
        assertEquals(Option.of(FEATURE), AheadUpdate.of(loaded(), FEATURE_LOG).named());
        assertEquals(
                Option.of(FEATURE), AheadUpdate.of(loaded(), origin("feature")).named());
    }

    @Test
    void theLogOfABranchNoLongerHighlightedIsDropped() {
        Model selected = Update.update(loaded(), new SelectionMsg.BranchSelected(FEATURE_LOG))
                .model();
        Model other = Update.update(selected, new SelectionMsg.BranchSelected(new BranchRef.Local(MAIN)))
                .model();

        Update.Next late = Update.update(other, TestModels.branchLogLoaded(FEATURE_LOG, FEATURE_COMMITS));

        assertEquals(List.empty(), late.cmds());
        assertEquals(other.repoView(), late.model().repoView());
    }

    @Test
    void whatWasReadForAnotherTipOfTheHighlightedBranchIsNotUsed() {
        Model logRead = featureLogRead();

        Model stale = Update.update(
                        logRead,
                        new LoadMsg.AheadLoaded(
                                AheadUpdate.of(logRead, FEATURE_LOG), Option.of(ahead(HEAD, origin("main")))))
                .model();

        assertEquals(Option.none(), AheadUpdate.branch(stale, FEATURE_LOG));
    }

    @Test
    void theCommitsOfTheHighlightedBranchAreReadAgainWhenItsBaseMovedButNotWhenItMovedToo() {
        Model logRead = featureLogRead();
        Cmd.LoadAhead load = AheadUpdate.of(logRead, FEATURE_LOG);
        Model read = Update.update(
                        logRead,
                        new LoadMsg.AheadLoaded(load, Option.of(ahead(FEATURE_TIP, new BranchRef.Local(MAIN)))))
                .model();

        Update.Next baseMoved = localBranchesRead(
                read, List.of(new Branch(MAIN, true, new Sha("cccc")), new Branch(FEATURE, false, FEATURE_TIP)));
        Update.Next bothMoved = localBranchesRead(
                read, List.of(new Branch(MAIN, true, new Sha("cccc")), new Branch(FEATURE, false, new Sha("eeee"))));

        assertTrue(baseMoved.cmds().contains(load), baseMoved.cmds().toString());
        // Its log is read again, and what is not on its base after it.
        assertTrue(
                bothMoved.cmds().contains(TestModels.loadBranchLog(FEATURE_LOG)),
                bothMoved.cmds().toString());
        assertFalse(bothMoved.cmds().contains(load), bothMoved.cmds().toString());
    }

    @Test
    void aFailedReadForTheHighlightedBranchIsTriedAgainOnTheNextRefresh() {
        Model logRead = featureLogRead();
        Cmd.LoadAhead load = AheadUpdate.of(logRead, FEATURE_LOG);

        Model failed = Update.update(logRead, load.failed(refused("no"))).model();

        assertEquals(Option.none(), AheadUpdate.branch(failed, FEATURE_LOG));
        assertTrue(Update.refresh(failed).cmds().contains(load));
        assertFalse(Update.refresh(logRead).cmds().contains(load));
    }

    @Test
    void theCommitsAreReadAgainWhenTheBaseMoved() {
        Model model = loaded();
        Cmd.LoadAhead load = AheadUpdate.ofHead(model);
        Model read = Update.update(
                        model, new LoadMsg.AheadLoaded(load, Option.of(ahead(HEAD, new BranchRef.Local(FEATURE)))))
                .model();

        Update.Next moved = localBranchesRead(
                read, List.of(new Branch(MAIN, true, new Sha("aaaa")), new Branch(FEATURE, false, new Sha("eeee"))));
        Update.Next still = localBranchesRead(read, BRANCHES);

        assertTrue(moved.cmds().contains(load));
        assertFalse(still.cmds().contains(load));
    }

    @Test
    void whatWasReadForAnotherHeadIsNotUsed() {
        Model model = loaded();
        Model stale = Update.update(
                        model,
                        new LoadMsg.AheadLoaded(
                                AheadUpdate.ofHead(model), Option.of(ahead(new Sha("bbbb"), origin("main")))))
                .model();

        assertEquals(Option.none(), AheadUpdate.head(stale));
        assertEquals(Option.none(), stale.commit(FEATURE_TIP));
    }

    @Test
    void whatIsNotOnTheBaseIsNotReadTwiceWhenTheLogIsReadAgainAnyway() {
        // HEAD moved to another branch: its log is read again, and what is not on its base after it.
        Model moved = Update.update(loaded(), new LoadMsg.StatusLoaded(MOVED)).model();
        Update.Next checkedOut = localBranchesRead(
                moved, List.of(new Branch(MAIN, false, new Sha("aaaa")), new Branch(FEATURE, true, new Sha("bbbb"))));
        assertFalse(
                checkedOut.cmds().exists(Cmd.LoadAhead.class::isInstance),
                checkedOut.cmds().toString());

        // The highlighted branch moved: its log is read again, and what is not on its base after it.
        Model selected = Update.update(loaded(), new SelectionMsg.BranchSelected(FEATURE_LOG))
                .model();
        Model logRead = Update.update(selected, TestModels.branchLogLoaded(FEATURE_LOG, FEATURE_COMMITS))
                .model();
        Update.Next branchMoved = localBranchesRead(
                logRead, List.of(new Branch(MAIN, true, HEAD), new Branch(FEATURE, false, new Sha("eeee"))));
        assertEquals(List.of(TestModels.loadBranchLog(FEATURE_LOG)), branchMoved.cmds());
    }

    @Test
    void checkingOutABranchWhereHeadIsReadsWhatIsNotOnItsBase() {
        Model model = loaded();

        Update.Next checkedOut =
                localBranchesRead(model, List.of(new Branch(MAIN, false, HEAD), new Branch(FEATURE, true, HEAD)));

        assertTrue(checkedOut.cmds().contains(AheadUpdate.ofHead(checkedOut.model())));
    }

    @Test
    void aFailedReadIsTriedAgainOnTheNextRefresh() {
        Model model = loaded();
        Cmd.LoadAhead load = AheadUpdate.ofHead(model);

        Model failed = Update.update(model, load.failed(refused("no"))).model();

        assertEquals(Option.none(), AheadUpdate.head(failed));
        assertTrue(Update.refresh(failed).cmds().contains(load));
    }

    @Test
    void togglingSwitchesBetweenTheWholeLogAndOnlyWhatIsNotOnTheBase() {
        Model only = Update.update(loaded(), new AheadMsg.Toggled()).model();

        assertTrue(only.repoView().onlyAhead());
        assertFalse(
                Update.update(only, new AheadMsg.Toggled()).model().repoView().onlyAhead());
    }
}
