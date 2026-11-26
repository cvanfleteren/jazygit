package net.vanfleteren.jazygit.kernel.content;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.gh.model.PullRequestDetails;
import net.vanfleteren.jazygit.core.gh.model.ReviewRemarks;
import net.vanfleteren.jazygit.core.git.model.*;
import net.vanfleteren.jazygit.core.git.model.SampleData;
import net.vanfleteren.jazygit.core.repo.*;
import net.vanfleteren.jazygit.core.repo.PrNumber;
import net.vanfleteren.jazygit.features.changes.diff.CommitFilesMsg;
import net.vanfleteren.jazygit.features.changes.panes.files.FilesPanel;
import net.vanfleteren.jazygit.features.github.remarks.RemarksMsg;
import net.vanfleteren.jazygit.kernel.settings.DiffLook;
import net.vanfleteren.jazygit.kernel.settings.DiffStyle;
import net.vanfleteren.jazygit.kernel.state.*;
import net.vanfleteren.jazygit.kernel.state.AreaId;
import net.vanfleteren.jazygit.kernel.state.diff.*;
import net.vanfleteren.jazygit.kernel.state.load.*;
import net.vanfleteren.jazygit.kernel.state.preferences.*;
import net.vanfleteren.jazygit.kernel.state.view.*;
import net.vanfleteren.jazygit.kernel.ui.content.Shown;
import net.vanfleteren.jazygit.kernel.ui.content.Tone;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;
import net.vanfleteren.jazygit.kernel.ui.text.Placeholders;
import org.junit.jupiter.api.Test;

/**
 * The tests of FilesContentViewTest with GitHub in the app (ADR 171).
 */
class FilesContentViewGitHubTest {

    private final Model model = TestModels.loaded(SampleData.git(), "feature/initial-layout");

    private static final DiffLook RICH_LOOK = new DiffLook(DiffStyle.RICH, true, true, true);

    private static final DiffText STASHED =
            DiffText.of("diff --git a/a.txt b/a.txt\n--- a/a.txt\n+++ b/a.txt\n@@ -1 +1 @@\n-a\n+b\n");

    /**
     * The new version of the file a stash changed, {@link #STASHED}, as it is asked for: still read.
     */
    private static Version stashVersion() {
        ScrolledContent content = new ScrolledContent.StashChanges(new Sha("5555"), STASHED);
        ReadVersion read = ReadVersion.of(
                new AreaId("stash-changes"),
                content,
                new WholeFile.Source.OfStash(
                        new Sha("5555"), new CommitFile("a.txt", ChangeType.MODIFIED, Option.none())),
                "a.txt",
                DiffOptions.DEFAULT);
        return new Version(content, "a.txt", Option.of(FileVersion.NEW), read.file(), Loadable.loading());
    }

    @Test
    void whenOnlyTheFilesOfAPullRequestWithRemarksAreListedAndNoneIsTheDiffShownBeforeIsNot() {
        Changes of = new Changes.OfPullRequest(new PullRequestCommits(
                new PrNumber(7), "owner/repo", "", new Sha("b".repeat(40)), new Sha("a".repeat(40))));
        CommitFile file = new CommitFile("src/a.txt", ChangeType.MODIFIED);
        Model open = Update.update(
                        Update.update(
                                        net.vanfleteren.jazygit.kernel.content.PullRequestContentTest.withGitHub(),
                                        new CommitFilesMsg.Opened(of, Scopes.BRANCHES.pane()))
                                .model(),
                        new LoadMsg.CommitFilesLoaded(of, List.of(file)))
                .model();
        Model selected = Update.update(
                        Update.update(open, new SelectionMsg.CommitFilesSelected(List.of(file)))
                                .model(),
                        new LoadMsg.CommitFileDiffLoaded(
                                of, List.of(file), open.preferences().diffOptions(), DiffText.of("+a\n")))
                .model();
        Model unresolved =
                Update.update(selected, new CommitFilesMsg.ListedCycled()).model();
        assertInstanceOf(Shown.Area.class, ContentView.of(selected, FilesPanel.PANE));

        // While the remarks are read.
        assertEquals(
                ContentViewFixtures.note("Files of #7", Placeholders.loading()),
                ContentView.of(unresolved, FilesPanel.PANE));

        Model none = Update.update(
                        unresolved,
                        new RemarksMsg.Loaded(
                                new PrNumber(7),
                                new ReviewRemarks(
                                        PullRequestDetails.State.OPEN, Option.none(), Option.none(), List.empty()),
                                HashSet.empty(),
                                0))
                .model();
        assertEquals(
                ContentViewFixtures.note("Files of #7", "No file has unresolved remarks"),
                ContentView.of(none, FilesPanel.PANE));
        assertEquals(
                ContentViewFixtures.note("Files of #7", "No file has remarks"),
                ContentView.of(
                        Update.update(none, new CommitFilesMsg.ListedCycled()).model(), FilesPanel.PANE));

        Failure failure = new Failure.Bug(new Exception("no network"));
        assertEquals(
                ContentViewFixtures.note("Files of #7", Placeholders.error(failure), Tone.ERROR),
                ContentView.of(
                        Update.update(unresolved, new RemarksMsg.Failed(new PrNumber(7), failure, HashSet.empty(), 0))
                                .model(),
                        FilesPanel.PANE));
    }
}
