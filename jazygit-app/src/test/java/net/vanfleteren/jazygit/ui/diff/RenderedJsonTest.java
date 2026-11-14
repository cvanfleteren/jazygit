package net.vanfleteren.jazygit.ui.diff;

import static org.junit.jupiter.api.Assertions.*;

import dev.tamboui.text.Span;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Vector;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.util.stream.Collectors;
import net.vanfleteren.jazygit.gh.model.ReviewThread.Side;
import net.vanfleteren.jazygit.git.model.FileVersion;
import net.vanfleteren.jazygit.git.model.ParsedDiff;
import net.vanfleteren.jazygit.state.diff.Anchor;
import net.vanfleteren.jazygit.state.diff.Expansion;
import net.vanfleteren.jazygit.ui.theme.Theme;
import org.junit.jupiter.api.Test;

/**
 * A version of a JSON file rendered (ADR 122).
 */
class RenderedJsonTest {

    @Test
    void fieldsAreDrawnAsATreeInTheOrderOfTheFile() {
        List<String> rows = text("""
                {
                  "version": 3,
                  "flags": {
                    "b": {"enabled": true},
                    "a": {"enabled": false, "owner": "someone with a long name", "rollout": 100, "x": 1}
                  }
                }""");

        assertEquals(
                List.of(
                        "version  3",
                        "flags {2}",
                        "├── b  {enabled: true}",
                        "└── a {4}",
                        "    ├── enabled  false",
                        "    ├── owner  \"someone with a long name\"",
                        "    ├── rollout  100",
                        "    └── x  1"),
                rows);
    }

    @Test
    void eachRowStandsForItsLinesAndIsAStopByItsPath() {
        List<RenderedJson.JsonRow> rows = rows("""
                {
                  "a": [1, 2],
                  "b": {
                    "c/d": {"e": [1]}
                  }
                }""", HashMap.empty());

        assertEquals(
                List.of(Option.of("/a"), Option.of("/b"), Option.of("/b/c~1d"), Option.of("/b/c~1d/e")),
                rows.map(RenderedJson.JsonRow::pointer));
        assertEquals(List.of(List.of(2), List.of(3), List.of(4), List.of(4)), rows.map(RenderedJson.JsonRow::lines));
    }

    @Test
    void aNodeOfMoreThanTwentyEntriesStartsClosedWithAPreview() {
        String json = "{\"many\": ["
                + List.range(0, 25)
                        .map(at -> "{\"id\": " + at + ", \"v\": " + at + "}")
                        .mkString(", ") + "], \"few\": {\"a\": [1], \"b\": [2]}}";

        assertEquals(List.of("many [25]  {id, v}", "few {2}", "├── a  [1]", "└── b  [2]"), text(json));
    }

    @Test
    void whatTheUserChoseOverridesHowANodeStarts() {
        String json = "{\"many\": [" + List.range(0, 21).mkString(", ") + "], \"few\": {\"a\": [1], \"b\": [2]}}";

        List<String> rows = text(json, HashMap.of("/many", true, "/few", false), 60);

        assertEquals("many [21]", rows.head());
        assertEquals("few {2}  a, b", rows.last());
    }

    @Test
    void manyScalarsArePackedToTheWidth() {
        String json = "{\"n\": [" + List.range(100, 130).mkString(", ") + "]}";

        List<String> rows = text(json, HashMap.of("/n", true), 40);

        assertEquals("n [30]", rows.head());
        assertEquals("├── 0–4    100, 101, 102, 103, 104", rows.get(1));
        assertEquals("└── 25–29  125, 126, 127, 128, 129", rows.last());
    }

    @Test
    void anArrayOfRecordsIsATable() {
        List<String> rows = text("""
                {"checks": [
                  {"name": "verify", "seconds": 388},
                  {"name": "native-image", "seconds": 0}
                ]}""");

        assertEquals(
                List.of(
                        "checks [2]",
                        "│      name          seconds",
                        "├── 0  verify            388",
                        "└── 1  native-image        0"),
                rows);
    }

    @Test
    void theKeysOfATableAreNoStop() {
        List<RenderedJson.JsonRow> rows = rows("[{\"a\": 1}, {\"a\": 2}]", HashMap.empty());

        assertEquals(List.of(Option.none(), Option.of("/0"), Option.of("/1")), rows.map(RenderedJson.JsonRow::pointer));
    }

    @Test
    void anObjectInAnArrayIsNamedByAFieldWhichItDoesNotRepeat() {
        List<String> rows = text("""
                [
                  {"size": 1, "name": "build", "steps": [{"run": "mvn", "seconds": 12}]},
                  {"size": 2, "steps": [{"run": "gh", "seconds": 3}]}
                ]""");

        assertEquals(
                List.of(
                        "0  name: \"build\" {3}",
                        "├── size  1",
                        "└── steps [1]",
                        "    └── 0  {run: \"mvn\", seconds: 12}",
                        "1  size: 2 {2}",
                        "└── steps [1]",
                        "    └── 0  {run: \"gh\", seconds: 3}"),
                rows);
    }

