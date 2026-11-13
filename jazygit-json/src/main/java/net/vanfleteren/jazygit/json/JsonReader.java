package net.vanfleteren.jazygit.json;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.LinkedHashMap;
import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
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
import net.vanfleteren.jazygit.json.JsonTree.Field;
import net.vanfleteren.jazygit.json.JsonTree.JsonArray;
import net.vanfleteren.jazygit.json.JsonTree.JsonObject;
import net.vanfleteren.jazygit.json.JsonTree.Kind;
import net.vanfleteren.jazygit.json.JsonTree.Scalar;
import org.eclipse.parsson.JsonProviderImpl;

/**
 * Builds a {@link Json}, or a {@link JsonTree} (ADR 122), with Parsson's streaming parser, the only class that uses
 * Parsson (ADR 42).
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

    static Either<NotJson, JsonTree> tree(String text) {
        if (text.isBlank()) {
            return Either.left(new NotJson.Nothing());
        }
        try (JsonParser parser = PROVIDER.createParser(new StringReader(text))) {
            JsonTree tree = node(parser, parser.next(), 1);
            return atEnd(parser)
                    ? Either.right(tree)
                    : Either.left(
                            new NotJson.MoreAfter(asInt(parser.getLocation().getLineNumber())));
        } catch (NotRead e) {
            return Either.left(e.why);
        } catch (JsonParsingException e) {
            return Either.left(broken(text, e));
        } catch (JsonException e) {
            return Either.left(new NotJson.Broken(0, 0, Option.none()));
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

    private static JsonTree node(JsonParser parser, Event event, int depth) {
        if (depth > MAX_DEPTH) {
            throw new NotRead(new NotJson.TooDeep(MAX_DEPTH));
        }
        int line = asInt(parser.getLocation().getLineNumber());
        return switch (event) {
            case START_OBJECT -> treeObject(parser, line, depth);
            case START_ARRAY -> treeArray(parser, line, depth);
            case VALUE_STRING -> new Scalar(line, Kind.STRING, parser.getString());
            case VALUE_NUMBER -> new Scalar(line, Kind.NUMBER, parser.getString());
            case VALUE_TRUE -> new Scalar(line, Kind.BOOLEAN, "true");
            case VALUE_FALSE -> new Scalar(line, Kind.BOOLEAN, "false");
            case VALUE_NULL -> new Scalar(line, Kind.NULL, "null");
            default -> throw new IllegalStateException("A JSON value does not start with " + event);
        };
    }

    // Of two fields with the same key the last holds, where it is written, with the lines of the keys before it.
    private static JsonObject treeObject(JsonParser parser, int line, int depth) {
        List<Field> fields = List.empty();
        for (Event event = parser.next(); event != Event.END_OBJECT; event = parser.next()) {
            String key = parser.getString();
            int keyLine = asInt(parser.getLocation().getLineNumber());
            JsonTree value = node(parser, parser.next(), depth + 1);
            List<Integer> ignored = fields.filter(field -> field.key().equals(key))
                    .reverse()
                    .flatMap(field -> field.ignored().append(field.line()));
            fields = fields.filter(field -> !field.key().equals(key)).prepend(new Field(key, keyLine, value, ignored));
        }
        return new JsonObject(line, asInt(parser.getLocation().getLineNumber()), fields.reverse());
    }

    private static JsonArray treeArray(JsonParser parser, int line, int depth) {
        List<JsonTree> elements = List.empty();
        for (Event event = parser.next(); event != Event.END_ARRAY; event = parser.next()) {
            elements = elements.prepend(node(parser, event, depth + 1));
        }
        return new JsonArray(line, asInt(parser.getLocation().getLineNumber()), elements.reverse());
    }

    // Where Parsson stopped, and the character of the text there: none where the text stops. Its column is that of
    // the last character of the token it stopped at.
    private static NotJson.Broken broken(String text, JsonParsingException e) {
        int line = asInt(e.getLocation().getLineNumber());
        int column = asInt(e.getLocation().getColumnNumber());
        Option<String> found = List.of(text.split("\\R", -1))
                .drop(line - 1)
                .headOption()
                .filter(written -> column >= 1 && column <= written.length())
                .map(written -> written.substring(column - 1, written.offsetByCodePoints(column - 1, 1)));
        return new NotJson.Broken(line, column, found);
    }

    // Parsson counts in longs; no text read here has more lines or columns than an int holds (ADR 79).
    private static int asInt(long number) {
        return (int) number;
    }

    private static JArray array(JsonParser parser, int depth) {
        List<Json> elements = List.empty();
        for (Event event = parser.next(); event != Event.END_ARRAY; event = parser.next()) {
            elements = elements.prepend(value(parser, event, depth + 1));
        }
        return new JArray(elements.reverse());
    }

    // Why a tree could not be read, out of the recursion that found it.
    private static final class NotRead extends RuntimeException {
        private final transient NotJson why;

        NotRead(NotJson why) {
            super(null, null, false, false);
            this.why = why;
        }
    }
}
