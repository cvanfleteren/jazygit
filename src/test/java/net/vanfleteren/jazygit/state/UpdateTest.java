package net.vanfleteren.jazygit.state;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.model.RepoStatus.Head;
import net.vanfleteren.jazygit.git.model.RepoStatus;
import net.vanfleteren.jazygit.state.Cmd.LoadBranches;
import net.vanfleteren.jazygit.state.Cmd.LoadCommits;
import net.vanfleteren.jazygit.state.Cmd.LoadStatus;
import net.vanfleteren.jazygit.state.Loadable.Failed;
import net.vanfleteren.jazygit.state.Loadable.Loaded;
import net.vanfleteren.jazygit.state.Loadable.Loading;
import net.vanfleteren.jazygit.state.Msg.Tick;
import net.vanfleteren.jazygit.state.Update.Next;
import org.junit.jupiter.api.Test;

import static net.vanfleteren.jazygit.state.TestModels.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * The core state transitions: startup, periodic refresh and load failures.
 */
class UpdateTest {

    @Test
    void initStartsLoadingStatusAndBranches() {
        Next next = Update.init("repo");

        assertEquals("repo", next.model().repositoryName());
        assertInstanceOf(Loading.class, next.model().status());
        assertInstanceOf(Loading.class, next.model().commits());
        assertEquals(List.of(new LoadStatus(), new LoadBranches(),
                new net.vanfleteren.jazygit.state.Cmd.LoadRemoteBranches(), new net.vanfleteren.jazygit.state.Cmd.LoadStashes()), next.cmds());
    }

    @Test
    void firstStatusLoadsCommits() {
        Next next = Update.update(Update.init("repo").model(), new LoadMsg.StatusLoaded(CLEAN));

        assertEquals(new Loaded<>(CLEAN), next.model().status());
        assertEquals(List.of(new LoadCommits()), next.cmds());
    }

    @Test
    void tickDoesNotRepeatLoadsThatAreStillRunning() {
        Model model = Update.init("repo").model();

        assertEquals(List.of(), Update.update(model, new Tick()).cmds());

        Model statusDone = Update.update(model, new LoadMsg.StatusLoaded(CLEAN)).model();
        assertEquals(List.of(new LoadStatus()), Update.update(statusDone, new Tick()).cmds());
    }

    @Test
    void unchangedStatusKeepsTheSameInstanceAndDoesNotReloadCommits() {
        Model model = loaded();

        Next next = Update.update(model, new LoadMsg.StatusLoaded(new RepoStatus(Head.onBranch("main", new Sha("aaaa")), List.of())));

        assertSame(model.status(), next.model().status());
        assertEquals(List.of(), next.cmds());
    }

    @Test
    void workingTreeChangeReplacesStatusButNotCommits() {
        Model model = loaded();

        Next next = Update.update(model, new LoadMsg.StatusLoaded(DIRTY));

        assertEquals(new Loaded<>(DIRTY), next.model().status());
        assertSame(model.commits(), next.model().commits());
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

        assertSame(model.branches(),
                Update.update(model, new LoadMsg.BranchesLoaded(BRANCHES)).model().branches());
        assertSame(model.commits(),
                Update.update(model, new LoadMsg.CommitsLoaded(COMMITS)).model().commits());
    }

    @Test
    void failuresAreStoredAndRetriedOnTheNextTick() {
        Model model = Update.update(loaded(), new LoadMsg.Failed(new LoadCommits(), refused("boom"))).model();
        assertEquals(new Failed<>(refused("boom")), model.commits());
        assertEquals(List.of(new LoadStatus(), new LoadBranches(), new net.vanfleteren.jazygit.state.Cmd.LoadRemoteBranches(), new net.vanfleteren.jazygit.state.Cmd.LoadStashes(), new LoadCommits()),
                Update.update(model, new Tick()).cmds());

        Model statusFailed = Update.update(Update.init("repo").model(), new LoadMsg.Failed(new LoadStatus(), refused("no git")))
                .model();
        assertEquals(new Failed<>(refused("no git")), statusFailed.status());
        assertEquals(List.of(new LoadStatus()), Update.update(statusFailed, new Tick()).cmds());
    }

    @Test
    void aRetryThatIsStillRunningIsNotStartedAgain() {
        Model failed = Update.update(loaded(), new LoadMsg.Failed(new LoadCommits(), refused("boom"))).model();
        Model retrying = Update.update(failed, new Tick()).model();

        assertEquals(false, Update.update(retrying, new Tick()).cmds().contains(new LoadCommits()));
        Model retried = Update.update(retrying, new LoadMsg.CommitsLoaded(COMMITS)).model();
        assertEquals(false, retried.refreshing().contains(new LoadCommits()));
    }
}
