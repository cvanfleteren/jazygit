package net.vanfleteren.jazygit.kernel.ui.widgets;

import dev.tamboui.toolkit.Toolkit;
import dev.tamboui.toolkit.element.StyledElement;

/**
 * A coloured symbol, as of how the checks or the review of a change are doing (ADR 48): told apart by its shape as
 * well as its colour (ADR 45).
 *
 * @param text     the symbol, one column wide where it stands for a state
 * @param cssClass the class of its colour
 */
public record Symbol(String text, String cssClass) {

    /**
     * The symbol as it is drawn.
     */
    public StyledElement<?> element() {
        return Toolkit.text(text).addClass(cssClass);
    }
}
