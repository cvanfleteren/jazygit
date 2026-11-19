package net.vanfleteren.jazygit.changes.diff;

import static net.vanfleteren.jazygit.state.TestModels.loaded;
import static org.junit.jupiter.api.Assertions.*;

import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import io.vavr.collection.List;
import io.vavr.collection.Vector;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.gh.model.ReviewThread.Side;
import net.vanfleteren.jazygit.git.model.Blame;
import net.vanfleteren.jazygit.git.model.BlameAt;
import net.vanfleteren.jazygit.git.model.BlameOptions;
import net.vanfleteren.jazygit.git.model.ChangeType;
import net.vanfleteren.jazygit.git.model.DiffText;
import net.vanfleteren.jazygit.git.model.Diffs;
import net.vanfleteren.jazygit.git.model.FileContent;
import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.git.model.FileVersion;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.settings.DiffStyle;
import net.vanfleteren.jazygit.state.AreaId;
import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.Loadable;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;
import net.vanfleteren.jazygit.state.diff.Anchor;
import net.vanfleteren.jazygit.state.diff.Changes;
import net.vanfleteren.jazygit.state.diff.DiffCursorAt;
import net.vanfleteren.jazygit.state.diff.Scrolled;
import net.vanfleteren.jazygit.state.diff.ScrolledContent;
import net.vanfleteren.jazygit.state.diff.Version;
import net.vanfleteren.jazygit.state.load.LoadMsg;
import net.vanfleteren.jazygit.state.load.ReadBlame;
import net.vanfleteren.jazygit.state.load.ReadVersion;
import net.vanfleteren.jazygit.state.load.SelectionMsg;
import net.vanfleteren.jazygit.state.view.CommitFilesView;
import net.vanfleteren.jazygit.state.view.FilesShown;
import net.vanfleteren.jazygit.state.view.Preview;
import net.vanfleteren.jazygit.state.view.UnchangedNode;
import net.vanfleteren.jazygit.ui.keys.Keymap;
import net.vanfleteren.jazygit.ui.keys.Scopes;
import org.junit.jupiter.api.Test;

/**
 * Blame on a version of a file (ADR 147): {@code b} turns it on and off for the area, and what shows blamed is read
 * once, as it is shown.
 */
class BlameUpdateTest {

    private static final AreaId AREA = new AreaId("stash-changes");
    private static final Sha STASH = new Sha("5555");
    private static final String HEADER = "diff --git a/a.txt b/a.txt\n--- a/a.txt\n+++ b/a.txt\n";
    private static final DiffText CHANGES = DiffText.of(HEADER + "@@ -1,3 +1,3 @@\n one\n-two\n+Two\n three\n");
    private static final DiffText WHOLE = CHANGES;
    private static final ScrolledContent CONTENT = new ScrolledContent.StashChanges(STASH, CHANGES);
    private static final Anchor.OnLine ADDED = new Anchor.OnLine("a.txt", Side.RIGHT, 2);
    private static final Blame BLAME = Blame.uncommitted(3, "a.txt");
    private static final String STAGED_CHANGE = HEADER + "@@ -1,2 +1,2 @@\n one\n-two\n+Two\n";
    private static final String UNSTAGED_CHANGE = HEADER + "@@ -1,2 +1,2 @@\n one\n-Two\n+TWO\n";
    private static final String SAVED_CHANGE = HEADER + "@@ -1,2 +1,3 @@\n one\n-Two\n+TWO\n+three\n";
    private static final String ADDED_CHANGE = "diff --git a/a.txt b/a.txt\nnew file mode 100644\n--- /dev/null\n"
            + "+++ b/a.txt\n@@ -0,0 +1,2 @@\n+one\n+Two\n";

    @Test
    void bOnTheNewVersionReadsItsBlameInTheStash() {
        Next blamed = Update.update(newVersion(), new VersionMsg.BlameToggled(at()));

        ReadBlame read = onlyRead(blamed.cmds());
        assertEquals(FileVersion.NEW, read.version());
        assertEquals(List.of(new BlameAt.InCommit(STASH, "a.txt")), read.at());
        assertEquals(BlameOptions.DEFAULT, read.options());
        assertEquals(Option.of(Loadable.loading()), version(blamed.model()).flatMap(Version::blame));
    }

