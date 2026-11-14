package net.vanfleteren.jazygit.yaml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.yaml.YamlTree.Alias;
import net.vanfleteren.jazygit.yaml.YamlTree.Block;
import net.vanfleteren.jazygit.yaml.YamlTree.Document;
import net.vanfleteren.jazygit.yaml.YamlTree.Field;
import net.vanfleteren.jazygit.yaml.YamlTree.Kind;
import net.vanfleteren.jazygit.yaml.YamlTree.Mapping;
import net.vanfleteren.jazygit.yaml.YamlTree.Properties;
import net.vanfleteren.jazygit.yaml.YamlTree.Scalar;
import net.vanfleteren.jazygit.yaml.YamlTree.Sequence;
import net.vanfleteren.jazygit.yaml.YamlTree.Style;
import org.junit.jupiter.api.Test;

/**
 * Text read as {@link YamlTree} documents, the lines of its nodes kept (ADR 123).
 */
class YamlTreeTest {

    private static final Properties NONE = Properties.NONE;

    @Test
    void everyNodeKeepsTheLineItStartsOnAndABlockCollectionTheLineItsLastValueEndsOn() {
        assertEquals(
                new Mapping(
                        1,
                        4,
                        NONE,
                        List.of(
                                field("name", 1, plain(1, Kind.STRING, "verify")),
                                field(
                                        "steps",
                                        2,
                                        new Sequence(
                                                3,
                                                4,
                                                NONE,
                                                List.of(plain(3, Kind.NUMBER, "1"), plain(4, Kind.BOOLEAN, "true")))))),
                root("""
                        name: verify
                        steps:
                          - 1
                          - true

                        # a comment after it
                        """));
    }

    @Test
    void aPlainValueIsWhatTheCoreSchemaOfYaml12ReadsItAs() {
        Mapping mapping = (Mapping) root("""
                a: on
                b: NO
                c: 3.10
                d: 0x1F
                e: ~
                f:
                g: "1"
                h: !!str 1
                """);

        assertEquals(
                List.of(
                        Kind.STRING,
                        Kind.STRING,
                        Kind.NUMBER,
                        Kind.NUMBER,
                        Kind.NULL,
                        Kind.NULL,
                        Kind.STRING,
                        Kind.STRING),
                mapping.fields().map(field -> ((Scalar) field.value()).kind()));
        assertEquals(
                List.of("on", "NO", "3.10", "0x1F", "~", "", "1", "1"),
                mapping.fields().map(field -> ((Scalar) field.value()).text()));
    }

    @Test
    void aQuotedValueKeepsHowItIsQuotedItsEscapesUndone() {
        assertEquals(
                List.of(
                        new Scalar(1, 1, NONE, Kind.STRING, Style.SINGLE_QUOTED, "it's"),
                        new Scalar(1, 1, NONE, Kind.STRING, Style.DOUBLE_QUOTED, "a\nb")),
                ((Sequence) root("['it''s', \"a\\nb\"]")).elements());
    }

    @Test
    void aBlockKeepsTheLinesOfItsSourceWithoutTheirIndentationNorTheBlankLinesItEndsWith() {
        Mapping mapping = (Mapping) root("""
                run: |
                  # a comment is text here
                  ./mvnw verify \\
                    -Pnative

                  echo done

                about: >
                  folded
                  text
                next: x
                """);

        assertEquals(
                new Block(
                        1,
                        6,
                        NONE,
                        false,
                        List.of("# a comment is text here", "./mvnw verify \\", "  -Pnative", "", "echo done")),
                mapping.fields().head().value());
        assertEquals(
                new Block(8, 10, NONE, true, List.of("folded", "text")),
                mapping.fields().get(1).value());
        assertEquals(11, mapping.fields().get(2).line());
    }

    @Test
    void aBlockWithItsTagOrAnchorOnTheLineBeforeStartsAtItsIndicator() {
        Mapping mapping = (Mapping) root("""
                run: !shell
                  |
                  a
                  b
                script: &s
                  >
                  c
                d: 1
                """);

        assertEquals(
                new Block(2, 4, new Properties(Option.of("!shell"), Option.none()), false, List.of("a", "b")),
                mapping.fields().head().value());
        assertEquals(
                new Block(6, 7, new Properties(Option.none(), Option.of("s")), true, List.of("c")),
                mapping.fields().get(1).value());
    }

    @Test
    void aSequenceAndAMappingOfFiftyThousandEntriesAreReadInTimeLinearInThem() {
        String sequence = List.range(0, 50_000).map(at -> "- " + at).mkString("\n");
        String mapping = List.range(0, 50_000).map(at -> "k" + at + ": " + at).mkString("\n");

        long started = System.nanoTime();
        int read = ((Sequence) root(sequence)).elements().size()
                + ((Mapping) root(mapping)).fields().size();
        long millis = (System.nanoTime() - started) / 1_000_000;

        assertEquals(100_000, read);
        // Appending to lists, reading 40 000 entries took forty seconds.
        assertTrue(millis < 5_000, millis + " ms");
    }