    @Test
    void aClosedNodeShowsAsMuchOfWhatItHoldsAsFitsButNotTheFieldThatNamesIt() {
        String json = "[{\"name\": \"build\", \"runner\": {\"os\": [\"x\"]}, \"cache\": [{\"a\": 1}]}, {\"keys\": {"
                + List.range(0, 30)
                        .map(at -> "\"key-" + at + "\": [" + at + "]")
                        .mkString(", ") + "}}]";

        List<String> rows = text(json, HashMap.of("/0", false, "/1/keys", false), 80);

        assertEquals("0  name: \"build\" {3}  runner, cache", rows.head());
        assertEquals("└── keys {30}  key-0, key-1, key-2, key-3, key-4, …", rows.last());
    }

    @Test
    void ofAKeyWrittenTwiceTheLastIsDrawnWhereItIsWrittenAndStandsForAll() {
        List<RenderedJson.JsonRow> rows = rows("""
                {
                  "name": "first",
                  "size": 1,
                  "name": "last"
                }""", HashMap.empty());

        assertEquals(List.of("size  1", "name  \"last\"  (also on line 2, ignored)"), rows.map(RenderedJsonTest::text));
        assertEquals(List.of(4, 2), rows.last().lines());
    }

    @Test
    void scalarsKeepHowTheyAreWrittenAndNewlinesShowAsArrows() {
        assertEquals(List.of("n  1e3", "s  \"a↵b\"", "z  null"), text("{\"n\": 1e3, \"s\": \"a\\nb\", \"z\": null}"));
    }

    @Test
    void aRootScalarOrAnEmptyRootIsOneRow() {
        assertEquals(List.of("\"just\""), text("\"just\""));
        assertEquals(List.of("[]"), text("[]"));
    }

    @Test
    void textThatIsNoJsonSaysWhy() {
        assertEquals(
                Either.left("Not rendered: line 2, column 8, unexpected 'true'"),
                RenderedJson.rows(Vector.of("[1,", "  true true]"), false, 80, HashMap.empty(), Theme.SpanStyles.NONE)
                        .map(rows -> rows.size()));
        assertEquals(
                Either.left("Not rendered: line 1, column 11, unexpected string"),
                RenderedJson.rows(Vector.of("{\"a\": \"b\" \"c\"}"), false, 80, HashMap.empty(), Theme.SpanStyles.NONE)
                        .map(rows -> rows.size()));
        assertEquals(
                Either.left("Not rendered: line 1, column 8, unexpected end"),
                RenderedJson.rows(Vector.of("{\"a\": 1"), false, 80, HashMap.empty(), Theme.SpanStyles.NONE)
                        .map(rows -> rows.size()));
    }

    @Test
    void aLineBreakInAStringSaysSo() {
        assertEquals(
                Either.left("Not rendered: line 1, column 4, unexpected line break"),
                RenderedJson.rows(Vector.of("[\"a", "b\"]"), false, 80, HashMap.empty(), Theme.SpanStyles.NONE)
                        .map(rows -> rows.size()));
    }

    @Test
    void aCharacterThatDrawsNothingOrBadlyIsNamedByItsCodePoint() {
        assertEquals(
                Either.left("Not rendered: line 1, column 4, unexpected character U+0009"),
                RenderedJson.rows(Vector.of("[\"a\tb\"]"), false, 80, HashMap.empty(), Theme.SpanStyles.NONE)
                        .map(rows -> rows.size()));
        assertEquals(
                Either.left("Not rendered: line 1, column 4, unexpected character U+000D"),
                RenderedJson.rows(Vector.of("[\"a\rb\"]"), false, 80, HashMap.empty(), Theme.SpanStyles.NONE)
                        .map(rows -> rows.size()));
    }

    @Test
    void aFileStartingWithAByteOrderMarkIsRendered() {
        assertEquals(List.of("a  1"), text("\uFEFF{\n  \"a\": 1\n}"));
    }

    @Test
    void aLongArrayOfScalarsIsPackedAndALongTableDrawnInTimeLinearInThem() {
        String numbers = List.range(0, 20_000).mkString("{\"a\": [", ",\n", "]}");
        String records = List.range(0, 20_000)
                .map(at -> "{\"id\": " + at + ", \"x\": \"v\"}")
                .mkString("{\"b\": [", ",\n", "]}");

        long started = System.nanoTime();
        List<String> packed = text(numbers, HashMap.of("/a", true), 120);
        List<String> table = text(records, HashMap.of("/b", true), 120);
        long millis = (System.nanoTime() - started) / 1_000_000;

        assertEquals("└── 19993–19999  19993, 19994, 19995, 19996, 19997, 19998, 19999", packed.last());
        assertEquals(20_002, table.size());
        // Quadratic, the packing took eleven seconds and the table one.
        assertTrue(millis < 3_000, millis + " ms");
    }