    @Test
    void theBlameReadShowsAndBAgainStopsBlaming() {
        Model read = blameRead(Update.update(newVersion(), new VersionMsg.BlameToggled(at())));
        assertEquals(Option.of(new Loadable.Loaded<>(BLAME)), version(read).flatMap(Version::blame));

        Next off = Update.update(read, new VersionMsg.BlameToggled(at()));

        assertEquals(List.of(), off.cmds());
        assertEquals(Option.none(), version(off.model()).flatMap(Version::blame));
        // Turned on again, what was read shows at once.
        Next on = Update.update(off.model(), new VersionMsg.BlameToggled(at()));
        assertEquals(List.of(), on.cmds());
        assertEquals(
                Option.of(new Loadable.Loaded<>(BLAME)), version(on.model()).flatMap(Version::blame));
    }

    @Test
    void theOriginalShowsBlamedTooReadAtTheParentOfTheStash() {
        Model read = blameRead(Update.update(newVersion(), new VersionMsg.BlameToggled(at())));

        Next original = Update.update(read, new VersionMsg.Cycled(at()));

        ReadBlame blame = onlyRead(original.cmds());
        assertEquals(FileVersion.ORIGINAL, blame.version());
        assertEquals(List.of(new BlameAt.InCommit(STASH, 1, "a.txt")), blame.at());
        // Back to the new version, through the diff, its blame shows without being read again.
        Model diff =
                Update.update(original.model(), new VersionMsg.Cycled(at())).model();
        Next back = Update.update(diff, new VersionMsg.Cycled(at()));
        assertEquals(List.of(), back.cmds().filter(ReadBlame.class::isInstance));
        assertEquals(
                Option.of(new Loadable.Loaded<>(BLAME)), version(back.model()).flatMap(Version::blame));
    }

    @Test
    void enterOnABlamedLineOpensTheFilesOfItsCommitOnTheFileAsItWasNamedThere() {
        Sha older = new Sha("c".repeat(40));
        Blame.Commit commit = new Blame.Commit(older, "Alex Moreau", Instant.EPOCH, "first", false);
        Blame.Commit stash = new Blame.Commit(STASH, "Alex Moreau", Instant.EPOCH, "WIP on main", false);
        Blame blame = new Blame(Vector.of(
                new Blame.Line(commit, 1, "old.txt", Option.none()),
                new Blame.Line(stash, 2, "a.txt", Option.none()),
                new Blame.Line(Blame.NOT_COMMITTED, 3, "a.txt", Option.none())));
        Model read = blameRead(Update.update(newVersion(), new VersionMsg.BlameToggled(at())), blame);

        Model opened = Update.update(read, new VersionMsg.LineOpened(onLine(1))).model();

        CommitFilesView files = opened.repoView().files().commitFiles().get();
        assertEquals(new Changes.OfCommit(older), files.of());
        assertEquals(Scopes.COMMITS.pane(), files.pane());
        assertEquals(Option.of("old.txt"), files.wanted());
        // A line of the stash shown, and one not committed yet, open nothing.
        assertEquals(
                Option.none(),
                Update.update(read, new VersionMsg.LineOpened(onLine(2)))
                        .model()
                        .repoView()
                        .files()
                        .commitFiles());
        assertEquals(
                Option.none(),
                Update.update(read, new VersionMsg.LineOpened(onLine(3)))
                        .model()
                        .repoView()
                        .files()
                        .commitFiles());
    }

    @Test
    void enterOnALineOfTheStashOrItsIndexOrNotCommittedYetOpensNothing() {
        Blame.Commit index = new Blame.Commit(
                new Sha("2".repeat(40)), "Alex Moreau", Instant.EPOCH, "index on main: 1234567 base", false);
        Blame.Commit stash = new Blame.Commit(STASH, "Alex Moreau", Instant.EPOCH, "WIP on main", false);
        Blame blame = new Blame(Vector.of(
                new Blame.Line(index, 1, "a.txt", Option.none()),
                new Blame.Line(stash, 2, "a.txt", Option.none()),
                new Blame.Line(Blame.NOT_COMMITTED, 3, "a.txt", Option.none())));
        Model read = blameRead(Update.update(newVersion(), new VersionMsg.BlameToggled(at())), blame);
        Version blamed = version(read).get();

        for (int line = 1; line <= 3; line++) {
            Next next = Update.update(read, new VersionMsg.LineOpened(onLine(line)));

            assertEquals(Option.none(), blamed.opening(onLine(line).cursor()), "line " + line);
            assertEquals(List.of(), next.cmds(), "line " + line);
            assertEquals(read, next.model(), "line " + line);
        }
    }

