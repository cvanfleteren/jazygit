package net.vanfleteren.jazygit.features.changes.files.all;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.util.concurrent.atomic.AtomicInteger;
import net.vanfleteren.jazygit.core.git.StubGit;
import net.vanfleteren.jazygit.core.git.files.StubStatus;
import net.vanfleteren.jazygit.core.git.model.*;
import net.vanfleteren.jazygit.core.git.process.GitError;
import net.vanfleteren.jazygit.core.repo.*;
import net.vanfleteren.jazygit.features.changes.discard.DiscardMsg;
import net.vanfleteren.jazygit.features.changes.files.FilesError;
import net.vanfleteren.jazygit.features.changes.stage.StageMsg;
import net.vanfleteren.jazygit.kernel.state.AppError;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Loadable;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Program;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.state.Update;
import net.vanfleteren.jazygit.kernel.state.load.LoadMsg;
import net.vanfleteren.jazygit.kernel.state.load.SelectionMsg;
import net.vanfleteren.jazygit.kernel.state.view.FilesShown;
import net.vanfleteren.jazygit.kernel.state.view.Preview;
import net.vanfleteren.jazygit.kernel.state.view.UnchangedNode;
import org.junit.jupiter.api.Test;

/**
 * Listing all files of the working tree in the files pane (ADR 108): they are read when they are first listed and
 * again with a change to the status, a file without changes is read from disk once highlighted, and staging or
 * discarding one says it has none.
 */
class AllFilesUpdateTest {

    private static final FileEntry CHANGED = new FileEntry(new RepoPath("a.txt"), ChangeType.MODIFIED, false, true);
    private static final FileContent B = new FileContent.Text(
            DiffText.of("diff --git a/b.txt b/b.txt\n--- a/b.txt\n+++ b/b.txt\n@@ -1,1 +1,1 @@\n b\n"));

    private final AtomicInteger listed = new AtomicInteger();

    private Program program() {
        StubStatus status = new StubStatus() {
            @Override
            public Either<GitError, RepoStatus> read() {
                return Either.right(
                        new RepoStatus(Head.onBranch(new BranchName("main"), new Sha("aaaa")), List.of(CHANGED)));
            }

            @Override
            public Either<GitError, List<RepoPath>> allFiles() {
                listed.incrementAndGet();
                return Either.right(List.of("a.txt", "b.txt", "docs/c.txt").map(RepoPath::new));
            }

            @Override
            public Either<GitError, FileContent> content(RepoPath path) {
                return path.equals(new RepoPath("b.txt")) ? Either.right(B) : StubStatus.super.content(path);
            }
        };
        return TestModels.program(
                new Env(StubGit.of().withStatus(status)), Runnable::run, Runnable::run, Runnable::run);
    }

    @Test
    void allFilesAreReadOnceListedAndListedWithTheChangedOnes() {
        Program program = program();
        assertEquals(0, listed.get());

        program.dispatch(new AllFilesMsg.Toggled());

        assertTrue(program.model().repoView().files().shown().all());
        assertEquals(1, listed.get());
        assertEquals(
                new Loadable.Loaded<>(List.of(
                        CHANGED,
                        FileEntry.unchanged(new RepoPath("b.txt")),
                        FileEntry.unchanged(new RepoPath("docs/c.txt")))),
                program.model().repo().workingFiles(true));
        assertEquals(
                new Loadable.Loaded<>(List.of(CHANGED)), program.model().repo().workingFiles(false));
    }

    @Test
    void untilAllFilesAreReadTheyAreLoading() {
        Model model = TestModels.loaded().mapRepoView(view -> view.mapFiles(files -> files.withShown(FilesShown.ALL)));

        assertEquals(Loadable.loading(), model.repo().workingFiles(true));
    }

