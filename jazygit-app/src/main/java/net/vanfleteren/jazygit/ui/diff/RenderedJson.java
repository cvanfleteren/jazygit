package net.vanfleteren.jazygit.ui.diff;

import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.widgets.tree.GuideStyle;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Vector;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.json.JsonTree;
import net.vanfleteren.jazygit.json.NotJson;
import net.vanfleteren.jazygit.state.diff.JsonFolds;
import net.vanfleteren.jazygit.ui.theme.Theme;

/**
 * A version of a JSON file rendered (ADR 122): a tree of rows, its guides as tamboui draws those of a tree, a
 * container of scalars that fits on one line, a long array of scalars packed into as many values a row as the width
 * holds, an array of records as a table, an object in an array named by one of its fields, and each node that folds
 * open or closed as the user chose. Each row knows the lines of the source it stands for, and the node it is, which
 * the cursor stops on.
 */
final class RenderedJson {

    private static final GuideStyle GUIDES = GuideStyle.UNICODE;
    private static final String GAP = "  ";
    private static final String COLUMN_GAP = "  ";
    private static final String NEWLINE = "↵";
    private static final int PREVIEW = 40;

    private RenderedJson() {}

    /**
     * The rows of the JSON {@code lines}, packed to {@code width} columns, its nodes open as {@code chose} says
     * ({@link JsonFolds#open}), styled as {@code styles} says; or, when they are no JSON, or {@code cut} short, the line
     * that says why, to show over them as source.
     */
    static Either<String, List<JsonRow>> rows(
            Vector<String> lines, boolean cut, int width, Map<String, Boolean> chose, Theme.SpanStyles styles) {
        if (cut) {
            return Either.left(Messages.get("content.json.cut"));
        }
        return JsonTree.read(lines.mkString("\n"))
                .mapLeft(RenderedJson::why)
                .map(root -> new Drawing(width, chose, styles).root(root));
    }

    /**
     * What the line over JSON that could not be read says.
     */
    static String why(NotJson not) {
        return switch (not) {
            case NotJson.Nothing nothing -> Messages.get("content.json.nothing");
            case NotJson.MoreAfter(int line) -> Messages.get("content.json.moreAfter", String.valueOf(line));
            case NotJson.TooDeep(int depth) -> Messages.get("content.json.tooDeep", String.valueOf(depth));
            case NotJson.Broken(int line, int column, var found) when line < 1 -> Messages.get("content.json.broken");
            case NotJson.Broken(int line, int column, var found) ->
                Messages.get(
                        "content.json.unexpected",
                        String.valueOf(line),
                        String.valueOf(column),
                        found.map(token -> token.startsWith("\"")
                                        ? Messages.get("content.json.string")
                                        : "'" + token + "'")
                                .getOrElse(() -> Messages.get("content.json.end")));
        };
    }

    private static List<Entry> entries(JsonTree tree, String pointer) {
        return switch (tree) {
            case JsonTree.JsonObject object ->
                object.fields()
                        .map(field -> new Entry(
                                Option.of(field.key()),
                                JsonFolds.child(pointer, field.key()),
                                field.line(),
                                field.value(),
                                field.ignored()));
            case JsonTree.JsonArray array ->
                array.elements()
                        .zipWithIndex((element, at) -> new Entry(
                                Option.none(),
                                JsonFolds.child(pointer, String.valueOf(at)),
                                element.line(),
                                element,
                                List.empty()));
            case JsonTree.Scalar scalar -> List.empty();
        };
    }

    private static JsonTree.JsonObject without(JsonTree.JsonObject object, JsonTree.Field field) {
        return new JsonTree.JsonObject(
                object.line(), object.end(), object.fields().remove(field));
    }

    private static String connector(boolean last, boolean top) {
        return top ? "" : last ? GUIDES.lastBranch() : GUIDES.branch();
    }

    private static String continued(boolean last, boolean top) {
        return top ? "" : last ? GUIDES.space() : GUIDES.vertical();
    }

    private static String count(JsonTree tree) {
        int size = JsonFolds.size(tree);
        return tree instanceof JsonTree.JsonObject ? "{" + size + "}" : "[" + size + "]";
    }