    @Test
    void enterWhileTheBlameIsReadOpensNothing() {
        Model reading =
                Update.update(newVersion(), new VersionMsg.BlameToggled(at())).model();

        Next next = Update.update(reading, new VersionMsg.LineOpened(onLine(1)));

        assertEquals(Option.none(), version(reading).get().opening(onLine(1).cursor()));
        assertEquals(List.of(), next.cmds().filter(cmd -> !(cmd instanceof ReadBlame)));
        assertEquals(Option.none(), next.model().repoView().files().commitFiles());
    }

    @Test
    void enterSendsTheLineOpenedOnlyWhereTheContentPanelSaysItOpensACommit() {
        Keymap keys = ExpandAction.ACTION.keys().apply(Keymap.empty());
        DiffCursorAt line = onLine(1);
        DiffCursorAt opening = new DiffCursorAt(
                line.area(),
                line.at(),
                line.cursor(),
                line.next(),
                line.previous(),
                line.marked(),
                false,
                Option.none(),
                true);

        assertEquals(Option.none(), keys.handle(Scopes.DIFF, KeyEvent.ofKey(KeyCode.ENTER), Option.of(line)));
        assertEquals(
                Option.of(new VersionMsg.LineOpened(opening)),
                keys.handle(Scopes.DIFF, KeyEvent.ofKey(KeyCode.ENTER), Option.of(opening)));
    }

    @Test
    void enterOnALineOfTheDiffOpensNothing() {
        Next next = Update.update(loaded(), new VersionMsg.LineOpened(at()));

        assertEquals(List.of(), next.cmds());
        assertEquals(Option.none(), next.model().repoView().files().commitFiles());
    }

    @Test
    void bOnTheDiffDoesNothing() {
        Next next = Update.update(loaded(), new VersionMsg.BlameToggled(at()));

        assertEquals(List.of(), next.cmds());
        assertEquals(Option.none(), version(next.model()));
    }

    @Test
    void aVersionStillReadIsBlamedOnceItIsRead() {
        Next asked = Update.update(loaded(), new VersionMsg.Cycled(at()));
        Next blamed = Update.update(asked.model(), new VersionMsg.BlameToggled(at()));
        assertEquals(List.of(), blamed.cmds());

        ReadVersion read = (ReadVersion) asked.cmds().head();
        Next shown =
                TestModels.done(TestModels.running(blamed.model(), read), read, new LoadMsg.VersionRead(read, WHOLE));

        assertEquals(FileVersion.NEW, onlyRead(shown.cmds()).version());
    }

    @Test
    void aBlameThatFailedIsReadAgainOnTheNextB() {
        Next blamed = Update.update(newVersion(), new VersionMsg.BlameToggled(at()));
        ReadBlame read = onlyRead(blamed.cmds());
        Model failed = TestModels.done(
                        TestModels.running(blamed.model(), read),
                        read,
                        new LoadMsg.Failed(read, TestModels.refused("fatal: no such path")))
                .model();
        assertTrue(version(failed).flatMap(Version::blame).exists(Loadable::isFailed));

        Next again = Update.update(failed, new VersionMsg.BlameToggled(at()));

        assertEquals(read, onlyRead(again.cmds()));
        assertTrue(version(again.model()).exists(Version::blamed));
    }

    @Test
    void ctrlWBlamesAgainIgnoringWhitespaceAndDropsTheBlameReadBefore() {
        Next blamed = Update.update(newVersion(), new VersionMsg.BlameToggled(at()));
        ReadBlame before = onlyRead(blamed.cmds());
        Model running = TestModels.running(blamed.model(), before);

        Next ignoring = Update.update(running, new DiffOptionsMsg.WhitespaceToggled());

        ReadBlame after = onlyRead(ignoring.cmds());
        assertEquals(new BlameOptions(true, Option.none()), after.options());
        Model late = TestModels.done(ignoring.model(), before, new LoadMsg.BlameRead(before, BLAME))
                .model();
        assertEquals(Option.of(Loadable.loading()), version(late).flatMap(Version::blame));
    }

