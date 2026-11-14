package net.vanfleteren.jazygit.json;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.HashMap;
import io.vavr.collection.LinkedHashMap;
import io.vavr.collection.List;
import io.vavr.collection.Vector;
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
    private static final String BOM = "\uFEFF";
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

    // A byte order mark that starts the text is no part of it, as RFC 8259 lets a reader have it; its lines stay as
    // they are.
    static Either<NotJson, JsonTree> tree(String given) {
        String text = given.startsWith(BOM) ? given.substring(1) : given;
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

    // Of two fields with the same key the last holds, where it is written, with the lines of the keys before it. Each
    // key's place among the fields is kept, so an object of many keys is read in time linear in them: a field written
    // again leaves an empty place where it was.
    private static JsonObject treeObject(JsonParser parser, int line, int depth) {
        Vector<Option<Field>> places = Vector.empty();
        HashMap<String, Integer> at = HashMap.empty();
        for (Event event = parser.next(); event != Event.END_OBJECT; event = parser.next()) {
            String key = parser.getString();
            int keyLine = asInt(parser.getLocation().getLineNumber());
            JsonTree value = node(parser, parser.next(), depth + 1);
            List<Integer> ignored = List.empty();
            Option<Integer> before = at.get(key);
            if (before.isDefined()) {
                Field field = places.get(before.get()).get();
                ignored = field.ignored().append(field.line());
                places = places.update(before.get(), Option.none());
            }
            places = places.append(Option.of(new Field(key, keyLine, value, ignored)));
            at = at.put(key, places.size() - 1);
        }
        return new JsonObject(
                line,
                asInt(parser.getLocation().getLineNumber()),
                places.flatMap(place -> place).toList());
    }

    private static JsonArray treeArray(JsonParser parser, int line, int depth) {
        List<JsonTree> elements = List.empty();
        for (Event event = parser.next(); event != Event.END_ARRAY; event = parser.next()) {
            elements = elements.prepend(node(parser, event, depth + 1));
        }
        return new JsonArray(line, asInt(parser.getLocation().getLineNumber()), elements.reverse());
    }

    // Where the token Parsson stopped at starts, and the token as written. Parsson's column is that of the last
    // character of the token, so it is read back to where the token starts. Just past the end of a line, as for a
    // line break in a string, the line break is found there. Past the end of the text, as Parsson puts a text that
    // stops early, nothing is found, at the end of the text's last line, not counting the line breaks it ends in. Lines
    // are broken where Parsson breaks them: at a line feed, after a carriage return or not.
    private static NotJson.Broken broken(String text, JsonParsingException e) {
        Vector<String> lines = Vector.of(text.split("\r?\n", -1));
        int line = asInt(e.getLocation().getLineNumber());
        int column = asInt(e.getLocation().getColumnNumber());
        Option<String> written = Option.when(line >= 1 && line < lines.size(), () -> lines.get(line - 1))
                .orElse(() -> Option.when(line == lines.size() && line >= 1, lines::last));
        return written.filter(on -> column >= 1 && column <= on.length() + 1)
                .filter(on -> column <= on.length() || line < lines.size())
                .map(on -> column > on.length()
                        ? new NotJson.Broken(line, column, Option.of("\n"))
                        : new NotJson.Broken(
                                line,
                                tokenStart(on, column - 1) + 1,
                                Option.of(on.substring(tokenStart(on, column - 1), column))))
                .getOrElse(() -> end(lines));
    }

    // Where a text that stops early stops: at the end of its last line that is not empty.
    private static NotJson.Broken end(Vector<String> lines) {
        Vector<String> written = lines.reverse().dropWhile(String::isEmpty).reverse();
        return written.isEmpty()
                ? new NotJson.Broken(1, 1, Option.none())
                : new NotJson.Broken(written.size(), written.last().length() + 1, Option.none());
    }

    // Where the token that ends at index last of a line starts: a string at its opening quote, a word or a number at
    // its first character, any other character at itself.
    private static int tokenStart(String written, int last) {
        char end = written.charAt(last);
        if (end == '"') {
            for (int at = last - 1; at >= 0; at--) {
                if (written.charAt(at) == '"' && !escaped(written, at)) {
                    return at;
                }
            }
            return last;
        }
        int start = last;
        while (start > 0 && isWordChar(written.charAt(start - 1)) && isWordChar(end)) {
            start--;
        }
        return start;
    }

    // Whether the character at index at follows an odd number of backslashes.
    private static boolean escaped(String written, int at) {
        int backslashes = 0;
        for (int before = at - 1; before >= 0 && written.charAt(before) == '\\'; before--) {
            backslashes++;
        }
        return backslashes % 2 == 1;
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '.' || c == '-' || c == '+';
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
