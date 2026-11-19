package net.vanfleteren.jazygit.content.diff;

import dev.tamboui.text.Line;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Vector;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.state.diff.Rendered;
import net.vanfleteren.jazygit.state.diff.Tree;
import net.vanfleteren.jazygit.state.diff.TreeFolds;
import net.vanfleteren.jazygit.ui.i18n.Messages;
import net.vanfleteren.jazygit.ui.theme.Theme;

/**
 * A version of a JSON, a YAML or an XML file rendered (ADR 122, 123, 151): a tree of rows, its guides as tamboui draws
 * those of a tree, a container of scalars that fits on one line, a long sequence of scalars packed into as many values
 * a row as the width holds, a sequence of records as a table, a mapping in a sequence named by one of its fields, a
 * block of YAML as its lines, each document of a file of more than one as a node, an element of XML with its
 * attributes, its text, its comments and its records as a table, and each node that folds open or closed as the user
 * chose. Each row knows the lines of the source it stands for, and the node it is, which the cursor stops on.
 *
 * <p>Why a file is not rendered {@link TreeUnread} says; {@link TreeDrawing} draws the rows of a tree, and
 * {@link XmlDrawing} those of XML, with what {@link TreeText} writes of its nodes.
 */
final class RenderedTree {

    private RenderedTree() {}

    /**
     * The rows of the {@code lines} of a file of {@code kind}, JSON, YAML or XML, packed to {@code width} columns, its
     * nodes open as {@code chose} says ({@link TreeFolds#open}), styled as {@code styles} says; or, when they cannot be
     * read, or are {@code cut} short, the line that says why, to show over them as source.
     */
    static Either<String, List<TreeRow>> rows(
            Rendered kind,
            Vector<String> lines,
            boolean cut,
            int width,
            Map<String, Boolean> chose,
            Theme.SpanStyles styles) {
        return read(kind, lines, cut, width, chose, styles).mapLeft(NotRendered::why);
    }

    /**
     * As {@link #rows}, but when they cannot be read, also where reading stopped, when that is known.
     */
    static Either<NotRendered, List<TreeRow>> read(
            Rendered kind,
            Vector<String> lines,
            boolean cut,
            int width,
            Map<String, Boolean> chose,
            Theme.SpanStyles styles) {
        if (cut) {
            return Either.left(new NotRendered(
                    Messages.get(
                            switch (kind) {
                                case YAML -> "content.yaml.cut";
                                case XML -> "content.xml.cut";
                                default -> "content.json.cut";
                            }),
                    Option.none()));
        }
        return Tree.read(kind, lines.mkString("\n"))
                .mapLeft(unread -> new NotRendered(TreeUnread.why(unread), TreeUnread.place(unread)))
                .map(documents -> new TreeDrawing(width, chose, styles).documents(documents));
    }

    /**
     * Why a version is not rendered, and where in its source reading stopped, when that is known.
     *
     * @param why what the line over its source says
     */
    record NotRendered(String why, Option<Place> at) {}

    /**
     * A place in the source of a version.
     *
     * @param line   counted from 1
     * @param column counted from 1
     */
    record Place(int line, int column) {}

    /**
     * A row of a version of JSON, YAML or XML rendered.
     *
     * @param line    what it draws
     * @param pointer the path of what it draws, which the cursor stops on; none for the first row of a table, which
     *                is part of the row before it
     * @param lines   the lines of the source it stands for, counted from 1, the one it starts on first
     */
    record TreeRow(Line line, Option<String> pointer, List<Integer> lines) {}
}
