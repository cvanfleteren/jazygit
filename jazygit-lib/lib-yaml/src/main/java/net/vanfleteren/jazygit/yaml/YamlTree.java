package net.vanfleteren.jazygit.yaml;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;

/**
 * YAML as it is written, read to be drawn (ADR 123): each key and value with the line it starts on, a mapping or a
 * sequence with the line it ends on too, a value as it is written, an alias as itself, never what it names.
 */
public sealed interface YamlTree {

    /**
     * The documents {@code text} holds, at least one, or why it holds none.
     */
    static Either<NotYaml, List<Document>> read(String text) {
        return YamlReader.read(text);
    }

    /**
     * The line it starts on, counted from 1.
     */
    int line();

    /**
     * The line it ends on, counted from 1.
     */
    int end();

    /**
     * A mapping: its fields in the order they are written. Of two fields with the same key, the last holds, where it
     * is written; it keeps the lines of the keys before it.
     */
    record Mapping(int line, int end, Properties properties, List<Field> fields) implements YamlTree {}

    record Sequence(int line, int end, Properties properties, List<YamlTree> elements) implements YamlTree {}

    /**
     * A value on a line, or on lines, of its own.
     *
     * @param kind  what YAML 1.2's core schema reads a plain value as; a quoted one is a string
     * @param style how it is written
     * @param text  its value, its escapes undone, as a plain one is written
     */
    record Scalar(int line, int end, Properties properties, Kind kind, Style style, String text) implements YamlTree {}

    /**
     * A block, {@code |} or {@code >}: the lines of its source after the line of its indicator, without their
     * indentation, so its {@code n}th line is on line {@code line + n}.
     *
     * @param line   the line of its indicator
     * @param folded whether it is written {@code >}
     */
    record Block(int line, int end, Properties properties, boolean folded, List<String> lines) implements YamlTree {}

    /**
     * An alias, {@code *anchor}: the node of that anchor used again, which is not read again here.
     */
    record Alias(int line, String anchor) implements YamlTree {
        @Override
        public int end() {
            return line;
        }
    }

    /**
     * A field of a mapping.
     *
     * @param key     its key: a scalar as its text, any other node as it reads in flow style
     * @param line    the line of its key
     * @param ignored the lines of the keys of earlier fields with the same key, which this one holds over
     */
    record Field(String key, int line, YamlTree value, List<Integer> ignored) {}

    /**
     * What is written before a node.
     *
     * @param tag    its tag, as {@code !Ref}, or one of YAML's own as {@code !!binary}
     * @param anchor the name of its anchor, without the {@code &}
     */
    record Properties(Option<String> tag, Option<String> anchor) {

        public static final Properties NONE = new Properties(Option.none(), Option.none());
    }

    /**
     * A document of the file.
     *
     * @param line the line of its {@code ---}, or of its root where it has none
     */
    record Document(int line, YamlTree root) {}

    /**
     * The kinds of scalar.
     */
    enum Kind {
        STRING,
        NUMBER,
        BOOLEAN,
        NULL
    }

    /**
     * How a scalar is written.
     */
    enum Style {
        PLAIN,
        SINGLE_QUOTED,
        DOUBLE_QUOTED
    }
}
