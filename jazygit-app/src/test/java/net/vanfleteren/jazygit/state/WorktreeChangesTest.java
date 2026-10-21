package net.vanfleteren.jazygit.state;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.nio.file.Path;
import java.time.Instant;
import net.vanfleteren.jazygit.feature.branch.tabs.BranchesTabMsg;
import net.vanfleteren.jazygit.gh.StubGitHub;
import net.vanfleteren.jazygit.git.GitError;
import net.vanfleteren.jazygit.git.GitInfoProvider;
import net.vanfleteren.jazygit.git.StubGitProvider;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.model.Worktree;
import net.vanfleteren.jazygit.state.Cmd.LoadWorktreeChanges;
import net.vanfleteren.jazygit.state.Update.Next;
import org.junit.jupiter.api.Test;

/**
 * Which worktrees have changes is read only while the branches pane shows the worktrees.
 */
class WorktreeChangesTest {

    private static final Path HERE = Path.of("/repos/app");
    private static final Path LINKED = Path.of("/repos/app-feature");
    private static final Path GONE = Path.of("/repos/app-gone");
    private static final LoadWorktreeChanges LOAD = new LoadWorktreeChanges(List.of(LINKED.toString()));

    private static Worktree worktree(Path path, boolean current, boolean prunable) {
        return new Worktree(
                path,
                Option.of(new Sha("aaaa")),
                Option.of(new Branch.Name(path.getFileName().toString())),
                current,
                current,
                Option.none(),
                prunable);
    }

    private static Model withWorktrees() {
        return Update.update(
                        TestModels.loaded(),
                        new LoadMsg.WorktreesLoaded(List.of(
                                worktree(HERE, true, false),
                                worktree(LINKED, false, false),
                                worktree(GONE, false, true))))
                .model();
    }

    private static Model onWorktreesTab(Model model) {
        return model.mapRepoView(repoView -> repoView.withBranchesTab(BranchesTab.WORKTREES));
    }

    @Test
    void aTickReadsTheOtherWorktreesThatAreOnDiskOnlyWhileTheWorktreesAreShown() {
        assertFalse(Update.refresh(withWorktrees()).cmds().exists(cmd -> cmd instanceof LoadWorktreeChanges));
        assertTrue(Update.refresh(onWorktreesTab(withWorktrees())).cmds().contains(LOAD));
    }

    @Test
    void aTickDoesNotReadThemWhileThePullRequestsAreShown() {
        Model pullRequests = onWorktreesTab(withWorktrees()).withScreen(Screen.PULL_REQUESTS);

        assertFalse(Update.update(pullRequests, new Msg.Tick(Instant.parse("2026-10-21T10:00:00Z")))
                .cmds()
                .exists(cmd -> cmd instanceof LoadWorktreeChanges));
    }

    @Test
    void aTickDoesNotReadThemAgainWhileTheyAreBeingRead() {
        Model reading = Update.refresh(onWorktreesTab(withWorktrees())).model();

        assertFalse(Update.refresh(reading).cmds().exists(cmd -> cmd instanceof LoadWorktreeChanges));
    }

    @Test
    void aTickWaitsAWhileAfterTheyWereReadBeforeReadingThemAgain() {
        Instant read = Instant.parse("2026-10-21T10:00:00Z");
        Model done = Update.update(
                        Update.refresh(onWorktreesTab(withWorktrees()).withNow(read))
                                .model(),
                        new LoadMsg.WorktreeChangesLoaded(List.of(LINKED.toString()), HashMap.empty()))
                .model();

        assertFalse(Update.update(done, new Msg.Tick(read.plusMillis(500)))
                .cmds()
                .exists(cmd -> cmd instanceof LoadWorktreeChanges));
        assertTrue(Update.update(done, new Msg.Tick(read.plus(Update.WORKTREE_CHANGES_INTERVAL)))
                .cmds()
                .contains(LOAD));
    }

    @Test
    void failingToReadThemAlsoMakesATickWait() {
        Instant read = Instant.parse("2026-10-21T10:00:00Z");
        Model failed = Update.update(
                        Update.refresh(onWorktreesTab(withWorktrees()).withNow(read))
                                .model(),
                        new LoadMsg.Failed(LOAD, new Failure.Git(new GitError.TimedOut("status", LINKED))))
                .model();

        assertFalse(Update.update(failed, new Msg.Tick(read.plusMillis(500)))
                .cmds()
                .exists(cmd -> cmd instanceof LoadWorktreeChanges));
    }

    @Test
    void theyAreReadInTheBackgroundLeavingOutTheWorktreesThatCannotBeRead() {
        Path broken = Path.of("/repos/app-broken");
        GitInfoProvider git = new StubGitProvider() {
            @Override
            public Either<GitError.ProcessError, Boolean> hasChanges(Path worktree) {
                return worktree.equals(broken)
                        ? Either.left(new GitError.TimedOut("status", worktree))
                        : Either.right(true);
            }
        };
        LoadWorktreeChanges load = new LoadWorktreeChanges(List.of(broken.toString(), LINKED.toString()));

        assertTrue(load.background());
        assertEquals(
                new LoadMsg.WorktreeChangesLoaded(load.worktrees(), HashMap.of(LINKED.toString(), true)),
                load.run(new Env(git, StubGitHub.empty())));
    }

    @Test
    void switchingToTheWorktreesReadsThemRightAway() {
        Next next = Update.update(withWorktrees(), new BranchesTabMsg.Chosen(BranchesTab.WORKTREES));

        assertEquals(List.of(LOAD), next.cmds());
        assertTrue(next.model().jobs().refreshing().contains(LOAD));
    }

    @Test
    void whatWasReadReplacesWhatWasKnownAndTheShownWorktreeGoesByItsStatus() {
        Model model = Update.update(
                        Update.refresh(onWorktreesTab(withWorktrees())).model(),
                        new LoadMsg.WorktreeChangesLoaded(
                                List.of(LINKED.toString()), HashMap.of(LINKED.toString(), true)))
                .model();

        assertFalse(model.jobs().refreshing().contains(LOAD));
        assertEquals(Option.of(true), model.repo().hasChanges(worktree(LINKED, false, false)));
        assertEquals(Option.none(), model.repo().hasChanges(worktree(GONE, false, true)));
        assertEquals(Option.of(false), model.repo().hasChanges(worktree(HERE, true, false)));
    }

    @Test
    void failingToReadThemKeepsWhatWasKnown() {
        Model known = withWorktrees().mapRepo(repo -> repo.withWorktreeChanges(HashMap.of(LINKED.toString(), true)));

        Model model = Update.update(
                        known,
                        new LoadMsg.Failed(
                                LOAD,
                                new Failure.Git(new net.vanfleteren.jazygit.git.GitError.TimedOut("status", LINKED))))
                .model();

        assertEquals(HashMap.of(LINKED.toString(), true), model.repo().worktreeChanges());
        assertEquals(Option.none(), model.outcome().error());
    }
}
