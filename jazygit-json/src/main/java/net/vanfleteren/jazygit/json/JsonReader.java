package net.vanfleteren.jazygit.json;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.LinkedHashMap;
import io.vavr.collection.List;
import io.vavr.control.Either;
import jakarta.json.JsonException;
import jakarta.json.spi.JsonProvider;
import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParser.Event;
import jakarta.json.stream.JsonParsingException;
import java.io.StringReader;
import java.math.BigDecimal;
import net.vanfleteren.jazygit.json.Json.JArray;
import net.vanfleteren.jazygit.json.Json.JBool;
import net.vanfleteren.jazygit.json.Json.JNull;
import net.vanfleteren.jazygit.json.Json.JNumber;
import net.vanfleteren.jazygit.json.Json.JObject;
import net.vanfleteren.jazygit.json.Json.JString;
import org.eclipse.parsson.JsonProviderImpl;

/**
 * Builds a {@link Json} with Parsson's streaming parser, the only class that uses Parsson (ADR 42).
 */
final class JsonReader {

    // Made directly, not looked up by JsonProvider.provider() through a ServiceLoader.
    private static final JsonProvider PROVIDER = new JsonProviderImpl();

    // Bounds the recursion of value: deeper JSON is an error, not a StackOverflowError (JsonTest). Parsson's own
    // limit, 1000, throws a bare RuntimeException.
    private static final int MAX_DEPTH = 500;

    private JsonReader() {}

    static Either<DecodeError, Json> read(String text) {
        try (JsonParser parser = PROVIDER.createParser(new StringReader(text))) {
            // Parsson fails on text without a value, rather than having no next event.
            if (text.isBlank()) {
                return Either.left(DecodeError.of("expected JSON, got nothing"));
            }
            Json json = value(parser, parser.next(), 1);
            return atEnd(parser)
                    ? Either.right(json)
                    : Either.left(DecodeError.of("expected one JSON value, got more after it"));
        } catch (JsonException e) {
            return Either.left(DecodeError.of("not JSON: " + e.getMessage()));
        }
    }

    // Parsson fails on whatever follows the value, rather than having a next event.
    private static boolean atEnd(JsonParser parser) {
        try {
            return !parser.hasNext();
        } catch (JsonParsingException e) {
            return false;
        }
    }

    private static Json value(JsonParser parser, Event event, int depth) {
        if (depth > MAX_DEPTH) {
            throw new JsonParsingException("nested deeper than " + MAX_DEPTH, parser.getLocation());
        }
        return switch (event) {
            case START_OBJECT -> object(parser, depth);
            case START_ARRAY -> array(parser, depth);
            case VALUE_STRING -> new JString(parser.getString());
            case VALUE_NUMBER -> new JNumber(number(parser));
            case VALUE_TRUE -> new JBool(true);
            case VALUE_FALSE -> new JBool(false);
            case VALUE_NULL -> new JNull();
            default -> throw new IllegalStateException("A JSON value does not start with " + event);
        };
    }

    // Parsson's limit on the length of a number, 1100 characters, throws an UnsupportedOperationException, and
    // BigDecimal a NumberFormatException for an exponent that does not fit in an int, like 1e99999999999.
    private static BigDecimal number(JsonParser parser) {
        try {
            return parser.getBigDecimal();
        } catch (UnsupportedOperationException | NumberFormatException e) {
            throw new JsonParsingException(e.getMessage(), e, parser.getLocation());
        }
    }

    private static JObject object(JsonParser parser, int depth) {
        List<Tuple2<String, Json>> fields = List.empty();
        for (Event event = parser.next(); event != Event.END_OBJECT; event = parser.next()) {
            String name = parser.getString();
            fields = fields.prepend(Tuple.of(name, value(parser, parser.next(), depth + 1)));
        }
        return new JObject(LinkedHashMap.ofEntries(fields.reverse()));
    }

    private static JArray array(JsonParser parser, int depth) {
        List<Json> elements = List.empty();
        for (Event event = parser.next(); event != Event.END_ARRAY; event = parser.next()) {
            elements = elements.prepend(value(parser, event, depth + 1));
        }
        return new JArray(elements.reverse());
    }
}
