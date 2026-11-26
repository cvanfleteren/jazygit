package net.vanfleteren.jazygit.kernel.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.core.git.model.ChangeType;
import net.vanfleteren.jazygit.core.git.model.Conflict;
import net.vanfleteren.jazygit.core.git.model.FileEntry;
import net.vanfleteren.jazygit.core.git.model.RepoStatus;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.Head;
import net.vanfleteren.jazygit.core.repo.InProgress;
import net.vanfleteren.jazygit.core.repo.RepoPath;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.core.repo.Stash;
import org.junit.jupiter.api.Test;

/**
 * A stash is found by its sha among the stashes loaded.
 */
class RepoTest {

    private static final Stash WORK = new Stash("stash@{0}", new Sha("5555"), "work", Option.none(), Instant.EPOCH);
    private static final Stash OTHER = new Stash("stash@{1}", new Sha("6666"), "other", Option.none(), Instant.EPOCH);

    @Test
    void findsALoadedStashByItsSha() {
        Repo repo = Repo.LOADING.withStashes(new Loadable.Loaded<>(List.of(WORK, OTHER)));

        assertEquals(Option.of(OTHER), repo.stash(new Sha("6666")));
    }

    @Test
    void findsNoStashThatIsNotListed() {
        Repo repo = Repo.LOADING.withStashes(new Loadable.Loaded<>(List.of(WORK)));

        assertEquals(Option.none(), repo.stash(new Sha("6666")));
    }

    @Test
    void findsNoStashWhileTheyLoad() {
        assertEquals(Option.none(), Repo.LOADING.stash(new Sha("5555")));
    }

    /**
     * A repository whose panes have all been read once: its status, branches, log, stashes and worktrees.
     */
    private static Repo firstRead() {
        return Repo.LOADING
                .withStatus(new Loadable.Loaded<>(new RepoStatus(new Head.Detached(new Sha("1111")), List.empty())))
                .withBranches(new Loadable.Loaded<>(List.empty()))
                .withCommits(new Loadable.Loaded<>(List.empty()))
                .withStashes(new Loadable.Loaded<>(List.empty()))
                .withWorktrees(new Loadable.Loaded<>(List.empty()));
    }

    @Test
    void whatThePanesShowIsReadOnceEachOfThemIsLoadedOrFailed() {
        assertFalse(Repo.LOADING.firstRead());
        assertTrue(firstRead().firstRead());
        // A read that failed shows its error, not that it is loading.
        assertTrue(firstRead()
                .withCommits(new Loadable.Failed<>(new Failure.Bug(new IllegalStateException("no log"))))
                .firstRead());
        // The files of all the working tree, and what is ahead, are read later, or only once asked for.
        assertTrue(firstRead()
                .withAllFiles(Loadable.loading())
                .withAhead(Loadable.loading())
                .firstRead());
    }

    @Test
    void whatThePanesShowIsNotReadWhileOneOfThemIsLoading() {
        assertFalse(firstRead().withStatus(Loadable.loading()).firstRead());
        assertFalse(firstRead().withBranches(Loadable.loading()).firstRead());
        assertFalse(firstRead().withCommits(Loadable.loading()).firstRead());
        assertFalse(firstRead().withStashes(Loadable.loading()).firstRead());
        assertFalse(firstRead().withWorktrees(Loadable.loading()).firstRead());
    }

    private static final FileEntry CONFLICTED =
            FileEntry.conflicted(new RepoPath("a.txt"), new Conflict(Conflict.Side.UPDATED, Conflict.Side.UPDATED));
    private static final FileEntry CHANGED = new FileEntry(new RepoPath("b.txt"), ChangeType.MODIFIED, true, false);

    private static Repo stopped(Option<InProgress> inProgress, List<String> resolved) {
        return Repo.LOADING.withStatus(
                new Loadable.Loaded<>(new RepoStatus(new Head.Detached(new Sha("1111")), List.of(CONFLICTED, CHANGED))
                        .withInProgress(inProgress)
                        .withResolved(resolved.map(RepoPath::new))));
    }

    private static final Option<InProgress> REBASING =
            Option.of(new InProgress.Rebasing(Option.of(new BranchName("feature")), new Sha("aaaa"), 1, 2));

    @Test
    void aFileResolvedToHeadIsListedWithTheChangedFilesWithoutChangesOfItsOwn() {
        // b.txt was resolved to a change, c.txt back to what HEAD has: git lists only b.txt as changed (ADR 198).
        Repo repo = stopped(REBASING, List.of("b.txt", "c.txt"));

        assertEquals(
                new Loadable.Loaded<>(List.of(CONFLICTED, CHANGED, FileEntry.unchanged(new RepoPath("c.txt")))),
                repo.workingFiles(false));
    }

    @Test
    void aFileResolvedIsListedOnlyWhileTheOperationIsInProgress() {
        Repo repo = stopped(Option.none(), List.of("c.txt"));

        assertEquals(new Loadable.Loaded<>(List.of(CONFLICTED, CHANGED)), repo.workingFiles(false));
    }
}
