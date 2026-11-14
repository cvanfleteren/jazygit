package net.vanfleteren.jazygit.ui.diff;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
import net.vanfleteren.jazygit.state.diff.Rendered;
import net.vanfleteren.jazygit.ui.text.TextWidth;
import net.vanfleteren.jazygit.ui.theme.Theme;
import org.junit.jupiter.api.Test;

/**
 * A version of YAML rendered (ADR 123): drawn as JSON is (ADR 122), with a drawing of its own for each thing only YAML
 * has.
 */
class RenderedYamlTest {

    @Test
    void aWorkflowIsDrawnWithItsValuesAsWrittenItsMatrixAsATableAndItsScriptAsItsLines() {
        assertEquals(
                List.of(
                        "name  verify",
                        "on {2}",
                        "├── push {1}",
                        "│   └── branches  [main]",
                        "└── pull_request  null",
                        "jobs {1}",
                        "└── build {3}",
                        "    ├── runs-on  ${{ matrix.os }}",
                        "    ├── strategy {1}",
                        "    │   └── include [2]",
                        "    │       │      os            java",
                        "    │       ├── 0  ubuntu-24.04    21",
                        "    │       └── 1  macos-15        25",
                        "    └── steps [2]",
                        "        ├── 0  {uses: actions/checkout@v4}",
                        "        └── 1  name: Build {3}",
                        "            ├── run |4",
                        "            │       # no token",
                        "            │       ./mvnw -B verify \\",
                        "            │         -Pnative",
                        "            │       echo \"done\"",
                        "            └── env  {GH_TOKEN: ${{ secrets.TOKEN }}}"),
                text("""
                        name: verify
                        on:
                          push:
                            branches: [main]
                          pull_request:
                        jobs:
                          build:
                            runs-on: ${{ matrix.os }}
                            strategy:
                              include:
                                - {os: ubuntu-24.04, java: 21}
                                - {os: macos-15, java: 25}
                            steps:
                              - uses: actions/checkout@v4
                              - name: Build
                                run: |
                                  # no token
                                  ./mvnw -B verify \\
                                    -Pnative
                                  echo "done"
                                env:
                                  GH_TOKEN: ${{ secrets.TOKEN }}
                        """));
    }

    @Test
    void eachLineOfABlockIsARowOfItsOwnStandingForItsLine() {
        List<RenderedTree.TreeRow> rows = rows("run: |\n  a\n\n  b\nnext: x\n", HashMap.empty());

        assertEquals(
                List.of(
                        Option.of("/run"),
                        Option.of("/run/0"),
                        Option.of("/run/1"),
                        Option.of("/run/2"),
                        Option.of("/next")),
                rows.map(RenderedTree.TreeRow::pointer));
        assertEquals(
                List.of(List.of(1), List.of(2), List.of(3), List.of(4), List.of(5)),
                rows.map(RenderedTree.TreeRow::lines));
    }

    @Test
    void aClosedBlockShowsItsFirstLineAndStandsForAllOfThem() {
        List<RenderedTree.TreeRow> rows = rows("run: |\n  first\n  second\n", HashMap.of("/run", false));

        assertEquals(List.of("run |2  first …"), rows.map(RenderedYamlTest::text));
        assertEquals(List.of(List.of(1, 2, 3)), rows.map(RenderedTree.TreeRow::lines));
    }

    @Test
    void aFoldedBlockIsDrawnAsTheLinesOfItsSource() {
        assertEquals(
                List.of("about >4", "    A long text,", "    folded.", "", "    Again."),
                text("about: >\n  A long text,\n  folded.\n\n  Again.\n").map(String::stripTrailing));
    }

    @Test
    void anchorsAliasesAndMergeKeysAreDrawnAsWrittenNeverExpanded() {
        assertEquals(
                List.of(
                        "x-common &common {2}",
                        "├── restart  unless-stopped",
                        "└── environment &env  {LOG_LEVEL: info, TZ: Europe/Brussels}",
                        "services {1}",
                        "└── worker {4}",
                        "    ├── <<  *common",
                        "    ├── image  ghcr.io/acme/worker:1.5",
                        "    ├── environment  *env",
                        "    └── ports  [\"8080:8080\"]",
                        "loop &loop  [*loop]"),
                text("""
                        x-common: &common
                          restart: unless-stopped
                          environment: &env
                            LOG_LEVEL: info
                            TZ: Europe/Brussels
                        services:
                          worker:
                            <<: *common
                            image: ghcr.io/acme/worker:1.5
                            environment: *env
                            ports: ["8080:8080"]
                        loop: &loop [*loop]
                        """));
    }

