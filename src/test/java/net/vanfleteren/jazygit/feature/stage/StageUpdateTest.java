package net.vanfleteren.jazygit.feature.stage;

import net.vanfleteren.jazygit.feature.stage.StageCmd.Stage;
import net.vanfleteren.jazygit.feature.stage.StageCmd.Unstage;
import net.vanfleteren.jazygit.git.model.ChangeType;
import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.state.Cmd.LoadBranches;
import net.vanfleteren.jazygit.state.Cmd.LoadStatus;
import net.vanfleteren.jazygit.state.LogEntry;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static net.vanfleteren.jazygit.state.TestModels.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The staging transitions.
 */
class StageUpdateTest {

    @Test
    void toggleStagesTheFilesWithUnstagedChanges() {
        FileEntry unstaged = new FileEntry("a.txt", ChangeType.MODIFIED);
        FileEntry untracked = new FileEntry("b.txt", ChangeType.UNTRACKED);
        FileEntry staged = new FileEntry("c.txt", ChangeType.MODIFIED, true, false);

        Next next = Update.update(loaded(), new StageMsg.Requested(List.of(unstaged, untracked, staged)));

        assertEquals(List.of(new Stage(List.of("a.txt", "b.txt"))), next.cmds());
    }

    @Test
    void toggleStagesAPartiallyStagedFile() {
        FileEntry both = new FileEntry("a.txt", ChangeType.MODIFIED, true, true);

        assertEquals(List.of(new Stage(List.of("a.txt"))),
                Update.update(loaded(), new StageMsg.Requested(List.of(both))).cmds());
    }

    @Test
    void toggleUnstagesAddedFilesAndResetsTheOthers() {
        FileEntry added = new FileEntry("a.txt", ChangeType.ADDED);
        FileEntry modified = new FileEntry("b.txt", ChangeType.MODIFIED, true, false);
        FileEntry deleted = new FileEntry("c.txt", ChangeType.DELETED, true, false);

        Next next = Update.update(loaded(), new StageMsg.Requested(List.of(added, modified, deleted)));

        assertEquals(List.of(new Unstage(List.of("a.txt"), List.of("b.txt", "c.txt"))), next.cmds());
    }

    @Test
    void toggleOfNothingDoesNothing() {
        assertEquals(List.of(), Update.update(loaded(), new StageMsg.Requested(List.of())).cmds());
    }

    @Test
    void doneLogsTheExecutedCommands() {
        Next staged = Update.update(loaded(), new StageMsg.Done(StageMsg.Action.STAGE, List.of("git add -- a.txt")));
        assertEquals(List.of(new LogEntry("Stage", List.of("git add -- a.txt"))), staged.model().commandLog());

        Next unstaged = Update.update(staged.model(),
                new StageMsg.Done(StageMsg.Action.UNSTAGE, List.of("git reset --quiet HEAD -- a.txt")));
        assertEquals(List.of("Stage", "Unstage"), unstaged.model().commandLog().stream().map(LogEntry::title).toList());

        assertEquals(List.of(), Update.update(loaded(), new StageMsg.Done(StageMsg.Action.STAGE, List.of()))
                .model().commandLog());
    }

    @Test
    void toggledReloadsAndFailureIsReported() {
        assertEquals(List.of(new LoadStatus(), new LoadBranches()),
                Update.update(loaded(), new StageMsg.Done(StageMsg.Action.STAGE, List.of())).cmds());

        Next failed = Update.update(loaded(), new StageMsg.Failed("fatal: pathspec"));
        assertEquals(Optional.of("Staging failed: fatal: pathspec"), failed.model().error());
        assertEquals(List.of(new LoadStatus(), new LoadBranches()), failed.cmds());
    }
}
