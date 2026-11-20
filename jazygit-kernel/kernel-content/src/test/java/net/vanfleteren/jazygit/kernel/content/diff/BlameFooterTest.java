package net.vanfleteren.jazygit.kernel.content.diff;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.Vector;
import io.vavr.control.Option;
import java.time.Duration;
import java.time.Instant;
import net.vanfleteren.jazygit.core.gh.model.ReviewThread.Side;
import net.vanfleteren.jazygit.core.git.model.Blame;
import net.vanfleteren.jazygit.core.git.model.DiffOptions;
import net.vanfleteren.jazygit.core.git.model.DiffText;
import net.vanfleteren.jazygit.core.git.model.FileVersion;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.kernel.state.Loadable;
import net.vanfleteren.jazygit.kernel.state.diff.Anchor;
import net.vanfleteren.jazygit.kernel.state.diff.Blames;
import net.vanfleteren.jazygit.kernel.state.diff.ScrolledContent;
import net.vanfleteren.jazygit.kernel.state.diff.Version;
import net.vanfleteren.jazygit.kernel.state.diff.WholeFile;
import org.junit.jupiter.api.Test;

/**
 * The row under an area whose version is shown blamed (ADR 147).
 */
class BlameFooterTest {

    private static final String HEADER = "diff --git a/a.txt b/a.txt\n--- a/a.txt\n+++ b/a.txt\n";
    private static final DiffText WHOLE = DiffText.of(HEADER + "@@ -1,2 +1,2 @@\n one\n-two\n+TWO\n");
    private static final Instant NOW = Instant.parse("2026-11-18T12:00:00Z");
    private static final Blame.Commit FIRST = new Blame.Commit(
            new Sha("a".repeat(40)), "Alex Moreau", NOW.minus(Duration.ofDays(70)), "A version cycles", false);

    @Test
    void itSaysTheCommitOfTheLineOfTheCursor() {
        Blame blame = new Blame(Vector.of(FIRST, Blame.NOT_COMMITTED)
                .zipWithIndex()
                .map(at -> new Blame.Line(at._1(), at._2() + 1, "a.txt", Option.none())));
        Version version = blamed(FileVersion.NEW, new Loadable.Loaded<>(blame));

        assertEquals(
                Option.of("aaaaaaa Alex Moreau · 2 months ago · A version cycles"),
                BlameFooter.text(version, Option.of(new Anchor.OnLine("a.txt", Side.RIGHT, 1)), NOW));
        assertEquals(
                Option.of("Not committed yet"),
                BlameFooter.text(version, Option.of(new Anchor.OnLine("a.txt", Side.RIGHT, 2)), NOW));
        // On the bar of the file, which is no line.
        assertEquals(Option.of(""), BlameFooter.text(version, Option.of(new Anchor.File("a.txt")), NOW));
    }

    @Test
    void theOriginalFindsItsLinesByTheirAnchorsInTheDiff() {
        Blame blame = new Blame(Vector.of(FIRST, Blame.NOT_COMMITTED)
                .zipWithIndex()
                .map(at -> new Blame.Line(at._1(), at._2() + 1, "a.txt", Option.none())));
        Version version = blamed(FileVersion.ORIGINAL, new Loadable.Loaded<>(blame));

        // The removed line is line 2 of the original, on the left.
        assertEquals(
                Option.of("Not committed yet"),
                BlameFooter.text(version, Option.of(new Anchor.OnLine("a.txt", Side.LEFT, 2)), NOW));
    }

    @Test
    void whileTheBlameIsReadItSaysSoAndNothingWithoutBlame() {
        assertEquals(
                Option.of("reading blame…"),
                BlameFooter.text(blamed(FileVersion.NEW, Loadable.loading()), Option.none(), NOW));
        assertEquals(
                Option.none(),
                BlameFooter.text(blamed(FileVersion.NEW, Loadable.loading()).withBlamed(false), Option.none(), NOW));
    }

    @Test
    void anOldCommitIsSaidToBeMonthsOrYearsAgo() {
        assertEquals("3d ago", BlameFooter.ago(Duration.ofDays(3)));
        assertEquals("2 months ago", BlameFooter.ago(Duration.ofDays(70)));
        assertEquals("3 years ago", BlameFooter.ago(Duration.ofDays(3 * 365 + 10)));
    }

    private static Version blamed(FileVersion shown, Loadable<Blame> blame) {
        ScrolledContent content = new ScrolledContent.Diff(WHOLE);
        return new Version(
                        content,
                        "a.txt",
                        Option.of(shown),
                        new WholeFile(content, new WholeFile.Source.OnDisk("a.txt"), "a.txt", DiffOptions.DEFAULT),
                        new Loadable.Loaded<>(WHOLE))
                .withBlamed(true)
                .withBlames(Blames.none(false).with(shown, blame));
    }
}
