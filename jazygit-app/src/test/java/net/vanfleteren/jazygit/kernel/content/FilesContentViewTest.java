package net.vanfleteren.jazygit.kernel.content;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.collection.Vector;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.model.*;
import net.vanfleteren.jazygit.core.git.model.SampleData;
import net.vanfleteren.jazygit.core.repo.*;
import net.vanfleteren.jazygit.features.changes.diff.CommitFilesMsg;
import net.vanfleteren.jazygit.features.changes.diff.DiffOptionsMsg;
import net.vanfleteren.jazygit.features.changes.diff.VersionMsg;
import net.vanfleteren.jazygit.features.changes.panes.files.FilesPanel;
import net.vanfleteren.jazygit.kernel.settings.DiffLook;
import net.vanfleteren.jazygit.kernel.settings.DiffStyle;
import net.vanfleteren.jazygit.kernel.state.*;
import net.vanfleteren.jazygit.kernel.state.AreaId;
import net.vanfleteren.jazygit.kernel.state.diff.*;
import net.vanfleteren.jazygit.kernel.state.load.*;
import net.vanfleteren.jazygit.kernel.state.preferences.*;
import net.vanfleteren.jazygit.kernel.state.view.*;
import net.vanfleteren.jazygit.kernel.ui.content.Shown;
import net.vanfleteren.jazygit.kernel.ui.content.ShownChanges;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;
import net.vanfleteren.jazygit.kernel.ui.text.Placeholders;
import org.junit.jupiter.api.Test;

/**
 * What {@link ContentView} shows next to the files pane, without drawing it: the staged and unstaged changes, the
 * files of a commit, the version of a file in place of its diff, and the titles that say how a diff is shown.
 */
class FilesContentViewTest {

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
    void theTitleSaysWhichVersionShowsWhileItIsReadAndWhenItCouldNotBe() {
        DiffText changes = STASHED;
        Version asked = stashVersion();

        assertEquals("Stash · new version (reading)", Expanded.versioned("Stash", asked));
        assertEquals(
                "Stash · original",
                Expanded.versioned(
                        "Stash",
                        asked.withShown(Option.of(FileVersion.ORIGINAL)).withWhole(new Loadable.Loaded<>(changes))));
        assertEquals(
                "Stash · new version (could not be read)",
                Expanded.versioned("Stash", asked.withWhole(new Loadable.Failed<>(TestModels.refused("boom")))));
        // Read, but without the file: the diff shows, and the title says the version could not be read.
        assertEquals(
                "Stash · new version (could not be read)",
                Expanded.versioned("Stash", asked.withWhole(new Loadable.Loaded<>(DiffText.of("")))));
        assertEquals("Stash", Expanded.versioned("Stash", asked.withShown(Option.none())));
        // Rendered, it says so too (ADR 103).
        assertEquals("Stash · new version, rendered (reading)", Expanded.versioned("Stash", asked.withRendered(true)));
        assertEquals(
                "Stash · original, rendered",
                Expanded.versioned(
                        "Stash",
                        asked.withShown(Option.of(FileVersion.ORIGINAL))
                                .withRendered(true)
                                .withWhole(new Loadable.Loaded<>(changes))));
    }

    @Test
    void theTitleSaysAVersionIsBlamedAndWhileItsBlameIsReadOrWhenItCouldNotBe() {
        Version blamed = stashVersion().withBlamed(true);
        Version read = blamed.withWhole(new Loadable.Loaded<>(STASHED));

        // While the file is read, that is what the title says (ADR 147).
        assertEquals("Stash · new version, blamed (reading)", Expanded.versioned("Stash", blamed));
        assertEquals("Stash · new version, blamed (reading blame)", Expanded.versioned("Stash", read));
        assertEquals(
                "Stash · new version, blamed",
                Expanded.versioned(
                        "Stash",
                        read.withBlames(Blames.none(false)
                                .with(FileVersion.NEW, new Loadable.Loaded<>(new Blame(Vector.empty()))))));
        assertEquals(
                "Stash · original, blamed (blame could not be read)",
                Expanded.versioned(
                        "Stash",
                        read.withShown(Option.of(FileVersion.ORIGINAL))
                                .withBlames(Blames.none(false)
                                        .with(
                                                FileVersion.ORIGINAL,
                                                new Loadable.Failed<>(TestModels.refused("boom"))))));
        // Rendered, it is not blamed, so the title says only that it is rendered.
        assertEquals("Stash · new version, rendered", Expanded.versioned("Stash", read.withRendered(true)));
    }

