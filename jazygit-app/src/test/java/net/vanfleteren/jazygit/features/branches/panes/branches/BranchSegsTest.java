package net.vanfleteren.jazygit.features.branches.panes.branches;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.model.*;
import net.vanfleteren.jazygit.core.repo.*;
import net.vanfleteren.jazygit.features.branches.branch.pull.PullCmd;
import net.vanfleteren.jazygit.kernel.ui.theme.Theme;
import net.vanfleteren.jazygit.kernel.ui.widgets.LoadableList;
import org.junit.jupiter.api.Test;

class BranchSegsTest {

    @Test
    void aGroupIsSetApartByOneMoreSpace() {
        assertEquals(
                List.of(
                        LoadableList.Seg.styled("  ↑2", Theme.Classes.AHEAD),
                        LoadableList.Seg.styled(" ↓5", Theme.Classes.BEHIND)),
                BranchSegs.group(List.of(
                        LoadableList.Seg.styled(" ↑2", Theme.Classes.AHEAD),
                        LoadableList.Seg.styled(" ↓5", Theme.Classes.BEHIND))));
        assertEquals(List.of(), BranchSegs.group(List.of()));
    }

    @Test
    void aBranchWhoseUpstreamIsGoneSaysSo() {
        assertEquals(
                List.of(LoadableList.Seg.styled(" gone", Theme.Classes.GONE)),
                BranchSegs.sync(Fixtures.branch(
                        new BranchName("b"),
                        false,
                        new Sha("1"),
                        java.time.Instant.EPOCH,
                        false,
                        false,
                        new Tracking.Gone(new BranchRef.Remote(new RemoteBranch("origin", new BranchName("b")))))));
        // A branch that was never pushed shows nothing.
        assertEquals(List.of(), BranchSegs.sync(Fixtures.branch(new BranchName("b"), false, new Sha("1"))));
    }

    @Test
    void aBranchCheckedOutInAnotherWorktreeSaysSo() {
        assertEquals(
                List.of(LoadableList.Seg.styled(" wt", Theme.Classes.DIM)),
                BranchSegs.worktree(new Branch(
                        new BranchName("b"),
                        false,
                        new Sha("1"),
                        java.time.Instant.EPOCH,
                        Option.none(),
                        false,
                        false,
                        new Tracking.None(),
                        Option.of(java.nio.file.Path.of("/repos/linked")))));
        assertEquals(List.of(), BranchSegs.worktree(Fixtures.branch(new BranchName("b"), false, new Sha("1"))));
    }

    @Test
    void syncShowsAnAheadArrowForUnpushedAndABehindOneForIncomingCommits() {
        java.time.Instant t = java.time.Instant.EPOCH;
        assertEquals(
                List.of(),
                BranchSegs.sync(Fixtures.branch(
                        new BranchName("b"),
                        false,
                        new Sha("1"),
                        t,
                        true,
                        false,
                        Option.of(new Upstream(
                                new BranchRef.Remote(new RemoteBranch("origin", new BranchName("b"))), 0, 0)))));
        assertEquals(
                List.of(LoadableList.Seg.styled(" ↑2", Theme.Classes.AHEAD)),
                BranchSegs.sync(Fixtures.branch(
                        new BranchName("b"),
                        false,
                        new Sha("1"),
                        t,
                        true,
                        false,
                        Option.of(new Upstream(
                                new BranchRef.Remote(new RemoteBranch("origin", new BranchName("b"))), 2, 0)))));
        assertEquals(
                List.of(LoadableList.Seg.styled(" ↓1", Theme.Classes.BEHIND)),
                BranchSegs.sync(Fixtures.branch(
                        new BranchName("b"),
                        false,
                        new Sha("1"),
                        t,
                        true,
                        false,
                        Option.of(new Upstream(
                                new BranchRef.Remote(new RemoteBranch("origin", new BranchName("b"))), 0, 1)))));
        assertEquals(
                List.of(
                        LoadableList.Seg.styled(" ↑2", Theme.Classes.AHEAD),
                        LoadableList.Seg.styled(" ↓1", Theme.Classes.BEHIND)),
                BranchSegs.sync(Fixtures.branch(
                        new BranchName("b"),
                        false,
                        new Sha("1"),
                        t,
                        true,
                        false,
                        Option.of(new Upstream(
                                new BranchRef.Remote(new RemoteBranch("origin", new BranchName("b"))), 2, 1)))));
    }

    @Test
    void aFastForwardIsShownAsAPull() {
        assertEquals(BranchSegs.busyLabel(PullCmd.PULL), BranchSegs.busyLabel(PullCmd.FAST_FORWARD));
    }
}