    @Test
    void thePreviewOfAClosedBlockIsCutToItsColumns() {
        List<RenderedTree.TreeRow> rows = rows("run: |\n  " + "東".repeat(30) + "\n  x\n", HashMap.of("/run", false));

        assertEquals(List.of("run |2  " + "東".repeat(20) + " …"), rows.map(RenderedYamlTest::text));
    }

    @Test
    void wideCharactersKeepTheColumnsOfATableInLine() {
        List<String> table = text("""
                        - {name: 東京, people: 37400068}
                        - {name: São Paulo, people: 12325232}
                        - {name: 🚀 launch, people: 1}
                        """).map(String::stripTrailing);

        assertEquals(
                List.of(
                        "   name       people",
                        "0  東京       37400068",
                        "1  São Paulo  12325232",
                        "2  🚀 launch         1"),
                table);
        assertEquals(1, table.tail().map(TextWidth::of).distinct().size());
    }

    @Test
    void aTagIsDrawnBeforeItsValueAlsoOnAContainer() {
        assertEquals(
                List.of(
                        "BucketName  !Sub ${AWS::StackName}-logs",
                        "Tags  [!Ref Owner]",
                        "Resource !Join [2]",
                        "├── 0  \"\"",
                        "└── 1  [\"arn:aws:s3:::\", !Ref Bucket, /*]",
                        "Data  !!binary aGk="),
                text("""
                        BucketName: !Sub ${AWS::StackName}-logs
                        Tags: [!Ref Owner]
                        Resource: !Join
                          - ""
                          - - "arn:aws:s3:::"
                            - !Ref Bucket
                            - /*
                        Data: !!binary aGk=
                        """));
    }

    @Test
    void oddKeysQuotesEmptyValuesAndKeysWrittenTwiceAreDrawnAsWritten() {
        assertEquals(
                List.of(
                        "[eu-west-1, eu-central-1]  primary",
                        "1  one",
                        "true  yes",
                        "quoted key  'single'",
                        "python  3.10",
                        "country  NO",
                        "empty  null",
                        "retries  3",
                        "name  last  (also on line 8, ignored)"),
                text("""
                        ? [eu-west-1, eu-central-1]
                        : primary
                        1: one
                        true: yes
                        "quoted key": 'single'
                        python: 3.10
                        country: NO
                        name: first
                        empty:
                        retries: 3
                        name: last
                        """));
    }

    @Test
    void eachDocumentOfAFileOfMoreThanOneIsANodeNamedByItsKindFirst() {
        List<RenderedTree.TreeRow> rows = rows("""
                apiVersion: apps/v1
                kind: Deployment
                metadata: {name: api}
                spec:
                  replicas: 3
                  selector: {app: api}
                ---
                apiVersion: v1
                kind: Service
                metadata: {name: api}
                """, HashMap.of("/1", false));

        assertEquals(
                List.of(
                        "--- 1  kind: Deployment {4}",
                        "├── apiVersion  apps/v1",
                        "├── metadata  {name: api}",
                        "└── spec {2}",
                        "    ├── replicas  3",
                        "    └── selector  {app: api}",
                        "--- 2  kind: Service {3}  apiVersion, metadata"),
                rows.map(RenderedYamlTest::text));
        assertEquals(Option.of("/0"), rows.head().pointer());
        assertEquals(List.of(7, 8, 9, 10), rows.last().lines());
    }

    @Test
    void aSequenceOfMoreThanTwentyRecordsStartsClosed() {
        String env = List.range(0, 22)
                .map(at -> "  - {name: V" + at + ", value: \"" + at + "\"}")
                .mkString("env:\n", "\n", "\n");

        assertEquals(List.of("env [22]  {name, value}"), text(env));
    }

