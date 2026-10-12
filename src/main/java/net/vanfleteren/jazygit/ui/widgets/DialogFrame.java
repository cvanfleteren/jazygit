package net.vanfleteren.jazygit.ui.widgets;

import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.elements.DialogElement;
import io.vavr.collection.List;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.ui.Theme;

import static dev.tamboui.toolkit.Toolkit.dialog;
import static dev.tamboui.toolkit.Toolkit.text;

/**
 * The frame every popup is drawn in: a rounded border with the title, padding, and optionally a dimmed hint
 * about the keys under the content.
 */
public final class DialogFrame {

    private DialogFrame() {
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
        return of(title, width, content.<Element>map(element -> element)
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