    @Test
    void aBlamedVersionReadAgainChangedIsBlamedAgain() {
        Model blamed = blamesRead(Update.update(
                versionsRead(Update.update(workingTree("", UNSTAGED_CHANGE), new VersionMsg.FilesCycled())),
                new VersionMsg.FilesBlameToggled()));
        assertEquals(
                Option.of(new Loadable.Loaded<>(BLAME)),
                versionIn(blamed, AreaId.UNSTAGED).flatMap(Version::blame));
        ReadVersion read = ReadVersion.of(
                AreaId.UNSTAGED, versionIn(blamed, AreaId.UNSTAGED).get().read());

        Next saved = TestModels.done(
                TestModels.running(blamed, read), read, new LoadMsg.VersionRead(read, DiffText.of(SAVED_CHANGE)));

        assertEquals(
                List.of(new BlameAt.OnDisk("a.txt")), onlyRead(saved.cmds()).at());
        assertEquals(
                Option.of(Loadable.loading()),
                versionIn(saved.model(), AreaId.UNSTAGED).flatMap(Version::blame));
    }

    @Test
    void aBlamedVersionReadAgainUnchangedKeepsItsBlame() {
        Model blamed = blamesRead(Update.update(
                versionsRead(Update.update(workingTree("", UNSTAGED_CHANGE), new VersionMsg.FilesCycled())),
                new VersionMsg.FilesBlameToggled()));
        ReadVersion read = ReadVersion.of(
                AreaId.UNSTAGED, versionIn(blamed, AreaId.UNSTAGED).get().read());

        Next same = TestModels.done(
                TestModels.running(blamed, read), read, new LoadMsg.VersionRead(read, DiffText.of(UNSTAGED_CHANGE)));

        assertEquals(List.of(), same.cmds().filter(ReadBlame.class::isInstance));
        assertEquals(
                Option.of(new Loadable.Loaded<>(BLAME)),
                versionIn(same.model(), AreaId.UNSTAGED).flatMap(Version::blame));
    }

    @Test
    void bInTheFilesPaneBlamesAFileWithoutChangesWithoutReadingItAgain() {
        DiffText onDisk = DiffText.of("diff --git a/b.txt b/b.txt\n--- a/b.txt\n+++ b/b.txt\n@@ -1,1 +1,1 @@\n b\n");
        Model model = rich().mapRepoView(view -> view.mapFiles(files -> files.withShown(FilesShown.ALL))
                .mapFiles(files -> files.withUnchanged(Option.of(new UnchangedNode.File(
                        new Preview<>("b.txt", new Loadable.Loaded<>(new FileContent.Text(onDisk))))))));

        Next blamed = Update.update(model, new VersionMsg.FilesBlameToggled());

        assertEquals(List.of(), blamed.cmds().filter(ReadVersion.class::isInstance));
        assertEquals(
                List.of(new BlameAt.OnDisk("b.txt")), onlyRead(blamed.cmds()).at());
        Version version = versionIn(blamed.model(), AreaId.FILE_CONTENT).get();
        assertTrue(version.blamed());
        assertEquals(Option.of(FileVersion.NEW), version.shown());
    }

