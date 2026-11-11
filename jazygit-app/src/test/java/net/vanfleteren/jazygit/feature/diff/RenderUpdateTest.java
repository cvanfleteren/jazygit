package net.vanfleteren.jazygit.feature.diff;

import static net.vanfleteren.jazygit.state.TestModels.loaded;
import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.copy.CopyMsg;
import net.vanfleteren.jazygit.gh.model.ReviewThread.Side;
import net.vanfleteren.jazygit.git.model.DiffText;
import net.vanfleteren.jazygit.git.model.FileVersion;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;
import net.vanfleteren.jazygit.state.diff.Anchor;
import net.vanfleteren.jazygit.state.diff.DiffCursorAt;
import net.vanfleteren.jazygit.state.diff.Scrolled;
import net.vanfleteren.jazygit.state.diff.ScrolledContent;
import net.vanfleteren.jazygit.state.diff.Version;
import net.vanfleteren.jazygit.state.load.LoadMsg;
import net.vanfleteren.jazygit.state.load.ReadVersion;
import org.junit.jupiter.api.Test;

/**
 * Showing a version of a Markdown file rendered, or as source again, with Ctrl+T (ADR 103).
 */
class RenderUpdateTest {

    private static final String AREA = "stash-changes";
    private static final Sha STASH = new Sha("5555");
    private static final String HEADER = "diff --git a/a.md b/a.md\n--- a/a.md\n+++ b/a.md\n";
    private static final DiffText CHANGES = DiffText.of(HEADER + "@@ -1,3 +1,3 @@\n # Title\n-old\n+new\n end\n");
    private static final DiffText WHOLE = CHANGES;
    private static final Anchor.OnLine ADDED = new Anchor.OnLine("a.md", Side.RIGHT, 2);

    @Test
    void onTheDiffTheNewVersionShowsRendered() {
        Next next = Update.update(loaded(), new VersionMsg.RenderToggled(at(CHANGES, ADDED)));

        assertEquals(1, next.cmds().size());
        Version version = version(next.model()).get();
        assertEquals(Option.of(FileVersion.NEW), version.shown());
        assertTrue(version.rendered());
    }

    @Test
    void onceReadItIsRenderingAndAgainItShowsAsSource() {
        Model rendered = readWhole(Update.update(loaded(), new VersionMsg.RenderToggled(at(CHANGES, ADDED))));
        assertEquals(Option.of(FileVersion.NEW), version(rendered).flatMap(Version::rendering));

        Next source = Update.update(rendered, new VersionMsg.RenderToggled(at(CHANGES, ADDED)));

        assertEquals(List.of(), source.cmds());
        assertEquals(Option.of(FileVersion.NEW), version(source.model()).flatMap(Version::showing));
        assertEquals(Option.none(), version(source.model()).flatMap(Version::rendering));
    }

    @Test
    void aVersionShownAsSourceShowsRenderedAsItIs() {
        Model newVersion = readWhole(Update.update(loaded(), new VersionMsg.Cycled(at(CHANGES, ADDED))));
        Model original = Update.update(newVersion, new VersionMsg.Cycled(at(CHANGES, ADDED)))
                .model();

        Next rendered = Update.update(original, new VersionMsg.RenderToggled(at(CHANGES, ADDED)));

        assertEquals(Option.of(FileVersion.ORIGINAL), version(rendered.model()).flatMap(Version::rendering));
    }

    @Test
    void ctrlOWhileRenderedShowsTheDiffAndThenAVersionRenderedAgain() {
        Model rendered = readWhole(Update.update(loaded(), new VersionMsg.RenderToggled(at(CHANGES, ADDED))));

        Model original = Update.update(rendered, new VersionMsg.Cycled(at(CHANGES, ADDED)))
                .model();
        assertEquals(Option.of(FileVersion.ORIGINAL), version(original).flatMap(Version::rendering));

        Model diff = Update.update(original, new VersionMsg.Cycled(at(CHANGES, ADDED)))
                .model();
        assertEquals(Option.none(), version(diff).flatMap(Version::shown));
        assertTrue(version(diff).get().rendered());

        Model again =
                Update.update(diff, new VersionMsg.Cycled(at(CHANGES, ADDED))).model();
        assertEquals(Option.of(FileVersion.NEW), version(again).flatMap(Version::rendering));
    }

    @Test
    void aDeletedFileShowsItsOriginalRendered() {
        DiffText deleted = DiffText.of("diff --git a/a.md b/a.md\ndeleted file mode 100644\n--- a/a.md\n+++ /dev/null\n"
                + "@@ -1,2 +0,0 @@\n-# Title\n-gone\n");
        Anchor.OnLine gone = new Anchor.OnLine("a.md", Side.LEFT, 2);

        Next next = Update.update(loaded(), new VersionMsg.RenderToggled(at(deleted, gone)));

        assertEquals(Option.of(FileVersion.ORIGINAL), version(next.model()).flatMap(Version::shown));
        assertTrue(version(next.model()).get().rendered());
    }

    @Test
    void aFileThatIsNoMarkdownIsLeftAsItIs() {
        DiffText text = DiffText.of(CHANGES.text().replace("a.md", "a.txt"));
        Model model = loaded();

        Next next =
                Update.update(model, new VersionMsg.RenderToggled(at(text, new Anchor.OnLine("a.txt", Side.RIGHT, 2))));

        assertSame(model, next.model());
        assertEquals(List.of(), next.cmds());
    }

    @Test
    void nothingIsCopiedFromAVersionRendered() {
        Model rendered = readWhole(Update.update(loaded(), new VersionMsg.RenderToggled(at(CHANGES, ADDED))));

        Next copied = Update.update(rendered, new CopyMsg.Asked(at(CHANGES, ADDED)));

        assertEquals(List.of(), copied.cmds());
    }

    /**
     * The cursor on {@code anchor} in the changes {@code diff} of the stash.
     */
    private static DiffCursorAt at(DiffText diff, Anchor anchor) {
        return new DiffCursorAt(
                AREA,
                new Scrolled(new ScrolledContent.StashChanges(STASH, diff), 0, 0, Option.of(anchor), Option.none()),
                anchor,
                Option.none(),
                Option.none(),
                Option.none());
    }

    /**
     * {@code asked} once the file it reads whole is read.
     */
    private static Model readWhole(Next asked) {
        ReadVersion read = (ReadVersion) asked.cmds().head();
        return TestModels.done(TestModels.running(asked.model(), read), read, new LoadMsg.VersionRead(read, WHOLE))
                .model();
    }

    private static Option<Version> version(Model model) {
        return model.repoView().versions().get(AREA);
    }
}
