package net.vanfleteren.jazygit.state;

import static net.vanfleteren.jazygit.state.TestModels.*;
import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.git.model.*;
import org.junit.jupiter.api.Test;

/**
 * Which commits of HEAD and of the highlighted branch are not on their base: what the base is, and when that is read.
 */
class AheadUpdateTest {

    private static final BranchName MAIN = new BranchName("main");
    private static final BranchName FEATURE = new BranchName("feature");
    private static final BranchRef FEATURE_LOG = new BranchRef.Local(FEATURE);

    private static BranchRef origin(String name) {
        return new BranchRef.Remote(new RemoteBranch(RemoteBranch.DEFAULT_REMOTE, new BranchName(name)));
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

    /**
     * {@code model} with the local branches {@code main}, the main branch, and {@code feature}, and {@code remote} as
     * the remote-tracking ones, as last read; and {@code parents} as the parents read last.
     */
    private static Model withBranches(
            Model model, List<RemoteRef> remote, io.vavr.collection.Map<BranchName, BranchName> parents) {
        return model.mapRepo(repo -> repo.withBranches(repo.branches()
                        .reload(List.of(
                                Fixtures.branch(MAIN, true, HEAD, Instant.EPOCH, false, true),
                                Fixtures.branch(FEATURE, false, FEATURE_TIP),
                                Fixtures.branch(new BranchName("parent"), false, new Sha("2222")))))
                .withRemoteBranches(repo.remoteBranches().reload(remote))
                .withParents(parents));
    }

    private static RemoteRef remote(String name, Sha tip) {
        return new RemoteRef(new RemoteBranch(RemoteBranch.DEFAULT_REMOTE, new BranchName(name)), tip, Instant.EPOCH);
    }

    /**
     * {@code model} with {@code main}, the main branch and checked out, at {@code main}, and {@code feature} at
     * {@code feature}.
     */
    private static Model withMain(Model model, Sha main, Sha feature) {
        return model.mapRepo(repo -> repo.withBranches(repo.branches().reload(mainAnd(main, feature))));
    }

    private static List<Branch> mainAnd(Sha main, Sha feature) {
        return List.of(
                Fixtures.branch(MAIN, true, main, Instant.EPOCH, false, true),
                Fixtures.branch(FEATURE, false, feature));
    }

    @Test
    void theBaseIsTheParentElseTheMainBranchTheRemoteOneFirstWhereTheBranchesReadHaveIt() {
        io.vavr.collection.Map<BranchName, BranchName> parented =
                io.vavr.collection.HashMap.of(FEATURE, new BranchName("parent"));
        List<RemoteRef> both = List.of(remote("parent", new Sha("3333")), remote("main", new Sha("1111")));

        assertEquals(
                Option.of(new Cmd.LoadAhead.Base(origin("parent"), new Sha("3333"))),
                AheadUpdate.of(withBranches(loaded(), both, parented), FEATURE_LOG)
                        .base());
        assertEquals(
                Option.of(new Cmd.LoadAhead.Base(new BranchRef.Local(new BranchName("parent")), new Sha("2222"))),
                AheadUpdate.of(withBranches(loaded(), List.empty(), parented), FEATURE_LOG)
                        .base());
        assertEquals(
                Option.of(new Cmd.LoadAhead.Base(origin("main"), new Sha("1111"))),
                AheadUpdate.of(withBranches(loaded(), both, io.vavr.collection.HashMap.empty()), FEATURE_LOG)
                        .base());
        assertEquals(
                Option.of(new Cmd.LoadAhead.Base(new BranchRef.Local(MAIN), HEAD)),
                AheadUpdate.of(withBranches(loaded(), List.empty(), io.vavr.collection.HashMap.empty()), FEATURE_LOG)
                        .base());
    }

    @Test
    void aBranchIsComparedFromWhereTheBranchesReadHaveIt() {
        Model model =
                withBranches(loaded(), List.of(remote("feature", new Sha("4444"))), io.vavr.collection.HashMap.empty());

        assertEquals(Option.of(FEATURE_TIP), AheadUpdate.of(model, FEATURE_LOG).tip());
        assertEquals(
                Option.of(new Sha("4444")),
                AheadUpdate.of(model, origin("feature")).tip());
        assertEquals(Option.none(), AheadUpdate.of(model, origin("gone")).tip());
    }

    @Test
    void aBranchIsNoBaseOfItself() {
        Model model =
                withBranches(loaded(), List.of(remote("main", new Sha("1111"))), io.vavr.collection.HashMap.empty());

        assertEquals(Option.none(), AheadUpdate.of(model, origin("main")).base());
        assertEquals(Option.none(), AheadUpdate.ofHead(model).base());
    }

    @Test
    void headIsComparedFromWhereTheStatusHasItADetachedOneWithTheMainBranch() {
        Model detached = loaded().mapRepo(repo -> repo.withStatus(
                        repo.status().reload(new RepoStatus(new Head.Detached(FEATURE_TIP), List.empty())))
                .withBranches(repo.branches()
                        .reload(List.of(
                                Fixtures.branch(MAIN, false, HEAD, Instant.EPOCH, false, true),
                                Fixtures.branch(FEATURE, false, FEATURE_TIP)))));

        assertEquals(Option.of(FEATURE_TIP), AheadUpdate.ofHead(detached).tip());
        assertEquals(
                Option.of(new Cmd.LoadAhead.Base(new BranchRef.Local(MAIN), HEAD)),
                AheadUpdate.ofHead(detached).base());
    }

    @Test
    void withoutABaseOrACommitNothingIsRead() {
        assertEquals(
                Either.right(Option.none()),
                new Cmd.LoadAhead(Option.none(), Option.of(HEAD), Option.none()).read(null));
        assertEquals(
                Either.right(Option.none()),
                new Cmd.LoadAhead(Option.none(), Option.none(), Option.of(new Cmd.LoadAhead.Base(origin("main"), HEAD)))
                        .read(null));
    }

    @Test
    void theCommitsAheadOfAnOlderHeadAreReadAgainWhenTheyArriveAfterTheLog() {
        // The branches arrive first and ask for the commits ahead of HEAD where the status had it; the status then
        // moves HEAD, and the log of it is read before those commits ahead arrive.
        Model before = withBranches(loaded(), List.empty(), io.vavr.collection.HashMap.empty());
        Cmd.LoadAhead old = AheadUpdate.ofHead(before);
        Model moved = Update.update(before, new LoadMsg.StatusLoaded(MOVED)).model();
        Cmd.LoadAhead current = AheadUpdate.ofHead(moved);
        List<Commit> log =
                List.of(Fixtures.commit(new Sha("bbbb"), "Ada", "ada@example.com", Instant.EPOCH, "Second", ""));
        Model logRead = Update.update(moved, new LoadMsg.CommitsLoaded(log, current, Either.right(Option.none())))
                .model();

        LoadMsg.AheadLoaded stale = new LoadMsg.AheadLoaded(old, Option.of(ahead(HEAD, new BranchRef.Local(MAIN))));
        Update.Next late = Update.update(logRead, stale);

        assertNotEquals(old, current);
        assertEquals(List.of(current), late.cmds());
        // What was read for the old HEAD is not kept, not even for a moment.
        assertEquals(logRead.repo().ahead(), late.model().repo().ahead());
        // When the load of now runs already, it is not started twice.
        Model running = logRead.mapJobs(jobs -> jobs.withStarted(List.of(current), Instant.EPOCH));
        assertEquals(List.empty(), Update.update(running, stale).cmds());
        // Nor when it is read with the log of HEAD, or with that of a branch.
        Model withLog = logRead.mapJobs(jobs -> jobs.withStarted(List.of(new Cmd.LoadCommits(current)), Instant.EPOCH));
        assertEquals(List.empty(), Update.update(withLog, stale).cmds());
        Model withBranchLog = logRead.mapJobs(
                jobs -> jobs.withStarted(List.of(new Cmd.LoadBranchLog(FEATURE_LOG, current)), Instant.EPOCH));
        assertEquals(List.empty(), Update.update(withBranchLog, stale).cmds());
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
        Model model = Update.update(
                        loaded().mapRepo(repo -> repo.withAhead(Loadable.loading())),
                        new LoadMsg.CommitsLoaded(COMMITS, load, Either.left(refused("boom"))))
                .model();

        assertEquals(Option.of(COMMITS), model.repo().commits().toOption());
        assertInstanceOf(Loadable.Failed.class, model.repo().ahead());
        assertTrue(Update.update(model, new Msg.Tick()).cmds().contains(load));
    }

    @Test
    void whatIsReadWithTheLogOfHeadIsReadAgainWhenTheBranchesArrivedMeanwhile() {
        // At the start the status arrives first: the log is asked for before the branches tell its base.
        Update.Next started = Update.update(
                Update.init("repo").model(),
                new LoadMsg.StatusLoaded(new RepoStatus(Head.onBranch(FEATURE, FEATURE_TIP), List.empty())));
        Cmd.LoadAhead issued = ((Cmd.LoadCommits) started.cmds().single()).ahead();
        Model branchesRead = Update.update(
                        started.model(),
                        new LoadMsg.BranchesLoaded(new AllBranches(
                                List.of(
                                        Fixtures.branch(MAIN, false, HEAD, Instant.EPOCH, false, true),
                                        Fixtures.branch(FEATURE, true, FEATURE_TIP)),
                                List.empty())))
                .model();

        Update.Next logRead = Update.update(
                branchesRead, new LoadMsg.CommitsLoaded(FEATURE_COMMITS, issued, Either.right(Option.none())));

        Cmd.LoadAhead current = AheadUpdate.ofHead(logRead.model());
        assertNotEquals(issued, current);
        assertTrue(logRead.cmds().contains(current));
        Ahead ahead = ahead(FEATURE_TIP, new BranchRef.Local(MAIN));
        Model read = Update.update(logRead.model(), new LoadMsg.AheadLoaded(current, Option.of(ahead)))
                .model();
        assertEquals(Option.of(ahead), AheadUpdate.head(read));
    }

    @Test
    void whatIsReadWithTheLogOfABranchIsReadAgainWhenTheMainBranchChangedMeanwhile() {
        Update.Next selected = Update.update(loaded(), new SelectionMsg.BranchSelected(FEATURE_LOG));
        Cmd.LoadAhead issued = ((Cmd.LoadBranchLog) selected.cmds().single()).ahead();
        Model mainKnown = localBranchesRead(
                        selected.model(),
                        List.of(
                                Fixtures.branch(MAIN, true, HEAD, Instant.EPOCH, false, true),
                                Fixtures.branch(FEATURE, false, FEATURE_TIP)))
                .model();

        Update.Next logRead = Update.update(
                mainKnown,
                new LoadMsg.BranchLogLoaded(FEATURE_LOG, FEATURE_COMMITS, issued, Either.right(Option.none())));

        Cmd.LoadAhead current = AheadUpdate.of(logRead.model(), FEATURE_LOG);
        assertNotEquals(issued, current);
        assertTrue(logRead.cmds().contains(current));
        Model read = Update.update(
                        logRead.model(),
                        new LoadMsg.AheadLoaded(current, Option.of(ahead(FEATURE_TIP, origin("main")))))
                .model();
        assertEquals(Option.of(ahead(FEATURE_TIP, origin("main"))), AheadUpdate.branch(read, FEATURE_LOG));
    }

    @Test
    void theCommitsAheadAreReadAgainWhenTheParentsTellAnotherBase() {
        Model model = featureLogRead();
        BranchName head = model.repo().currentBranch().get().name();
        Cmd.LoadBaseDistance load = LoadUpdate.baseDistance(model);

        Update.Next read = Update.update(
                model,
                new LoadMsg.BaseDistanceLoaded(
                        load,
                        io.vavr.collection.HashMap.of(head, FEATURE, FEATURE, MAIN),
                        io.vavr.collection.HashMap.empty()));

        assertEquals(
                Option.of(new Cmd.LoadAhead.Base(new BranchRef.Local(FEATURE), FEATURE_TIP)),
                AheadUpdate.ofHead(read.model()).base());
        assertEquals(
                Option.of(new Cmd.LoadAhead.Base(new BranchRef.Local(MAIN), HEAD)),
                AheadUpdate.of(read.model(), FEATURE_LOG).base());
        assertTrue(read.cmds().contains(AheadUpdate.ofHead(read.model())));
        assertTrue(read.cmds().contains(AheadUpdate.of(read.model(), FEATURE_LOG)));

        // The same parents again change no base.
        assertEquals(
                List.empty(),
                Update.update(
                                read.model(),
                                new LoadMsg.BaseDistanceLoaded(
                                        load, read.model().repo().parents(), io.vavr.collection.HashMap.empty()))
                        .cmds());
    }

    @Test
    void aFailedReadOfAheadIsRetriedAlthoughWhatWasReadStays() {
        Cmd.LoadAhead load = AheadUpdate.ofHead(loaded());
        Model failed = Update.update(loaded(), new LoadMsg.Failed(load, refused("boom")))
                .model();
        assertEquals(loaded().repo().ahead(), failed.repo().ahead());
        assertTrue(Update.refresh(failed).cmds().contains(load));
    }

    @Test
    void aRetryThatCameBackIsNoLongerRunning() {
        Cmd.LoadAhead load = AheadUpdate.ofHead(loaded());
        Model failed = Update.update(
                        loaded().mapRepo(repo -> repo.withAhead(Loadable.loading())),
                        new LoadMsg.Failed(load, refused("boom")))
                .model();
        Model retrying = TestModels.stepped(failed, new Msg.Tick()).model();
        assertTrue(retrying.jobs().runs(load));

        Model retried = TestModels.done(
                        retrying, load, new LoadMsg.AheadLoaded(load, Option.of(ahead(HEAD, origin("main")))))
                .model();
        assertFalse(retried.jobs().runs(load));
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
        Model logRead = withMain(featureLogRead(), HEAD, FEATURE_TIP);
        Cmd.LoadAhead load = AheadUpdate.of(logRead, FEATURE_LOG);
        Model read = Update.update(
                        logRead,
                        new LoadMsg.AheadLoaded(load, Option.of(ahead(FEATURE_TIP, new BranchRef.Local(MAIN)))))
                .model();

        Update.Next baseMoved = localBranchesRead(read, mainAnd(new Sha("cccc"), FEATURE_TIP));
        Update.Next bothMoved = localBranchesRead(read, mainAnd(new Sha("cccc"), new Sha("eeee")));

        Cmd.LoadAhead moved = AheadUpdate.of(baseMoved.model(), FEATURE_LOG);
        assertEquals(Option.of(new Cmd.LoadAhead.Base(new BranchRef.Local(MAIN), new Sha("cccc"))), moved.base());
        assertTrue(baseMoved.cmds().contains(moved), baseMoved.cmds().toString());
        // Its log is read again, and what is not on its base after it.
        assertEquals(
                List.of(new Cmd.LoadBranchLog(FEATURE_LOG, AheadUpdate.of(bothMoved.model(), FEATURE_LOG))),
                bothMoved.cmds());
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
        // main was made from feature.
        Model model = loaded().mapRepo(repo -> repo.withParents(io.vavr.collection.HashMap.of(MAIN, FEATURE)));
        Cmd.LoadAhead load = AheadUpdate.ofHead(model);
        Model read = Update.update(
                        model, new LoadMsg.AheadLoaded(load, Option.of(ahead(HEAD, new BranchRef.Local(FEATURE)))))
                .model();

        Update.Next moved = localBranchesRead(
                read,
                List.of(
                        Fixtures.branch(MAIN, true, new Sha("aaaa")),
                        Fixtures.branch(FEATURE, false, new Sha("eeee"))));
        Update.Next still = localBranchesRead(read, BRANCHES);

        assertEquals(Option.of(new Cmd.LoadAhead.Base(new BranchRef.Local(FEATURE), FEATURE_TIP)), load.base());
        assertTrue(moved.cmds().contains(AheadUpdate.ofHead(moved.model())));
        assertFalse(still.cmds().exists(Cmd.LoadAhead.class::isInstance));
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
                moved,
                List.of(
                        Fixtures.branch(MAIN, false, new Sha("aaaa")),
                        Fixtures.branch(FEATURE, true, new Sha("bbbb"))));
        assertFalse(
                checkedOut.cmds().exists(Cmd.LoadAhead.class::isInstance),
                checkedOut.cmds().toString());

        // The highlighted branch moved: its log is read again, and what is not on its base after it.
        Model selected = Update.update(loaded(), new SelectionMsg.BranchSelected(FEATURE_LOG))
                .model();
        Model logRead = Update.update(selected, TestModels.branchLogLoaded(FEATURE_LOG, FEATURE_COMMITS))
                .model();
        Update.Next branchMoved = localBranchesRead(
                logRead, List.of(Fixtures.branch(MAIN, true, HEAD), Fixtures.branch(FEATURE, false, new Sha("eeee"))));
        assertEquals(
                List.of(new Cmd.LoadBranchLog(FEATURE_LOG, AheadUpdate.of(branchMoved.model(), FEATURE_LOG))),
                branchMoved.cmds());
    }

    @Test
    void checkingOutABranchWhereHeadIsReadsWhatIsNotOnItsBase() {
        Model model = withMain(loaded(), HEAD, FEATURE_TIP);

        Update.Next checkedOut = localBranchesRead(
                model,
                List.of(
                        Fixtures.branch(MAIN, false, HEAD, Instant.EPOCH, false, true),
                        Fixtures.branch(FEATURE, true, HEAD)));

        assertEquals(
                Option.of(new Cmd.LoadAhead.Base(new BranchRef.Local(MAIN), HEAD)),
                AheadUpdate.ofHead(checkedOut.model()).base());
        assertTrue(checkedOut.cmds().contains(AheadUpdate.ofHead(checkedOut.model())));
    }

    @Test
    void aFailedReadIsTriedAgainOnTheNextRefresh() {
        Model model = loaded().mapRepo(repo -> repo.withAhead(Loadable.loading()));
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
