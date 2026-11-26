package net.vanfleteren.jazygit.features.changes.panes.files;

import static org.junit.jupiter.api.Assertions.*;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.tui.event.KeyCode;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.gh.model.CommentRights;
import net.vanfleteren.jazygit.core.gh.model.PullRequestDetails;
import net.vanfleteren.jazygit.core.gh.model.ReviewRemarks;
import net.vanfleteren.jazygit.core.gh.model.ReviewThread;
import net.vanfleteren.jazygit.core.git.Git;
import net.vanfleteren.jazygit.core.git.StubGit;
import net.vanfleteren.jazygit.core.git.files.Status;
import net.vanfleteren.jazygit.core.git.files.StubStatus;
import net.vanfleteren.jazygit.core.git.model.*;
import net.vanfleteren.jazygit.core.git.process.GitError;
import net.vanfleteren.jazygit.core.repo.*;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.PrNumber;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.features.changes.diff.CommitFilesMsg;
import net.vanfleteren.jazygit.features.github.remarks.RemarksMsg;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.state.Update;
import net.vanfleteren.jazygit.kernel.state.diff.BaseTip;
import net.vanfleteren.jazygit.kernel.state.diff.Changes;
import net.vanfleteren.jazygit.kernel.state.diff.DiffSide;
import net.vanfleteren.jazygit.kernel.state.load.LoadMsg;
import net.vanfleteren.jazygit.kernel.state.load.SelectionMsg;
import net.vanfleteren.jazygit.kernel.ui.RenderedText;
import net.vanfleteren.jazygit.kernel.ui.SettledPilot;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;
import org.junit.jupiter.api.Test;

/**
 * The tests of FilesPanelTest with GitHub in the app (ADR 171).
 */
class FilesPanelGitHubTest {

    private static final Changes COMMIT = new Changes.OfCommit(new Sha("aaaa"));

    private static final String MODEL = "core/src/main/java/model";

    private static final List<CommitFile> MODEL_AND_TEST = List.of(
            new CommitFile(MODEL + "/WordDiff.java", ChangeType.MODIFIED),
            new CommitFile("core/src/test/java/model/WordDiffTest.java", ChangeType.MODIFIED));

    // Rows: /, src, main, A.java, test, ATest.java, BTest.java, README.md.
    private static final List<FileEntry> FILTERED = List.of(
            Fixtures.fileEntry("src/main/A.java", ChangeType.MODIFIED),
            Fixtures.fileEntry("src/test/ATest.java", ChangeType.MODIFIED),
            Fixtures.fileEntry("src/test/BTest.java", ChangeType.MODIFIED),
            Fixtures.fileEntry("README.md", ChangeType.MODIFIED));

    private final FilesPanel panel = new FilesPanel(msg -> {}, () -> {});

    /**
     * The name of the file selected once only the {@code remaining} files are changed, when {@code selected}
     * was, among the changed {@code names}, which are all in the root.
     */
    private String selectedAfter(String selected, List<String> names, List<String> remaining) throws Exception {
        // Below the root.
        return selectedAfter(names.indexOf(selected) + 1, names, remaining);
    }