    @Test
    void ctrlOInTheFilesPaneShowsTheNewVersionInTheStagedAndTheUnstagedArea() {
        String header = "diff --git a/a.txt b/a.txt\n--- a/a.txt\n+++ b/a.txt\n";
        Model dirty = Update.update(TestModels.loaded(), new LoadMsg.StatusLoaded(TestModels.DIRTY))
                .model();
        Model rich = Update.update(dirty, new DiffOptionsMsg.StyleChosen(DiffStyle.RICH))
                .model();
        Model model = Update.update(rich, new SelectionMsg.FilesSelected(TestModels.DIRTY.files()))
                .model();
        model = Update.update(
                        model,
                        new LoadMsg.FileDiffLoaded(
                                TestModels.DIRTY.files(),
                                model.preferences().diffOptions(),
                                new Diffs(
                                        header + "@@ -1,2 +1,2 @@\n one\n-two\n+Two\n",
                                        header + "@@ -1,2 +1,2 @@\n one\n-Two\n+TWO\n")))
                .model();

        Model cycled = Update.update(model, new VersionMsg.FilesCycled()).model();

        Shown.Stacked shown = (Shown.Stacked) ContentView.of(cycled, FilesPanel.PANE);
        assertEquals(
                List.of("Staged changes · new version (reading)", "Unstaged changes · new version (reading)"),
                shown.areas().map(Shown.Area::title));
    }

    @Test
    void withoutChangedFilesTheFilesSaySoInsteadOfLoading() {
        assertEquals(
                ContentViewFixtures.note("Diff", "No changed files"),
                ContentView.of(TestModels.loaded(), FilesPanel.PANE));
    }

    @Test
    void theFilesOfACommitShowWhatItChangedInTheHighlightedNodeInTheStyleChosen() {
        Changes of = new Changes.OfCommit(new Sha("aaaa"));
        CommitFile file = new CommitFile("src/a.txt", ChangeType.MODIFIED);
        Model rich = TestModels.loaded()
                .mapPreferences(preferences ->
                        preferences.withSettings(preferences.settings().withDiffStyle(DiffStyle.RICH)));
        Model open = Update.update(
                        Update.update(rich, new CommitFilesMsg.Opened(of, Scopes.COMMITS.pane()))
                                .model(),
                        new LoadMsg.CommitFilesLoaded(of, List.of(file)))
                .model();
        Model selected = Update.update(
                        Update.update(open, new SelectionMsg.CommitFilesSelected(List.of(file)))
                                .model(),
                        new LoadMsg.CommitFileDiffLoaded(
                                of, List.of(file), rich.preferences().diffOptions(), DiffText.of("+a\n")))
                .model();

        assertEquals(
                new Shown.Area(
                        CommitFilesSource.AREA,
                        "aaaa · src/a.txt",
                        new ScrolledContent.FileChanges(of, List.of(file), DiffText.of("+a\n")),
                        new DiffBody(new ShownChanges(ContentViewFixtures.whole("+a\n"), RICH_LOOK))),
                ContentView.of(selected, FilesPanel.PANE));
        assertEquals(
                ContentViewFixtures.note("Files of aaaa", Placeholders.loading()),
                ContentView.of(open, FilesPanel.PANE));
    }

