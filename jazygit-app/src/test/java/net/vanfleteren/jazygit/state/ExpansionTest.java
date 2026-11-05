package net.vanfleteren.jazygit.state;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.ChangeType;
import net.vanfleteren.jazygit.git.model.CommitFile;
import net.vanfleteren.jazygit.git.model.DiffContext;
import net.vanfleteren.jazygit.git.model.DiffOptions;
import net.vanfleteren.jazygit.git.model.DiffText;
import net.vanfleteren.jazygit.git.model.Folding;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.state.Cmd.ReadWholeFile.Source;
import org.junit.jupiter.api.Test;

/**
 * The files of a diff folded from what was read of them whole (ADR 77).
 */
class ExpansionTest {

    private static final String HEADER = "diff --git a/a.txt b/a.txt\n--- a/a.txt\n+++ b/a.txt\n";
    private static final ScrolledContent CONTENT = new ScrolledContent.StashChanges(new Sha("5555"), DiffText.of(""));
    private static final Cmd.ReadWholeFile READ = new Cmd.ReadWholeFile(
            "stash-changes",
            CONTENT,
            new Source.OfStash(new Sha("5555"), new CommitFile("a.txt", ChangeType.MODIFIED)),
            "a.txt",
            DiffOptions.DEFAULT);

    @Test
    void aFileReadWholeIsFoldedAndTellsHowLongItIs() {
        Expansion.Folded folded = expanded(whole(30, 10, false)).folded(shown(10), DiffContext.DEFAULT);

        assertEquals(new Expansion.Fold(Option.of(30), Expansion.Read.WHOLE), folded.fold("a.txt"));
        assertEquals(List.of("@@ -1,13 +1,13 @@"), headers(folded));
    }

    @Test
    void aReadCutBeforeTheEndIsFoldedUpToTheCutAndTellsNoLength() {
        Expansion.Folded folded = expanded(whole(30, 10, true)).folded(shown(10), DiffContext.DEFAULT);

        assertEquals(new Expansion.Fold(Option.none(), Expansion.Read.CUT), folded.fold("a.txt"));
        assertEquals(List.of("@@ -1,13 +1,13 @@"), headers(folded));
    }

    @Test
    void aReadCutBeforeAChangeCannotBeFoldedAndSaysSo() {
        // The read stops at line 20; the file changed at line 25.
        Expansion.Folded folded = expanded(whole(20, 0, true)).folded(shown(25), DiffContext.DEFAULT);

        assertEquals(new Expansion.Fold(Option.none(), Expansion.Read.TOO_LONG), folded.fold("a.txt"));
        assertEquals(List.of("@@ -22,7 +22,7 @@"), headers(folded));
    }

    @Test
    void aReadWithoutTheChangesShownIsNotFoldedAsWhenTheFileChangedSince() {
        Expansion.Folded folded = expanded(whole(30, 12, false)).folded(shown(10), DiffContext.DEFAULT);

        assertEquals(Expansion.Fold.UNKNOWN, folded.fold("a.txt"));
        assertEquals(List.of("@@ -7,7 +7,7 @@"), headers(folded));
    }

    /**
     * The file read whole as {@code whole}, lines 1 to 6 expanded.
     */
    private static Expansion expanded(DiffText whole) {
        return new Expansion(
                CONTENT,
                HashMap.of(
                        "a.txt",
                        new Expansion.Expanded(READ, new Loadable.Loaded<>(whole), List.of(new Folding.Lines(1, 6)))));
    }

    /**
     * The first {@code lines} lines of the file, line {@code changed} changed, if any, cut there if {@code truncated}.
     */
    private static DiffText whole(int lines, int changed, boolean truncated) {
        return DiffText.of(
                HEADER + "@@ -1," + lines + " +1," + lines + " @@\n"
                        + List.rangeClosed(1, lines).map(n -> line(n, changed)).mkString(),
                truncated);
    }

    /**
     * Line {@code changed} changed, as git shows it with three lines of context.
     */
    private static net.vanfleteren.jazygit.git.model.ParsedDiff shown(int changed) {
        int from = changed - 3;
        return DiffText.of(HEADER + "@@ -" + from + ",7 +" + from + ",7 @@\n"
                        + List.rangeClosed(from, changed + 3)
                                .map(n -> line(n, changed))
                                .mkString())
                .parsed();
    }

    private static String line(int number, int changed) {
        return number == changed ? "-line" + number + "\n+changed\n" : " line" + number + "\n";
    }

    private static List<String> headers(Expansion.Folded folded) {
        return folded.diff().files().head().hunks().map(hunk -> hunk.hunk().header());
    }
}
