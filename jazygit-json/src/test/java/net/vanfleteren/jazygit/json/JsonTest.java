package net.vanfleteren.jazygit.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.LinkedHashMap;
import io.vavr.collection.List;
import io.vavr.control.Either;
import java.math.BigDecimal;
import net.vanfleteren.jazygit.json.Json.JArray;
import net.vanfleteren.jazygit.json.Json.JBool;
import net.vanfleteren.jazygit.json.Json.JNull;
import net.vanfleteren.jazygit.json.Json.JNumber;
import net.vanfleteren.jazygit.json.Json.JObject;
import net.vanfleteren.jazygit.json.Json.JString;
import org.junit.jupiter.api.Test;

/**
 * Text parsed as {@link Json}.
 */
class JsonTest {

    @Test
    void everyKindOfValueIsRead() {
        Either<DecodeError, Json> json = Json.parse("""
                {"name": "verify", "count": 3, "ratio": 1.5, "done": true, "open": false, "author": null,
                 "labels": ["bug", 7]}""");

        assertEquals(
                Either.right(new JObject(LinkedHashMap.of(
                        "name", new JString("verify"),
                        "count", new JNumber(new BigDecimal("3")),
                        "ratio", new JNumber(new BigDecimal("1.5")),
                        "done", new JBool(true),
                        "open", new JBool(false),
                        "author", new JNull(),
                        "labels", new JArray(List.of(new JString("bug"), new JNumber(new BigDecimal("7"))))))),
                json);
    }

    @Test
    void theFieldsOfAnObjectKeepTheirOrder() {
        JObject object = (JObject) Json.parse("{\"b\": 1, \"a\": 2, \"c\": 3}").get();

        assertEquals(List.of("b", "a", "c"), object.fields().keySet().toList());
    }

    @Test
    void escapesAreUndone() {
        assertEquals(Either.right(new JString("a\tb\n\"c\" é")), Json.parse("\"a\\tb\\n\\\"c\\\" \\u00e9\""));
    }

    @Test
    void emptyObjectsAndArraysAreRead() {
        assertEquals(
                Either.right(new JArray(List.of(new JObject(LinkedHashMap.empty()), new JArray(List.empty())))),
                Json.parse("[{}, []]"));
    }

    @Test
    void textThatIsNotJsonIsAnError() {
        DecodeError error = Json.parse("{\"a\": ").getLeft();

        assertTrue(error.path().isEmpty());
        assertTrue(error.message().startsWith("not JSON: "), error.message());
    }

    @Test
    void nothingIsAnError() {
        assertEquals(Either.left(DecodeError.of("expected JSON, got nothing")), Json.parse("  \n"));
    }

    @Test
    void jsonNestedTooDeeplyIsAnError() {
        // JsonReader's limit of nesting is what keeps its recursion from overflowing the stack.
        assertEquals(
                Either.left(DecodeError.of("not JSON: nested deeper than 500")),
                Json.parse("[".repeat(3000) + "]".repeat(3000)));
    }

    @Test
    void jsonNestedAsDeeplyAsAllowedIsRead() {
        assertTrue(Json.parse("[".repeat(500) + "]".repeat(500)).isRight());
    }

    @Test
    void whitespaceAfterTheValueIsNotMore() {
        assertEquals(Either.right(new JArray(List.empty())), Json.parse(" [] \n"));
    }

    @Test
    void moreThanOneValueIsAnError() {
        // As gh prints the pages of --paginate without --slurp.
        assertEquals(Either.left(DecodeError.of("expected one JSON value, got more after it")), Json.parse("{} {}"));
    }

    @Test
    void theKindOfAValueIsTold() {
        assertEquals(
                List.of("an object", "an array", "a string", "a number", "a boolean", "null"),
                List.of("{}", "[]", "\"x\"", "1", "true", "null")
                        .map(text -> Json.parse(text).get().kind()));
    }
}
