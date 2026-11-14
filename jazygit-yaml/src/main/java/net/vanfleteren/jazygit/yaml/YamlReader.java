package net.vanfleteren.jazygit.yaml;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Vector;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.util.Iterator;
import java.util.Optional;
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
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.api.lowlevel.Parse;
import org.snakeyaml.engine.v2.common.Anchor;
import org.snakeyaml.engine.v2.common.ScalarStyle;
import org.snakeyaml.engine.v2.events.AliasEvent;
import org.snakeyaml.engine.v2.events.CollectionStartEvent;
import org.snakeyaml.engine.v2.events.DocumentStartEvent;
import org.snakeyaml.engine.v2.events.Event;
import org.snakeyaml.engine.v2.events.MappingEndEvent;
import org.snakeyaml.engine.v2.events.MappingStartEvent;
import org.snakeyaml.engine.v2.events.NodeEvent;
import org.snakeyaml.engine.v2.events.ScalarEvent;
import org.snakeyaml.engine.v2.events.SequenceEndEvent;
import org.snakeyaml.engine.v2.events.SequenceStartEvent;
import org.snakeyaml.engine.v2.exceptions.Mark;
import org.snakeyaml.engine.v2.exceptions.MarkedYamlEngineException;
import org.snakeyaml.engine.v2.exceptions.ReaderException;
import org.snakeyaml.engine.v2.exceptions.YamlEngineException;
import org.snakeyaml.engine.v2.nodes.Tag;
import org.snakeyaml.engine.v2.resolver.CoreScalarResolver;
import org.snakeyaml.engine.v2.resolver.ScalarResolver;

/**
 * Builds {@link YamlTree} documents from SnakeYAML Engine's events, the only class that uses SnakeYAML (ADR 123). Its
 * events, not its nodes: a node of SnakeYAML puts what an alias names where the alias is.
 */
final class YamlReader {

    // Bounds the recursion of node: deeper YAML is an error, not a StackOverflowError, as JSON is (ADR 122).
    static final int MAX_DEPTH = 500;

    private static final LoadSettings SETTINGS = LoadSettings.builder().build();
    private static final ScalarResolver CORE = new CoreScalarResolver(false);

    private YamlReader() {}

    static Either<NotYaml, List<Document>> read(String text) {
        Vector<String> lines = Vector.of(text.split("\n", -1)).map(YamlReader::withoutReturn);
        try {
            Events events = new Events(new Parse(SETTINGS).parseString(text).iterator());
            events.skip();
            List<Document> documents = documents(events, lines);
            return documents.isEmpty() ? Either.left(new NotYaml.Nothing()) : Either.right(documents);
        } catch (MarkedYamlEngineException e) {
            Option<Mark> mark =
                    Option.ofOptional(e.getProblemMark()).orElse(() -> Option.ofOptional(e.getContextMark()));
            return Either.left(new NotYaml.Broken(
                    mark.map(at -> at.getLine() + 1).getOrElse(1),
                    mark.map(at -> at.getColumn() + 1).getOrElse(1),
                    Option.of(e.getProblem()).getOrElse(e::getMessage)));
        } catch (ReaderException e) {
            int[] read = text.codePoints().limit(e.getPosition()).toArray();
            String before = new String(read, 0, read.length);
            String onLine = before.substring(before.lastIndexOf('\n') + 1);
            return Either.left(new NotYaml.Broken(
                    1 + (int) before.chars().filter(c -> c == '\n').count(),
                    1 + onLine.codePointCount(0, onLine.length()),
                    Option.of(e.getMessage()).getOrElse("special characters are not allowed")));
        } catch (TooDeep e) {
            return Either.left(new NotYaml.TooDeep(MAX_DEPTH));
        } catch (YamlEngineException e) {
            return Either.left(new NotYaml.Broken(1, 1, e.getMessage()));
        }
    }

    private static List<Document> documents(Events events, Vector<String> lines) {
        List<Document> documents = List.empty();
        while (events.peek() instanceof DocumentStartEvent start) {
            events.skip();
            YamlTree root = node(events, lines, 1);
            events.skip();
            documents = documents.append(new Document(start.isExplicit() ? line(start) : root.line(), root));
        }
        return documents;
    }