    @Test
    void theyAreReadAgainOnlyWhenTheStatusChangesWhileTheyAreListed() {
        Program program = program();
        RepoStatus same = program.model().repo().status().toOption().get();
        program.dispatch(new LoadMsg.StatusLoaded(same));
        assertEquals(0, listed.get());

        program.dispatch(new AllFilesMsg.Toggled());
        program.dispatch(new LoadMsg.StatusLoaded(same));
        assertEquals(1, listed.get());

        program.dispatch(new LoadMsg.StatusLoaded(same.withFiles(List.empty())));
        assertEquals(2, listed.get());

        program.dispatch(new AllFilesMsg.Toggled());
        program.dispatch(new LoadMsg.StatusLoaded(same));
        assertEquals(2, listed.get());
    }

    @Test
    void aFileWithoutChangesHighlightedIsReadFromDisk() {
        Program program = program();
        program.dispatch(new AllFilesMsg.Toggled());

        program.dispatch(new SelectionMsg.UnchangedFileSelected(new RepoPath("b.txt")));

        assertEquals(
                Option.of(new UnchangedNode.File(new Preview<>(new RepoPath("b.txt"), new Loadable.Loaded<>(B)))),
                program.model().repoView().files().unchanged());
    }

    @Test
    void aDirectoryWithoutChangesNeedsNothingRead() {
        Program program = program();
        program.dispatch(new AllFilesMsg.Toggled());

        program.dispatch(new SelectionMsg.UnchangedDirectorySelected(new RepoPath("docs"), 1));

        assertEquals(
                Option.of(new UnchangedNode.Directory(new RepoPath("docs"), 1)),
                program.model().repoView().files().unchanged());
    }

    @Test
    void aNodeWithChangesHighlightedShowsItsDiffAgain() {
        Program program = program();
        program.dispatch(new AllFilesMsg.Toggled());
        program.dispatch(new SelectionMsg.FilesSelected(List.of(CHANGED)));
        program.dispatch(new SelectionMsg.UnchangedFileSelected(new RepoPath("b.txt")));

        // The diff read before is shown again, without reading it again.
        program.dispatch(new SelectionMsg.FilesSelected(List.of(CHANGED)));

        assertEquals(Option.none(), program.model().repoView().files().unchanged());
        assertTrue(program.model().repoView().preview().fileDiff().isDefined());
    }

    @Test
    void leavingAllFilesDropsTheNodeWithoutChanges() {
        Program program = program();
        program.dispatch(new AllFilesMsg.Toggled());
        program.dispatch(new SelectionMsg.UnchangedFileSelected(new RepoPath("b.txt")));

        program.dispatch(new AllFilesMsg.Toggled());

        assertFalse(program.model().repoView().files().shown().all());
        assertEquals(Option.none(), program.model().repoView().files().unchanged());
    }

    @Test
    void stagingOrDiscardingWhatHasNoChangesSaysSo() {
        Model model = TestModels.loaded();
        List<FileEntry> docs = List.of(
                FileEntry.unchanged(new RepoPath("docs/guide/a.md")), FileEntry.unchanged(new RepoPath("docs/b.md")));

        assertEquals(
                Option.of(new FilesError.Unchanged(new RepoPath("b.txt"))),
                error(
                        model,
                        new StageMsg.Requested(List.of(FileEntry.unchanged(new RepoPath("b.txt"))), Option.none())));
        assertEquals(
                Option.of(new FilesError.Unchanged(new RepoPath("docs"))),
                error(model, new StageMsg.Requested(docs, Option.of(new RepoPath("docs")))));
        assertEquals(
                Option.of(new FilesError.Unchanged(new RepoPath("docs"))),
                error(model, new DiscardMsg.Requested(docs)));
    }

    @Test
    void discardingADirectoryNamesOnlyItsChangedFiles() {
        Model asked = Update.update(
                        TestModels.loaded(),
                        new DiscardMsg.Requested(List.of(CHANGED, FileEntry.unchanged(new RepoPath("b.txt")))))
                .model();

        assertEquals(Option.none(), asked.outcome().error());
        assertTrue(asked.popup().isDefined());
        assertTrue(asked.popup().get().toString().contains("a.txt"));
        assertFalse(asked.popup().get().toString().contains("b.txt"));
    }

    private static Option<AppError> error(Model model, net.vanfleteren.jazygit.kernel.state.Msg msg) {
        return Update.update(model, msg).model().outcome().error();
    }
}