    @Test
    void bInTheFilesPaneBlamesBothAreasOfTheWorkingTreeAsTheFirstSays() {
        Model shown =
                versionsRead(Update.update(workingTree(STAGED_CHANGE, UNSTAGED_CHANGE), new VersionMsg.FilesCycled()));
        // Only the staged area is blamed: the unstaged one, first, says both are to be.
        Model stagedOnly = blamesRead(Update.update(shown, new VersionMsg.BlameToggled(staged(shown))));
        assertTrue(versionIn(stagedOnly, AreaId.STAGED).exists(Version::blamed));

        Next both = Update.update(stagedOnly, new VersionMsg.FilesBlameToggled());

        assertEquals(List.of(new BlameAt.OnDisk("a.txt")), onlyRead(both.cmds()).at());
        assertTrue(versionIn(both.model(), AreaId.UNSTAGED).exists(Version::blamed));
        assertTrue(versionIn(both.model(), AreaId.STAGED).exists(Version::blamed));

        Next originals = Update.update(blamesRead(both), new VersionMsg.FilesCycled());
        assertEquals(
                List.of(new BlameAt.InIndex("a.txt"), new BlameAt.AtHead("a.txt")),
                originals
                        .cmds()
                        .filter(ReadBlame.class::isInstance)
                        .map(ReadBlame.class::cast)
                        .map(read -> read.at().head())
                        .sortBy(at -> at instanceof BlameAt.InIndex ? 0 : 1));

        Next off = Update.update(blamesRead(originals), new VersionMsg.FilesBlameToggled());
        assertFalse(versionIn(off.model(), AreaId.UNSTAGED).exists(Version::blamed));
        assertFalse(versionIn(off.model(), AreaId.STAGED).exists(Version::blamed));
    }

    @Test
    void bInTheFilesPaneDoesNothingOnAVersionRendered() {
        List<FileEntry> markdown = List.of(new FileEntry("a.md", ChangeType.MODIFIED, false, true));
        Model rendered = versionsRead(Update.update(
                workingTree(markdown, "", UNSTAGED_CHANGE.replace("a.txt", "a.md")),
                new VersionMsg.FilesRenderToggled()));
        assertTrue(versionIn(rendered, AreaId.UNSTAGED).exists(Version::rendered));

        Next next = Update.update(rendered, new VersionMsg.FilesBlameToggled());

        assertEquals(List.of(), next.cmds());
        assertFalse(versionIn(next.model(), AreaId.UNSTAGED).exists(Version::blamed));
    }

    @Test
    void bInTheFilesPaneDoesNothingOnAnUntrackedFile() {
        List<FileEntry> untracked = List.of(new FileEntry("a.txt", ChangeType.UNTRACKED, false, true));
        Model shown =
                versionsRead(Update.update(workingTree(untracked, "", ADDED_CHANGE), new VersionMsg.FilesCycled()));
        assertEquals(
                Option.of(FileVersion.NEW), versionIn(shown, AreaId.UNSTAGED).flatMap(Version::shown));

        Next next = Update.update(shown, new VersionMsg.FilesBlameToggled());

        assertEquals(List.of(), next.cmds());
        assertFalse(versionIn(next.model(), AreaId.UNSTAGED).exists(Version::blamed));
    }

    @Test
    void ctrlTToRenderedAndBackShowsTheBlameAgainWithoutReadingIt() {
        List<FileEntry> markdown = List.of(new FileEntry("a.md", ChangeType.MODIFIED, false, true));
        Model shown = versionsRead(Update.update(
                workingTree(markdown, "", UNSTAGED_CHANGE.replace("a.txt", "a.md")), new VersionMsg.FilesCycled()));
        Model blamed = blamesRead(Update.update(shown, new VersionMsg.FilesBlameToggled()));

        Next rendered = Update.update(blamed, new VersionMsg.FilesRenderToggled());
        assertTrue(versionIn(rendered.model(), AreaId.UNSTAGED).exists(Version::rendered));
        Next source = Update.update(rendered.model(), new VersionMsg.FilesRenderToggled());

        assertEquals(List.of(), rendered.cmds().appendAll(source.cmds()).filter(ReadBlame.class::isInstance));
        assertFalse(versionIn(source.model(), AreaId.UNSTAGED).exists(Version::rendered));
        assertEquals(
                Option.of(new Loadable.Loaded<>(BLAME)),
                versionIn(source.model(), AreaId.UNSTAGED).flatMap(Version::blame));
    }

    /**
     * The working tree with its one file, {@code a.txt}, changed and highlighted, and its {@code staged} and
     * {@code unstaged} changes read.
     */
    private static Model workingTree(String staged, String unstaged) {
        return workingTree(List.of(new FileEntry("a.txt", ChangeType.MODIFIED, true, true)), staged, unstaged);
    }

