package net.vanfleteren.jazygit.kernel.ui.content;

import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.kernel.ui.theme.Theme;

/**
 * Lines a source puts above what another source shows next to the same pane, as the reflog says which of its entries
 * the commit shown is (ADR 187). A value, as a body is.
 *
 * @param lines the lines, each of its parts
 */
public record Heading(List<List<Part>> lines) {

    /**
     * The lines, drawn with {@code styles}.
     */
    public List<Line> drawn(Theme.SpanStyles styles) {
        return lines.map(
                parts -> Line.from(parts.map(part -> part.drawn(styles)).toJavaList()));
    }

    /**
     * How many lines it has.
     */
    public int height() {
        return lines.size();
    }

    /**
     * Some text of a line, styled by the class {@code style} if it has one.
     *
     * @param text  the text
     * @param style the class of the theme it is drawn in; none for the plain colour
     */
    public record Part(String text, Option<String> style) {

        /**
         * {@code text} in the plain colour.
         */
        public static Part of(String text) {
            return new Part(text, Option.none());
        }

        /**
         * {@code text} in the class {@code style}.
         */
        public static Part styled(String text, String style) {
            return new Part(text, Option.of(style));
        }

        private Span drawn(Theme.SpanStyles styles) {
            return style.map(css -> Span.styled(text, styles.of(css))).getOrElse(() -> Span.raw(text));
        }
    }
}