    /**
     * What a closed node shows of what it holds: the keys of an object, the keys of the objects of an array, or the
     * first values of an array of scalars, as many as fit in {@value #PREVIEW} columns.
     */
    private static String preview(JsonTree tree) {
        if (tree instanceof JsonTree.JsonObject object) {
            return fitting(object.fields().map(JsonTree.Field::key));
        }
        JsonTree.JsonArray array = (JsonTree.JsonArray) tree;
        Option<JsonTree> first = array.elements().headOption();
        if (first.exists(element -> element instanceof JsonTree.JsonObject)) {
            return "{" + fitting(((JsonTree.JsonObject) first.get()).fields().map(JsonTree.Field::key)) + "}";
        }
        return fitting(array.elements()
                .takeWhile(element -> !JsonFolds.container(element))
                .map(element -> text((JsonTree.Scalar) element))
                .appendAll(array.elements().exists(JsonFolds::container) ? List.of("…") : List.empty()));
    }

    /**
     * As many of {@code parts} as fit in {@value #PREVIEW} columns, joined by commas, and an ellipsis when that is not
     * all of them.
     */
    private static String fitting(List<String> parts) {
        int shown = 0;
        int used = 0;
        for (String part : parts) {
            used += part.length() + 2;
            if (used > PREVIEW) {
                break;
            }
            shown++;
        }
        List<String> fit = parts.take(shown);
        return shown < parts.size() ? fit.append("…").mkString(", ") : fit.mkString(", ");
    }

    /**
     * How a scalar is written: a string in quotes, its newlines as {@value #NEWLINE}.
     */
    private static String text(JsonTree.Scalar scalar) {
        return scalar.kind() == JsonTree.Kind.STRING ? "\"" + flat(scalar.text()) + "\"" : scalar.text();
    }

    /**
     * How a scalar is written in a table: a string without its quotes.
     */
    private static String cellText(JsonTree.Scalar scalar) {
        return scalar.kind() == JsonTree.Kind.STRING ? flat(scalar.text()) : scalar.text();
    }

    private static String flat(String text) {
        return text.replace("\r\n", NEWLINE).replace("\n", NEWLINE).replace("\r", NEWLINE);
    }

    private static String syntax(JsonTree.Kind kind) {
        return switch (kind) {
            case STRING -> Theme.Classes.SYNTAX_STRING;
            case NUMBER -> Theme.Classes.SYNTAX_NUMBER;
            case BOOLEAN, NULL -> Theme.Classes.SYNTAX_KEYWORD;
        };
    }

    private static int end(JsonTree tree) {
        return switch (tree) {
            case JsonTree.JsonObject object -> object.end();
            case JsonTree.JsonArray array -> array.end();
            case JsonTree.Scalar scalar -> scalar.line();
        };
    }

    private static List<Integer> range(int from, int to) {
        return List.rangeClosed(from, Math.max(from, to));
    }

    private static String pad(String text, int width) {
        return text.length() >= width ? text : text + " ".repeat(width - text.length());
    }

    private static String padLeft(String text, int width) {
        return text.length() >= width ? text : " ".repeat(width - text.length()) + text;
    }

    /**
     * A row of a version of JSON rendered.
     *
     * @param line    what it draws
     * @param pointer the path of what it draws, which the cursor stops on; none for the first row of a table, which
     *                is part of the row before it
     * @param lines   the lines of the source it stands for, counted from 1, the one it starts on first
     */
    record JsonRow(Line line, Option<String> pointer, List<Integer> lines) {}

    /**
     * Draws the rows of a tree.
     */
    private record Drawing(int width, Map<String, Boolean> chose, Theme.SpanStyles styles) {

        List<JsonRow> root(JsonTree root) {
            if (!JsonFolds.container(root)) {
                return List.of(
                        new JsonRow(Line.from(scalar((JsonTree.Scalar) root)), Option.of(""), List.of(root.line())));
            }
            if (JsonFolds.size(root) == 0) {
                return List.of(new JsonRow(
                        Line.from(dim(root instanceof JsonTree.JsonObject ? "{}" : "[]")),
                        Option.of(""),
                        List.of(root.line())));
            }
            return children(root, "", "", true);
        }