    private static YamlTree node(Events events, Vector<String> lines, int depth) {
        if (depth > MAX_DEPTH) {
            throw new TooDeep();
        }
        Event event = events.next();
        return switch (event) {
            case ScalarEvent scalar -> scalar(scalar, lines);
            case AliasEvent alias -> new Alias(line(alias), alias.getAlias().getValue());
            case SequenceStartEvent start -> sequence(start, events, lines, depth);
            case MappingStartEvent start -> mapping(start, events, lines, depth);
            default -> throw new YamlEngineException("expected a node, got " + event.getEventId());
        };
    }

    private static Sequence sequence(SequenceStartEvent start, Events events, Vector<String> lines, int depth) {
        List<YamlTree> elements = List.empty();
        while (!(events.peek() instanceof SequenceEndEvent)) {
            elements = elements.append(node(events, lines, depth + 1));
        }
        Event end = events.next();
        return new Sequence(line(start), end(start, end, elements.map(YamlTree::end)), properties(start), elements);
    }

    private static Mapping mapping(MappingStartEvent start, Events events, Vector<String> lines, int depth) {
        List<Field> written = List.empty();
        while (!(events.peek() instanceof MappingEndEvent)) {
            YamlTree key = node(events, lines, depth + 1);
            YamlTree value = node(events, lines, depth + 1);
            written = written.append(new Field(key(key), key.line(), value, List.empty()));
        }
        Event end = events.next();
        return new Mapping(
                line(start),
                end(start, end, written.map(field -> field.value().end())),
                properties(start),
                lastHolds(written));
    }

    /**
     * {@code written} with only the last field of each key, where it is written, holding the lines of the keys of the
     * others: in one pass over it, for a mapping of thousands of keys.
     */
    private static List<Field> lastHolds(List<Field> written) {
        Map<String, List<Integer>> lines = written.foldLeft(
                HashMap.empty(),
                (done, field) -> done.put(
                        field.key(),
                        done.get(field.key()).getOrElse(List.empty()).append(field.line())));
        Map<String, Integer> last =
                written.zipWithIndex().foldLeft(HashMap.empty(), (done, at) -> done.put(at._1.key(), at._2));
        return written.zipWithIndex()
                .filter(at -> last.get(at._1.key()).contains(at._2))
                .map(at -> new Field(
                        at._1.key(),
                        at._1.line(),
                        at._1.value(),
                        lines.get(at._1.key()).get().init()));
    }

    /**
     * The line a mapping or a sequence ends on: of its closing bracket when it is written in flow style, else of the
     * end of what it holds last. SnakeYAML ends a block at the next token, which may be lines of comments later.
     */
    private static int end(CollectionStartEvent start, Event end, List<Integer> ends) {
        return start.isFlow() ? line(end) : ends.max().getOrElse(line(start));
    }

    private static YamlTree scalar(ScalarEvent event, Vector<String> lines) {
        Properties properties = properties(event);
        int line = line(event);
        int end = event.getEndMark().map(mark -> mark.getLine() + 1).orElse(line);
        String value = event.getValue();
        return switch (event.getScalarStyle()) {
            case LITERAL, FOLDED -> block(event, properties, lines);
            case SINGLE_QUOTED -> new Scalar(line, end, properties, Kind.STRING, Style.SINGLE_QUOTED, value);
            case DOUBLE_QUOTED, JSON_SCALAR_STYLE ->
                new Scalar(line, end, properties, Kind.STRING, Style.DOUBLE_QUOTED, value);
            case PLAIN -> new Scalar(line, end, properties, kind(event, value), Style.PLAIN, value);
        };
    }

    /**
     * What a plain value is, as YAML 1.2's core schema reads it: {@code on} and {@code NO} are strings. A value tagged
     * a string, {@code !!str 1}, is one.
     */
    private static Kind kind(ScalarEvent event, String value) {
        if (event.getTag().filter(Tag.STR.getValue()::equals).isPresent()) {
            return Kind.STRING;
        }
        Tag tag = CORE.resolve(value, true);
        return tag.equals(Tag.NULL)
                ? Kind.NULL
                : tag.equals(Tag.BOOL)
                        ? Kind.BOOLEAN
                        : tag.equals(Tag.INT) || tag.equals(Tag.FLOAT) ? Kind.NUMBER : Kind.STRING;
    }

