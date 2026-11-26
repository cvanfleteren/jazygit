package net.vanfleteren.jazygit.features.branches.panes.branches;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.model.*;
import net.vanfleteren.jazygit.core.repo.*;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.kernel.state.*;
import net.vanfleteren.jazygit.kernel.ui.theme.Theme;
import net.vanfleteren.jazygit.kernel.ui.widgets.LoadableList;
import org.junit.jupiter.api.Test;

class WorktreesListTest {

    @Test
    void aWorktreeSaysWhatItHasCheckedOutAndWhatKeepsItFromBeingRemoved() {
        Worktree detached = new Worktree(
                java.nio.file.Path.of("/repos/x"),
                Option.of(new Head.Detached(new Sha("1234567890"))),
                false,
                false,
                Option.of(""),
                true);

        assertEquals(
                "x (detached at 1234567) locked missing",
                WorktreesList.row(Repo.LOADING, detached).text());
        assertEquals(
                List.of(
                        Option.none(),
                        Option.of(Theme.Classes.DIM),
                        Option.of(Theme.Classes.DIM),
                        Option.of(Theme.Classes.GONE)),
                WorktreesList.row(Repo.LOADING, detached).segs().map(LoadableList.Seg::cssClass));
    }

    @Test
    void aWorktreeWhoseHeadGitCannotResolveSaysSo() {
        Worktree broken =
                new Worktree(java.nio.file.Path.of("/repos/x"), Option.none(), false, false, Option.none(), false);

        assertEquals("x (HEAD unknown)", WorktreesList.row(Repo.LOADING, broken).text());
    }

    @Test
    void aWorktreeWithChangesIsMarkedAndItsBranchSaysHowFarItIsFromItsUpstream() {
        java.nio.file.Path path = java.nio.file.Path.of("/repos/x");
        Worktree worktree = new Worktree(
                path,
                Option.of(new Head.OnBranch(new BranchName("feature"), Option.of(new Sha("1234567890")))),
                false,
                false,
                Option.none(),
                false);
        Repo repo = Repo.LOADING
                .withBranches(new Loadable.Loaded<>(List.of(Fixtures.branch(
                        new BranchName("feature"),
                        false,
                        new Sha("1234567890"),
                        java.time.Instant.EPOCH,
                        true,
                        false,
                        Option.of(new Upstream(
                                new BranchRef.Remote(new RemoteBranch("origin", new BranchName("feature"))), 2, 1))))))
                .withWorktreeChanges(io.vavr.collection.HashMap.of(path.toString(), true));

        LoadableList.Row row = WorktreesList.row(repo, worktree);

        assertEquals("x ● feature ↑2 ↓1", row.text());
        assertEquals(
                List.of(
                        Option.none(),
                        Option.of(Theme.Classes.UNSTAGED),
                        Option.none(),
                        Option.of(Theme.Classes.AHEAD),
                        Option.of(Theme.Classes.BEHIND)),
                row.segs().map(LoadableList.Seg::cssClass));
        assertEquals(
                "x feature ↑2 ↓1",
                WorktreesList.row(
                                repo.withWorktreeChanges(io.vavr.collection.HashMap.of(path.toString(), false)),
                                worktree)
                        .text());
    }

    @Test
    void theWorktreeThatIsShownStandsOutByTheStyleOfItsNameAlone() {
        Worktree shown = new Worktree(
                java.nio.file.Path.of("/repos/x"),
                Option.of(new Head.OnBranch(new BranchName("feature"), Option.of(new Sha("1234567890")))),
                false,
                true,
                Option.none(),
                false);

        LoadableList.Row row = WorktreesList.row(Repo.LOADING, shown);

        assertEquals("x feature", row.text());
        assertEquals(Option.of(Theme.Classes.CURRENT), row.segs().head().cssClass());
    }

    @Test
    void aWorktreeLockedForAReasonSaysWhy() {
        Worktree locked = new Worktree(
                java.nio.file.Path.of("/repos/x"),
                Option.of(new Head.OnBranch(new BranchName("feature"), Option.of(new Sha("1234567890")))),
                false,
                false,
                Option.of("on usb"),
                false);

        assertEquals(
                "x feature locked: on usb",
                WorktreesList.row(Repo.LOADING, locked).text());
    }

    @Test
    void aWorktreeInTheMiddleOfSomethingSaysWhatInPlaceOfTheHeadARebaseDetaches() {
        Sha main = new Sha("1234567890");
        Worktree rebasing = new Worktree(
                        java.nio.file.Path.of("/repos/x"),
                        Option.of(new Head.Detached(main)),
                        false,
                        false,
                        Option.none(),
                        false)
                .withMidway(Option.of(
                        new Midway(new InProgress.Rebasing(Option.of(new BranchName("feature")), main, 1, 2), 1)));
        Worktree merging = new Worktree(
                        java.nio.file.Path.of("/repos/y"),
                        Option.of(new Head.OnBranch(new BranchName("topic"), Option.of(main))),
                        false,
                        false,
                        Option.none(),
                        false)
                .withMidway(Option.of(new Midway(new InProgress.Merging(main), 0)));
        Repo repo = Repo.LOADING.withBranches(
                new Loadable.Loaded<>(List.of(Fixtures.branch(new BranchName("main"), true, main))));

        LoadableList.Row row = WorktreesList.row(repo, rebasing);

        assertEquals("x REBASING feature 1/2 onto main · 1 conflict", row.text());
        assertEquals(Option.of(Theme.Classes.IN_PROGRESS), row.segs().last().cssClass());
        assertEquals("y topic MERGING main", WorktreesList.row(repo, merging).text());
    }

    @Test
    void theWorktreeShownSaysWhatItsStatusSaysAnotherWhatItWasListedWith() {
        // Its status is read again more often than the worktrees, as after staging a resolved conflict: the row says
        // what the header says.
        Sha main = new Sha("1234567890");
        InProgress.Rebasing rebasing = new InProgress.Rebasing(Option.of(new BranchName("feature")), main, 1, 2);
        Worktree listed = new Worktree(
                        java.nio.file.Path.of("/repos/x"),
                        Option.of(new Head.Detached(main)),
                        false,
                        true,
                        Option.none(),
                        false)
                .withMidway(Option.of(new Midway(rebasing, 1)));
        Repo resolved = Repo.LOADING
                .withBranches(new Loadable.Loaded<>(List.of(Fixtures.branch(new BranchName("main"), true, main))))
                .withStatus(new Loadable.Loaded<>(
                        new RepoStatus(new Head.Detached(main), List.empty(), Option.of(rebasing))));
        Repo merged = resolved.withStatus(
                new Loadable.Loaded<>(new RepoStatus(new Head.Detached(main), List.empty(), Option.none())));
        Worktree other = new Worktree(listed.path(), listed.head(), false, false, Option.none(), false)
                .withMidway(listed.midway());

        assertEquals(
                "x REBASING feature 1/2 onto main",
                WorktreesList.row(resolved, listed).text());
        assertEquals(
                "x (detached at 1234567)", WorktreesList.row(merged, listed).text());
        assertEquals(
                "x REBASING feature 1/2 onto main · 1 conflict",
                WorktreesList.row(merged, other).text());
        // Until its status is read, as it was listed; without the branches, the commit is named by its sha.
        assertEquals(
                "x REBASING feature 1/2 onto 1234567 · 1 conflict",
                WorktreesList.row(Repo.LOADING, listed).text());
    }
}
