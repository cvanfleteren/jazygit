package net.vanfleteren.jazygit.state;

import static net.vanfleteren.jazygit.state.TestModels.*;
import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.Head;
import net.vanfleteren.jazygit.git.model.RepoStatus;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.settings.SettingsStore;
import net.vanfleteren.jazygit.state.Cmd.LoadBranches;
import net.vanfleteren.jazygit.state.Cmd.LoadStatus;
import net.vanfleteren.jazygit.state.Loadable.Failed;
import net.vanfleteren.jazygit.state.Loadable.Loaded;
import net.vanfleteren.jazygit.state.Loadable.Loading;
import net.vanfleteren.jazygit.state.Msg.Tick;
import net.vanfleteren.jazygit.state.Update.Launch;
import net.vanfleteren.jazygit.state.Update.Next;
import net.vanfleteren.jazygit.state.Update.Step;
import org.junit.jupiter.api.Test;

/**
 * The core state transitions: startup, periodic refresh and load failures.
 */
class UpdateTest {

    @Test
    void branchesThatCannotBeReadFailBothListsAsTheyAreReadTogether() {
        Model failed = Update.update(
                        Update.init("repo").model(), new LoadMsg.Failed(new LoadBranches(), refused("boom")))
                .model();

        assertInstanceOf(Failed.class, failed.repo().branches());
        assertInstanceOf(Failed.class, failed.repo().remoteBranches());
    }

    @Test
    void aFailedReadKeepsWhatWasReadBefore() {
        Model before = loaded();
        Model after = Update.update(before, new LoadMsg.Failed(new LoadBranches(), refused("boom")))
                .model();
        Model afterStatus = Update.update(after, new LoadMsg.Failed(new LoadStatus(), refused("boom")))
                .model();
        Model afterCommits = Update.update(afterStatus, new LoadMsg.Failed(TestModels.loadCommits(), refused("boom")))
                .model();

        assertEquals(before.repo(), afterCommits.repo());
    }

    @Test
    void initStartsLoadingStatusAndBranches() {
        Next next = Update.init("repo");

        assertEquals("repo", next.model().repositoryName());
        assertInstanceOf(Loading.class, next.model().repo().status());
        assertInstanceOf(Loading.class, next.model().repo().commits());
        assertEquals(
                List.of(
                        new LoadStatus(),
                        new LoadBranches(),
                        new net.vanfleteren.jazygit.state.Cmd.LoadStashes(),
                        new net.vanfleteren.jazygit.state.Cmd.LoadWorktrees()),
                next.cmds());
    }

    @Test
    void initShowsTheRepositoryWhereItLands() {
        Landing landing = new Landing(BranchesTab.WORKTREES, Option.of("branches"));

        Model started = Update.init("repo", SettingsStore.Loaded.DEFAULT, Option.none(), landing)
                .model();

        assertEquals(BranchesTab.WORKTREES, started.repoView().branchesTab());
        assertEquals(Option.of("branches"), started.shownPane());
        // Where the app starts: the local branches, and no pane yet, so the files get the focus.
        Model fresh = Update.init("repo").model();
        assertEquals(BranchesTab.LOCAL, fresh.repoView().branchesTab());
        assertEquals(Option.none(), fresh.shownPane());
    }

    @Test
    void firstStatusLoadsCommits() {
        Next next = Update.update(Update.init("repo").model(), new LoadMsg.StatusLoaded(CLEAN));

        assertEquals(new Loaded<>(CLEAN), next.model().repo().status());
        assertEquals(List.of(new Cmd.LoadCommits(AheadUpdate.ofHead(next.model()))), next.cmds());
    }

    @Test
    void tickDoesNotRepeatLoadsThatAreStillRunning() {
        Model model = Update.init("repo").model();

        // Started anew, the repository is read at once; a tick then only checks the files.
        assertEquals(
                List.of(new Cmd.CheckMetadata()),
                Update.update(model, new Tick()).cmds());

        Model statusDone = Update.update(model, new LoadMsg.StatusLoaded(CLEAN)).model();
        assertEquals(
                List.of(new LoadStatus(), new Cmd.CheckMetadata()),
                Update.update(statusDone, new Tick()).cmds());
    }

    @Test
    void everyMessageTellsTheTimeAndTheClockNothingElse() {
        Model model = loaded();
        Instant now = Instant.parse("2026-10-22T10:00:00Z");

        Step step = Update.step(model, new Msg.Clock(), now);

        assertEquals(model.withNow(now), step.model());
        assertEquals(List.empty(), step.launches());
    }