        /**
         * The rows of what {@code tree} at {@code pointer} holds, after the guides {@code prefix}; at the top, with no
         * connectors of their own.
         */
        private List<JsonRow> children(JsonTree tree, String pointer, String prefix, boolean top) {
            Option<List<String>> table = JsonFolds.table(tree);
            if (table.isDefined()) {
                return table(((JsonTree.JsonArray) tree), table.get(), pointer, prefix, top);
            }
            if (JsonFolds.packed(tree)) {
                return packed((JsonTree.JsonArray) tree, pointer, prefix, top);
            }
            List<Entry> entries = entries(tree, pointer);
            int digits = String.valueOf(Math.max(0, entries.size() - 1)).length();
            return entries.zipWithIndex().flatMap(at -> {
                Entry entry = at._1;
                boolean last = at._2 == entries.size() - 1;
                Span label =
                        entry.key().map(key -> Span.raw(key)).getOrElse(() -> dim(pad(String.valueOf(at._2), digits)));
                return entry(entry, label, prefix + connector(last, top), prefix + continued(last, top));
            });
        }

        /**
         * The rows of {@code entry}, its label {@code label} after the guides {@code guides}, what it holds after the
         * guides {@code inner}.
         */
        private List<JsonRow> entry(Entry entry, Span label, String guides, String inner) {
            JsonTree value = entry.value();
            List<Span> head = List.of(dim(guides), label);
            List<Integer> ignored = entry.ignored();
            Span note = ignored.isEmpty()
                    ? Span.raw("")
                    : dim(GAP
                            + Messages.get(
                                    ignored.size() == 1 ? "content.json.duplicate" : "content.json.duplicates",
                                    ignored.mkString(", ")));
            if (!JsonFolds.container(value)) {
                return List.of(new JsonRow(
                        line(head.append(Span.raw(GAP))
                                .append(scalar((JsonTree.Scalar) value))
                                .append(note)),
                        Option.of(entry.pointer()),
                        range(entry.line(), value.line()).appendAll(ignored)));
            }
            if (JsonFolds.oneLine(value)) {
                return List.of(new JsonRow(
                        line(head.append(Span.raw(GAP))
                                .appendAll(oneLine(value))
                                .append(note)),
                        Option.of(entry.pointer()),
                        range(entry.line(), end(value)).appendAll(ignored)));
            }
            boolean open = JsonFolds.open(chose, entry.pointer(), value);
            Option<JsonTree.Field> naming = entry.key().isEmpty() && value instanceof JsonTree.JsonObject object
                    ? JsonFolds.naming(object)
                    : Option.none();
            List<Span> named = naming.map(field ->
                            List.of(Span.raw(GAP), dim(field.key() + ": "), scalar((JsonTree.Scalar) field.value())))
                    .getOrElse(List.empty());
            // The field that names it is not drawn again in it, nor in its preview.
            JsonTree shown = naming.map(field -> (JsonTree) without((JsonTree.JsonObject) value, field))
                    .getOrElse(value);
            List<Span> count = List.of(Span.raw(" "), dim(count(value)));
            List<Span> preview = open ? List.empty() : List.of(Span.raw(GAP), dim(preview(shown)));
            JsonRow row = new JsonRow(
                    line(head.appendAll(named)
                            .appendAll(count)
                            .appendAll(preview)
                            .append(note)),
                    Option.of(entry.pointer()),
                    (open ? List.of(entry.line()) : range(entry.line(), end(value))).appendAll(ignored));
            if (!open) {
                return List.of(row);
            }
            return children(shown, entry.pointer(), inner, false).prepend(row);
        }

        /**
         * The rows of the array {@code array} drawn as a table with the {@code columns}: the keys, then a row of each
         * of its objects.
         */
        private List<JsonRow> table(
                JsonTree.JsonArray array, List<String> columns, String pointer, String prefix, boolean top) {
            List<List<JsonTree.Scalar>> cells = array.elements()
                    .map(element ->
                            ((JsonTree.JsonObject) element).fields().map(field -> (JsonTree.Scalar) field.value()));
            List<Integer> widths = columns.zipWithIndex(
                    (key, column) -> cells.map(row -> cellText(row.get(column)).length())
                            .append(key.length())
                            .max()
                            .getOrElse(0));
            int digits = String.valueOf(array.elements().size() - 1).length();
            String index = " ".repeat(digits) + GAP;
            JsonRow keys = new JsonRow(
                    line(List.of(dim(prefix + (top ? "" : GUIDES.vertical())), Span.raw(index))
                            .appendAll(columns.zipWithIndex((key, column) -> List.of(
                                            Span.raw(pad(key, widths.get(column))),
                                            Span.raw(column == columns.size() - 1 ? "" : COLUMN_GAP)))
                                    .flatMap(spans -> spans))),
                    Option.none(),
                    List.of(array.line()));
            return array.elements()
                    .zipWithIndex((element, at) -> {
                        boolean last = at == array.elements().size() - 1;
                        List<Span> row = cells.get(at)
                                .zipWithIndex((cell, column) -> List.of(
                                        cell(cell, widths.get(column)),
                                        Span.raw(column == columns.size() - 1 ? "" : COLUMN_GAP)))
                                .flatMap(spans -> spans);
                        return new JsonRow(
                                line(List.of(
                                                dim(prefix + connector(last, top)),
                                                dim(padLeft(String.valueOf(at), digits)),
                                                Span.raw(GAP))
                                        .appendAll(row)),
                                Option.of(JsonFolds.child(pointer, String.valueOf(at))),
                                range(element.line(), end(element)));
                    })
                    .prepend(keys);
        }

