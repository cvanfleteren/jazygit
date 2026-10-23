package net.vanfleteren.jazygit.state;

import static net.vanfleteren.jazygit.state.TestModels.*;
import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
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
import net.vanfleteren.jazygit.state.Update.Next;
import org.junit.jupiter.api.Test;

/**
 * The core state transitions: startup, periodic refresh and load failures.
 */
class UpdateTest {

    @Test
    void branchesThatCannotBeReadFailBothListsAsTheyAreReadTogether() {
        Model failed = Update.update(loaded(), new LoadMsg.Failed(new LoadBranches(), refused("boom")))
                .model();

        assertInstanceOf(Failed.class, failed.repo().branches());
        assertInstanceOf(Failed.class, failed.repo().remoteBranches());
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
    void theClockOnlyTellsTheTime() {
        Model model = loaded();
        Instant now = Instant.parse("2026-10-22T10:00:00Z");

        Next next = Update.update(model, new Msg.Clock(now));

        assertEquals(model.withNow(now), next.model());
        assertEquals(List.empty(), next.cmds());
        // A tick reads the repository again, but leaves the time to the clock.
        assertEquals(now, Update.update(next.model(), new Tick()).model().now());
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
        Model model = Update.update(loaded(), new LoadMsg.Failed(TestModels.loadCommits(), refused("boom")))
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
    void aRetryThatIsStillRunningIsNotStartedAgain() {
        Model failed = Update.update(loaded(), new LoadMsg.Failed(TestModels.loadCommits(), refused("boom")))
                .model();
        Model retrying = Update.update(failed, new Tick()).model();

        assertFalse(Update.update(retrying, new Tick()).cmds().contains(TestModels.loadCommits()));
        Model retried =
                Update.update(retrying, TestModels.commitsLoaded(COMMITS)).model();
        assertFalse(retried.jobs().refreshing().contains(TestModels.loadCommits()));
    }
}