    @Test
    void theCommandsAskedForRunEachWithAnIdOfItsOwnFromTheTimeTheyWereAskedFor() {
        Model model = loaded();
        Instant now = Instant.parse("2026-10-22T10:00:00Z");
        List<Cmd> asked = Update.update(model.withNow(now), new Tick()).cmds();

        Step ticked = Update.step(model, new Tick(), now);

        assertEquals(asked, ticked.launches().map(Launch::cmd));
        assertEquals(List.range(0L, (long) asked.size()), ticked.launches().map(Launch::id));
        assertEquals(
                ticked.launches().map(launch -> new Jobs.Running(launch.cmd(), now)),
                ticked.launches()
                        .map(launch ->
                                ticked.model().jobs().running().get(launch.id()).get()));
    }

    @Test
    void anEqualCommandAskedForAgainGetsAnotherId() {
        Instant now = Instant.parse("2026-10-22T10:00:00Z");
        Step ticked = Update.step(loaded(), new Tick(), now);
        Model done = ticked.launches()
                .foldLeft(
                        ticked.model(),
                        (model, launch) -> Update.step(model, new Msg.Done(launch.id(), Option.none()), now)
                                .model())
                // What they reported would let the tick read them again.
                .mapJobs(jobs -> jobs.withRefreshing(HashSet.empty()));

        Step again = Update.step(done, new Tick(), now.plusSeconds(1));

        assertEquals(ticked.launches().map(Launch::cmd), again.launches().map(Launch::cmd));
        assertEquals(
                ticked.launches().map(launch -> launch.id() + ticked.launches().size()),
                again.launches().map(Launch::id));
    }

    @Test
    void aCommandThatIsDoneStopsRunningAndWhatItReportedIsHandled() {
        Model model = loaded();
        Instant now = Instant.parse("2026-10-22T10:00:00Z");
        Step ticked = Update.step(model, new Tick(), now);
        Launch status = ticked.launches()
                .find(launch -> launch.cmd() instanceof LoadStatus)
                .get();
        RepoStatus changed = new RepoStatus(Head.onBranch(new BranchName("main"), new Sha("bbbb")), List.of());

        Step done = Update.step(
                ticked.model(), new Msg.Done(status.id(), Option.of(new LoadMsg.StatusLoaded(changed))), now);

        assertFalse(done.model().jobs().running().containsKey(status.id()));
        assertEquals(new Loaded<>(changed), done.model().repo().status());
    }

    @Test
    void aCommandThatIsDoneWithoutReportingOnlyStopsRunning() {
        Model model = loaded();
        Instant now = Instant.parse("2026-10-22T10:00:00Z");
        Step ticked = Update.step(model, new Tick(), now);
        Launch first = ticked.launches().head();

        Step done = Update.step(ticked.model(), new Msg.Done(first.id(), Option.none()), now);

        assertEquals(
                ticked.model().mapJobs(jobs -> jobs.withRunning(jobs.running().remove(first.id()))), done.model());
        assertEquals(List.empty(), done.launches());
    }

    @Test
    void aCommandThatShowedLingersOnceItIsDoneAndOneThatDidNotDoesNot() {
        Instant now = Instant.parse("2026-10-22T10:00:00Z");
        Step ticked = Update.step(loaded(), new Tick(), now);
        Launch first = ticked.launches().head();
        Launch second = ticked.launches().last();

        Model quick = Update.step(ticked.model(), new Msg.Done(first.id(), Option.none()), now.plusMillis(299))
                .model();
        Model slow = Update.step(quick, new Msg.Done(second.id(), Option.none()), now.plusSeconds(1))
                .model();

        assertEquals(Option.none(), quick.jobs().lingering());
        assertEquals(
                Option.of(new Jobs.Lingering(
                        second.cmd().label().get(), now.plusSeconds(1).plus(Jobs.LINGER))),
                slow.jobs().lingering());
    }

    @Test
    void theFirstLoadsRunFromTheStart() {
        Instant now = Instant.parse("2026-10-22T10:00:00Z");

        Step begun = Update.begin("repo", SettingsStore.Loaded.DEFAULT, Option.none(), Landing.START, now);

        assertEquals(now, begun.model().now());
        assertEquals(Update.init("repo").cmds(), begun.launches().map(Launch::cmd));
        assertTrue(begun.model()
                .jobs()
                .running()
                .values()
                .forAll(running -> running.started().equals(now)));
        assertEquals(begun.launches().size(), begun.model().jobs().running().size());
    }

