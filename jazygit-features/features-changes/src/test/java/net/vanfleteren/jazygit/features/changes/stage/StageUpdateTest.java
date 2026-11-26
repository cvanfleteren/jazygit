package net.vanfleteren.jazygit.features.changes.stage;

import static net.vanfleteren.jazygit.core.git.StubGit.executed;
import static net.vanfleteren.jazygit.kernel.state.TestModels.*;
import static net.vanfleteren.jazygit.kernel.ui.text.ErrorTexts.errorText;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.core.git.model.ChangeType;
import net.vanfleteren.jazygit.core.git.model.Conflict;
import net.vanfleteren.jazygit.core.git.model.FileEntry;
import net.vanfleteren.jazygit.core.git.model.Fixtures;
import net.vanfleteren.jazygit.core.git.model.MarkedFile;
import net.vanfleteren.jazygit.core.repo.RepoPath;
import net.vanfleteren.jazygit.features.changes.stage.StageCmd.Stage;
import net.vanfleteren.jazygit.features.changes.stage.StageCmd.Unstage;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Update;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.load.LoadBranches;
import net.vanfleteren.jazygit.kernel.state.load.LoadStatus;
import net.vanfleteren.jazygit.kernel.state.operation.LogEntry;
import net.vanfleteren.jazygit.kernel.state.view.FileFilter;
import org.junit.jupiter.api.Test;

/**
 * The staging transitions.
 */
class StageUpdateTest {

    private static final FileEntry CONFLICTED =
            FileEntry.conflicted(new RepoPath("a.txt"), new Conflict(Conflict.Side.UPDATED, Conflict.Side.UPDATED));
    private static final FileEntry MODIFIED = Fixtures.fileEntry("b.txt", ChangeType.MODIFIED);

    @Test
    void stagingAFileInConflictReadsItForConflictMarkersFirst() {
        Next next = Update.update(loaded(), new StageMsg.Requested(List.of(CONFLICTED, MODIFIED)));

        assertEquals(
                List.of(new ReadMarkersCmd(
                        List.of(CONFLICTED, MODIFIED), Option.none(), List.of(new RepoPath("a.txt")))),
                next.cmds());
    }

    @Test
    void aFileInConflictWithoutMarkersIsStaged() {
        Next next = Update.update(
                loaded(), new StageMsg.MarkersRead(List.of(CONFLICTED, MODIFIED), Option.none(), List.empty()));

        assertEquals(List.of(new Stage(List.of(new RepoPath("a.txt"), new RepoPath("b.txt")))), next.cmds());
    }

    @Test
    void aFileInConflictWithMarkersAsksFirst() {
        List<MarkedFile> marked = List.of(new MarkedFile(new RepoPath("a.txt"), List.of(3, 9)));

        Next next = Update.update(loaded(), new StageMsg.MarkersRead(List.of(CONFLICTED), Option.none(), marked));

        assertEquals(List.empty(), next.cmds());
        assertEquals(
                Option.of(new MarkersLeftPopup(List.of(CONFLICTED), Option.none(), marked)),
                next.model().popup(MarkersLeftPopup.class));
    }

    @Test
    void stagingAnywayStagesWhatWasAskedAbout() {
        List<MarkedFile> marked = List.of(new MarkedFile(new RepoPath("a.txt"), List.of(3)));
        Model asked = Update.update(
                        loaded(), new StageMsg.MarkersRead(List.of(CONFLICTED, MODIFIED), Option.none(), marked))
                .model();

        Next next = Update.update(asked, new StageMsg.StagedAnyway());

        assertEquals(Option.none(), next.model().popup(MarkersLeftPopup.class));
        assertEquals(List.of(new Stage(List.of(new RepoPath("a.txt"), new RepoPath("b.txt")))), next.cmds());
    }

    @Test
    void stagingAnywayWithNothingAskedDoesNothing() {
        assertEquals(
                List.empty(),
                Update.update(loaded(), new StageMsg.StagedAnyway()).cmds());
    }

