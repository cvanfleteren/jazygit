package net.vanfleteren.jazygit.feature.branch;

import static net.vanfleteren.jazygit.git.StubGitProvider.executed;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.MergeMode;
import net.vanfleteren.jazygit.state.Cmd.LoadBranches;
import net.vanfleteren.jazygit.state.Cmd.LoadStashes;
import net.vanfleteren.jazygit.state.Cmd.LoadStatus;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;
import org.junit.jupiter.api.Test;

import static net.vanfleteren.jazygit.state.TestModels.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Merging a branch into the checked out one.
 */
class MergeUpdateTest {

    @Test
    void aMergeRequestAsksHowToMerge() {
        Next asked = Update.update(withCheckoutError(loaded(), "foo", "boom"), new MergeMsg.Requested("feature"));

        assertEquals(Option.of(new MergePopup("main", "feature")), asked.model().popup());
        assertEquals(Option.none(), asked.model().error());
        assertEquals(List.of(), asked.cmds());
    }

    @Test
    void theCheckedOutBranchAndUnknownBranchesCannotBeMerged() {
        assertEquals(Option.none(), Update.update(loaded(), new MergeMsg.Requested("main")).model().popup());
        assertEquals(Option.none(), Update.update(loaded(), new MergeMsg.Requested("gone")).model().popup());
    }

    @Test
    void choosingMergesTheBranchThatWayAndCancellingDoesNot() {
        Model asked = Update.update(loaded(), new MergeMsg.Requested("feature")).model();

        Next chosen = Update.update(asked, new MergeMsg.Chosen(MergeMode.SQUASH));
        assertEquals(List.of(new BranchCmd.Merge("feature", MergeMode.SQUASH)), chosen.cmds());
        assertEquals(Option.none(), chosen.model().popup());

        Next cancelled = Update.update(asked, new MergeMsg.Cancelled());
        assertEquals(List.of(), cancelled.cmds());
        assertEquals(Option.none(), cancelled.model().popup());

        assertEquals(List.of(), Update.update(loaded(), new MergeMsg.Chosen(MergeMode.SQUASH)).cmds());
    }

    @Test
    void aMergeIsLoggedAndReloadsAndAFailedOneIsReported() {
        Next done = Update.update(loaded(), succeeded(new BranchCmd.Merge("feature", MergeMode.FAST_FORWARD), "git merge --ff --no-edit feature"));
        assertEquals(List.of(new LoadStatus(), new LoadBranches(), new net.vanfleteren.jazygit.state.Cmd.LoadRemoteBranches(), new LoadStashes()), done.cmds());
        assertEquals(executed("git merge --ff --no-edit feature"), done.model().commandLog().last().commands());

        Next failed = Update.update(loaded(), failed(new BranchCmd.Merge("feature", MergeMode.FAST_FORWARD), "CONFLICT (content)"));
        assertEquals(Option.of("Merging feature failed: CONFLICT (content)"), errorText(failed.model()));
    }
}
