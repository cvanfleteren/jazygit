package net.vanfleteren.jazygit.feature.stash;

import static net.vanfleteren.jazygit.git.StubGitProvider.executed;
import static net.vanfleteren.jazygit.state.TestModels.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.stash.StashCmd.Stash;
import net.vanfleteren.jazygit.git.model.ChangeType;
import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.git.model.RepoStatus;
import net.vanfleteren.jazygit.git.model.RepoStatus.Head;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.state.*;
import net.vanfleteren.jazygit.state.Update.Next;
import org.junit.jupiter.api.Test;

class StashUpdateTest {

    private static final net.vanfleteren.jazygit.git.model.Stash STASH =
            new net.vanfleteren.jazygit.git.model.Stash("stash@{0}", new Sha("s0"), "x");
    // Named like the listed one, as a stash that was added since; it is another one.
    private static final net.vanfleteren.jazygit.git.model.Stash UNLISTED =
            new net.vanfleteren.jazygit.git.model.Stash("stash@{0}", new Sha("s1"), "y");

    private static Model withChanges() {
        Model model = loaded();
        Head head = ((net.vanfleteren.jazygit.state.Loadable.Loaded<RepoStatus>) model.status())
                .value()
                .head();
        return Update.update(
                        model,
                        new LoadMsg.StatusLoaded(
                                new RepoStatus(head, List.of(new FileEntry("a.txt", ChangeType.MODIFIED)))))
                .model();
    }

    @Test
    void requestingOpensTheDialogWhenThereAreChanges() {
        assertEquals(
                Option.of(new StashPopup()),
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

        assertEquals(List.of(new Stash(Option.of("my work"))), next.cmds());
        assertEquals(Option.none(), next.model().popup());
    }

    @Test
    void aBlankNameLeavesNamingTheStashToGit() {
        Model open = Update.update(withChanges(), new StashMsg.Requested()).model();

        Next next = Update.update(open, new StashMsg.Confirmed(" "));

        assertEquals(List.of(new Stash(Option.none())), next.cmds());
    }

    @Test
    void doneLogsTheCommandsAndFailureIsReported() {
        Next done =
                Update.update(loaded(), succeeded(new StashCmd.Stash(Option.of("x")), "git stash push --message x"));
        assertEquals(
                List.of(new LogEntry(Operation.STASH, executed("git stash push --message x"))),
                done.model().commandLog());

        Next failed = Update.update(loaded(), failed(new StashCmd.Stash(Option.of("x")), "boom"));
        assertEquals(Option.of("Stash failed: boom"), errorText(failed.model()));
    }

    @Test
    void poppingAListedStashRunsGit() {
        Model model = Update.update(loaded(), new LoadMsg.StashesLoaded(List.of(STASH)))
                .model();

        assertEquals(
                List.of(new StashCmd.Pop(STASH)),
                Update.update(model, new StashMsg.PopRequested(STASH)).cmds());
        assertEquals(
                List.of(),
                Update.update(model, new StashMsg.PopRequested(UNLISTED)).cmds());
    }

    @Test
    void droppingAListedStashAsksForConfirmationFirst() {
        Model model = Update.update(loaded(), new LoadMsg.StashesLoaded(List.of(STASH)))
                .model();

        Next asked = Update.update(model, new StashMsg.DropRequested(STASH));
        assertEquals(List.of(), asked.cmds());
        assertEquals(Option.of(new StashDropPopup(STASH)), asked.model().popup());

        Next confirmed = Update.update(asked.model(), new StashMsg.DropConfirmed());
        assertEquals(List.of(new StashCmd.Drop(STASH)), confirmed.cmds());
        assertEquals(Option.none(), confirmed.model().popup());

        assertEquals(
                Option.none(),
                Update.update(model, new StashMsg.DropRequested(UNLISTED))
                        .model()
                        .popup());
    }

    @Test
    void cancellingTheDropClosesTheDialogWithoutDropping() {
        Model model = Update.update(loaded(), new LoadMsg.StashesLoaded(List.of(STASH)))
                .model();
        Model asked = Update.update(model, new StashMsg.DropRequested(STASH)).model();

        Next cancelled = Update.update(asked, new StashMsg.DropCancelled());

        assertEquals(Option.none(), cancelled.model().popup());
        assertEquals(List.of(), cancelled.cmds());
        assertEquals(
                List.of(), Update.update(model, new StashMsg.DropConfirmed()).cmds());
    }

    @Test
    void droppedLogsTheCommandsAndFailureIsReported() {
        Next dropped = Update.update(loaded(), succeeded(new StashCmd.Drop(STASH), "git stash drop stash@{0}"));
        assertEquals(
                List.of(new LogEntry(Operation.STASH_DROP, executed("git stash drop stash@{0}"))),
                dropped.model().commandLog());

        Next failed = Update.update(loaded(), failed(new StashCmd.Drop(STASH), "no such stash"));
        assertEquals(Option.of("Dropping the stash failed: no such stash"), errorText(failed.model()));
    }

    @Test
    void poppedLogsTheCommandsAndFailureIsReported() {
        Next popped = Update.update(loaded(), succeeded(new StashCmd.Pop(STASH), "git stash pop stash@{0}"));
        assertEquals(
                List.of(new LogEntry(Operation.STASH_POP, executed("git stash pop stash@{0}"))),
                popped.model().commandLog());

        Next failed = Update.update(loaded(), failed(new StashCmd.Pop(STASH), "conflict"));
        assertEquals(Option.of("Unstash failed: conflict"), errorText(failed.model()));
    }
}
