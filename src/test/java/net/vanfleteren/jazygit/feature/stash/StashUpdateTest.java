package net.vanfleteren.jazygit.feature.stash;

import static net.vanfleteren.jazygit.git.StubGitProvider.executed;
import net.vanfleteren.jazygit.state.Operation;
import io.vavr.collection.List;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.model.RepoStatus.Head;
import io.vavr.control.Option;
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

import static net.vanfleteren.jazygit.state.TestModels.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class StashUpdateTest {

    private static Model withChanges() {
        Model model = loaded();
        Head head = ((net.vanfleteren.jazygit.state.Loadable.Loaded<RepoStatus>) model.status()).value().head();
        return Update.update(model, new LoadMsg.StatusLoaded(
                new RepoStatus(head, List.of(new FileEntry("a.txt", ChangeType.MODIFIED))))).model();
    }

    @Test
    void requestingOpensTheDialogWhenThereAreChanges() {
        assertEquals(Option.of(new StashPopup()),
                Update.update(withChanges(), new StashMsg.Requested()).model().popup());
    }

    @Test
    void requestingWithoutChangesReportsThat() {
        Model model = Update.update(loaded(), new StashMsg.Requested()).model();

        assertEquals(Option.none(), model.popup(StashPopup.class));
        assertEquals(Option.of("No changes to stash"), errorText(model));
    }

    @Test
    void escapeClosesTheDialogWithoutStashing() {
        Model open = Update.update(withChanges(), new StashMsg.Requested()).model();

        Next next = Update.update(open, new StashMsg.Cancelled());

        assertEquals(Option.none(), next.model().popup());
        assertEquals(List.of(), next.cmds());
    }

    @Test
    void confirmingStashesUnderTheEnteredName() {
        Model open = Update.update(withChanges(), new StashMsg.Requested()).model();

        Next next = Update.update(open, new StashMsg.Confirmed("  my work "));

        assertEquals(List.of(new Stash("my work")), next.cmds());
        assertEquals(Option.none(), next.model().popup());
    }

    @Test
    void aBlankNameIsReplacedByTheBranchAndTheHeadCommit() {
        Model open = Update.update(withChanges(), new StashMsg.Requested()).model();
        net.vanfleteren.jazygit.git.model.Commit head = ((net.vanfleteren.jazygit.state.Loadable.Loaded<
                List<net.vanfleteren.jazygit.git.model.Commit>>) open.commits()).value().head();
        Model atHead = Update.update(open, new LoadMsg.StatusLoaded(new RepoStatus(Head.onBranch("main", new Sha(head.sha().value())), List.of(new FileEntry("a.txt", ChangeType.MODIFIED))))).model();

        Next next = Update.update(atHead, new StashMsg.Confirmed(" "));

        assertEquals(List.of(new Stash("WIP on main " + head.sha().shortSha() + " " + head.message())),
                next.cmds());
    }

    @Test
    void theGeneratedNameLeavesOutTheCommitInARepositoryWithoutCommits() {
        Model model = Update.update(loaded(), new LoadMsg.StatusLoaded(
                new RepoStatus(new Head.OnBranch("main", Option.none()), List.of(new FileEntry("a.txt", ChangeType.UNTRACKED))))).model();

        assertEquals("WIP on main", StashUpdate.defaultName(model));
    }

    @Test
    void doneLogsTheCommandsAndFailureIsReported() {
        Next done = Update.update(loaded(), succeeded(new StashCmd.Stash("x"), "git stash push --message x"));
        assertEquals(List.of(new LogEntry(Operation.STASH, executed("git stash push --message x"))), done.model().commandLog());

        Next failed = Update.update(loaded(), failed(new StashCmd.Stash("x"), "boom"));
        assertEquals(Option.of("Stash failed: boom"), errorText(failed.model()));
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
        assertEquals(Option.of(new StashDropPopup("stash@{0}")), asked.model().popup());

        Next confirmed = Update.update(asked.model(), new StashMsg.DropConfirmed());
        assertEquals(List.of(new StashCmd.Drop("stash@{0}")), confirmed.cmds());
        assertEquals(Option.none(), confirmed.model().popup());

        assertEquals(Option.none(), Update.update(model, new StashMsg.DropRequested("stash@{5}")).model().popup());
    }

    @Test
    void cancellingTheDropClosesTheDialogWithoutDropping() {
        Model model = Update.update(loaded(), new LoadMsg.StashesLoaded(
                List.of(new net.vanfleteren.jazygit.git.model.Stash("stash@{0}", "x")))).model();
        Model asked = Update.update(model, new StashMsg.DropRequested("stash@{0}")).model();

        Next cancelled = Update.update(asked, new StashMsg.DropCancelled());

        assertEquals(Option.none(), cancelled.model().popup());
        assertEquals(List.of(), cancelled.cmds());
        assertEquals(List.of(), Update.update(model, new StashMsg.DropConfirmed()).cmds());
    }

    @Test
    void droppedLogsTheCommandsAndFailureIsReported() {
        Next dropped = Update.update(loaded(), succeeded(new StashCmd.Drop("stash@{0}"), "git stash drop stash@{0}"));
        assertEquals(List.of(new LogEntry(Operation.STASH_DROP, executed("git stash drop stash@{0}"))),
                dropped.model().commandLog());

        Next failed = Update.update(loaded(), failed(new StashCmd.Drop("stash@{0}"), "no such stash"));
        assertEquals(Option.of("Dropping the stash failed: no such stash"), errorText(failed.model()));
    }

    @Test
    void poppedLogsTheCommandsAndFailureIsReported() {
        Next popped = Update.update(loaded(), succeeded(new StashCmd.Pop("stash@{0}"), "git stash pop stash@{0}"));
        assertEquals(List.of(new LogEntry(Operation.STASH_POP, executed("git stash pop stash@{0}"))),
                popped.model().commandLog());

        Next failed = Update.update(loaded(), failed(new StashCmd.Pop("stash@{0}"), "conflict"));
        assertEquals(Option.of("Unstash failed: conflict"), errorText(failed.model()));
    }
}