    /**
     * The name of the node selected once only the {@code remaining} files are changed, when the node in
     * {@code row} was, among the changed {@code names}.
     */
    private String selectedAfter(int row, List<String> names, List<String> remaining) throws Exception {
        Model before = TestModels.loaded(fixed(names.map(n -> Fixtures.fileEntry(n, ChangeType.MODIFIED))));
        Model after = TestModels.loaded(fixed(remaining.map(n -> Fixtures.fileEntry(n, ChangeType.MODIFIED))));

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(before, FilesPanel.PANE))) {
            RenderedText.of(testRunner, () -> panel.render(before, FilesPanel.PANE));
            RenderedText.of(testRunner, () -> panel.tree().selected(row));
            RenderedText.of(testRunner, () -> panel.render(after, FilesPanel.PANE));
            String name = panel.tree().selectedNode().data().name();

            SettledPilot.of(testRunner).quit();
            return name;
        }
    }

    @Test
    void theFilesOfABranchNameTheBaseTheyAreComparedWith() throws Exception {
        Changes of = new Changes.OfBranch(
                new BranchName("feature"),
                new BaseTip(new BranchRef.Remote(new RemoteBranch("origin", new BranchName("main"))), new Sha("bbbb")),
                new Sha("aaaa"));
        Model open = Update.update(
                        Update.update(
                                        net.vanfleteren.jazygit.kernel.content.PullRequestContentTest.withGitHub(),
                                        new CommitFilesMsg.Opened(of, Scopes.BRANCHES.pane()))
                                .model(),
                        new LoadMsg.CommitFilesLoaded(of, List.of(new CommitFile("b.txt", ChangeType.ADDED))))
                .model();

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(open, FilesPanel.PANE))) {
            String files = RenderedText.ofLarge(testRunner, () -> panel.render(open, FilesPanel.PANE));
            assertTrue(files.contains("Files of feature · vs origin/main"), files);

            SettledPilot.of(testRunner).quit();
        }
    }

    @Test
    void theFilesOfAPullRequestCountTheirUnresolvedRemarksByEitherPathOfARename() throws Exception {
        Changes of = new Changes.OfPullRequest(new PullRequestCommits(
                new PrNumber(7), "owner/repo", "", new Sha("b".repeat(40)), new Sha("a".repeat(40))));
        Model open = Update.update(
                        Update.update(
                                        net.vanfleteren.jazygit.kernel.content.PullRequestContentTest.withGitHub(),
                                        new CommitFilesMsg.Opened(of, Scopes.BRANCHES.pane()))
                                .model(),
                        new LoadMsg.CommitFilesLoaded(
                                of,
                                List.of(
                                        new CommitFile("new.txt", ChangeType.RENAMED, Option.of("old.txt")),
                                        new CommitFile("b.txt", ChangeType.MODIFIED))))
                .model();
        // git sees a rename where GitHub sees a file deleted and one added: a remark on the old file has its path.
        Model read = Update.update(
                        open,
                        new RemarksMsg.Loaded(
                                new PrNumber(7),
                                new ReviewRemarks(
                                        PullRequestDetails.State.MERGED,
                                        Option.none(),
                                        Option.none(),
                                        List.of(
                                                remark("old.txt", DiffSide.OLD, false),
                                                remark("new.txt", DiffSide.NEW, false),
                                                remark("b.txt", DiffSide.NEW, true))),
                                HashSet.empty(),
                                0))
                .model();

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(read, FilesPanel.PANE))) {
            String files = RenderedText.ofLarge(testRunner, () -> panel.render(read, FilesPanel.PANE));
            assertTrue(files.contains("Files of #7 · merged"), files);
            assertTrue(files.contains("R old.txt -> new.txt 💬2"), files);
            assertTrue(files.contains("M b.txt ✓1"), files);
            assertFalse(files.contains("b.txt 💬"), files);

            SettledPilot.of(testRunner).quit();
        }
    }

    @Test
    void theFilesOfAPullRequestCanBeListedOnlyWhenTheyHaveRemarks() throws Exception {
        Changes of = new Changes.OfPullRequest(new PullRequestCommits(
                new PrNumber(7), "owner/repo", "", new Sha("b".repeat(40)), new Sha("a".repeat(40))));
        Model open = Update.update(
                        Update.update(
                                        net.vanfleteren.jazygit.kernel.content.PullRequestContentTest.withGitHub(),
                                        new CommitFilesMsg.Opened(of, Scopes.BRANCHES.pane()))
                                .model(),
                        new LoadMsg.CommitFilesLoaded(
                                of,
                                List.of(
                                        new CommitFile("dir/open.txt", ChangeType.MODIFIED),
                                        new CommitFile("done.txt", ChangeType.MODIFIED),
                                        new CommitFile("quiet.txt", ChangeType.MODIFIED))))
                .model();
        Model unresolved = Update.update(
                        Update.update(
                                        open,
                                        new RemarksMsg.Loaded(
                                                new PrNumber(7),
                                                new ReviewRemarks(
                                                        PullRequestDetails.State.OPEN,
                                                        Option.none(),
                                                        Option.none(),
                                                        List.of(
                                                                remark("dir/open.txt", DiffSide.NEW, false),
                                                                remark("done.txt", DiffSide.NEW, true))),
                                                HashSet.empty(),
                                                0))
                                .model(),
                        new CommitFilesMsg.ListedCycled())
                .model();
        Model remarked =
                Update.update(unresolved, new CommitFilesMsg.ListedCycled()).model();
        Model none = Update.update(
                        unresolved,
                        new RemarksMsg.Loaded(
                                new PrNumber(7),
                                new ReviewRemarks(
                                        PullRequestDetails.State.OPEN,
                                        Option.none(),
                                        Option.none(),
                                        List.of(remark("done.txt", DiffSide.NEW, true))),
                                HashSet.empty(),
                                0))
                .model();

        try (ToolkitTestRunner testRunner =
                ToolkitTestRunner.runTest(() -> panel.render(unresolved, FilesPanel.PANE))) {
            String files = RenderedText.ofLarge(testRunner, () -> panel.render(unresolved, FilesPanel.PANE));
            assertTrue(files.contains("Files of #7 · unresolved remarks"), files);
            assertTrue(files.contains("dir"), files);
            assertTrue(files.contains("open.txt 💬1"), files);
            assertFalse(files.contains("done.txt"), files);
            assertFalse(files.contains("quiet.txt"), files);

            files = RenderedText.ofLarge(testRunner, () -> panel.render(remarked, FilesPanel.PANE));
            assertTrue(files.contains("Files of #7 · with remarks"), files);
            assertTrue(files.contains("done.txt ✓1"), files);
            assertFalse(files.contains("quiet.txt"), files);

            files = RenderedText.ofLarge(testRunner, () -> panel.render(none, FilesPanel.PANE));
            assertTrue(files.contains("No file has unresolved remarks"), files);
            assertFalse(files.contains("done.txt"), files);

            SettledPilot.of(testRunner).quit();
        }
    }

    @Test
    void aHighlightedFileLeftOutOfTheFilesWithRemarksMovesTheHighlightToTheNearestRowListed() throws Exception {
        Changes of = new Changes.OfPullRequest(new PullRequestCommits(
                new PrNumber(7), "owner/repo", "", new Sha("b".repeat(40)), new Sha("a".repeat(40))));
        Model opened = Update.update(
                        net.vanfleteren.jazygit.kernel.content.PullRequestContentTest.withGitHub(),
                        new CommitFilesMsg.Opened(of, Scopes.BRANCHES.pane()))
                .model();
        Model loaded = Update.update(
                        opened,
                        new LoadMsg.CommitFilesLoaded(
                                of,
                                List.of("a", "b", "c", "d")
                                        .map(name -> new CommitFile("dir/" + name + ".txt", ChangeType.MODIFIED))))
                .model();
        Model reading = Update.update(loaded, new CommitFilesMsg.ListedCycled()).model();
        Model read = Update.update(
                        loaded,
                        new RemarksMsg.Loaded(
                                new PrNumber(7),
                                new ReviewRemarks(
                                        PullRequestDetails.State.OPEN,
                                        Option.none(),
                                        Option.none(),
                                        List.of(
                                                remark("dir/a.txt", DiffSide.NEW, false),
                                                remark("dir/d.txt", DiffSide.NEW, false))),
                                HashSet.empty(),
                                0))
                .model();
        Model unresolved =
                Update.update(read, new CommitFilesMsg.ListedCycled()).model();

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(opened, FilesPanel.PANE))) {
            RenderedText.of(testRunner, () -> panel.render(opened, FilesPanel.PANE));
            String files = RenderedText.ofLarge(testRunner, () -> panel.render(reading, FilesPanel.PANE));
            assertTrue(files.contains("Reading the remarks…"), files);

            RenderedText.of(testRunner, () -> panel.render(read, FilesPanel.PANE));
            // The root, dir, then its files: b.txt, between a.txt and d.txt, which have remarks.
            panel.commitTree().selected(3);
            RenderedText.of(testRunner, () -> panel.render(read, FilesPanel.PANE));
            assertEquals("dir/b.txt", highlighted(panel));

            RenderedText.of(testRunner, () -> panel.render(unresolved, FilesPanel.PANE));
            assertEquals("dir/a.txt", highlighted(panel));

            SettledPilot.of(testRunner).quit();
        }
    }

    private static ReviewThread remark(String path, DiffSide side, boolean resolved) {
        return new ReviewThread(
                path,
                net.vanfleteren.jazygit.features.github.remarks.FromGitHub.side(side),
                Option.of(1),
                Option.none(),
                net.vanfleteren.jazygit.features.github.remarks.FromGitHub.side(side),
                Option.of(1),
                resolved,
                false,
                ReviewThread.Subject.LINE,
                List.of(new ReviewThread.Comment(
                        "bob",
                        java.time.Instant.EPOCH,
                        "Hm",
                        Option.none(),
                        new ReviewThread.Comment.Id("C"),
                        new ReviewThread.Comment.RestId("1"),
                        CommentRights.ALL)),
                0,
                new ReviewThread.Id("T"),
                ReviewThread.Rights.ALL);
    }

    /**
     * What Escape in the files pane of {@code model}, with the focus, sends.
     */
    private static java.util.List<Msg> escaped(Model model) throws Exception {
        java.util.List<Msg> sent = new java.util.ArrayList<>();
        FilesPanel panel = new FilesPanel(sent::add, () -> {});
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(model, FilesPanel.PANE))) {
            SettledPilot.focus(testRunner, FilesPanel.ID);
            SettledPilot.of(testRunner).pause();
            sent.clear();
            SettledPilot.of(testRunner).press(KeyCode.ESCAPE);
            SettledPilot.of(testRunner).pause();
            SettledPilot.of(testRunner).quit();
        }
        return sent.stream()
                .filter(msg -> !(msg instanceof SelectionMsg.FilesSelected))
                .toList();
    }

    /**
     * The path of the node highlighted when the files of a commit open with {@code files}.
     */
    private static String highlightedOnOpen(List<CommitFile> files) throws Exception {
        FilesPanel panel = new FilesPanel(msg -> {}, () -> {});
        Model opened = opened(TestModels.loaded(fixed(List.of())));
        Model loaded = loaded(opened, files);

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(opened, FilesPanel.PANE))) {
            RenderedText.of(testRunner, () -> panel.render(opened, FilesPanel.PANE));
            RenderedText.of(testRunner, () -> panel.render(loaded, FilesPanel.PANE));
            String path = highlighted(panel);
            SettledPilot.of(testRunner).quit();
            return path;
        }
    }

    /**
     * {@code model} with the files of {@link #COMMIT} opened from the commits pane, while they are read.
     */
    private static Model opened(Model model) {
        return Update.update(model, new CommitFilesMsg.Opened(COMMIT, Scopes.COMMITS.pane()))
                .model();
    }

    /**
     * {@code model} once the files of {@link #COMMIT} are read as {@code files}.
     */
    private static Model loaded(Model model, List<CommitFile> files) {
        return Update.update(model, new LoadMsg.CommitFilesLoaded(COMMIT, files))
                .model();
    }

    private static String highlighted(FilesPanel panel) {
        return panel.commitTree().selectedNode().data().path();
    }

    private static Git fixed(List<FileEntry> files) {
        return new FixedRepo(files).git();
    }

    private record FixedRepo(List<FileEntry> files) {

        public Status status() {
            return new StubStatus() {
                @Override
                public Either<GitError, RepoStatus> read() {
                    return Either.right(new RepoStatus(Head.onBranch(new BranchName("main"), new Sha("aaaa")), files));
                }
            };
        }

        Git git() {
            return StubGit.of().withStatus(status());
        }
    }
}