    @Test
    void aVersionCutShortIsNotRendered() {
        assertEquals(
                Either.left("Not rendered: the file is too long to render as JSON"),
                RenderedJson.rows(Vector.of("{}"), true, 80, HashMap.empty(), Theme.SpanStyles.NONE)
                        .map(rows -> rows.size()));
    }

    @Test
    void theNewVersionShowsItsBarThenItsRowsWithAMarkerBesideThoseThatChanged() {
        List<String> lines = drawnText(drawn(CONFIG, FileVersion.NEW, false));

        assertEquals(" new version  a.json", lines.head());
        assertEquals(List.of("  few {2}", "+ ├── a  [2]", "  └── b  [2]", "  n  1"), lines.tail());
    }

    @Test
    void eachRowIsAStopButTheBarIsOneToo() {
        RichDiff.Drawn drawn = drawn(CONFIG, FileVersion.NEW, false);

        assertEquals(
                List.of(
                        new Anchor.File("a.json"),
                        new Anchor.Node("a.json", "/few", new Anchor.OnLine("a.json", Side.RIGHT, 2)),
                        new Anchor.Node("a.json", "/few/a", new Anchor.OnLine("a.json", Side.RIGHT, 3)),
                        new Anchor.Node("a.json", "/few/b", new Anchor.OnLine("a.json", Side.RIGHT, 4)),
                        new Anchor.Node("a.json", "/n", new Anchor.OnLine("a.json", Side.RIGHT, 6))),
                drawn.stops().map(stop -> stop.mark().anchor()));
    }

    @Test
    void aClosedNodeIsMarkedWhenALineInItChanged() {
        RichDiff.Drawn drawn = drawn(CONFIG, FileVersion.NEW, false, HashMap.of("/few", false));

        assertEquals(List.of("+ few {2}  a, b", "  n  1"), drawnText(drawn).tail());
    }

    @Test
    void theOriginalMarksWhatChangedAsRemoved() {
        assertEquals(
                "- ├── a  [1]",
                drawnText(drawn(CONFIG, FileVersion.ORIGINAL, false)).get(2));
    }

    @Test
    void jsonThatCannotBeReadIsItsSourceAfterALineThatSaysWhy() {
        String broken =
                "diff --git a/a.json b/a.json\n--- a/a.json\n+++ b/a.json\n@@ -1,2 +1,2 @@\n-[1,\n+[1,,\n  2]\n";
        RichDiff.Drawn drawn = drawn(broken, FileVersion.NEW, false);

        assertEquals(
                List.of("Not rendered: line 1, column 4, unexpected ','", "+ [1,,", "   2]"),
                drawnText(drawn).tail());
        assertEquals(
                List.of(new Anchor.File("a.json")),
                drawn.stops().map(stop -> stop.mark().anchor()));
    }

    @Test
    void aVersionCutShortIsItsSource() {
        assertEquals(
                "Not rendered: the file is too long to render as JSON",
                drawnText(drawn(CONFIG, FileVersion.NEW, true)).get(1));
    }

    private static final String CONFIG = "diff --git a/a.json b/a.json\n--- a/a.json\n+++ b/a.json\n"
            + "@@ -1,7 +1,7 @@\n"
            + " {\n"
            + "   \"few\": {\n"
            + "-    \"a\": [1],\n"
            + "+    \"a\": [2],\n"
            + "     \"b\": [2]\n"
            + "   },\n"
            + "   \"n\": 1\n"
            + " }\n";

    private static RichDiff.Drawn drawn(String diff, FileVersion version, boolean cut) {
        return drawn(diff, version, cut, HashMap.empty());
    }

    private static RichDiff.Drawn drawn(String diff, FileVersion version, boolean cut, Map<String, Boolean> nodes) {
        ParsedDiff.ParsedFile whole = ParsedDiff.of(diff).files().head();
        return RichDiff.drawn(
                new Expansion.Folded(
                        new ParsedDiff(List.empty(), List.of(version.of(whole))),
                        HashMap.of("a.json", Expansion.Fold.of(version, cut, 1, true, nodes))),
                true,
                true,
                Option.none(),
                Option.none(),
                Theme.SpanStyles.NONE,
                40);
    }

    private static List<String> drawnText(RichDiff.Drawn drawn) {
        return drawn.lines()
                .map(line -> line.spans().stream().map(Span::content).collect(Collectors.joining()))
                .map(String::stripTrailing);
    }

    private static List<String> text(String json) {
        return text(json, HashMap.empty(), 80);
    }

    private static List<String> text(String json, Map<String, Boolean> chose, int width) {
        return RenderedJson.rows(Vector.of(json.split("\n", -1)), false, width, chose, Theme.SpanStyles.NONE)
                .get()
                .map(RenderedJsonTest::text);
    }

    private static String text(RenderedJson.JsonRow row) {
        return row.line().spans().stream().map(Span::content).collect(Collectors.joining());
    }

    private static List<RenderedJson.JsonRow> rows(String json, Map<String, Boolean> chose) {
        return RenderedJson.rows(Vector.of(json.split("\n", -1)), false, 80, chose, Theme.SpanStyles.NONE)
                .get();
    }
}
