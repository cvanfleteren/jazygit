package net.vanfleteren.jazygit.feature.inprogress;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.branch.checkout.CheckoutMsg;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.ChangeType;
import net.vanfleteren.jazygit.git.model.Conflict;
import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.git.model.Fixtures;
import net.vanfleteren.jazygit.git.model.Head;
import net.vanfleteren.jazygit.git.model.InProgress;
import net.vanfleteren.jazygit.git.model.InProgressAction;
import net.vanfleteren.jazygit.git.model.RepoStatus;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.state.LoadMsg;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;
import org.junit.jupiter.api.Test;

/**
 * Continuing, aborting or skipping the operation the repository is in the middle of.
 */
class InProgressUpdateTest {

    private static final InProgress REBASING =
            new InProgress.Rebasing(Option.of(new BranchName("feature")), new Sha("aaaa"), 1, 2);
    private static final FileEntry CONFLICTED =
            FileEntry.conflicted("a.txt", new Conflict(Conflict.Side.UPDATED, Conflict.Side.UPDATED));

    /**
     * The sample model, with the repository in the middle of {@code inProgress}, and {@code files} changed.
     */
    private static Model inMiddleOf(Option<InProgress> inProgress, List<FileEntry> files) {
        RepoStatus status = new RepoStatus(new Head.Detached(new Sha("bbbb")), files, inProgress);
        return Update.update(TestModels.loaded(), new LoadMsg.StatusLoaded(status))
                .model();
    }

    @Test
    void requestingAsksWhatToDoWithTheOperationInProgress() {
        Model model = inMiddleOf(Option.of(REBASING), List.of(CONFLICTED));

        assertEquals(
                Option.of(new InProgressPopup(REBASING, 1)),
                Update.update(model, new InProgressMsg.Requested()).model().popup(InProgressPopup.class));
    }

    @Test
    void requestingWithNothingInProgressDoesNothing() {
        Model model = inMiddleOf(Option.none(), List.of(Fixtures.fileEntry("a.txt", ChangeType.MODIFIED)));

        assertEquals(model, Update.update(model, new InProgressMsg.Requested()).model());
    }

    @Test
    void continuingStartsItOnceNoConflictsRemain() {
        Model asked = Update.update(inMiddleOf(Option.of(REBASING), List.of()), new InProgressMsg.Requested())
                .model();

        Next next = Update.update(asked, new InProgressMsg.Chosen(InProgressAction.CONTINUE));

        assertEquals(Option.none(), next.model().popup(InProgressPopup.class));
        assertEquals(List.of(new InProgressCmd(REBASING, InProgressAction.CONTINUE)), next.cmds());
    }

    @Test
    void continuingWhileConflictsRemainSaysSo() {
        Model asked = Update.update(inMiddleOf(Option.of(REBASING), List.of(CONFLICTED)), new InProgressMsg.Requested())
                .model();

        Next next = Update.update(asked, new InProgressMsg.Chosen(InProgressAction.CONTINUE));

        assertEquals(Option.none(), next.model().popup(InProgressPopup.class));
        assertEquals(List.of(), next.cmds());
        assertEquals(Option.of("1 file still has conflicts"), TestModels.errorText(next.model()));
    }

    @Test
    void abortingAndSkippingAskForConfirmationFirst() {
        Model asked = Update.update(inMiddleOf(Option.of(REBASING), List.of(CONFLICTED)), new InProgressMsg.Requested())
                .model();

        List.of(InProgressAction.ABORT, InProgressAction.SKIP).forEach(action -> {
            Next next = Update.update(asked, new InProgressMsg.Chosen(action));

            assertEquals(Option.none(), next.model().popup(InProgressPopup.class));
            assertEquals(
                    Option.of(new InProgressConfirmPopup(REBASING, action)),
                    next.model().popup(InProgressConfirmPopup.class));
            assertEquals(List.of(), next.cmds());
        });
    }

    @Test
    void confirmingStartsIt() {
        Model asked = Update.update(inMiddleOf(Option.of(REBASING), List.of(CONFLICTED)), new InProgressMsg.Requested())
                .model();
        Model confirming = Update.update(asked, new InProgressMsg.Chosen(InProgressAction.ABORT))
                .model();

        Next next = Update.update(confirming, new InProgressMsg.Confirmed());

        assertEquals(Option.none(), next.model().popup(InProgressConfirmPopup.class));
        assertEquals(List.of(new InProgressCmd(REBASING, InProgressAction.ABORT)), next.cmds());
    }

    @Test
    void cancellingClosesThePopupsWithoutDoingAnything() {
        Model asked = Update.update(inMiddleOf(Option.of(REBASING), List.of(CONFLICTED)), new InProgressMsg.Requested())
                .model();
        Model confirming = Update.update(asked, new InProgressMsg.Chosen(InProgressAction.ABORT))
                .model();

        Next cancelled = Update.update(asked, new InProgressMsg.Cancelled());
        Next notConfirmed = Update.update(confirming, new InProgressMsg.Cancelled());

        assertEquals(Option.none(), cancelled.model().popup(InProgressPopup.class));
        assertEquals(List.of(), cancelled.cmds());
        assertEquals(Option.none(), notConfirmed.model().popup(InProgressConfirmPopup.class));
        assertEquals(List.of(), notConfirmed.cmds());
    }

    @Test
    void aMergeCannotBeSkipped() {
        Model asked = Update.update(
                        inMiddleOf(Option.of(new InProgress.Merging(new Sha("cccc"))), List.of(CONFLICTED)),
                        new InProgressMsg.Requested())
                .model();

        assertEquals(
                asked,
                Update.update(asked, new InProgressMsg.Chosen(InProgressAction.SKIP))
                        .model());
    }

    @Test
    void operationsThatChangeTheHistoryWaitUntilItIsDone() {
        Model model = inMiddleOf(Option.of(REBASING), List.of());

        Next next = Update.update(model, new CheckoutMsg.Requested(new BranchName("feature")));

        assertEquals(List.of(), next.cmds());
        assertEquals(
                Option.of("Checkout of feature failed: a rebase is in progress; continue or abort it first (M)"),
                TestModels.errorText(next.model()));
    }

    @Test
    void operationsThatChangeTheHistoryWaitUntilNoConflictsRemain() {
        Model model = inMiddleOf(Option.none(), List.of(CONFLICTED));

        Next next = Update.update(model, new CheckoutMsg.Requested(new BranchName("feature")));

        assertEquals(List.of(), next.cmds());
        assertEquals(
                Option.of("Checkout of feature failed: 1 file still has conflicts"),
                TestModels.errorText(next.model()));
    }
}