    @Test
    void toggleStagesTheFilesWithUnstagedChanges() {
        FileEntry unstaged = Fixtures.fileEntry("a.txt", ChangeType.MODIFIED);
        FileEntry untracked = Fixtures.fileEntry("b.txt", ChangeType.UNTRACKED);
        FileEntry staged = new FileEntry(new RepoPath("c.txt"), ChangeType.MODIFIED, true, false);

        Next next = Update.update(loaded(), new StageMsg.Requested(List.of(unstaged, untracked, staged)));

        assertEquals(List.of(new Stage(List.of(new RepoPath("a.txt"), new RepoPath("b.txt")))), next.cmds());
    }

    @Test
    void toggleStagesAPartiallyStagedFile() {
        FileEntry both = new FileEntry(new RepoPath("a.txt"), ChangeType.MODIFIED, true, true);

        assertEquals(
                List.of(new Stage(List.of(new RepoPath("a.txt")))),
                Update.update(loaded(), new StageMsg.Requested(List.of(both))).cmds());
    }

    @Test
    void toggleUnstagesAddedFilesAndResetsTheOthers() {
        FileEntry added = Fixtures.fileEntry("a.txt", ChangeType.ADDED);
        FileEntry modified = new FileEntry(new RepoPath("b.txt"), ChangeType.MODIFIED, true, false);
        FileEntry deleted = new FileEntry(new RepoPath("c.txt"), ChangeType.DELETED, true, false);

        Next next = Update.update(loaded(), new StageMsg.Requested(List.of(added, modified, deleted)));

        assertEquals(
                List.of(new Unstage(
                        List.of(new RepoPath("a.txt")), List.of(new RepoPath("b.txt"), new RepoPath("c.txt")))),
                next.cmds());
    }

    @Test
    void toggleUnstagesBothSidesOfARename() {
        FileEntry renamed = FileEntry.renamed(new RepoPath("src/b.txt"), new RepoPath("a.txt"), true, false);

        assertEquals(
                List.of(new Unstage(List.of(new RepoPath("src/b.txt")), List.of(new RepoPath("a.txt")))),
                Update.update(loaded(), new StageMsg.Requested(List.of(renamed)))
                        .cmds());
        assertEquals(
                List.of(new Unstage(List.of(new RepoPath("src/b.txt")), List.of(new RepoPath("a.txt")))),
                Update.update(loaded(), new StageMsg.Requested(List.of(renamed), Option.of(new RepoPath("src"))))
                        .cmds());
    }

    @Test
    void toggleStagesTheChangesOfARename() {
        FileEntry edited = FileEntry.renamed(new RepoPath("b.txt"), new RepoPath("a.txt"), true, true);
        FileEntry intentToAdd = FileEntry.renamed(new RepoPath("d.txt"), new RepoPath("c.txt"), false, true);

        assertEquals(
                List.of(new Stage(List.of(new RepoPath("b.txt"), new RepoPath("c.txt"), new RepoPath("d.txt")))),
                Update.update(loaded(), new StageMsg.Requested(List.of(edited, intentToAdd)))
                        .cmds());
    }

    @Test
    void toggleOfADirectoryStagesTheDirectory() {
        FileEntry unstaged = Fixtures.fileEntry("src/a.txt", ChangeType.MODIFIED);
        FileEntry staged = new FileEntry(new RepoPath("src/c.txt"), ChangeType.MODIFIED, true, false);

        assertEquals(
                List.of(new Stage(List.of(new RepoPath("src")))),
                Update.update(
                                loaded(),
                                new StageMsg.Requested(List.of(unstaged, staged), Option.of(new RepoPath("src"))))
                        .cmds());
        assertEquals(
                List.of(new Stage(List.of(new RepoPath(".")))),
                Update.update(loaded(), new StageMsg.Requested(List.of(unstaged, staged), Option.of(new RepoPath(""))))
                        .cmds());
    }

