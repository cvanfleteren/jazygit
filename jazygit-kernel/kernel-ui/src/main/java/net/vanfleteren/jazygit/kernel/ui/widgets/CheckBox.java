package net.vanfleteren.jazygit.kernel.ui.widgets;

import static dev.tamboui.toolkit.Toolkit.*;

import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.StyledElement;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.bindings.KeyTrigger;
import io.vavr.collection.List;
import net.vanfleteren.jazygit.kernel.ui.theme.Theme;

/**
 * A box of a form that is ticked or not, as whether a pull request is a draft (ADR 18, 59). The box itself takes the
 * focus, which the themes show by reversing it, as a cursor, and in the border of the {@link #line line} it is on, as
 * for the other fields. Space or Enter on it ticks or unticks it.
 */
public final class CheckBox {

    /**
     * The class of a box, which the themes reverse when it has the focus.
     */
    public static final String CLASS = "checkbox";

    private static final int WIDTH = 3;
    // The line, and the border.
    private static final int HEIGHT = 3;

    private CheckBox() {}

    /**
     * The box, ticked or not.
     */
    public static String box(boolean ticked) {
        return ticked ? "[x]" : "[ ]";
    }

    /**
     * The box {@code id}, ticked or not, which calls {@code toggled} on Space or Enter; the other keys are those of
     * every field of the form.
     */
    public static StyledElement<?> of(String id, boolean ticked, Runnable toggled, FormKeys keys) {
        return text(box(ticked))
                .id(id)
                .addClass(CLASS)
                .focusable()
                .length(WIDTH)
                .onKeyEvent(event -> {
                    if (event.isConfirm() || KeyTrigger.ch(' ').matches(event)) {
                        toggled.run();
                        return EventResult.HANDLED;
                    }
                    return keys.handle(event);
                });
    }

    /**
     * The box, ticked or not, dimmed as it cannot be changed; it takes no focus, so Tab goes past it.
     */
    public static StyledElement<?> fixed(boolean ticked) {
        return text(box(ticked)).addClass(Theme.Classes.DIM).length(WIDTH);
    }

    /**
     * What a box is for, after it.
     */
    public static StyledElement<?> label(String text) {
        return text(" " + text);
    }

    /**
     * {@code parts}, boxes and what they are for, on one line in a border that shows whether one of the boxes
     * {@code ids} has the focus.
     */
    public static Element line(List<StyledElement<?>> parts, List<String> ids) {
        return new FocusBordered(
                panel(row(parts.toJavaArray(StyledElement[]::new))).length(HEIGHT), ids);
    }
}
