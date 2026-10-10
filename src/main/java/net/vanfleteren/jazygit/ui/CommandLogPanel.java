package net.vanfleteren.jazygit.ui;

import static dev.tamboui.toolkit.Toolkit.*;

import dev.tamboui.style.Color;
import dev.tamboui.style.Overflow;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.elements.Panel;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Model;

/**
 * Right-hand panel under the content panel, logging what the commands did. For now it only shows why the
 * last operation failed, when it did. It is display-only and does not take part in focus cycling.
 */
public final class CommandLogPanel {

    private CommandLogPanel() {
    }

    public static Panel render(Model model) {
        Element[] lines = model.error()
                .map(error -> new Element[]{text(error).red().overflow(Overflow.WRAP_WORD)})
                .orElseGet(() -> new Element[]{});
        return panel(Messages.get("panel.commandLog.title"), lines)
                .rounded()
                .borderColor(Color.WHITE);
    }
}