    @Test
    void anAliasIsItselfNeverWhatItNames() {
        Mapping mapping = (Mapping) root("""
                base: &base {a: 1}
                copy: *base
                loop: &loop [*loop]
                """);

        assertEquals(
                new Properties(Option.none(), Option.of("base")),
                ((Mapping) mapping.fields().head().value()).properties());
        assertEquals(new Alias(2, "base"), mapping.fields().get(1).value());
        assertEquals(
                new Sequence(3, 3, new Properties(Option.none(), Option.of("loop")), List.of(new Alias(3, "loop"))),
                mapping.fields().get(2).value());
    }

    @Test
    void aTagIsKeptWithItsNodeOneOfYamlsOwnShort() {
        Mapping mapping = (Mapping) root("""
                ref: !Ref Bucket
                data: !!binary aGk=
                join: !Join [a, b]
                """);

        assertEquals(
                List.of(Option.of("!Ref"), Option.of("!!binary"), Option.of("!Join")),
                mapping.fields().map(field -> switch (field.value()) {
                    case Scalar scalar -> scalar.properties().tag();
                    case Sequence sequence -> sequence.properties().tag();
                    default -> Option.<String>none();
                }));
    }

    @Test
    void ofTwoFieldsWithTheSameKeyTheLastHoldsWhereItIsWrittenWithTheLinesOfTheOthers() {
        assertEquals(
                List.of(
                        field("b", 2, plain(2, Kind.NUMBER, "2")),
                        new Field("a", 4, plain(4, Kind.NUMBER, "4"), List.of(1, 3))),
                ((Mapping) root("a: 1\nb: 2\na: 3\na: 4\n")).fields());
    }

    @Test
    void aKeyThatIsNoScalarIsKeptAsItReadsInFlowStyle() {
        assertEquals(
                List.of("[eu-west-1, eu-central-1]", "1", "{a: \"b\"}"),
                ((Mapping) root("""
                                ? [eu-west-1, eu-central-1]
                                : primary
                                1: one
                                ? {a: "b"}
                                : c
                                """)).fields().map(Field::key));
    }

    @Test
    void everyDocumentIsReadEachFromItsLine() {
        assertEquals(
                Either.right(List.of(
                        new Document(1, new Mapping(1, 1, NONE, List.of(field("a", 1, plain(1, Kind.NUMBER, "1"))))),
                        new Document(2, new Mapping(3, 3, NONE, List.of(field("b", 3, plain(3, Kind.NUMBER, "2"))))))),
                YamlTree.read("a: 1\n---\nb: 2\n"));
    }

    @Test
    void textThatIsNoYamlSaysWhereReadingStoppedAndWhatWasMissed() {
        assertEquals(Either.left(new NotYaml.Broken(6, 7, "could not find expected ':'")), YamlTree.read("""
                        spec:
                          containers:
                            - name: api
                              image: x
                              {{- with .Values.resources }}
                              resources:
                        """));
        assertEquals(
                Either.left(new NotYaml.Broken(2, 1, "found YAML 2.0, which is not read")),
                YamlTree.read("# a comment\n%YAML 2.0\n---\na: 1\n"));
        assertEquals(Either.left(new NotYaml.Nothing()), YamlTree.read("# only a comment\n"));
        assertEquals(Either.left(new NotYaml.Nothing()), YamlTree.read(""));
    }

    @Test
    void nodesNestedDeeperThanTheyAreReadAreNoTree() {
        String deep = "[".repeat(YamlReader.MAX_DEPTH + 1) + "]".repeat(YamlReader.MAX_DEPTH + 1);

        assertEquals(Either.left(new NotYaml.TooDeep(YamlReader.MAX_DEPTH)), YamlTree.read(deep));
    }

    @Test
    void aFileOfAliasesOfAliasesStaysAsSmallAsItIsWritten() {
        String laughs = List.range(1, 10)
                .map(at -> "l" + at + ": &l" + at + " ["
                        + List.fill(9, "*l" + (at - 1)).mkString(", ") + "]")
                .prepend("l0: &l0 lol")
                .mkString("\n");

        Mapping mapping = (Mapping) root(laughs);

        assertEquals(10, mapping.fields().size());
        assertEquals(9, ((Sequence) mapping.fields().last().value()).elements().size());
    }

    private static YamlTree root(String text) {
        return YamlTree.read(text).get().head().root();
    }

    private static Scalar plain(int line, Kind kind, String text) {
        return new Scalar(line, line, NONE, kind, Style.PLAIN, text);
    }

    private static Field field(String key, int line, YamlTree value) {
        return new Field(key, line, value, List.empty());
    }
}
