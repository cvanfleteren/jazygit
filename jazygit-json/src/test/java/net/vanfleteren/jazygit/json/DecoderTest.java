package net.vanfleteren.jazygit.json;

import static net.vanfleteren.jazygit.json.Decoder.at;
import static net.vanfleteren.jazygit.json.Decoder.bool;
import static net.vanfleteren.jazygit.json.Decoder.byField;
import static net.vanfleteren.jazygit.json.Decoder.enumOf;
import static net.vanfleteren.jazygit.json.Decoder.fail;
import static net.vanfleteren.jazygit.json.Decoder.field;
import static net.vanfleteren.jazygit.json.Decoder.instant;
import static net.vanfleteren.jazygit.json.Decoder.integer;
import static net.vanfleteren.jazygit.json.Decoder.list;
import static net.vanfleteren.jazygit.json.Decoder.longValue;
import static net.vanfleteren.jazygit.json.Decoder.map2;
import static net.vanfleteren.jazygit.json.Decoder.map3;
import static net.vanfleteren.jazygit.json.Decoder.map8;
import static net.vanfleteren.jazygit.json.Decoder.nullable;
import static net.vanfleteren.jazygit.json.Decoder.string;
import static net.vanfleteren.jazygit.json.Decoder.succeed;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * What decoders make of JSON, and where and why they fail.
 */
class DecoderTest {

    private static final Decoder<Check> CHECK = map3(
            field("name", string()),
            field("state", enumOf(State.class)),
            field("startedAt", nullable(instant())),
            Check::new);

    private static <T> Either<String, T> decode(Decoder<T> decoder, String text) {
        return decoder.parse(text).mapLeft(DecodeError::text);
    }

    @Test
    void valuesAreDecoded() {
        assertEquals(Either.right("verify"), decode(string(), "\"verify\""));
        assertEquals(Either.right(true), decode(bool(), "true"));
        assertEquals(Either.right(42), decode(integer(), "42"));
        assertEquals(Either.right(9_000_000_000L), decode(longValue(), "9000000000"));
        assertEquals(
                Either.right(Instant.parse("2026-10-26T22:00:02Z")), decode(instant(), "\"2026-10-26T22:00:02Z\""));
        assertEquals(Either.right(State.SUCCESS), decode(enumOf(State.class), "\"SUCCESS\""));
    }

    @Test
    void aValueOfAnotherKindFails() {
        assertEquals(Either.left("expected a string, got null"), decode(string(), "null"));
        assertEquals(Either.left("expected a boolean, got a string"), decode(bool(), "\"true\""));
        assertEquals(Either.left("expected a number, got a string"), decode(integer(), "\"42\""));
        assertEquals(Either.left("expected an object, got an array"), decode(field("a", string()), "[]"));
        assertEquals(Either.left("expected an array, got an object"), decode(list(string()), "{}"));
    }

    @Test
    void aNumberThatDoesNotFitFails() {
        assertEquals(Either.left("expected an int, got 1.5"), decode(integer(), "1.5"));
        assertEquals(Either.left("expected an int, got 9000000000"), decode(integer(), "9000000000"));
        assertEquals(Either.left("expected a long, got 1E+20"), decode(longValue(), "1e20"));
    }

    @Test
    void textThatIsNotWhatItShouldBeFails() {
        assertEquals(Either.left("expected an instant, got \"yesterday\""), decode(instant(), "\"yesterday\""));
        assertEquals(
                Either.left("expected one of SUCCESS, FAILURE, got \"SKIPPED\""),
                decode(enumOf(State.class), "\"SKIPPED\""));
    }

    @Test
    void aRecordIsDecodedFromItsFields() {
        assertEquals(
                Either.right(new Check("verify", State.SUCCESS, Option.of(Instant.parse("2026-10-26T22:00:02Z")))),
                decode(
                        CHECK,
                        "{\"name\": \"verify\", \"state\": \"SUCCESS\", \"startedAt\": \"2026-10-26T22:00:02Z\"}"));
    }

    @Test
    void nullIsNoneWhereItIsTaken() {
        assertEquals(
                Either.right(new Check("verify", State.FAILURE, Option.none())),
                decode(CHECK, "{\"name\": \"verify\", \"state\": \"FAILURE\", \"startedAt\": null}"));
    }

