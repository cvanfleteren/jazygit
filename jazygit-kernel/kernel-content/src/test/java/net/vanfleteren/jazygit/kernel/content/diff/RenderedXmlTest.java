package net.vanfleteren.jazygit.kernel.content.diff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Vector;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.util.stream.Collectors;
import net.vanfleteren.jazygit.core.git.model.FileVersion;
import net.vanfleteren.jazygit.core.git.model.ParsedDiff;
import net.vanfleteren.jazygit.kernel.state.diff.Expansion;
import net.vanfleteren.jazygit.kernel.state.diff.Rendered;
import net.vanfleteren.jazygit.kernel.state.diff.Tree;
import net.vanfleteren.jazygit.kernel.state.diff.TreeFolds;
import net.vanfleteren.jazygit.kernel.ui.content.Drawn;
import net.vanfleteren.jazygit.kernel.ui.theme.Theme;
import org.junit.jupiter.api.Test;

/**
 * A version of XML rendered (ADR 151): drawn as JSON is (ADR 122), its elements with their attributes, their text and
 * their comments, records as a table.
 */
class RenderedXmlTest {

    private static final String POM = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!-- the build -->
            <project xmlns="urn:pom">
                <modelVersion>4.0.0</modelVersion>
                <description>
                    Reads XML,
                    to be drawn.
                </description>
                <dependencies>
                    <dependency>
                        <groupId>io.vavr</groupId>
                        <artifactId>vavr</artifactId>
                    </dependency>
                    <!-- for tests -->
                    <dependency>
                        <groupId>org.junit</groupId>
                        <artifactId>junit</artifactId>
                        <scope>test</scope>
                    </dependency>
                </dependencies>
                <build/>
            </project>""";

    @Test
    void aPomIsDrawnAsItsElementsItsDependenciesAsATableAndItsCommentsWhereTheyAre() {
        assertEquals(
                List.of(
                        "<!-- the build -->",
                        "project xmlns=\"urn:pom\" {4}",
                        "├── modelVersion  4.0.0",
                        "├── description ≡2",
                        "│       Reads XML,",
                        "│       to be drawn.",
                        "├── dependencies {3}",
                        "│   │      groupId    artifactId  scope",
                        "│   ├── 1  io.vavr    vavr",
                        "│   ├── <!-- for tests -->",
                        "│   └── 2  org.junit  junit       test",
                        "└── build"),
                text(POM));
    }

    @Test
    void eachRowStandsForItsLinesAndIsAStopByItsPathAsXPathWritesIt() {
        List<RenderedTree.TreeRow> rows = rows(POM, HashMap.empty());

        assertEquals(
                List.of(
                        Option.of("/comment()"),
                        Option.of("/project"),
                        Option.of("/project/modelVersion"),
                        Option.of("/project/description"),
                        Option.of("/project/description/0"),
                        Option.of("/project/description/1"),
                        Option.of("/project/dependencies"),
                        Option.none(),
                        Option.of("/project/dependencies/dependency[1]"),
                        Option.of("/project/dependencies/comment()"),
                        Option.of("/project/dependencies/dependency[2]"),
                        Option.of("/project/build")),
                rows.map(RenderedTree.TreeRow::pointer));
        assertEquals(
                List.of(
                        List.of(2),
                        List.of(3),
                        List.of(4),
                        List.of(5),
                        List.of(6),
                        List.of(7),
                        List.of(9),
                        List.of(9),
                        List.of(10, 11, 12, 13),
                        List.of(14),
                        List.of(15, 16, 17, 18, 19),
                        List.of(21)),
                rows.map(RenderedTree.TreeRow::lines));
    }

    @Test
    void everyNodeThatFoldsIsANodeByThePathOfItsRow() {
        Map<String, Tree> nodes = TreeFolds.nodes(Tree.read(Rendered.XML, POM).get());

        assertEquals(
                List.of("/project", "/project/description", "/project/dependencies"),
                nodes.keySet().toList());
    }

    @Test
    void aStartTagOfMoreLinesStandsForAllOfThemAndAClosedElementForAllItsLines() {
        String xml = """
                <a>
                  <b x="1"
                     y="2">
                    <c/>
                  </b>
                </a>""";

        assertEquals(
                List.of(List.of(1), List.of(2, 3), List.of(4)),
                rows(xml, HashMap.empty()).map(RenderedTree.TreeRow::lines));
        assertEquals(
                List.of(List.of(1), List.of(2, 3, 4, 5)),
                rows(xml, HashMap.of("/a/b", false)).map(RenderedTree.TreeRow::lines));
    }

    @Test
    void textMixedWithElementsIsRowsInItsPlace() {
        assertEquals(
                List.of("p {5}", "├── Hello", "├── b  you", "├── , and all", "├── i  of", "└── you!"),
                text("<p>Hello <b>you</b>, and all <i>of</i>\n  you!</p>"));
    }

    @Test
    void aTextOfMoreLinesAmongElementsIsARowForEachLine() {
        assertEquals(List.of("p {2}", "├── b  x", "└── one", "    two"), text("<p><b>x</b>\n  one\n  two\n</p>"));
    }

    @Test
    void aSectionOfCdataIsItsTextAfterAMark() {
        assertEquals(List.of("script  CDATA a < b"), text("<script><![CDATA[a < b]]></script>"));
    }

    @Test
    void aCommentOfMoreLinesIsANodeOfItsLines() {
        String xml = "<!--\n  Licensed\n  to you\n-->\n<a/>";

        assertEquals(List.of("<!-- ≡2", "    Licensed", "    to you", "a"), text(xml));
        assertEquals(List.of("<!-- ≡2  Licensed …", "a"), text(xml, HashMap.of("/comment()", false)));
        assertEquals(
                List.of(List.of(1, 2, 3, 4), List.of(5)),
                rows(xml, HashMap.of("/comment()", false)).map(RenderedTree.TreeRow::lines));
    }

    @Test
    void anElementOfMoreThanTwentyChildrenStartsClosedWithAPreviewButTheRootNever() {
        String items = List.range(0, 21).map(at -> "<item>i" + at + "</item>").mkString("\n");
        List<String> drawn = text("<r>\n<list>\n" + items + "\n</list>\n" + items + "\n</r>");

        assertEquals("r {22}", drawn.head());
        assertEquals("├── list [21]  i0, i1, i2, i3, i4, i5, i6, i7, i8, i9, …", drawn.get(1));
        assertEquals(23, drawn.size());
    }

    @Test
    void aClosedElementShowsTheNamesOfWhatItHoldsOrOfWhatItsFirstHolds() {
        String xml = """
                <a>
                  <b><c>1</c><d/></b>
                  <e><f><g/><h/></f><f><g/></f></e>
                </a>""";

        assertEquals(
                List.of("a {2}", "├── b {2}  c, d", "└── e [2]  {g, h}"),
                text(xml, HashMap.of("/a/b", false, "/a/e", false)));
    }

    @Test
    void recordsOfAttributesAreATableToo() {
        assertEquals(
                List.of(
                        "xs:simpleType name=\"Colour\" [3]",
                        "│      @value  @id",
                        "├── 1  red",
                        "├── 2  green   g",
                        "└── 3  blue"),
                text("""
                        <xs:simpleType name="Colour">
                          <xs:enumeration value="red"/>
                          <xs:enumeration value="green" id="g"/>
                          <xs:enumeration value="blue"/>
                        </xs:simpleType>"""));
    }

    @Test
    void aLineBreakReferredToInTextIsAnArrowOnTheRowOfTheLineItIsWrittenOn() {
        assertEquals(List.of("a  x↵y"), text("<a>x&#xD;&#xA;y</a>"));
        String xml = "<a>\n  x&#10;\n  y\n</a>\n<!-- end -->";
        assertEquals(List.of("a ≡2", "    x↵", "    y", "<!-- end -->"), text(xml));
        assertEquals(
                List.of(List.of(1), List.of(2), List.of(3), List.of(5)),
                rows(xml, HashMap.empty()).map(RenderedTree.TreeRow::lines));
    }

    @Test
    void anElementWithAttributesAndTextHasBothOnItsRow() {
        assertEquals(List.of("a [1]", "└── b x=\"1 & 2\"  text"), text("<a><b x='1 &amp; 2'>text</b></a>"));
    }

    @Test
    void xmlThatIsNotWellFormedSaysWhereAndWhy() {
        assertEquals(Either.left("Not rendered: line 3, column 1, </a> closes <b>"), why("<a>\n<b>\n</a>"));
        assertEquals(Either.left("Not rendered: line 1, column 6, expected '='"), why("<a b '1'/>"));
        assertEquals(Either.left("Not rendered: line 1, column 2, expected a name"), why("< a/>"));
        assertEquals(Either.left("Not rendered: line 1, column 6, expected a quoted value"), why("<a b=1/>"));
        assertEquals(Either.left("Not rendered: line 1, column 8, the text ends inside <a>"), why("<a><b/>"));
        assertEquals(Either.left("Not rendered: line 1, column 5, more after the root element"), why("<a/><b/>"));
        assertEquals(Either.left("Not rendered: line 1, column 1, text outside the root element"), why("x<a/>"));
        assertEquals(Either.left("Not rendered: line 1, column 1, </a> closes no element"), why("</a>"));
        assertEquals(
                Either.left("Not rendered: line 1, column 10, attribute 'b' written twice"), why("<a b='1' b='2'/>"));
        assertEquals(
                Either.left("Not rendered: line 1, column 7, '<' in the value of an attribute"), why("<a b='<'/>"));
        assertEquals(Either.left("Not rendered: line 1, column 4, '&' starts no reference"), why("<a>& b</a>"));
        assertEquals(Either.left("Not rendered: the file holds no XML element"), why("<!-- -->"));
        assertEquals(
                Either.left("Not rendered: nested deeper than 500 levels"),
                why("<a>".repeat(501) + "</a>".repeat(501)));
        assertEquals(
                Either.left("Not rendered: line 1, column 11, '--' inside a comment"), why("<a><!-- a -- b --></a>"));
        assertEquals(
                Either.left("Not rendered: line 1, column 9, no white space before an attribute"),
                why("<a b=\"1\"c=\"2\"/>"));
        assertEquals(
                Either.left("Not rendered: line 1, column 6, ']]>' outside a section of CDATA"), why("<a>x ]]> y</a>"));
        assertEquals(
                Either.left("Not rendered: line 1, column 4, character U+0001 is not allowed in XML"),
                why("<a>\u0001</a>"));
        assertEquals(
                Either.left("Not rendered: line 1, column 4, the XML declaration is not at the start of the file"),
                why("<a><?xml version=\"1.0\"?></a>"));
    }

    @Test
    void manySiblingsAreDrawnInTimeLinearInThem() {
        String records = List.range(0, 20_000)
                .map(at -> "<dependency><id>" + at + "</id><scope>test</scope></dependency>")
                .mkString("<project><dependencies>\n", "\n", "\n</dependencies></project>");
        String mixed = List.range(0, 20_000)
                .map(at -> "<b>" + at + "</b> text <!-- c -->")
                .mkString("<p>\n", "\n", "\n</p>");

        long started = System.nanoTime();
        List<String> table = text(records, HashMap.of("/project/dependencies", true));
        List<String> rows = text(mixed, HashMap.of("/p", true));
        long millis = (System.nanoTime() - started) / 1_000_000;

        assertEquals(20_003, table.size());
        assertEquals(60_001, rows.size());
        assertTrue(millis < 3_000, millis + " ms");
    }

    @Test
    void aVersionCutShortIsNotRendered() {
        assertEquals(
                Either.left("Not rendered: the file is too long to render as XML"),
                RenderedTree.rows(Rendered.XML, Vector.of("<a/>"), true, 80, HashMap.empty(), Theme.SpanStyles.NONE)
                        .map(rows -> rows.size()));
    }

    @Test
    void theNewVersionMarksTheRowsOfTheLinesThatChanged() {
        String diff = "diff --git a/a.xml b/a.xml\n--- a/a.xml\n+++ b/a.xml\n"
                + "@@ -1,4 +1,4 @@\n"
                + " <a>\n"
                + "-  <b>1</b>\n"
                + "+  <b>2</b>\n"
                + "   <c/>\n"
                + " </a>\n";

        assertEquals(
                List.of("  a {2}", "+ ├── b  2", "  └── c"),
                drawnText(drawn(diff, FileVersion.NEW)).tail());
    }

    @Test
    void xmlThatIsNotWellFormedIsItsSourceAfterALineThatSaysWhy() {
        String diff = "diff --git a/a.xml b/a.xml\n--- a/a.xml\n+++ b/a.xml\n"
                + "@@ -1,2 +1,2 @@\n"
                + "-<a></a>\n"
                + "+<a></b>\n"
                + " \n";

        assertEquals(
                List.of("Not rendered: line 1, column 4, </b> closes <a>", "+ <a></b>", "     ^", ""),
                drawnText(drawn(diff, FileVersion.NEW)).tail());
    }

    @Test
    void theLineThatSaysWhyAndTheMarkOfWhereReadingStoppedAreInTheColourOfAnError() {
        String diff = "diff --git a/a.xml b/a.xml\n--- a/a.xml\n+++ b/a.xml\n"
                + "@@ -1,1 +1,1 @@\n"
                + "-<a></a>\n"
                + "+<a></b>\n";
        Style error = Style.EMPTY.fg(Color.RED);
        Theme.SpanStyles styles = cssClass -> cssClass.equals(Theme.Classes.ERROR) ? error : Style.EMPTY;

        List<Line> lines = drawn(diff, FileVersion.NEW, styles).lines().tail();

        assertEquals(error, lines.get(0).spans().getFirst().style());
        assertEquals(error, lines.get(2).spans().getLast().style());
    }

    private static Drawn drawn(String diff, FileVersion version) {
        return drawn(diff, version, Theme.SpanStyles.NONE);
    }

    private static Drawn drawn(String diff, FileVersion version, Theme.SpanStyles styles) {
        ParsedDiff.ParsedFile whole = ParsedDiff.of(diff).files().head();
        return RichDiff.drawn(
                new Expansion.Folded(
                        new ParsedDiff(List.empty(), List.of(version.of(whole))),
                        HashMap.of("a.xml", Expansion.Fold.of(version, false, 1, true, HashMap.empty()))),
                Option.none(),
                Option.none(),
                RichDiff.Drawing.of(styles).withWidth(60));
    }

    private static List<String> drawnText(Drawn drawn) {
        return drawn.lines()
                .map(line -> line.spans().stream().map(Span::content).collect(Collectors.joining()))
                .map(String::stripTrailing);
    }

    private static Either<String, Integer> why(String xml) {
        return RenderedTree.rows(
                        Rendered.XML, Vector.of(xml.split("\n", -1)), false, 80, HashMap.empty(), Theme.SpanStyles.NONE)
                .map(List::size);
    }

    private static List<String> text(String xml) {
        return text(xml, HashMap.empty());
    }

    private static List<String> text(String xml, Map<String, Boolean> chose) {
        return rows(xml, chose).map(RenderedXmlTest::text);
    }

    private static String text(RenderedTree.TreeRow row) {
        return row.line().spans().stream()
                .map(Span::content)
                .collect(Collectors.joining())
                .stripTrailing();
    }

    private static List<RenderedTree.TreeRow> rows(String xml, Map<String, Boolean> chose) {
        return RenderedTree.rows(Rendered.XML, Vector.of(xml.split("\n", -1)), false, 80, chose, Theme.SpanStyles.NONE)
                .get();
    }
}