    /**
     * A block, its lines as the source has them after the line of its indicator up to where SnakeYAML ended it,
     * without the blank lines it ends with, and without the indentation they share.
     */
    private static Block block(ScalarEvent event, Properties properties, Vector<String> lines) {
        int indicator = event.getStartMark().map(Mark::getLine).orElse(0);
        int last = event.getEndMark()
                .map(mark -> mark.getColumn() == 0 ? mark.getLine() - 1 : mark.getLine())
                .orElse(indicator);
        List<String> source = List.rangeClosed(indicator + 1, Math.min(last, lines.size() - 1))
                .map(lines::get)
                .reverse()
                .dropWhile(String::isBlank)
                .reverse();
        int indent = source.filter(line -> !line.isBlank())
                .map(YamlReader::indentation)
                .min()
                .getOrElse(0);
        List<String> content = source.map(line -> line.isBlank() ? "" : line.substring(indent));
        return new Block(
                indicator + 1,
                indicator + 1 + content.size(),
                properties,
                event.getScalarStyle() == ScalarStyle.FOLDED,
                content);
    }

    private static int indentation(String line) {
        return line.length() - line.stripLeading().length();
    }

    /**
     * The key of a field as text: a scalar as its value, any other node as it reads in flow style.
     */
    private static String key(YamlTree key) {
        return key instanceof Scalar scalar ? scalar.text() : flow(key);
    }

    private static String flow(YamlTree tree) {
        String properties = tree instanceof Alias ? "" : properties(tree);
        return properties
                + switch (tree) {
                    case Scalar scalar ->
                        switch (scalar.style()) {
                            case PLAIN -> scalar.text();
                            case SINGLE_QUOTED -> "'" + scalar.text() + "'";
                            case DOUBLE_QUOTED -> "\"" + scalar.text() + "\"";
                        };
                    case Mapping mapping ->
                        mapping.fields()
                                .map(field -> field.key() + ": " + flow(field.value()))
                                .mkString("{", ", ", "}");
                    case Sequence sequence ->
                        sequence.elements().map(YamlReader::flow).mkString("[", ", ", "]");
                    case Block block -> block.folded() ? ">" : "|";
                    case Alias alias -> "*" + alias.anchor();
                };
    }

    private static String properties(YamlTree tree) {
        Properties properties = switch (tree) {
            case Mapping mapping -> mapping.properties();
            case Sequence sequence -> sequence.properties();
            case Scalar scalar -> scalar.properties();
            case Block block -> block.properties();
            case Alias alias -> Properties.NONE;
        };
        return properties.tag().map(tag -> tag + " ").getOrElse("")
                + properties.anchor().map(anchor -> "&" + anchor + " ").getOrElse("");
    }

    private static Properties properties(NodeEvent event) {
        Optional<String> tag = switch (event) {
            case ScalarEvent scalar -> scalar.getTag();
            case CollectionStartEvent start -> start.getTag();
            default -> Optional.empty();
        };
        return new Properties(
                Option.ofOptional(tag)
                        .map(written -> written.startsWith(Tag.PREFIX)
                                ? "!!" + written.substring(Tag.PREFIX.length())
                                : written),
                Option.ofOptional(event.getAnchor()).map(Anchor::getValue));
    }

    private static int line(Event event) {
        return event.getStartMark().map(mark -> mark.getLine() + 1).orElse(1);
    }

    private static String withoutReturn(String line) {
        return line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
    }

    /**
     * SnakeYAML's events, one looked at ahead.
     */
    private static final class Events {

        private final Iterator<Event> events;
        private Event ahead;

        Events(Iterator<Event> events) {
            this.events = events;
        }

        Event peek() {
            if (ahead == null) {
                ahead = events.next();
            }
            return ahead;
        }

        Event next() {
            Event next = peek();
            ahead = null;
            return next;
        }

        void skip() {
            if (ahead == null) {
                events.next();
            }
            ahead = null;
        }
    }

    /**
     * Thrown where the nodes are nested deeper than {@link #MAX_DEPTH}, caught by {@link #read}.
     */
    private static final class TooDeep extends RuntimeException {
        TooDeep() {
            super(null, null, false, false);
        }
    }
}
