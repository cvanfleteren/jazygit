package net.vanfleteren.jazygit.content.diff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import io.vavr.collection.HashMap;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.collection.Vector;
import io.vavr.control.Option;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.stream.Collectors;
import net.vanfleteren.jazygit.git.model.Blame;
import net.vanfleteren.jazygit.git.model.FileVersion;
import net.vanfleteren.jazygit.git.model.ParsedDiff;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.state.Loadable;
import net.vanfleteren.jazygit.state.diff.Blamed;
import net.vanfleteren.jazygit.state.diff.Expansion;
import net.vanfleteren.jazygit.ui.text.Rows;
import net.vanfleteren.jazygit.ui.theme.Theme;
import org.junit.jupiter.api.Test;

/**
 * The column of the blame of a version shown blamed (ADR 147).
 */
class BlameColumnTest {

    private static final ParsedDiff.ParsedFile WHOLE =
            ParsedDiff.of("""
                    diff --git a/f.txt b/f.txt
                    --- a/f.txt
                    +++ b/f.txt
                    @@ -1,4 +1,4 @@
                     one
                     two
                    -three
                    +THREE
                     four
                    """).files().head();

    private static final Blame.Commit OLD = commit('a', "Alex Moreau", LocalDate.of(2025, 3, 1));
    private static final Blame.Commit NEW = commit('b', "Sam", LocalDate.of(2026, 9, 14));

    // Each shade colours its bar; the other classes have no colour.
    private static final Theme.SpanStyles SHADED =
            cssClass -> cssClass.startsWith(Theme.Classes.BLAME_AGE) ? Style.EMPTY.fg(Color.YELLOW) : Style.EMPTY;

    @Test
    void eachRunOfLinesFromOneCommitIsLabelledOnItsFirstLineAndBarredOnAll() {
        Blame blame = blame(OLD, OLD, Blame.NOT_COMMITTED, NEW);

        List<String> lines = text(drawn(blamed(blame), 120, SHADED).lines());

        assertEquals(
                List.of(
                        " new version  f.txt",
                        "▌ aaaaaaa AM 2025-03-01   1 │   one",
                        "▌                         2 │   two",
                        "  uncommitted             3 │ + THREE",
                        "▌ bbbbbbb Sa 2026-09-14   4 │   four"),
                lines.map(String::stripTrailing));
    }

    @Test
    void aLineOfTheCommitWhoseVersionThisIsIsLabelledAsSuch() {
        Blame blame = blame(OLD, OLD, NEW, OLD);

        List<String> lines = text(drawn(new Blamed(new Loadable.Loaded<>(blame), HashSet.of(NEW.sha())), 120, SHADED)
                        .lines())
                .map(String::stripTrailing);

        assertEquals("▌ this commit             3 │ + THREE", lines.get(3));
        assertEquals("▌ aaaaaaa AM 2025-03-01   4 │   four", lines.get(4));
    }

    @Test
    void aThemeWithoutTheShadesDrawsNoBar() {
        List<String> lines = text(drawn(blamed(blame(OLD, OLD, OLD, OLD)), 120, Theme.SpanStyles.NONE)
                .lines());

        assertEquals("  aaaaaaa AM 2025-03-01   1 │   one", lines.get(1).stripTrailing());
    }

    @Test
    void whileTheBlameIsReadTheColumnIsBlankAsWide() {
        List<String> lines =
                text(drawn(new Blamed(new Loadable.Loading<>()), 120, SHADED).lines());

        assertEquals("                          1 │   one", lines.get(1).stripTrailing());
    }

    @Test
    void inANarrowAreaTheColumnIsTheBarAndTheShaAndTheTitleSaysSo() {
        Blame blame = blame(OLD, OLD, Blame.NOT_COMMITTED, NEW);

        RichDiff.Drawn wide = drawn(blamed(blame), 120, SHADED);
        RichDiff.Drawn narrow = drawn(blamed(blame), 70, SHADED);

        assertEquals(
                List.of("▌ aaaaaaa   1 │   one", "▌           2 │   two", "  not yet   3 │ + THREE"),
                text(narrow.lines()).drop(1).take(3).map(String::stripTrailing));
        assertEquals(Option.of("blame shortened to fit"), narrow.note());
        assertTrue(wide.note().isEmpty());
    }

    @Test
    void aLineIsSearchedAfterTheColumnOfItsBlame() {
        Rows rows = drawn(blamed(blame(OLD, OLD, OLD, NEW)), 120, SHADED).rows();

        List<String> searched = List.range(1, rows.size())
                .map(row ->
                        rows.get(row).rawContent().substring(rows.source(row).from()));

        assertEquals(List.of("one", "two", "THREE", "four"), searched.map(String::stripTrailing));
    }

    private static RichDiff.Drawn drawn(Blamed blamed, int width, Theme.SpanStyles styles) {
        return RichDiff.drawn(
                new Expansion.Folded(
                        new ParsedDiff(List.empty(), List.of(FileVersion.NEW.of(WHOLE))),
                        HashMap.of(
                                WHOLE.file().path(),
                                Expansion.Fold.of(FileVersion.NEW, false, 1).withBlame(Option.of(blamed)))),
                false,
                false,
                Option.none(),
                Option.none(),
                styles,
                width,
                false);
    }

    private static Blamed blamed(Blame blame) {
        return new Blamed(new Loadable.Loaded<>(blame));
    }

    private static Blame blame(Blame.Commit... commits) {
        return new Blame(Vector.of(commits)
                .zipWithIndex()
                .map(at -> new Blame.Line(at._1(), at._2() + 1, "f.txt", Option.none())));
    }

    private static Blame.Commit commit(char digit, String author, LocalDate date) {
        return new Blame.Commit(
                new Sha(String.valueOf(digit).repeat(40)),
                author,
                date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant(),
                "summary",
                false);
    }

    private static List<String> text(List<Line> lines) {
        return lines.map(line -> line.spans().stream().map(Span::content).collect(Collectors.joining()));
    }
}