    private static Model workingTree(List<FileEntry> files, String staged, String unstaged) {
        Model model =
                Update.update(rich(), new SelectionMsg.FilesSelected(files)).model();
        return Update.update(
                        model,
                        new LoadMsg.FileDiffLoaded(
                                files, model.preferences().diffOptions(), new Diffs(staged, unstaged)))
                .model();
    }

    /**
     * A loaded model drawing diffs in the rich view, where a version shows, as the tests start in the plain one.
     */
    private static Model rich() {
        Model rich = Update.update(loaded(), new DiffOptionsMsg.StyleChosen(DiffStyle.RICH))
                .model();
        assertEquals(DiffStyle.RICH, rich.preferences().diffLook().style());
        return rich;
    }

    /**
     * {@code asked} once each file of the working tree it reads whole is read, as the changes its area shows.
     */
    private static Model versionsRead(Next asked) {
        return asked.cmds()
                .filter(ReadVersion.class::isInstance)
                .map(ReadVersion.class::cast)
                .foldLeft(
                        asked.model(),
                        (model, read) -> TestModels.done(
                                        TestModels.running(model, read),
                                        read,
                                        new LoadMsg.VersionRead(read, ((ScrolledContent.Diff) read.content()).diff()))
                                .model());
    }

    /**
     * {@code asked} once each blame it reads is read.
     */
    private static Model blamesRead(Next asked) {
        return asked.cmds()
                .filter(ReadBlame.class::isInstance)
                .map(ReadBlame.class::cast)
                .foldLeft(
                        asked.model(),
                        (model, read) -> TestModels.done(
                                        TestModels.running(model, read), read, new LoadMsg.BlameRead(read, BLAME))
                                .model());
    }

    /**
     * The cursor in the staged area of {@code model}, on the line its change added.
     */
    private static DiffCursorAt staged(Model model) {
        ScrolledContent content = new ScrolledContent.Diff(DiffText.of(STAGED_CHANGE));
        Anchor.OnLine two = new Anchor.OnLine("a.txt", Side.RIGHT, 2);
        return new DiffCursorAt(
                AreaId.STAGED,
                model.scrolled(AreaId.STAGED, content),
                two,
                Option.none(),
                Option.none(),
                Option.none());
    }

    private static Option<Version> versionIn(Model model, AreaId area) {
        return model.repoView().versions().get(area);
    }

    /**
     * The new version of the file of the stash shown, read whole.
     */
    private static Model newVersion() {
        Next asked = Update.update(loaded(), new VersionMsg.Cycled(at()));
        ReadVersion read = (ReadVersion) asked.cmds().head();
        Model shown = TestModels.done(
                        TestModels.running(asked.model(), read), read, new LoadMsg.VersionRead(read, WHOLE))
                .model();
        assertEquals(Option.of(FileVersion.NEW), version(shown).flatMap(Version::showing));
        return shown;
    }

    /**
     * {@code asked} once the one blame it reads is read.
     */
    private static Model blameRead(Next asked) {
        return blameRead(asked, BLAME);
    }

    private static Model blameRead(Next asked, Blame blame) {
        ReadBlame read = onlyRead(asked.cmds());
        return TestModels.done(TestModels.running(asked.model(), read), read, new LoadMsg.BlameRead(read, blame))
                .model();
    }

    private static DiffCursorAt onLine(int number) {
        Anchor.OnLine line = new Anchor.OnLine("a.txt", Side.RIGHT, number);
        return new DiffCursorAt(
                AREA,
                new Scrolled(CONTENT, 0, 0, Option.of(line), Option.none()),
                line,
                Option.none(),
                Option.none(),
                Option.none());
    }

    private static ReadBlame onlyRead(List<Cmd> cmds) {
        List<Cmd> reads = cmds.filter(ReadBlame.class::isInstance);
        assertEquals(1, reads.size(), cmds.toString());
        return (ReadBlame) reads.head();
    }

    private static DiffCursorAt at() {
        return new DiffCursorAt(
                AREA,
                new Scrolled(CONTENT, 0, 0, Option.of(ADDED), Option.none()),
                ADDED,
                Option.none(),
                Option.none(),
                Option.none());
    }

    private static Option<Version> version(Model model) {
        return model.repoView().versions().get(AREA);
    }
}
