package net.vanfleteren.jazygit.kernel.ui.widgets;

import static dev.tamboui.toolkit.Toolkit.row;
import static dev.tamboui.toolkit.Toolkit.text;

import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.StyledElement;
import io.vavr.collection.List;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;
import net.vanfleteren.jazygit.kernel.ui.text.TextWidth;
import net.vanfleteren.jazygit.kernel.ui.theme.Theme;

/**
 * The lines of a dialog that shows a change as labelled rows, as the pull requests of every forge do (ADR 175): each
 * label right-aligned in a column, dimmed, then two spaces and the value; what goes on below a value starts under it.
 */
public final class LabelledLines {

    /**
     * How many columns the labels are right-aligned in.
     */
    public static final int LABEL_COLUMN = 11;

    /**
     * What a line starts with to begin under the values.
     */
    public static final String INDENT = " ".repeat(LABEL_COLUMN + 2);

    private LabelledLines() {}

    /**
     * The label of {@code labelKey}, right-aligned in the label column and dimmed, with the two spaces after it.
     */
    public static StyledElement<?> label(String labelKey) {
        return text(TextWidth.alignRight(Messages.get(labelKey), LABEL_COLUMN) + "  ")
                .addClass(Theme.Classes.DIM);
    }

    /**
     * The elements of {@code lines}, with blank lines after them to take {@code height} lines; as many as there are
     * when they take more.
     */
    public static List<Element> filled(List<Line> lines, int height) {
        int blanks = height - lines.map(Line::height).sum().intValue();
        return lines.appendAll(List.fill(Math.max(0, blanks), Line::blank)).map(Line::element);
    }

    /**
     * A part of the dialog, and how many lines it takes.
     */
    public record Line(Element element, int height) {

        public static Line of(StyledElement<?> element) {
            return new Line(element, 1);
        }

        public static Line blank() {
            return of(text(""));
        }

        /**
         * {@code text}, wrapped at the content width of a dialog {@code width} wide, and cut after {@code maxLines}.
         */
        public static Line wrapped(String text, String cssClass, int width, int maxLines) {
            return new Line(
                    DialogFrame.wrapped(text, width, maxLines).addClass(cssClass),
                    DialogFrame.wrappedHeight(text, width, maxLines));
        }

        /**
         * A row with {@code labelKey}'s label in the label column and {@code value} next to it.
         */
        public static Line labelled(String labelKey, StyledElement<?>... value) {
            return labelled(labelKey, List.of(value));
        }

        /**
         * A row with {@code labelKey}'s label in the label column and {@code value} next to it.
         */
        public static Line labelled(String labelKey, List<StyledElement<?>> value) {
            return pieces(List.<StyledElement<?>>of(label(labelKey)).appendAll(value));
        }

        /**
         * A row of {@code value} under the values.
         */
        public static Line indented(List<StyledElement<?>> value) {
            return pieces(List.<StyledElement<?>>of(text(INDENT)).appendAll(value));
        }

        public static Line pieces(List<StyledElement<?>> pieces) {
            return of(pieces.size() == 1 ? pieces.head() : row(pieces.toJavaArray(StyledElement[]::new)));
        }
    }
}
