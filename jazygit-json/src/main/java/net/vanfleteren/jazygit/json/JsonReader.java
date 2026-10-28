package net.vanfleteren.jazygit.json;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.LinkedHashMap;
import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.json.Json.JArray;
import net.vanfleteren.jazygit.json.Json.JBool;
import net.vanfleteren.jazygit.json.Json.JNull;
import net.vanfleteren.jazygit.json.Json.JNumber;
import net.vanfleteren.jazygit.json.Json.JObject;
import net.vanfleteren.jazygit.json.Json.JString;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.core.json.JsonFactory;

/**
 * Builds a {@link Json} with Jackson's streaming parser, the only class that uses Jackson (ADR 42).
 */
final class JsonReader {

    private static final JsonFactory FACTORY = new JsonFactory();

    private JsonReader() {}

    static Either<DecodeError, Json> read(String text) {
        try (JsonParser parser = FACTORY.createParser(ObjectReadContext.empty(), text)) {
            JsonToken first = parser.nextToken();
            if (first == null) {
                return Either.left(DecodeError.of("expected JSON, got nothing"));
            }
            Json json = value(parser, first);
            return parser.nextToken() == null
                    ? Either.right(json)
                    : Either.left(DecodeError.of("expected one JSON value, got more after it"));
        } catch (JacksonException e) {
            return Either.left(DecodeError.of("not JSON: " + e.getOriginalMessage()));
        }
    }

    private static Json value(JsonParser parser, JsonToken token) {
        return switch (token) {
            case START_OBJECT -> object(parser);
            case START_ARRAY -> array(parser);
            case VALUE_STRING -> new JString(parser.getString());
            case VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT -> new JNumber(parser.getDecimalValue());
            case VALUE_TRUE -> new JBool(true);
            case VALUE_FALSE -> new JBool(false);
            case VALUE_NULL -> new JNull();
            default -> throw new IllegalStateException("A JSON value does not start with " + token);
        };
    }

    private static JObject object(JsonParser parser) {
        List<Tuple2<String, Json>> fields = List.empty();
        for (JsonToken token = parser.nextToken(); token != JsonToken.END_OBJECT; token = parser.nextToken()) {
            String name = parser.currentName();
            fields = fields.prepend(Tuple.of(name, value(parser, parser.nextToken())));
        }
        return new JObject(LinkedHashMap.ofEntries(fields.reverse()));
    }

    private static JArray array(JsonParser parser) {
        List<Json> elements = List.empty();
        for (JsonToken token = parser.nextToken(); token != JsonToken.END_ARRAY; token = parser.nextToken()) {
            elements = elements.prepend(value(parser, token));
        }
        return new JArray(elements.reverse());
    }
}
