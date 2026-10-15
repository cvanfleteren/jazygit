package net.vanfleteren.jazygit.feature.branch;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.DeleteScope;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.model.Tracking;
import net.vanfleteren.jazygit.git.model.Upstream;
import net.vanfleteren.jazygit.state.LoadMsg;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;

import static net.vanfleteren.jazygit.state.TestModels.errorText;
import static net.vanfleteren.jazygit.state.TestModels.loaded;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A branch that is checked out in another worktree, which git keeps from being checked out, deleted or
 * fast-forwarded here: that is said up front, instead of running a command git refuses.
 */
class WorktreeBranchUpdateTest {

    private static final Path LINKED = Path.of("/repos/linked");
    private static final Branch.Name FEATURE = new Branch.Name("feature");
    private static final Branch.Name LOCAL = new Branch.Name("local");

    /**
     * {@code feature}, which is on its remote, and {@code local}, which is not, are both checked out in another
     * worktree.
     */
    private static Model elsewhere() {
        RemoteBranch upstream = new RemoteBranch("origin", FEATURE);
        return Update.update(loaded(), new LoadMsg.BranchesLoaded(List.of(
                new Branch(new Branch.Name("main"), true, new Sha("aaaa")),
                new Branch(FEATURE, false, new Sha("ffff"), Instant.EPOCH, true, false,
                        new Tracking.Tracks(new Upstream(upstream, 0, 1)), Option.of(LINKED)),
                new Branch(LOCAL, false, new Sha("1111"), Instant.EPOCH, false, false,
                        new Tracking.None(), Option.of(LINKED))))).model();
    }

    @Test
    void itIsNotCheckedOutAndTheErrorSaysWhere() {
        Next next = Update.update(elsewhere(), new CheckoutMsg.Requested(FEATURE));

        assertEquals(List.of(), next.cmds());
        assertEquals(Option.of("Checkout of feature failed: it is checked out in the worktree at /repos/linked"),
                errorText(next.model()));
    }

    @Test
    void itIsNotFastForwarded() {
        Next next = Update.update(elsewhere(), new PullMsg.Requested(FEATURE));

        assertEquals(List.of(), next.cmds());
        assertEquals(Option.of("Fast-forwarding branch feature failed: it is checked out in the worktree at /repos/linked"),
                errorText(next.model()));
    }

    @Test
    void itCanOnlyBeDeletedOnItsRemote() {
        Model open = Update.update(elsewhere(), new DeleteBranchMsg.Requested(FEATURE)).model();

        assertEquals(Option.of(new DeleteBranchPopup(FEATURE)), open.popup());
        for (DeleteScope scope : List.of(DeleteScope.LOCAL, DeleteScope.BOTH)) {
            assertEquals(List.of(), Update.update(open, new DeleteBranchMsg.Chosen(scope)).cmds());
        }
        assertEquals(List.of(new BranchCmd.DeleteBranch(FEATURE, new RemoteBranch("origin", FEATURE), DeleteScope.REMOTE)),
                Update.update(open, new DeleteBranchMsg.Chosen(DeleteScope.REMOTE)).cmds());
    }

    @Test
    void withoutARemoteBranchItIsNotDeletedAndTheErrorSaysWhere() {
        Next next = Update.update(elsewhere(), new DeleteBranchMsg.Requested(LOCAL));

        assertEquals(Option.of("Deleting branch local failed: it is checked out in the worktree at /repos/linked"),
                errorText(next.model()));
        assertEquals(Option.none(), next.model().popup(DeleteBranchPopup.class));
    }

    @Test
    void itCanStillBeMergedRebasedOntoAndRenamed() {
        Model model = elsewhere();

        assertEquals(Option.of(new MergePopup(new Branch.Name("main"), FEATURE)),
                Update.update(model, new MergeMsg.Requested(FEATURE)).model().popup());
        assertEquals(Option.of(new RenameBranchPopup(FEATURE)),
                Update.update(model, new RenameBranchMsg.Requested(FEATURE)).model().popup());
    }
}