    @Test
    void aMissingFieldFailsEvenWhereNullIsTaken() {
        assertEquals(
                Either.left("startedAt: missing"), decode(CHECK, "{\"name\": \"verify\", \"state\": \"FAILURE\"}"));
    }

    @Test
    void theErrorSaysWhereItFailed() {
        Decoder<List<Check>> checks = at("data.commit.checks", list(CHECK));

        assertEquals(Either.left("data.commit.checks[1].name: expected a string, got null"), decode(checks, """
                        {"data": {"commit": {"checks": [
                          {"name": "build", "state": "SUCCESS", "startedAt": null},
                          {"name": null, "state": "SUCCESS", "startedAt": null}]}}}"""));
    }

    @Test
    void anErrorInAnArrayOfArraysSaysBothIndexes() {
        assertEquals(
                Either.left("rows[0][1]: expected a number, got a string"),
                decode(field("rows", list(list(integer()))), "{\"rows\": [[1, \"2\"]]}"));
    }

    @Test
    void aPathOfFieldsIsReadThroughNestedObjects() {
        assertEquals(
                Either.right(3), decode(at("commits.totalCount", integer()), "{\"commits\": {\"totalCount\": 3}}"));
    }

    @Test
    void textThatIsNotJsonFailsAsItCannotBeParsed() {
        assertEquals("not JSON: ", decode(string(), "{").getLeft().substring(0, "not JSON: ".length()));
    }

    @Test
    void aDecodedValueIsMapped() {
        assertEquals(Either.right(6), decode(integer().map(n -> n * 2), "3"));
    }

    @Test
    void aDecodedValueCanPickTheDecoderOfTheRest() {
        Decoder<String> labelled = field("kind", string())
                .flatMap(kind -> kind.equals("text") ? field("text", string()) : fail("no text in a " + kind));

        assertEquals(Either.right("hello"), decode(labelled, "{\"kind\": \"text\", \"text\": \"hello\"}"));
        assertEquals(Either.left("no text in a image"), decode(labelled, "{\"kind\": \"image\"}"));
    }

    @Test
    void succeedAndFailIgnoreTheValue() {
        assertEquals(Either.right("always"), decode(succeed("always"), "null"));
        assertEquals(Either.left("never"), decode(fail("never"), "{}"));
    }

    @Test
    void aUnionIsDecodedByItsTypename() {
        Decoder<Option<String>> node = byField(
                "__typename",
                HashMap.of(
                        "CheckRun", field("name", string()).map(Option::some),
                        "StatusContext", field("context", string()).map(Option::some)),
                succeed(Option.none()));
        // A search hit that is not a pull request answers only its __typename.
        assertEquals(
                Either.right(List.of(Option.some("verify"), Option.some("ci/travis"), Option.none())),
                decode(list(node), """
                        [{"__typename": "CheckRun", "name": "verify"},
                         {"__typename": "StatusContext", "context": "ci/travis"},
                         {"__typename": "Issue"}]"""));
    }

    @Test
    void aUnionWithoutItsTypenameFails() {
        Decoder<String> node = byField("__typename", HashMap.empty(), succeed("other"));

        assertEquals(Either.left("[0].__typename: missing"), decode(list(node), "[{}]"));
    }

    @Test
    void eightFieldsMakeARecord() {
        Decoder<String> joined = map8(
                field("a", string()),
                field("b", string()),
                field("c", string()),
                field("d", string()),
                field("e", string()),
                field("f", string()),
                field("g", string()),
                field("h", string()),
                (a, b, c, d, e, f, g, h) -> a + b + c + d + e + f + g + h);

        assertEquals(Either.right("abcdefgh"), decode(joined, """
                        {"a": "a", "b": "b", "c": "c", "d": "d", "e": "e", "f": "f", "g": "g", "h": "h"}"""));
        assertEquals(Either.left("h: missing"), decode(joined, """
                        {"a": "a", "b": "b", "c": "c", "d": "d", "e": "e", "f": "f", "g": "g"}"""));
    }

    @Test
    void theFirstFieldThatFailsIsTheError() {
        Decoder<String> both = map2(field("a", integer()), field("b", integer()), (a, b) -> a + "," + b);

        assertEquals(Either.left("a: expected a number, got null"), decode(both, "{\"a\": null, \"b\": null}"));
    }

    private enum State {
        SUCCESS,
        FAILURE
    }

    private record Check(String name, State state, Option<Instant> startedAt) {}
}
