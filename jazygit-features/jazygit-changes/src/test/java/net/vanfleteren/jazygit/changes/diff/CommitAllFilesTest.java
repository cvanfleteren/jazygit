package net.vanfleteren.jazygit.changes.diff;

import static net.vanfleteren.jazygit.state.TestModels.loaded;
import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.util.concurrent.atomic.AtomicInteger;
import net.vanfleteren.jazygit.gh.StubGitHub;
import net.vanfleteren.jazygit.git.StubGit;
import net.vanfleteren.jazygit.git.files.StubDiffs;
import net.vanfleteren.jazygit.git.model.ChangeType;
import net.vanfleteren.jazygit.git.model.CommitFile;
import net.vanfleteren.jazygit.git.model.DiffOptions;
import net.vanfleteren.jazygit.git.model.DiffText;
import net.vanfleteren.jazygit.git.model.FileContent;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.process.GitError;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Loadable;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Program;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;
import net.vanfleteren.jazygit.state.diff.Changes;
import net.vanfleteren.jazygit.state.load.LoadCommitFileDiff;
import net.vanfleteren.jazygit.state.load.LoadCommitTree;
import net.vanfleteren.jazygit.state.load.LoadMsg;
import net.vanfleteren.jazygit.state.load.ReadCommitFileContent;
import net.vanfleteren.jazygit.state.load.SelectionMsg;
import net.vanfleteren.jazygit.state.view.CommitFilesView;
import net.vanfleteren.jazygit.state.view.Preview;
import net.vanfleteren.jazygit.state.view.UnchangedNode;
import net.vanfleteren.jazygit.ui.keys.Scopes;
import org.junit.jupiter.api.Test;

/**
 * Listing all files of a commit in the files of it (ADR 128): they are read once, with those it changed, a file it did
 * not change is read from the commit once highlighted, and a directory without changes says how many files it holds.
 */
class CommitAllFilesTest {

    private static final Sha SHA = new Sha("aaaa");
    private static final Changes COMMIT = new Changes.OfCommit(SHA);
    private static final CommitFile A = new CommitFile("a.txt", ChangeType.MODIFIED);
    private static final CommitFile GONE = new CommitFile("gone.txt", ChangeType.DELETED);
    private static final List<CommitFile> ALL = List.of(
            A, new CommitFile("b.txt", ChangeType.UNCHANGED), new CommitFile("docs/c.txt", ChangeType.UNCHANGED), GONE);
    private static final FileContent B = new FileContent.Text(
            DiffText.of("diff --git a/b.txt b/b.txt\n--- a/b.txt\n+++ b/b.txt\n@@ -1,1 +1,1 @@\n b\n"));

    private final AtomicInteger listed = new AtomicInteger();

    @Test
    void allFilesAreTheChangedOnesWithTheOthersOfTheCommitUnchangedSorted() {
        assertEquals(ALL, CommitFilesView.merged(List.of(A, GONE), List.of("a.txt", "b.txt", "docs/c.txt")));
    }

    @Test
    void allFilesAreReadTheFirstTimeTheyAreListedAndOnlyThen() {
        Next on = Update.update(withFiles(), new CommitFilesMsg.AllToggled());

        CommitFilesView view = on.model().repoView().files().commitFiles().get();
        assertTrue(view.allFiles());
        assertEquals(Option.of(Loadable.loading()), view.tree());
        assertEquals(Loadable.loading(), view.listedFiles());
        assertEquals(List.of(new LoadCommitTree(COMMIT)), on.cmds());

        Model read = Update.update(on.model(), new LoadMsg.CommitTreeLoaded(COMMIT, ALL))
                .model();
        assertEquals(
                new Loadable.Loaded<>(ALL),
                read.repoView().files().commitFiles().get().listedFiles());

        Next off = Update.update(read, new CommitFilesMsg.AllToggled());
        assertFalse(off.model().repoView().files().commitFiles().get().allFiles());
        assertEquals(
                new Loadable.Loaded<>(List.of(A, GONE)),
                off.model().repoView().files().commitFiles().get().listedFiles());

        Next again = Update.update(off.model(), new CommitFilesMsg.AllToggled());
        assertEquals(List.empty(), again.cmds());
        assertEquals(
                new Loadable.Loaded<>(ALL),
                again.model().repoView().files().commitFiles().get().listedFiles());
    }

    @Test
    void allFilesThatCouldNotBeReadAreReadAgainWhenListedAgain() {
        Model on = Update.update(withFiles(), new CommitFilesMsg.AllToggled()).model();
        Model failed = Update.update(
                        on, new LoadMsg.Failed(new LoadCommitTree(COMMIT), Failure.of(new GitError.Interrupted("x"))))
                .model();
        Model off = Update.update(failed, new CommitFilesMsg.AllToggled()).model();

        Next again = Update.update(off, new CommitFilesMsg.AllToggled());

        assertEquals(List.of(new LoadCommitTree(COMMIT)), again.cmds());
    }

