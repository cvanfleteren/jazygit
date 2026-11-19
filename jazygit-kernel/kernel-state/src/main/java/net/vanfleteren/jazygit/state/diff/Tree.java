package net.vanfleteren.jazygit.state.diff;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.json.JsonTree;
import net.vanfleteren.jazygit.json.NotJson;
import net.vanfleteren.jazygit.xml.NotXml;
import net.vanfleteren.jazygit.xml.XmlTree;
import net.vanfleteren.jazygit.yaml.NotYaml;
import net.vanfleteren.jazygit.yaml.YamlTree;

/**
 * Data as it is written, JSON, YAML or XML, read to be drawn and folded (ADR 122, 123, 151): what {@code lib-json},
 * {@code lib-yaml} and {@code lib-xml} read, as one tree, each node with the lines of its source. None of the
 * readers knows of the others.
 */
public sealed interface Tree {

    /**
     * The documents of {@code text}, a file of {@code kind}: one for JSON, one or more for YAML, one for XML, its root
     * the {@link Markup} of its top; or why it holds none.
     */
    static Either<Unread, List<Document>> read(Rendered kind, String text) {
        return switch (kind) {
            case JSON ->
                JsonTree.read(text)
                        .<Unread>mapLeft(Unread.NoJson::new)
                        .map(root -> List.of(new Document(root.line(), of(root))));
            case YAML ->
                YamlTree.read(text)
                        .<Unread>mapLeft(Unread.NoYaml::new)
                        .map(documents ->
                                documents.map(document -> new Document(document.line(), of(document.root()))));
            case XML ->
                XmlTree.read(text)
                        .<Unread>mapLeft(Unread.NoXml::new)
                        .map(nodes -> List.of(new Document(
                                nodes.head().line(),
                                new Markup(nodes.head().line(), nodes.last().end(), nodes.map(Tree::of)))));
            case MARKDOWN -> Either.left(new Unread.NoTree());
        };
    }

    /**
     * The line it starts on, counted from 1.
     */
    int line();

    /**
     * The line it ends on, counted from 1.
     */
    int end();

    private static Tree of(JsonTree tree) {
        return switch (tree) {
            case JsonTree.JsonObject object ->
                new Mapping(
                        object.line(),
                        object.end(),
                        Properties.NONE,
                        object.fields()
                                .map(field ->
                                        new Field(field.key(), field.line(), of(field.value()), field.ignored())));
            case JsonTree.JsonArray array ->
                new Sequence(
                        array.line(),
                        array.end(),
                        Properties.NONE,
                        array.elements().map(Tree::of));
            case JsonTree.Scalar scalar ->
                new Scalar(
                        scalar.line(),
                        scalar.line(),
                        Properties.NONE,
                        switch (scalar.kind()) {
                            case STRING -> Kind.STRING;
                            case NUMBER -> Kind.NUMBER;
                            case BOOLEAN -> Kind.BOOLEAN;
                            case NULL -> Kind.NULL;
                        },
                        scalar.kind() == JsonTree.Kind.STRING ? Quote.DOUBLE : Quote.NONE,
                        scalar.text());
        };
    }

    private static Tree of(YamlTree tree) {
        return switch (tree) {
            case YamlTree.Mapping mapping ->
                new Mapping(
                        mapping.line(),
                        mapping.end(),
                        of(mapping.properties()),
                        mapping.fields()
                                .map(field ->
                                        new Field(field.key(), field.line(), of(field.value()), field.ignored())));
            case YamlTree.Sequence sequence ->
                new Sequence(
                        sequence.line(),
                        sequence.end(),
                        of(sequence.properties()),
                        sequence.elements().map(Tree::of));
            case YamlTree.Scalar scalar ->
                new Scalar(
                        scalar.line(),
                        scalar.end(),
                        of(scalar.properties()),
                        switch (scalar.kind()) {
                            case STRING -> Kind.STRING;
                            case NUMBER -> Kind.NUMBER;
                            case BOOLEAN -> Kind.BOOLEAN;
                            case NULL -> Kind.NULL;
                        },
                        switch (scalar.style()) {
                            case PLAIN -> Quote.NONE;
                            case SINGLE_QUOTED -> Quote.SINGLE;
                            case DOUBLE_QUOTED -> Quote.DOUBLE;
                        },
                        scalar.text());
            case YamlTree.Block block ->
                new Block(block.line(), block.end(), of(block.properties()), block.folded(), block.lines());
            case YamlTree.Alias alias -> new Alias(alias.line(), alias.anchor());
        };
    }