    @Test
    void whileTheFilesAreFilteredADirectoryStandsForTheFilesShownUnderIt() {
        FileEntry unstaged = Fixtures.fileEntry("src/ATest.java", ChangeType.MODIFIED);
        FileEntry staged = new FileEntry(new RepoPath("src/BTest.java"), ChangeType.MODIFIED, true, false);
        FileEntry added = new FileEntry(new RepoPath("src/CTest.java"), ChangeType.ADDED, true, false);
        Model filtered = loaded().mapRepoView(repoView ->
                repoView.mapFiles(files -> files.withFilter(Option.of(new FileFilter("Test", Option.none())))));

        assertEquals(
                List.of(new Stage(List.of(new RepoPath("src/ATest.java")))),
                Update.update(filtered, new StageMsg.Requested(List.of(unstaged, staged), Option.of(new RepoPath(""))))
                        .cmds());
        assertEquals(
                List.of(new Stage(List.of(new RepoPath("src/ATest.java")))),
                Update.update(filtered, new StageMsg.Requested(List.of(unstaged), Option.of(new RepoPath("src"))))
                        .cmds());
        assertEquals(
                List.of(new Unstage(List.of(), List.of(new RepoPath("src/BTest.java")))),
                Update.update(filtered, new StageMsg.Requested(List.of(staged), Option.of(new RepoPath(""))))
                        .cmds());
        assertEquals(
                List.of(new Unstage(List.of(new RepoPath("src/CTest.java")), List.of(new RepoPath("src/BTest.java")))),
                Update.update(filtered, new StageMsg.Requested(List.of(added, staged), Option.of(new RepoPath("src"))))
                        .cmds());
    }

    @Test
    void anOpenLineWithNothingTypedFiltersNoDirectory() {
        FileEntry unstaged = Fixtures.fileEntry("src/a.txt", ChangeType.MODIFIED);
        Model typing = loaded().mapRepoView(repoView ->
                repoView.mapFiles(files -> files.withFilter(Option.of(new FileFilter("", Option.of(""))))));

        assertEquals(
                List.of(new Stage(List.of(new RepoPath("src")))),
                Update.update(typing, new StageMsg.Requested(List.of(unstaged), Option.of(new RepoPath("src"))))
                        .cmds());
    }

    @Test
    void toggleOfAStagedDirectoryResetsItUnlessItHasNewlyAddedFiles() {
        FileEntry modified = new FileEntry(new RepoPath("src/b.txt"), ChangeType.MODIFIED, true, false);
        FileEntry deleted = new FileEntry(new RepoPath("src/c.txt"), ChangeType.DELETED, true, false);
        FileEntry added = Fixtures.fileEntry("src/a.txt", ChangeType.ADDED);

        assertEquals(
                List.of(new Unstage(List.of(), List.of(new RepoPath(".")))),
                Update.update(loaded(), new StageMsg.Requested(List.of(modified, deleted), Option.of(new RepoPath(""))))
                        .cmds());
        assertEquals(
                List.of(new Unstage(List.of(new RepoPath("src/a.txt")), List.of(new RepoPath("src/b.txt")))),
                Update.update(
                                loaded(),
                                new StageMsg.Requested(List.of(added, modified), Option.of(new RepoPath("src"))))
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
                List.of(new LogEntry(StageCmd.STAGE, executed("git add -- a.txt"), Instant.EPOCH)),
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
                List.of(
                        new LoadStatus(),
                        LoadBranches.LISTED,
                        new net.vanfleteren.jazygit.kernel.state.load.LoadStashes(),
                        new net.vanfleteren.jazygit.kernel.state.load.LoadWorktrees()),
                Update.update(loaded(), succeeded(new StageCmd.Stage(List.of())))
                        .cmds());

        Next failed = Update.update(loaded(), failed(new StageCmd.Stage(List.of()), "fatal: pathspec"));
        assertEquals(Option.of("Staging failed: fatal: pathspec"), errorText(failed.model()));
        assertEquals(
                List.of(
                        new LoadStatus(),
                        LoadBranches.LISTED,
                        new net.vanfleteren.jazygit.kernel.state.load.LoadStashes(),
                        new net.vanfleteren.jazygit.kernel.state.load.LoadWorktrees()),
                failed.cmds());
    }
}
