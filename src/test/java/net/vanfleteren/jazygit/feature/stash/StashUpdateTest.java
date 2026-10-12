package net.vanfleteren.jazygit.feature.stash;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.feature.stash.StashCmd.Stash;
import net.vanfleteren.jazygit.git.model.ChangeType;
import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.git.model.RepoStatus;
import net.vanfleteren.jazygit.state.LoadMsg;
import net.vanfleteren.jazygit.state.LogEntry;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update.Next;
import net.vanfleteren.jazygit.state.Update;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static net.vanfleteren.jazygit.state.TestModels.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class StashUpdateTest {

    private static Model withChanges() {
        Model model = loaded();
        String oid = ((RepoStatus) ((net.vanfleteren.jazygit.state.Loadable.Loaded<RepoStatus>) model.status()).value())
                .headOid();
        return Update.update(model, new LoadMsg.StatusLoaded(
                new RepoStatus("main", oid, List.of(new FileEntry("a.txt", ChangeType.MODIFIED))))).model();
    }

    @Test
    void requestingOpensTheDialogWhenThereAreChanges() {
        assertEquals(Optional.of(new StashPopup()),
                Update.update(withChanges(), new StashMsg.Requested()).model().popup());
    }

    @Test
    void requestingWithoutChangesReportsThat() {
        Model model = Update.update(loaded(), new StashMsg.Requested()).model();

        assertEquals(Optional.empty(), model.popup());
        assertEquals(Optional.of("No changes to stash"), model.error());
    }

    @Test
    void escapeClosesTheDialogWithoutStashing() {
        Model open = Update.update(withChanges(), new StashMsg.Requested()).model();

        Next next = Update.update(open, new StashMsg.Cancelled());

        assertEquals(Optional.empty(), next.model().popup());
        assertEquals(List.of(), next.cmds());
    }

    @Test
    void confirmingStashesUnderTheEnteredName() {
        Model open = Update.update(withChanges(), new StashMsg.Requested()).model();

        Next next = Update.update(open, new StashMsg.Confirmed("  my work "));

        assertEquals(List.of(new Stash("my work")), next.cmds());
        assertEquals(Optional.empty(), next.model().popup());
    }

    @Test
    void aBlankNameIsReplacedByTheBranchAndTheHeadCommit() {
        Model open = Update.update(withChanges(), new StashMsg.Requested()).model();
        net.vanfleteren.jazygit.git.model.Commit head = ((net.vanfleteren.jazygit.state.Loadable.Loaded<
                List<net.vanfleteren.jazygit.git.model.Commit>>) open.commits()).value().head();
        Model atHead = Update.update(open, new LoadMsg.StatusLoaded(new RepoStatus("main", head.sha().value(),
                List.of(new FileEntry("a.txt", ChangeType.MODIFIED))))).model();

        Next next = Update.update(atHead, new StashMsg.Confirmed(" "));

        assertEquals(List.of(new Stash("WIP on main " + head.sha().shortSha() + " " + head.message())),
                next.cmds());
    }

    @Test
    void theGeneratedNameLeavesOutTheCommitInARepositoryWithoutCommits() {
        Model model = Update.update(loaded(), new LoadMsg.StatusLoaded(
                new RepoStatus("main", RepoStatus.INITIAL, List.of(new FileEntry("a.txt", ChangeType.UNTRACKED))))).model();

        assertEquals("WIP on main", StashUpdate.defaultName(model));
    }

    @Test
    void doneLogsTheCommandsAndFailureIsReported() {
        Next done = Update.update(loaded(), new StashMsg.Done(List.of("git stash push --message x")));
        assertEquals(List.of(new LogEntry("Stash", List.of("git stash push --message x"))), done.model().commandLog());

        Next failed = Update.update(loaded(), new StashMsg.Failed("boom"));
        assertEquals(Optional.of("Stash failed: boom"), failed.model().error());
    }

    @Test
    void poppingAListedStashRunsGit() {
        Model model = Update.update(loaded(), new LoadMsg.StashesLoaded(
                List.of(new net.vanfleteren.jazygit.git.model.Stash("stash@{0}", "x")))).model();

        assertEquals(List.of(new StashCmd.Pop("stash@{0}")),
                Update.update(model, new StashMsg.PopRequested("stash@{0}")).cmds());
        assertEquals(List.of(), Update.update(model, new StashMsg.PopRequested("stash@{5}")).cmds());
    }

    @Test
    void droppingAListedStashAsksForConfirmationFirst() {
        Model model = Update.update(loaded(), new LoadMsg.StashesLoaded(
                List.of(new net.vanfleteren.jazygit.git.model.Stash("stash@{0}", "x")))).model();

        Next asked = Update.update(model, new StashMsg.DropRequested("stash@{0}"));
        assertEquals(List.of(), asked.cmds());
        assertEquals(Optional.of(new StashDropPopup("stash@{0}")), asked.model().popup());

        Next confirmed = Update.update(asked.model(), new StashMsg.DropConfirmed());
        assertEquals(List.of(new StashCmd.Drop("stash@{0}")), confirmed.cmds());
        assertEquals(Optional.empty(), confirmed.model().popup());

        assertEquals(Optional.empty(), Update.update(model, new StashMsg.DropRequested("stash@{5}")).model().popup());
    }

    @Test
    void cancellingTheDropClosesTheDialogWithoutDropping() {
        Model model = Update.update(loaded(), new LoadMsg.StashesLoaded(
                List.of(new net.vanfleteren.jazygit.git.model.Stash("stash@{0}", "x")))).model();
        Model asked = Update.update(model, new StashMsg.DropRequested("stash@{0}")).model();

        Next cancelled = Update.update(asked, new StashMsg.DropCancelled());

        assertEquals(Optional.empty(), cancelled.model().popup());
        assertEquals(List.of(), cancelled.cmds());
        assertEquals(List.of(), Update.update(model, new StashMsg.DropConfirmed()).cmds());
    }

    @Test
    void droppedLogsTheCommandsAndFailureIsReported() {
        Next dropped = Update.update(loaded(), new StashMsg.Dropped(List.of("git stash drop 'stash@{0}'")));
        assertEquals(List.of(new LogEntry("Drop stash", List.of("git stash drop 'stash@{0}'"))),
                dropped.model().commandLog());

        Next failed = Update.update(loaded(), new StashMsg.DropFailed("no such stash"));
        assertEquals(Optional.of("Dropping the stash failed: no such stash"), failed.model().error());
    }

    @Test
    void poppedLogsTheCommandsAndFailureIsReported() {
        Next popped = Update.update(loaded(), new StashMsg.Popped(List.of("git stash pop 'stash@{0}'")));
        assertEquals(List.of(new LogEntry("Unstash", List.of("git stash pop 'stash@{0}'"))),
                popped.model().commandLog());

        Next failed = Update.update(loaded(), new StashMsg.PopFailed("conflict"));
        assertEquals(Optional.of("Unstash failed: conflict"), failed.model().error());
    }
}
