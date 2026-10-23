package net.vanfleteren.jazygit.feature.stage;

import static net.vanfleteren.jazygit.git.StubGitProvider.executed;
import static net.vanfleteren.jazygit.state.TestModels.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.stage.StageCmd.Stage;
import net.vanfleteren.jazygit.feature.stage.StageCmd.Unstage;
import net.vanfleteren.jazygit.git.model.ChangeType;
import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.git.model.Fixtures;
import net.vanfleteren.jazygit.state.Cmd.LoadBranches;
import net.vanfleteren.jazygit.state.Cmd.LoadStatus;
import net.vanfleteren.jazygit.state.LogEntry;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;
import org.junit.jupiter.api.Test;

/**
 * The staging transitions.
 */
class StageUpdateTest {

    @Test
    void toggleStagesTheFilesWithUnstagedChanges() {
        FileEntry unstaged = Fixtures.fileEntry("a.txt", ChangeType.MODIFIED);
        FileEntry untracked = Fixtures.fileEntry("b.txt", ChangeType.UNTRACKED);
        FileEntry staged = new FileEntry("c.txt", ChangeType.MODIFIED, true, false);

        Next next = Update.update(loaded(), new StageMsg.Requested(List.of(unstaged, untracked, staged)));

        assertEquals(List.of(new Stage(List.of("a.txt", "b.txt"))), next.cmds());
    }

    @Test
    void toggleStagesAPartiallyStagedFile() {
        FileEntry both = new FileEntry("a.txt", ChangeType.MODIFIED, true, true);

        assertEquals(
                List.of(new Stage(List.of("a.txt"))),
                Update.update(loaded(), new StageMsg.Requested(List.of(both))).cmds());
    }

    @Test
    void toggleUnstagesAddedFilesAndResetsTheOthers() {
        FileEntry added = Fixtures.fileEntry("a.txt", ChangeType.ADDED);
        FileEntry modified = new FileEntry("b.txt", ChangeType.MODIFIED, true, false);
        FileEntry deleted = new FileEntry("c.txt", ChangeType.DELETED, true, false);

        Next next = Update.update(loaded(), new StageMsg.Requested(List.of(added, modified, deleted)));

        assertEquals(List.of(new Unstage(List.of("a.txt"), List.of("b.txt", "c.txt"))), next.cmds());
    }

    @Test
    void toggleUnstagesBothSidesOfARename() {
        FileEntry renamed = FileEntry.renamed("src/b.txt", "a.txt", true, false);

        assertEquals(
                List.of(new Unstage(List.of("src/b.txt"), List.of("a.txt"))),
                Update.update(loaded(), new StageMsg.Requested(List.of(renamed)))
                        .cmds());
        assertEquals(
                List.of(new Unstage(List.of("src/b.txt"), List.of("a.txt"))),
                Update.update(loaded(), new StageMsg.Requested(List.of(renamed), Option.of("src")))
                        .cmds());
    }

    @Test
    void toggleStagesTheChangesOfARename() {
        FileEntry edited = FileEntry.renamed("b.txt", "a.txt", true, true);
        FileEntry intentToAdd = FileEntry.renamed("d.txt", "c.txt", false, true);

        assertEquals(
                List.of(new Stage(List.of("b.txt", "c.txt", "d.txt"))),
                Update.update(loaded(), new StageMsg.Requested(List.of(edited, intentToAdd)))
                        .cmds());
    }

    @Test
    void toggleOfADirectoryStagesTheDirectory() {
        FileEntry unstaged = Fixtures.fileEntry("src/a.txt", ChangeType.MODIFIED);
        FileEntry staged = new FileEntry("src/c.txt", ChangeType.MODIFIED, true, false);

        assertEquals(
                List.of(new Stage(List.of("src"))),
                Update.update(loaded(), new StageMsg.Requested(List.of(unstaged, staged), Option.of("src")))
                        .cmds());
        assertEquals(
                List.of(new Stage(List.of("."))),
                Update.update(loaded(), new StageMsg.Requested(List.of(unstaged, staged), Option.of("")))
                        .cmds());
    }

    @Test
    void toggleOfAStagedDirectoryResetsItUnlessItHasNewlyAddedFiles() {
        FileEntry modified = new FileEntry("src/b.txt", ChangeType.MODIFIED, true, false);
        FileEntry deleted = new FileEntry("src/c.txt", ChangeType.DELETED, true, false);
        FileEntry added = Fixtures.fileEntry("src/a.txt", ChangeType.ADDED);

        assertEquals(
                List.of(new Unstage(List.of(), List.of("."))),
                Update.update(loaded(), new StageMsg.Requested(List.of(modified, deleted), Option.of("")))
                        .cmds());
        assertEquals(
                List.of(new Unstage(List.of("src/a.txt"), List.of("src/b.txt"))),
                Update.update(loaded(), new StageMsg.Requested(List.of(added, modified), Option.of("src")))
                        .cmds());
    }

    @Test
    void toggleOfNothingDoesNothing() {
        assertEquals(
                List.of(),
                Update.update(loaded(), new StageMsg.Requested(List.of())).cmds());
    }

    @Test
    void doneLogsTheExecutedCommands() {
        Next staged = Update.update(loaded(), succeeded(new StageCmd.Stage(List.of()), "git add -- a.txt"));
        assertEquals(
                List.of(new LogEntry(StageCmd.STAGE, executed("git add -- a.txt"))),
                staged.model().outcome().commandLog());

        Next unstaged = Update.update(
                staged.model(),
                succeeded(new StageCmd.Unstage(List.of(), List.of()), "git reset --quiet HEAD -- a.txt"));
        assertEquals(
                List.of(StageCmd.STAGE, StageCmd.UNSTAGE),
                unstaged.model().outcome().commandLog().map(LogEntry::operation).toList());

        assertEquals(
                List.of(),
                Update.update(loaded(), succeeded(new StageCmd.Stage(List.of())))
                        .model()
                        .outcome()
                        .commandLog());
    }

    @Test
    void toggledReloadsAndFailureIsReported() {
        assertEquals(
                List.of(new LoadStatus(), new LoadBranches(), new net.vanfleteren.jazygit.state.Cmd.LoadStashes()),
                Update.update(loaded(), succeeded(new StageCmd.Stage(List.of())))
                        .cmds());

        Next failed = Update.update(loaded(), failed(new StageCmd.Stage(List.of()), "fatal: pathspec"));
        assertEquals(Option.of("Staging failed: fatal: pathspec"), errorText(failed.model()));
        assertEquals(
                List.of(new LoadStatus(), new LoadBranches(), new net.vanfleteren.jazygit.state.Cmd.LoadStashes()),
                failed.cmds());
    }
}