    @Test
    void yamlThatCannotBeReadSaysWhereSnakeYamlStoppedAndWhatItMissed() {
        assertEquals(
                Either.left("Not rendered: line 4, column 3, could not find expected ':'"),
                RenderedTree.rows(
                                Rendered.YAML,
                                Vector.of("spec:", "  name: api", "  {{- with .Values.x }}", "  resources:"),
                                false,
                                80,
                                HashMap.empty(),
                                Theme.SpanStyles.NONE)
                        .map(List::size));
        assertEquals(
                Either.left("Not rendered: the file holds no YAML document"),
                RenderedTree.rows(
                                Rendered.YAML,
                                Vector.of("# nothing"),
                                false,
                                80,
                                HashMap.empty(),
                                Theme.SpanStyles.NONE)
                        .map(List::size));
        assertEquals(
                Either.left("Not rendered: the file is too long to render as YAML"),
                RenderedTree.rows(Rendered.YAML, Vector.of("a: 1"), true, 80, HashMap.empty(), Theme.SpanStyles.NONE)
                        .map(List::size));
    }

    @Test
    void theNewVersionMarksTheLineOfABlockThatChanged() {
        RichDiff.Drawn drawn = drawn(WORKFLOW, FileVersion.NEW);

        assertEquals(
                List.of("  run |3", "      a", "+     b", "      c", "  next  x"),
                drawnText(drawn).tail());
        assertEquals(
                List.of(
                        new Anchor.File("a.yml"),
                        new Anchor.Node("a.yml", "/run", new Anchor.OnLine("a.yml", Side.RIGHT, 1)),
                        new Anchor.Node("a.yml", "/run/0", new Anchor.OnLine("a.yml", Side.RIGHT, 2)),
                        new Anchor.Node("a.yml", "/run/1", new Anchor.OnLine("a.yml", Side.RIGHT, 3)),
                        new Anchor.Node("a.yml", "/run/2", new Anchor.OnLine("a.yml", Side.RIGHT, 4)),
                        new Anchor.Node("a.yml", "/next", new Anchor.OnLine("a.yml", Side.RIGHT, 5))),
                drawn.stops().map(stop -> stop.mark().anchor()));
    }

    @Test
    void yamlThatCannotBeReadIsItsSourceAfterALineThatSaysWhy() {
        String broken =
                "diff --git a/a.yml b/a.yml\n--- a/a.yml\n+++ b/a.yml\n@@ -1,2 +1,2 @@\n-a: [1]\n+a: [1\n b: 2\n";

        assertEquals(
                List.of("Not rendered: line 2, column 2, expected ',' or ']', but got :", "+ a: [1", "  b: 2"),
                drawnText(drawn(broken, FileVersion.NEW)).tail());
    }

    private static final String WORKFLOW = "diff --git a/a.yml b/a.yml\n--- a/a.yml\n+++ b/a.yml\n"
            + "@@ -1,5 +1,5 @@\n"
            + " run: |\n"
            + "   a\n"
            + "-  B\n"
            + "+  b\n"
            + "   c\n"
            + " next: x\n";

    private static RichDiff.Drawn drawn(String diff, FileVersion version) {
        ParsedDiff.ParsedFile whole = ParsedDiff.of(diff).files().head();
        return RichDiff.drawn(
                new Expansion.Folded(
                        new ParsedDiff(List.empty(), List.of(version.of(whole))),
                        HashMap.of("a.yml", Expansion.Fold.of(version, false, 1, true, HashMap.empty()))),
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

    private static List<String> text(String yaml) {
        return rows(yaml, HashMap.empty()).map(RenderedYamlTest::text);
    }

    private static String text(RenderedTree.TreeRow row) {
        return row.line().spans().stream().map(Span::content).collect(Collectors.joining());
    }

    private static List<RenderedTree.TreeRow> rows(String yaml, Map<String, Boolean> chose) {
        return RenderedTree.rows(
                        Rendered.YAML, Vector.of(yaml.split("\n", -1)), false, 80, chose, Theme.SpanStyles.NONE)
                .get();
    }
}