    @Test
    void theLoadReadsTheChangedFilesAndTheTreeAndMergesThem() {
        Program program = program();
        program.dispatch(new CommitFilesMsg.Opened(COMMIT, Scopes.COMMITS.pane()));

        program.dispatch(new CommitFilesMsg.AllToggled());

        assertEquals(1, listed.get());
        assertEquals(
                new Loadable.Loaded<>(ALL),
                program.model().repoView().files().commitFiles().get().listedFiles());
    }

    @Test
    void aFileTheCommitDidNotChangeIsReadFromTheCommit() {
        Program program = program();
        program.dispatch(new CommitFilesMsg.Opened(COMMIT, Scopes.COMMITS.pane()));
        program.dispatch(new CommitFilesMsg.AllToggled());

        program.dispatch(new SelectionMsg.UnchangedFileSelected("b.txt"));

        assertEquals(
                Option.of(new UnchangedNode.File(new Preview<>("b.txt", new Loadable.Loaded<>(B)))),
                program.model().repoView().files().commitFiles().get().unchanged());
        // The working tree's node without changes stays as it is.
        assertEquals(Option.none(), program.model().repoView().files().unchanged());
    }

    @Test
    void readingAFileOfTheCommitIsTheLatestOfItsSlot() {
        Model on = Update.update(withFiles(), new CommitFilesMsg.AllToggled()).model();

        Next selected = Update.update(on, new SelectionMsg.UnchangedFileSelected("b.txt"));

        assertEquals(List.of(new ReadCommitFileContent(COMMIT, "b.txt")), selected.cmds());
    }

    @Test
    void aDirectoryWithoutChangesNeedsNothingRead() {
        Model on = Update.update(withFiles(), new CommitFilesMsg.AllToggled()).model();

        Next selected = Update.update(on, new SelectionMsg.UnchangedDirectorySelected("docs", 1));

        assertEquals(
                Option.of(new UnchangedNode.Directory("docs", 1)),
                selected.model().repoView().files().commitFiles().get().unchanged());
        assertEquals(List.empty(), selected.cmds());
    }

    @Test
    void aNodeWithChangesHighlightedShowsItsDiffAgain() {
        Model on = Update.update(withFiles(), new CommitFilesMsg.AllToggled()).model();
        Model diff = Update.update(on, new SelectionMsg.CommitFilesSelected(List.of(A)))
                .model();
        Model file = Update.update(diff, new SelectionMsg.UnchangedFileSelected("b.txt"))
                .model();

        // The diff read before is shown again, without reading it again.
        Next back = Update.update(file, new SelectionMsg.CommitFilesSelected(List.of(A)));

        assertEquals(
                Option.none(),
                back.model().repoView().files().commitFiles().get().unchanged());
        assertEquals(
                Option.of(List.of(A)),
                back.model().repoView().files().commitFiles().get().diff().map(Preview::key));
        assertFalse(back.cmds().exists(LoadCommitFileDiff.class::isInstance));
    }

    @Test
    void leavingAllFilesDropsTheNodeWithoutChanges() {
        Model on = Update.update(withFiles(), new CommitFilesMsg.AllToggled()).model();
        Model file = Update.update(on, new SelectionMsg.UnchangedDirectorySelected("docs", 1))
                .model();

        Model off = Update.update(file, new CommitFilesMsg.AllToggled()).model();

        assertEquals(Option.none(), off.repoView().files().commitFiles().get().unchanged());
    }

    @Test
    void allFilesOfAViewClosedSinceAreDropped() {
        Model on = Update.update(withFiles(), new CommitFilesMsg.AllToggled()).model();
        Model closed = Update.update(on, new CommitFilesMsg.Closed()).model();

        Model read =
                Update.update(closed, new LoadMsg.CommitTreeLoaded(COMMIT, ALL)).model();

        assertEquals(Option.none(), read.repoView().files().commitFiles());
    }

    private static Model withFiles() {
        Model opened = Update.update(loaded(), new CommitFilesMsg.Opened(COMMIT, Scopes.COMMITS.pane()))
                .model();
        return Update.update(opened, new LoadMsg.CommitFilesLoaded(COMMIT, List.of(A, GONE)))
                .model();
    }

    private Program program() {
        StubDiffs diffs = new StubDiffs() {
            @Override
            public Either<GitError, List<CommitFile>> commitFiles(Sha sha) {
                return Either.right(List.of(A, GONE));
            }

            @Override
            public Either<GitError, List<String>> treeFiles(Sha sha) {
                listed.incrementAndGet();
                return Either.right(List.of("a.txt", "b.txt", "docs/c.txt"));
            }

            @Override
            public Either<GitError, FileContent> treeContent(Sha sha, String path) {
                return sha.equals(SHA) && path.equals("b.txt")
                        ? Either.right(B)
                        : StubDiffs.super.treeContent(sha, path);
            }

            @Override
            public Either<GitError, DiffText> commitFileChanges(Sha sha, List<CommitFile> files, DiffOptions options) {
                return Either.right(DiffText.of(""));
            }
        };
        return TestModels.program(
                new Env(StubGit.of().withDiffs(diffs), StubGitHub.of()), Runnable::run, Runnable::run, Runnable::run);
    }
}
