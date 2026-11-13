package net.vanfleteren.jazygit.feature.files.all;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.util.concurrent.atomic.AtomicInteger;
import net.vanfleteren.jazygit.feature.discard.DiscardMsg;
import net.vanfleteren.jazygit.feature.files.FilesError;
import net.vanfleteren.jazygit.feature.stage.StageMsg;
import net.vanfleteren.jazygit.gh.StubGitHub;
import net.vanfleteren.jazygit.git.StubGit;
import net.vanfleteren.jazygit.git.files.StubStatus;
import net.vanfleteren.jazygit.git.model.*;
import net.vanfleteren.jazygit.git.process.GitError;
import net.vanfleteren.jazygit.state.AppError;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Loadable;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Program;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.load.LoadMsg;
import net.vanfleteren.jazygit.state.load.SelectionMsg;
import net.vanfleteren.jazygit.state.view.Preview;
import net.vanfleteren.jazygit.state.view.UnchangedNode;
import org.junit.jupiter.api.Test;

/**
 * Listing all files of the working tree in the files pane (ADR 108): they are read when they are first listed and
 * again with a change to the status, a file without changes is read from disk once highlighted, and staging or
 * discarding one says it has none.
 */
class AllFilesUpdateTest {

    private static final FileEntry CHANGED = new FileEntry("a.txt", ChangeType.MODIFIED, false, true);
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
            public Either<GitError, List<String>> allFiles() {
                listed.incrementAndGet();
                return Either.right(List.of("a.txt", "b.txt", "docs/c.txt"));
            }

            @Override
            public Either<GitError, FileContent> content(String path) {
                return path.equals("b.txt") ? Either.right(B) : StubStatus.super.content(path);
            }
        };
        return TestModels.program(
                new Env(StubGit.of().withStatus(status), StubGitHub.of()), Runnable::run, Runnable::run, Runnable::run);
    }

    @Test
    void allFilesAreReadOnceListedAndListedWithTheChangedOnes() {
        Program program = program();
        assertEquals(0, listed.get());

        program.dispatch(new AllFilesMsg.Toggled());

        assertTrue(program.model().repoView().allFiles());
        assertEquals(1, listed.get());
        assertEquals(
                new Loadable.Loaded<>(
                        List.of(CHANGED, FileEntry.unchanged("b.txt"), FileEntry.unchanged("docs/c.txt"))),
                program.model().repo().workingFiles(true));
        assertEquals(
                new Loadable.Loaded<>(List.of(CHANGED)), program.model().repo().workingFiles(false));
    }

    @Test
    void untilAllFilesAreReadTheyAreLoading() {
        Model model = TestModels.loaded().mapRepoView(view -> view.withAllFiles(true));

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

        program.dispatch(new SelectionMsg.UnchangedFileSelected("b.txt"));

        assertEquals(
                Option.of(new UnchangedNode.File(new Preview<>("b.txt", new Loadable.Loaded<>(B)))),
                program.model().repoView().unchanged());
    }

    @Test
    void aDirectoryWithoutChangesNeedsNothingRead() {
        Program program = program();
        program.dispatch(new AllFilesMsg.Toggled());

        program.dispatch(new SelectionMsg.UnchangedDirectorySelected("docs", 1));

        assertEquals(
                Option.of(new UnchangedNode.Directory("docs", 1)),
                program.model().repoView().unchanged());
    }

    @Test
    void aNodeWithChangesHighlightedShowsItsDiffAgain() {
        Program program = program();
        program.dispatch(new AllFilesMsg.Toggled());
        program.dispatch(new SelectionMsg.FilesSelected(List.of(CHANGED)));
        program.dispatch(new SelectionMsg.UnchangedFileSelected("b.txt"));

        // The diff read before is shown again, without reading it again.
        program.dispatch(new SelectionMsg.FilesSelected(List.of(CHANGED)));

        assertEquals(Option.none(), program.model().repoView().unchanged());
        assertTrue(program.model().repoView().fileDiff().isDefined());
    }

    @Test
    void leavingAllFilesDropsTheNodeWithoutChanges() {
        Program program = program();
        program.dispatch(new AllFilesMsg.Toggled());
        program.dispatch(new SelectionMsg.UnchangedFileSelected("b.txt"));

        program.dispatch(new AllFilesMsg.Toggled());

        assertFalse(program.model().repoView().allFiles());
        assertEquals(Option.none(), program.model().repoView().unchanged());
    }

    @Test
    void stagingOrDiscardingWhatHasNoChangesSaysSo() {
        Model model = TestModels.loaded();
        List<FileEntry> docs = List.of(FileEntry.unchanged("docs/guide/a.md"), FileEntry.unchanged("docs/b.md"));

        assertEquals(
                Option.of(new FilesError.Unchanged("b.txt")),
                error(model, new StageMsg.Requested(List.of(FileEntry.unchanged("b.txt")), Option.none())));
        assertEquals(
                Option.of(new FilesError.Unchanged("docs")),
                error(model, new StageMsg.Requested(docs, Option.of("docs"))));
        assertEquals(Option.of(new FilesError.Unchanged("docs")), error(model, new DiscardMsg.Requested(docs)));
    }

    @Test
    void discardingADirectoryNamesOnlyItsChangedFiles() {
        Model asked = Update.update(
                        TestModels.loaded(), new DiscardMsg.Requested(List.of(CHANGED, FileEntry.unchanged("b.txt"))))
                .model();

        assertEquals(Option.none(), asked.outcome().error());
        assertTrue(asked.popup().isDefined());
        assertTrue(asked.popup().get().toString().contains("a.txt"));
        assertFalse(asked.popup().get().toString().contains("b.txt"));
    }

    private static Option<AppError> error(Model model, net.vanfleteren.jazygit.state.Msg msg) {
        return Update.update(model, msg).model().outcome().error();
    }
}
