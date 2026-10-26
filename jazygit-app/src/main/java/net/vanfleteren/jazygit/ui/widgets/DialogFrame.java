package net.vanfleteren.jazygit.ui.widgets;

import static dev.tamboui.toolkit.Toolkit.*;

import dev.tamboui.style.Overflow;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.StyledElement;
import dev.tamboui.toolkit.elements.DialogElement;
import dev.tamboui.widgets.paragraph.Paragraph;
import io.vavr.collection.List;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.ui.TextWidth;
import net.vanfleteren.jazygit.ui.Theme;

/**
 * The frame every popup is drawn in: a rounded border with the title, and optionally a dimmed hint
 * about the keys under the content.
 */
public final class DialogFrame {

    /**
     * The width of a popup that lists choices, unless they need more.
     */
    public static final int LIST_WIDTH = 40;

    /**
     * The columns the border takes, on both sides together. There is no padding sideways: the content is drawn right
     * inside the border.
     */
    public static final int FRAME = 2;

    private DialogFrame() {}

    /**
     * The columns inside the border of a popup {@code width} wide, which content that fills its width draws in.
     */
    public static int contentWidth(int width) {
        return width - FRAME;
    }

    /**
     * {@code text}, wrapped at the content width of a popup {@code width} wide, and cut after {@code maxLines}.
     */
    public static StyledElement<?> wrapped(String text, int width, int maxLines) {
        return widget(Paragraph.builder()
                        .text(text)
                        .overflow(Overflow.WRAP_WORD)
                        .build())
                .length(wrappedHeight(text, width, maxLines));
    }

    /**
     * The lines {@link #wrapped} takes.
     */
    public static int wrappedHeight(String text, int width, int maxLines) {
        return TextWidth.lines(text, contentWidth(width), maxLines);
    }

    /**
     * A popup showing {@code content}.
     */
    public static DialogElement of(String title, int width, Element... content) {
        return dialog(title, content).rounded().width(width);
    }

    /**
     * A popup showing {@code content}, a blank line, and the hint {@code hintKey} says.
     */
    public static DialogElement withHint(String title, int width, String hintKey, List<? extends Element> content) {
        return of(
                title,
                width,
                content.<Element>map(element -> element)
                        .append(text(""))
                        .append(hint(hintKey))
                        .toJavaArray(Element[]::new));
    }

    /**
     * The dimmed text of the hint {@code hintKey}.
     */
    public static Element hint(String hintKey) {
        return text(Messages.get(hintKey)).addClass(Theme.Classes.DIM);
    }
}
