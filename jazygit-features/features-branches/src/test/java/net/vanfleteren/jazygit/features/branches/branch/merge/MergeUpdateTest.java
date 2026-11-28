package net.vanfleteren.jazygit.features.branches.branch.merge;

import static net.vanfleteren.jazygit.core.git.StubGit.executed;
import static net.vanfleteren.jazygit.features.branches.branch.checkout.CheckoutModels.withCheckoutError;
import static net.vanfleteren.jazygit.kernel.state.TestModels.*;
import static net.vanfleteren.jazygit.kernel.ui.text.ErrorTexts.errorText;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.model.MergeMode;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.core.repo.RemoteRef;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Update;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.load.LoadBranches;
import net.vanfleteren.jazygit.kernel.state.load.LoadStashes;
import net.vanfleteren.jazygit.kernel.state.load.LoadStatus;
import net.vanfleteren.jazygit.kernel.state.popup.PopupMsg;
import org.junit.jupiter.api.Test;

/**
 * Merging a branch into the checked out one.
 */
class MergeUpdateTest {

    @Test
    void aMergeRequestAsksHowToMerge() {
        Next asked = Update.update(
                withCheckoutError(loaded(), "foo", "boom"),
                new MergeMsg.Requested(new BranchRef.Local(new BranchName("feature"))));

        assertEquals(
                Option.of(new MergePopup(new BranchName("main"), new BranchRef.Local(new BranchName("feature")))),
                asked.model().popup());
        assertEquals(Option.none(), asked.model().outcome().error());
        assertEquals(List.of(), asked.cmds());
    }

    @Test
    void aKnownRemoteTrackingBranchCanBeMergedAndAnUnknownOneCannot() {
        RemoteBranch known = new RemoteBranch("origin", new BranchName("feature"));
        Model model = remoteBranchesRead(loaded(), List.of(new RemoteRef(known, new Sha("2"), java.time.Instant.EPOCH)))
                .model();

        Model asked =
                Update.update(model, new MergeMsg.Requested(known.tracking())).model();
        assertEquals(Option.of(new MergePopup(new BranchName("main"), known.tracking())), asked.popup());
        assertEquals(
                List.of(new MergeCmd(known.tracking(), MergeMode.MERGE_COMMIT)),
                Update.update(asked, new MergeMsg.Chosen(MergeMode.MERGE_COMMIT))
                        .cmds());
        assertEquals(
                Option.none(),
                Update.update(
                                model,
                                new MergeMsg.Requested(new RemoteBranch("origin", new BranchName("gone")).tracking()))
                        .model()
                        .popup());
    }

    @Test
    void theCheckedOutBranchAndUnknownBranchesCannotBeMerged() {
        assertEquals(
                Option.none(),
                Update.update(loaded(), new MergeMsg.Requested(new BranchRef.Local(new BranchName("main"))))
                        .model()
                        .popup());
        assertEquals(
                Option.none(),
                Update.update(loaded(), new MergeMsg.Requested(new BranchRef.Local(new BranchName("gone"))))
                        .model()
                        .popup());
    }

    @Test
    void choosingMergesTheBranchThatWayAndCancellingDoesNot() {
        Model asked = Update.update(loaded(), new MergeMsg.Requested(new BranchRef.Local(new BranchName("feature"))))
                .model();

        Next chosen = Update.update(asked, new MergeMsg.Chosen(MergeMode.SQUASH));
        assertEquals(
                List.of(new MergeCmd(new BranchRef.Local(new BranchName("feature")), MergeMode.SQUASH)), chosen.cmds());
        assertEquals(Option.none(), chosen.model().popup());

        Next cancelled = Update.update(asked, new PopupMsg.Dismissed(MergePopup.class));
        assertEquals(List.of(), cancelled.cmds());
        assertEquals(Option.none(), cancelled.model().popup());

        assertEquals(
                List.of(),
                Update.update(loaded(), new MergeMsg.Chosen(MergeMode.SQUASH)).cmds());
    }

    @Test
    void aMergeIsLoggedAndReloadsAndAFailedOneIsReported() {
        Next done = Update.update(
                loaded(),
                succeeded(
                        new MergeCmd(new BranchRef.Local(new BranchName("feature")), MergeMode.FAST_FORWARD),
                        "git merge --ff --no-edit feature"));
        assertEquals(
                List.of(
                        new LoadStatus(),
                        LoadBranches.LISTED,
                        new LoadStashes(),
                        new net.vanfleteren.jazygit.kernel.state.load.LoadWorktrees()),
                done.cmds());
        assertEquals(
                executed("git merge --ff --no-edit feature"),
                done.model().outcome().commandLog().last().commands());

        Next failed = Update.update(
                loaded(),
                failed(
                        new MergeCmd(new BranchRef.Local(new BranchName("feature")), MergeMode.FAST_FORWARD),
                        "CONFLICT (content)"));
        assertEquals(Option.of("Merging feature failed: CONFLICT (content)"), errorText(failed.model()));
    }
}
