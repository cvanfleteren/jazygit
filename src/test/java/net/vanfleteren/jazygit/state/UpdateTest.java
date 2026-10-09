package net.vanfleteren.jazygit.state;

import net.vanfleteren.jazygit.model.Branch;
import net.vanfleteren.jazygit.model.ChangeType;
import net.vanfleteren.jazygit.model.Commit;
import net.vanfleteren.jazygit.model.FileEntry;
import net.vanfleteren.jazygit.model.RepoStatus;
import net.vanfleteren.jazygit.state.Cmd.LoadBranches;
import net.vanfleteren.jazygit.state.Cmd.LoadCommits;
import net.vanfleteren.jazygit.state.Cmd.LoadStatus;
import net.vanfleteren.jazygit.state.Loadable.Failed;
import net.vanfleteren.jazygit.state.Loadable.Loaded;
import net.vanfleteren.jazygit.state.Loadable.Loading;
import net.vanfleteren.jazygit.state.Msg.BranchesLoaded;
import net.vanfleteren.jazygit.state.Msg.CommitsLoaded;
import net.vanfleteren.jazygit.state.Msg.LoadFailed;
import net.vanfleteren.jazygit.state.Msg.StatusLoaded;
import net.vanfleteren.jazygit.state.Msg.Tick;
import net.vanfleteren.jazygit.state.Update.Next;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * The state transitions, tested as plain functions: no git, no threads, no terminal.
 */
class UpdateTest {

    private static final RepoStatus CLEAN = new RepoStatus("main", "aaaa", List.of());
    private static final RepoStatus DIRTY = new RepoStatus("main", "aaaa",
            List.of(new FileEntry("a.txt", ChangeType.UNTRACKED)));
    private static final RepoStatus MOVED = new RepoStatus("main", "bbbb", List.of());
    private static final List<Branch> BRANCHES = List.of(new Branch("main", true));
    private static final List<Commit> COMMITS = List.of(new Commit("aaaa", "Ada", "2026-10-09", "First", "First"));

    @Test
    void initStartsLoadingStatusAndBranches() {
        Next next = Update.init("repo");

        assertEquals("repo", next.model().repositoryName());
        assertInstanceOf(Loading.class, next.model().status());
        assertInstanceOf(Loading.class, next.model().commits());
        assertEquals(List.of(new LoadStatus(), new LoadBranches()), next.cmds());
    }

    @Test
    void firstStatusLoadsCommits() {
        Next next = Update.update(Update.init("repo").model(), new StatusLoaded(CLEAN));

        assertEquals(new Loaded<>(CLEAN), next.model().status());
        assertEquals(List.of(new LoadCommits()), next.cmds());
    }

    @Test
    void tickDoesNotRepeatLoadsThatAreStillRunning() {
        Model model = Update.init("repo").model();

        assertEquals(List.of(), Update.update(model, new Tick()).cmds());

        Model statusDone = Update.update(model, new StatusLoaded(CLEAN)).model();
        assertEquals(List.of(new LoadStatus()), Update.update(statusDone, new Tick()).cmds());
    }

    @Test
    void unchangedStatusKeepsTheSameInstanceAndDoesNotReloadCommits() {
        Model model = loaded();

        Next next = Update.update(model, new StatusLoaded(new RepoStatus("main", "aaaa", List.of())));

        assertSame(model.status(), next.model().status());
        assertEquals(List.of(), next.cmds());
    }

    @Test
    void workingTreeChangeReplacesStatusButNotCommits() {
        Model model = loaded();

        Next next = Update.update(model, new StatusLoaded(DIRTY));

        assertEquals(new Loaded<>(DIRTY), next.model().status());
        assertSame(model.commits(), next.model().commits());
        assertEquals(List.of(), next.cmds());
    }

    @Test
    void movedHeadReloadsCommits() {
        Next next = Update.update(loaded(), new StatusLoaded(MOVED));

        assertEquals(List.of(new LoadCommits()), next.cmds());
    }

    @Test
    void unchangedBranchesAndCommitsKeepTheirInstances() {
        Model model = loaded();

        assertSame(model.branches(),
                Update.update(model, new BranchesLoaded(List.copyOf(BRANCHES))).model().branches());
        assertSame(model.commits(),
                Update.update(model, new CommitsLoaded(List.copyOf(COMMITS))).model().commits());
    }

    @Test
    void failuresAreStoredAndRetriedOnTheNextTick() {
        Model model = Update.update(loaded(), new LoadFailed(new LoadCommits(), "boom")).model();
        assertEquals(new Failed<>("boom"), model.commits());
        assertEquals(List.of(new LoadStatus(), new LoadBranches(), new LoadCommits()),
                Update.update(model, new Tick()).cmds());

        Model statusFailed = Update.update(Update.init("repo").model(), new LoadFailed(new LoadStatus(), "no git"))
                .model();
        assertEquals(new Failed<>("no git"), statusFailed.status());
        assertEquals(List.of(new LoadStatus()), Update.update(statusFailed, new Tick()).cmds());
    }

    /**
     * A model with everything loaded and nothing running.
     */
    private static Model loaded() {
        Model model = Update.init("repo").model();
        model = Update.update(model, new StatusLoaded(CLEAN)).model();
        model = Update.update(model, new BranchesLoaded(BRANCHES)).model();
        return Update.update(model, new CommitsLoaded(COMMITS)).model();
    }
}