    @Test
    void ignoringWhitespaceTheFilesSaySoAndThatOnlyWhitespaceChanged() {
        DiffOptions ignoring = DiffOptions.DEFAULT.withIgnoreWhitespace(true);
        Model dirty = Update.update(TestModels.loaded(), new LoadMsg.StatusLoaded(TestModels.DIRTY))
                .model()
                .mapPreferences(preferences -> preferences.withIgnoreWhitespace(ignoring.ignoreWhitespace()));
        List<FileEntry> files = TestModels.DIRTY.files();
        Model selected =
                Update.update(dirty, new SelectionMsg.FilesSelected(files)).model();
        Model blank = Update.update(selected, new LoadMsg.FileDiffLoaded(files, ignoring, new Diffs("", "")))
                .model();
        Model changed = Update.update(selected, new LoadMsg.FileDiffLoaded(files, ignoring, new Diffs("", "+a\n")))
                .model();

        assertEquals(
                ContentViewFixtures.note("Diff · ignoring whitespace", "No changes besides whitespace"),
                ContentView.of(blank, FilesPanel.PANE));
        assertEquals(
                List.of("Unstaged changes · ignoring whitespace"),
                ContentView.of(changed, FilesPanel.PANE).areas().map(Shown.Area::title));
    }

    @Test
    void theTitleSaysHowMuchContextIsShownWhenItIsNotThreeLines() {
        DiffOptions ignoring = DiffOptions.DEFAULT.withIgnoreWhitespace(true);

        assertEquals("Diff", Shows.titled("Diff", DiffOptions.DEFAULT));
        assertEquals(
                "Diff · 10 lines of context",
                Shows.titled("Diff", DiffOptions.DEFAULT.withContext(new DiffContext(10))));
        assertEquals(
                "Diff · 1 line of context", Shows.titled("Diff", DiffOptions.DEFAULT.withContext(new DiffContext(1))));
        assertEquals("Diff · no context", Shows.titled("Diff", DiffOptions.DEFAULT.withContext(new DiffContext(0))));
        assertEquals(
                "Diff · the whole file · ignoring whitespace",
                Shows.titled("Diff", ignoring.withContext(DiffContext.WHOLE_FILE)));
    }

    @Test
    void theFilesShowTheStagedChangesAboveTheUnstagedOnes() {
        Model selected = TestModels.withFilesSelected(
                SampleData.git(),
                List.of(
                        new FileEntry("a.txt", ChangeType.MODIFIED, true, false),
                        new FileEntry("b.txt", ChangeType.MODIFIED, false, true)));

        Shown.Stacked diffs = assertInstanceOf(Shown.Stacked.class, ContentView.of(selected, FilesPanel.PANE));
        assertEquals(
                List.of(FileDiffsSource.STAGED, FileDiffsSource.UNSTAGED),
                diffs.areas().map(Shown.Area::area));
        assertEquals(
                List.of("Staged changes", "Unstaged changes"), diffs.areas().map(Shown.Area::title));
    }

    @Test
    void theStagedAndUnstagedChangesAreDrawnInTheStyleChosen() {
        Model selected = TestModels.withFilesSelected(
                SampleData.git(),
                List.of(
                        new FileEntry("a.txt", ChangeType.MODIFIED, true, false),
                        new FileEntry("b.txt", ChangeType.MODIFIED, false, true)));
        Model rich = selected.mapPreferences(
                preferences -> preferences.withSettings(preferences.settings().withDiffStyle(DiffStyle.RICH)));

        assertEquals(
                List.of(ContentViewFixtures.PLAIN_LOOK, ContentViewFixtures.PLAIN_LOOK),
                ContentView.of(selected, FilesPanel.PANE)
                        .areas()
                        .map(area -> assertInstanceOf(DiffBody.class, area.body())
                                .changes()
                                .look()));
        assertEquals(
                List.of(RICH_LOOK, RICH_LOOK),
                ContentView.of(rich, FilesPanel.PANE)
                        .areas()
                        .map(area -> assertInstanceOf(DiffBody.class, area.body())
                                .changes()
                                .look()));
    }
}
