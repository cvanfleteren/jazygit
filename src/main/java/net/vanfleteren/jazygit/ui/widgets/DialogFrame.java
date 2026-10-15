package net.vanfleteren.jazygit.ui.widgets;

import static dev.tamboui.toolkit.Toolkit.*;

import dev.tamboui.style.Overflow;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.StyledElement;
import dev.tamboui.toolkit.elements.DialogElement;
import dev.tamboui.widgets.paragraph.Paragraph;
import io.vavr.collection.List;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.ui.Theme;

/**
 * The frame every popup is drawn in: a rounded border with the title, padding, and optionally a dimmed hint
 * about the keys under the content.
 */
public final class DialogFrame {

    /**
     * The width of a popup that lists choices, unless they need more.
     */
    public static final int LIST_WIDTH = 40;

    /**
     * The columns the border and the padding take, on both sides together.
     */
    public static final int FRAME = 2 + 2;

    private DialogFrame() {}

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
        return Math.min(maxLines, ConfirmDialog.lines(text, width - FRAME));
    }

    /**
     * A popup showing {@code content}.
     */
    public static DialogElement of(String title, int width, Element... content) {
        return dialog(title, content).rounded().width(width).padding(1);
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
