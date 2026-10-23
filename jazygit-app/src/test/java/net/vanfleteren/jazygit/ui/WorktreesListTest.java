package net.vanfleteren.jazygit.ui;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.*;
import net.vanfleteren.jazygit.state.*;
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
                Option.of(new Head.OnBranch(new Branch.Name("feature"), Option.of(new Sha("1234567890")))),
                false,
                false,
                Option.none(),
                false);
        Repo repo = Repo.LOADING
                .withBranches(new Loadable.Loaded<>(List.of(new Branch(
                        new Branch.Name("feature"),
                        false,
                        new Sha("1234567890"),
                        java.time.Instant.EPOCH,
                        true,
                        false,
                        Option.of(new Upstream(
                                new BranchRef.Remote(new RemoteBranch("origin", new Branch.Name("feature"))), 2, 1))))))
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
                Option.of(new Head.OnBranch(new Branch.Name("feature"), Option.of(new Sha("1234567890")))),
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
                Option.of(new Head.OnBranch(new Branch.Name("feature"), Option.of(new Sha("1234567890")))),
                false,
                false,
                Option.of("on usb"),
                false);

        assertEquals(
                "x feature locked: on usb",
                WorktreesList.row(Repo.LOADING, locked).text());
    }
}
