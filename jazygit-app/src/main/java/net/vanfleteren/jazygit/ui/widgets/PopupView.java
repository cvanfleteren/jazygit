package net.vanfleteren.jazygit.ui.widgets;

import dev.tamboui.toolkit.element.Element;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.state.Model;

/**
 * A dialog that shows while the model has its popup open.
 */
public interface PopupView {

    /**
     * The dialog, while the model has its popup open.
     */
    Option<Element> render(Model model);

    /**
     * The ids of the dialog's focusable parts; the first one gets the focus when it opens.
     */
    List<String> ids();
}
