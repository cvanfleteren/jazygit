package net.vanfleteren.jazygit.state;

import static net.vanfleteren.jazygit.state.TestModels.*;
import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.RepoStatus;
import net.vanfleteren.jazygit.git.model.RepoStatus.Head;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.settings.SettingsStore;
import net.vanfleteren.jazygit.state.Cmd.LoadBranches;
import net.vanfleteren.jazygit.state.Cmd.LoadCommits;
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
        assertEquals(List.of(new LoadCommits()), next.cmds());
    }

    @Test
    void tickDoesNotRepeatLoadsThatAreStillRunning() {
        Model model = Update.init("repo").model();

        assertEquals(List.of(), Update.update(model, new Tick(Instant.EPOCH)).cmds());

        Model statusDone = Update.update(model, new LoadMsg.StatusLoaded(CLEAN)).model();
        assertEquals(
                List.of(new LoadStatus()),
                Update.update(statusDone, new Tick(Instant.EPOCH)).cmds());
    }

    @Test
    void unchangedStatusKeepsTheSameInstanceAndDoesNotReloadCommits() {
        Model model = loaded();

        Next next = Update.update(
                model,
                new LoadMsg.StatusLoaded(
                        new RepoStatus(Head.onBranch(new Branch.Name("main"), new Sha("aaaa")), List.of())));

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

        assertEquals(List.of(new LoadCommits()), next.cmds());
    }

    @Test
    void unchangedBranchesAndCommitsKeepTheirInstances() {
        Model model = loaded();

        assertSame(
                model.repo().branches(),
                localBranchesRead(model, BRANCHES).model().repo().branches());
        assertSame(
                model.repo().commits(),
                Update.update(model, new LoadMsg.CommitsLoaded(COMMITS))
                        .model()
                        .repo()
                        .commits());
    }

    @Test
    void failuresAreStoredAndRetriedOnTheNextTick() {
        Model model = Update.update(loaded(), new LoadMsg.Failed(new LoadCommits(), refused("boom")))
                .model();
        assertEquals(new Failed<>(refused("boom")), model.repo().commits());
        assertEquals(
                List.of(
                        new LoadStatus(),
                        new LoadBranches(),
                        new net.vanfleteren.jazygit.state.Cmd.LoadStashes(),
                        new LoadCommits()),
                Update.update(model, new Tick(Instant.EPOCH)).cmds());

        Model statusFailed = Update.update(
                        Update.init("repo").model(), new LoadMsg.Failed(new LoadStatus(), refused("no git")))
                .model();
        assertEquals(new Failed<>(refused("no git")), statusFailed.repo().status());
        assertEquals(
                List.of(new LoadStatus()),
                Update.update(statusFailed, new Tick(Instant.EPOCH)).cmds());
    }

    @Test
    void aRetryThatIsStillRunningIsNotStartedAgain() {
        Model failed = Update.update(loaded(), new LoadMsg.Failed(new LoadCommits(), refused("boom")))
                .model();
        Model retrying = Update.update(failed, new Tick(Instant.EPOCH)).model();

        assertFalse(Update.update(retrying, new Tick(Instant.EPOCH)).cmds().contains(new LoadCommits()));
        Model retried =
                Update.update(retrying, new LoadMsg.CommitsLoaded(COMMITS)).model();
        assertFalse(retried.jobs().refreshing().contains(new LoadCommits()));
    }
}