    private static Tree of(XmlTree tree) {
        return switch (tree) {
            case XmlTree.Element element ->
                new Element(
                        element.line(),
                        element.tagEnd(),
                        element.end(),
                        element.name(),
                        element.attributes().map(attribute -> new Attribute(attribute.name(), attribute.value())),
                        element.children().map(Tree::of));
            case XmlTree.Text text -> new Text(text.line(), text.end(), text.cdata(), text.lines());
            case XmlTree.Comment comment ->
                new Comment(comment.line(), comment.end(), comment.first(), comment.lines());
        };
    }

    private static Properties of(YamlTree.Properties properties) {
        return new Properties(properties.tag(), properties.anchor());
    }

    /**
     * An object of JSON, a mapping of YAML: its fields in the order they are written, the last of two with the same
     * key holding.
     */
    record Mapping(int line, int end, Properties properties, List<Field> fields) implements Tree {}

    /**
     * An array of JSON, a sequence of YAML.
     */
    record Sequence(int line, int end, Properties properties, List<Tree> elements) implements Tree {}

    /**
     * A string, a number, a boolean or null.
     *
     * @param text its value, its escapes undone, a number as it is written
     */
    record Scalar(int line, int end, Properties properties, Kind kind, Quote quote, String text) implements Tree {}

    /**
     * A block of YAML, {@code |} or {@code >}: its {@code n}th line is on line {@code line + n}.
     *
     * @param line   the line of its indicator
     * @param folded whether it is written {@code >}
     * @param lines  its lines as the source has them, without their indentation
     */
    record Block(int line, int end, Properties properties, boolean folded, List<String> lines) implements Tree {}

    /**
     * An alias of YAML, {@code *anchor}, never what it names.
     */
    record Alias(int line, String anchor) implements Tree {
        @Override
        public int end() {
            return line;
        }
    }

    /**
     * The top of a document of XML: its root element, and the comments before and after it.
     */
    record Markup(int line, int end, List<Tree> nodes) implements Tree {}

    /**
     * An element of XML.
     *
     * @param tagEnd   the line its start tag ends on
     * @param name     its name as written, its prefix too
     * @param children its elements, texts and comments, in order
     */
    record Element(int line, int tagEnd, int end, String name, List<Attribute> attributes, List<Tree> children)
            implements Tree {}

    /**
     * An attribute of an element, a declaration of a namespace too.
     */
    record Attribute(String name, String value) {

        /**
         * Whether it declares a namespace: {@code xmlns}, or {@code xmlns:prefix}.
         */
        public boolean namespace() {
            return name.equals("xmlns") || name.startsWith("xmlns:");
        }
    }

    /**
     * Text of XML, or a section of CDATA, trimmed: its {@code n}th line is on line {@code line + n}.
     */
    record Text(int line, int end, boolean cdata, List<String> lines) implements Tree {}

    /**
     * A comment of XML.
     *
     * @param first the line of the first of its {@code lines}
     */
    record Comment(int line, int end, int first, List<String> lines) implements Tree {}

    /**
     * A field of a mapping.
     *
     * @param line    the line of its key
     * @param ignored the lines of the keys of earlier fields with the same key, which this one holds over
     */
    record Field(String key, int line, Tree value, List<Integer> ignored) {}

    /**
     * The tag and the anchor of a node of YAML; JSON has neither.
     */
    record Properties(Option<String> tag, Option<String> anchor) {

        public static final Properties NONE = new Properties(Option.none(), Option.none());

        /**
         * How they are drawn, each followed by a blank: {@code !Ref &base }; nothing when there are none.
         */
        public String text() {
            return tag.map(written -> written + " ").getOrElse("")
                    + anchor.map(name -> "&" + name + " ").getOrElse("");
        }
    }

    /**
     * A document of a file.
     *
     * @param line the line of its {@code ---}, or of its root where it has none
     */
    record Document(int line, Tree root) {}

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
     * How a scalar is quoted where it is written: a string of JSON always in double quotes.
     */
    enum Quote {
        NONE,
        SINGLE,
        DOUBLE
    }

    /**
     * Why a file could not be read as a tree.
     */
    sealed interface Unread {

        record NoJson(NotJson why) implements Unread {}

        record NoYaml(NotYaml why) implements Unread {}

        record NoXml(NotXml why) implements Unread {}

        /**
         * The file is of a kind that is not read as a tree, as Markdown.
         */
        record NoTree() implements Unread {}
    }
}
