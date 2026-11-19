package net.vanfleteren.jazygit.kernel.ui.content;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.model.DiffOptions;
import net.vanfleteren.jazygit.core.git.model.DiffText;
import net.vanfleteren.jazygit.core.git.model.FileVersion;
import net.vanfleteren.jazygit.kernel.settings.DiffLook;
import net.vanfleteren.jazygit.kernel.settings.DiffStyle;
import net.vanfleteren.jazygit.kernel.state.Loadable;
import net.vanfleteren.jazygit.kernel.state.diff.ScrolledContent;
import net.vanfleteren.jazygit.kernel.state.diff.Version;
import net.vanfleteren.jazygit.kernel.state.diff.WholeFile;
import org.junit.jupiter.api.Test;

class ShownChangesTest {

    private static final DiffText DIFF = DiffText.of("""
            diff --git a/a.txt b/a.txt
            index 1111111..2222222 100644
            --- a/a.txt
            +++ b/a.txt
            @@ -1,3 +1,3 @@
             one
            -two
            +TWO
             three
            """);

    private static final DiffLook RICH = new DiffLook(DiffStyle.RICH, true, true);

    /**
     * The new version of a.txt shown in place of its diff, read whole.
     */
    private static Version version() {
        ScrolledContent content = new ScrolledContent.Diff(DIFF);
        return new Version(
                content,
                "a.txt",
                Option.of(FileVersion.NEW),
                new WholeFile(content, new WholeFile.Source.OnDisk("a.txt"), "a.txt", DiffOptions.DEFAULT),
                new Loadable.Loaded<>(DIFF));
    }

    private static ShownChanges shown(Option<Version> version, DiffLook look) {
        return new ShownChanges(new ShownDiff(DIFF, Option.none()).versioned(version), look);
    }

    @Test
    void aDiffIsDrawnToTheWidthOfItsAreaOnlyWhenSplit() {
        assertTrue(
                shown(Option.none(), new DiffLook(DiffStyle.SPLIT, true, true)).drawnToWidth());
        assertFalse(shown(Option.none(), RICH).drawnToWidth());
    }

    @Test
    void aVersionRenderedOrBlamedIsDrawnToTheWidthOfItsArea() {
        assertTrue(shown(Option.of(version().withRendered(true)), RICH).drawnToWidth());
        assertTrue(shown(Option.of(version().withBlamed(true)), RICH).drawnToWidth());
        assertFalse(shown(Option.of(version()), RICH).drawnToWidth());
    }
}