        /**
         * The rows of the array of scalars {@code array}, as many of its values on each as fit in the width, each
         * named by the indices it holds.
         */
        private List<JsonRow> packed(JsonTree.JsonArray array, String pointer, String prefix, boolean top) {
            List<JsonTree.Scalar> values = array.elements().map(element -> (JsonTree.Scalar) element);
            int digits = String.valueOf(values.size() - 1).length();
            int label = 2 * digits + 1;
            int room = Math.max(16, width - prefix.length() - GUIDES.branch().length() - label - GAP.length());
            List<List<Integer>> rows = values.zipWithIndex().foldLeft(List.<List<Integer>>empty(), (done, value) -> {
                int used = done.lastOption()
                        .map(row -> row.map(at -> JsonFolds.width(values.get(at)) + 2)
                                .sum()
                                .intValue())
                        .getOrElse(Integer.MAX_VALUE);
                int needs = JsonFolds.width(value._1) + 2;
                return done.isEmpty() || used + needs > room
                        ? done.append(List.of(value._2))
                        : done.init().append(done.last().append(value._2));
            });
            return rows.zipWithIndex((row, at) -> {
                boolean last = at == rows.size() - 1;
                String indices = row.size() == 1 ? "" + row.head() : row.head() + "–" + row.last();
                List<Span> shown = row.zipWithIndex((index, column) ->
                                List.of(column == 0 ? Span.raw("") : dim(", "), scalar(values.get(index))))
                        .flatMap(spans -> spans);
                return new JsonRow(
                        line(List.of(dim(prefix + connector(last, top)), dim(pad(indices, label)), Span.raw(GAP))
                                .appendAll(shown)),
                        Option.of(JsonFolds.child(pointer, String.valueOf(row.head()))),
                        row.map(index -> values.get(index).line()).distinct());
            });
        }

        /**
         * The spans of {@code tree}, a container of scalars, on one line.
         */
        private List<Span> oneLine(JsonTree tree) {
            boolean object = tree instanceof JsonTree.JsonObject;
            List<List<Span>> parts = object
                    ? ((JsonTree.JsonObject) tree)
                            .fields()
                            .map(field ->
                                    List.of(Span.raw(field.key()), dim(": "), scalar((JsonTree.Scalar) field.value())))
                    : ((JsonTree.JsonArray) tree).elements().map(element -> List.of(scalar((JsonTree.Scalar) element)));
            List<Span> between = parts.zipWithIndex((part, at) -> at == 0 ? part : part.prepend(dim(", ")))
                    .flatMap(spans -> spans);
            return between.prepend(dim(object ? "{" : "[")).append(dim(object ? "}" : "]"));
        }

        private Span scalar(JsonTree.Scalar scalar) {
            return Span.styled(text(scalar), styles.of(syntax(scalar.kind())));
        }

        private Span cell(JsonTree.Scalar scalar, int width) {
            String text = cellText(scalar);
            String padded = scalar.kind() == JsonTree.Kind.NUMBER ? padLeft(text, width) : pad(text, width);
            return Span.styled(padded, styles.of(syntax(scalar.kind())));
        }

        private Span dim(String text) {
            return Span.styled(text, styles.of(Theme.Classes.DIM));
        }

        private static Line line(List<Span> spans) {
            return Line.from(spans.filter(span -> !span.isEmpty()).toJavaList());
        }
    }

    /**
     * What a node holds, as the rows after it show it: a field with its key, an element without one.
     */
    private record Entry(Option<String> key, String pointer, int line, JsonTree value, List<Integer> ignored) {}
}
