package net.vanfleteren.jazygit.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.json.JsonTree.Field;
import net.vanfleteren.jazygit.json.JsonTree.JsonArray;
import net.vanfleteren.jazygit.json.JsonTree.JsonObject;
import net.vanfleteren.jazygit.json.JsonTree.Kind;
import net.vanfleteren.jazygit.json.JsonTree.Scalar;
import org.junit.jupiter.api.Test;

/**
 * Text read as a {@link JsonTree}, the lines of its values kept (ADR 122).
 */
class JsonTreeTest {

    @Test
    void everyValueKeepsTheLineItStartsOnAndAContainerTheLineItEndsOn() {
        Either<NotJson, JsonTree> tree = JsonTree.read("""
                {
                  "name": "verify",
                  "steps": [
                    1,
                    true
                  ],
                  "author": null
                }""");

        assertEquals(
                Either.right(new JsonObject(
                        1,
                        8,
                        List.of(
                                new Field("name", 2, new Scalar(2, Kind.STRING, "verify"), List.empty()),
                                new Field(
                                        "steps",
                                        3,
                                        new JsonArray(
                                                3,
                                                6,
                                                List.of(
                                                        new Scalar(4, Kind.NUMBER, "1"),
                                                        new Scalar(5, Kind.BOOLEAN, "true"))),
                                        List.empty()),
                                new Field("author", 7, new Scalar(7, Kind.NULL, "null"), List.empty())))),
                tree);
    }

    @Test
    void aNumberIsKeptAsItIsWritten() {
        assertEquals(
                Either.right(new JsonArray(
                        1, 1, List.of(new Scalar(1, Kind.NUMBER, "1e3"), new Scalar(1, Kind.NUMBER, "1.50")))),
                JsonTree.read("[1e3, 1.50]"));
    }

    @Test
    void escapesInAStringAreUndone() {
        assertEquals(Either.right(new Scalar(1, Kind.STRING, "a\nb \"c\"")), JsonTree.read("\"a\\nb \\\"c\\\"\""));
    }

    @Test
    void ofTwoFieldsWithTheSameKeyTheLastHoldsWhereItIsWrittenWithTheLinesOfTheOthers() {
        JsonObject object = (JsonObject) JsonTree.read("""
                        {
                          "a": 1,
                          "b": 2,
                          "a": 3,
                          "a": 4
                        }""").get();

        assertEquals(
                List.of(
                        new Field("b", 3, new Scalar(3, Kind.NUMBER, "2"), List.empty()),
                        new Field("a", 5, new Scalar(5, Kind.NUMBER, "4"), List.of(2, 4))),
                object.fields());
    }

    @Test
    void blankTextHoldsNothing() {
        assertEquals(Either.left(new NotJson.Nothing()), JsonTree.read("  \n"));
    }

    @Test
    void whereReadingStopsTheCharacterFoundThereIsKept() {
        assertEquals(Either.left(new NotJson.Broken(1, 4, Option.of("]"))), JsonTree.read("[1,]"));
    }

    @Test
    void aStringFoundWhereItCannotBeIsFoundWholeWhereItStarts() {
        assertEquals(
                Either.left(new NotJson.Broken(2, 3, Option.of("\"b\""))), JsonTree.read("{\"a\": 1\n  \"b\": 2}"));
    }

    @Test
    void aWordFoundWhereItCannotBeIsFoundWholeWhereItStarts() {
        assertEquals(Either.left(new NotJson.Broken(2, 8, Option.of("true"))), JsonTree.read("[1,\n  true true]"));
    }

    @Test
    void aNumberFoundWhereItCannotBeIsFoundWholeWhereItStarts() {
        assertEquals(Either.left(new NotJson.Broken(1, 4, Option.of("1234"))), JsonTree.read("[1 1234]"));
    }

    @Test
    void aCharacterThatStartsNoTokenIsFound() {
        assertEquals(Either.left(new NotJson.Broken(1, 2, Option.of("'"))), JsonTree.read("{'a': 1}"));
    }

    @Test
    void textThatStopsEarlyFindsNothingAtItsEnd() {
        assertEquals(Either.left(new NotJson.Broken(2, 8, Option.none())), JsonTree.read("{\"a\":\n  [1, 2"));
    }

    @Test
    void textThatStopsEarlyAfterALineBreakStopsAtTheEndOfItsLastLine() {
        assertEquals(Either.left(new NotJson.Broken(1, 8, Option.none())), JsonTree.read("{\"a\": 1\n"));
    }

    @Test
    void aLineBreakInAStringIsFoundWhereTheLineBreaks() {
        assertEquals(Either.left(new NotJson.Broken(1, 4, Option.of("\n"))), JsonTree.read("[\"a\nb\"]"));
    }

    @Test
    void linesBreakOnlyAtALineFeedAsParssonBreaksThem() {
        assertEquals(
                Either.left(new NotJson.Broken(2, 7, Option.of("true"))),
                JsonTree.read("[\"x\u2028y\u0085z\",\n true true]"));
        assertEquals(Either.left(new NotJson.Broken(2, 7, Option.of("true"))), JsonTree.read("[1,\r\n true true]"));
    }

    @Test
    void aByteOrderMarkStartingTheTextIsNoPartOfIt() {
        assertEquals(
                Either.right(new JsonObject(
                        1, 1, List.of(new Field("a", 1, new Scalar(1, Kind.NUMBER, "1"), List.empty())))),
                JsonTree.read("\uFEFF{\"a\": 1}"));
    }

    @Test
    void anObjectOfManyKeysIsReadInTimeLinearInThem() {
        String many = List.range(0, 20_000).map(at -> "\"k" + at + "\": " + at).mkString("{", ",\n", "}");

        long started = System.nanoTime();
        JsonObject object = (JsonObject) JsonTree.read(many).get();
        long millis = (System.nanoTime() - started) / 1_000_000;

        assertEquals(20_000, object.fields().size());
        // Quadratic, it took eleven seconds.
        assertTrue(millis < 2_000, millis + " ms");
    }

    @Test
    void moreAfterTheValueIsNotJson() {
        assertEquals(Either.left(new NotJson.MoreAfter(2)), JsonTree.read("[1]\n2"));
    }

    @Test
    void nestingDeeperThanItIsReadIsTooDeep() {
        assertEquals(Either.left(new NotJson.TooDeep(500)), JsonTree.read("[".repeat(501) + "]".repeat(501)));
    }
}