    @Test
    void unchangedStatusKeepsTheSameInstanceAndDoesNotReloadCommits() {
        Model model = loaded();

        Next next = Update.update(
                model,
                new LoadMsg.StatusLoaded(
                        new RepoStatus(Head.onBranch(new BranchName("main"), new Sha("aaaa")), List.of())));

        assertSame(model.repo().status(), next.model().repo().status());
        assertEquals(List.of(), next.cmds());
    }

    @Test
    void workingTreeChangeReplacesStatusButNotCommits() {
        Model model = loaded();

        Next next = Update.update(model, new LoadMsg.StatusLoaded(DIRTY));

        assertEquals(new Loaded<>(DIRTY), next.model().repo().status());
        assertSame(model.repo().commits(), next.model().repo().commits());
        assertEquals(List.of(), next.cmds());
    }

    @Test
    void movedHeadReloadsCommits() {
        Next next = Update.update(loaded(), new LoadMsg.StatusLoaded(MOVED));

        assertEquals(List.of(TestModels.loadCommits()), next.cmds());
    }

    @Test
    void unchangedBranchesAndCommitsKeepTheirInstances() {
        Model model = loaded();

        assertSame(
                model.repo().branches(),
                localBranchesRead(model, BRANCHES).model().repo().branches());
        assertSame(
                model.repo().commits(),
                Update.update(model, TestModels.commitsLoaded(COMMITS))
                        .model()
                        .repo()
                        .commits());
    }

    @Test
    void failuresAreStoredAndRetriedOnTheNextTick() {
        Model model = Update.update(
                        loaded().mapRepo(repo -> repo.withCommits(Loadable.loading())),
                        new LoadMsg.Failed(TestModels.loadCommits(), refused("boom")))
                .model();
        assertEquals(new Failed<>(refused("boom")), model.repo().commits());
        assertEquals(
                List.of(new LoadStatus(), new Cmd.CheckMetadata(), TestModels.loadCommits()),
                Update.update(model, new Tick()).cmds());

        Model statusFailed = Update.update(
                        Update.init("repo").model(), new LoadMsg.Failed(new LoadStatus(), refused("no git")))
                .model();
        assertEquals(new Failed<>(refused("no git")), statusFailed.repo().status());
        assertEquals(
                List.of(new LoadStatus(), new Cmd.CheckMetadata()),
                Update.update(statusFailed, new Tick()).cmds());
    }

    @Test
    void aFailedLogReadIsRetriedAlthoughTheOldLogStays() {
        Model before = loaded();
        Model failed = Update.update(before, new LoadMsg.Failed(TestModels.loadCommits(), refused("boom")))
                .model();
        assertEquals(before.repo().commits(), failed.repo().commits());
        assertTrue(Update.update(failed, new Tick()).cmds().contains(TestModels.loadCommits()));

        Model retried = Update.update(
                        Update.update(failed, new Tick()).model(),
                        new LoadMsg.CommitsLoaded(
                                before.repo().commits().getOrElse(List.empty()),
                                TestModels.loadCommits().ahead(),
                                Either.right(Option.none())))
                .model();
        assertFalse(Update.update(retried, new Tick()).cmds().contains(TestModels.loadCommits()));
    }

    @Test
    void aRetryThatIsStillRunningIsNotStartedAgain() {
        Model failed = Update.update(loaded(), new LoadMsg.Failed(TestModels.loadCommits(), refused("boom")))
                .model();
        Model retrying = Update.update(failed, new Tick()).model();

        assertFalse(Update.update(retrying, new Tick()).cmds().contains(TestModels.loadCommits()));
        Model retried =
                Update.update(retrying, TestModels.commitsLoaded(COMMITS)).model();
        assertFalse(retried.jobs().refreshing().contains(TestModels.loadCommits()));
    }

    @Test
    void confirmingAnotherPopupLeavesTheOpenOneAlone() {
        Model asking = loaded().openPopup(new OtherPopup());

        Next next = Update.update(asking, new Msg.PopupConfirmed(EmptyPopup.class));

        assertEquals(List.of(), next.cmds());
        assertEquals(asking.popup(), next.model().popup());
    }

    @Test
    void aPopupThatAsksAboutNothingAsItStandsStaysOpen() {
        Model asking = loaded().openPopup(new EmptyPopup());

        Next next = Update.update(asking, new Msg.PopupConfirmed(EmptyPopup.class));

        assertEquals(List.of(), next.cmds());
        assertEquals(asking.popup(), next.model().popup());
    }

    // A popup whose confirmation starts nothing, as one does with what was entered left blank.
    private record EmptyPopup() implements ConfirmablePopup {

        @Override
        public Option<OperationCmd> confirm() {
            return Option.none();
        }
    }

    private record OtherPopup() implements Popup {}
}
