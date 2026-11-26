package net.vanfleteren.jazygit.kernel.ui.widgets;

import static dev.tamboui.toolkit.Toolkit.*;

import dev.tamboui.toolkit.element.Element;
import dev.tamboui.widgets.input.TextInputState;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.kernel.ui.edit.LineEditInput;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;

/**
 * The one-line title field of a form whose text lives in the model, as the title of a pull request (ADR 18, 59): it
 * knows the readline keys, Enter in it submits the form, and every change is sent to the model. The field only keeps
 * where its cursor is.
 */
public final class TitleField {

    // The line, and the border.
    private static final int HEIGHT = 3;

    private final TextInputState state = new TextInputState();
    private final String id;
    private final String label;
    private final String placeholder;

    /**
     * @param id          the id of the field
     * @param label       the key of the text in its border
     * @param placeholder the key of the text it shows while empty
     */
    public TitleField(String id, String label, String placeholder) {
        this.id = id;
        this.label = label;
        this.placeholder = placeholder;
    }

    /**
     * The text in the field, with every key typed so far, also those typed since the model was last rendered.
     */
    public String text() {
        return state.text();
    }

    /**
     * The field showing {@code shown}, which the model has: what was typed in it, or what the form opened with, after
     * which the cursor goes to its end.
     *
     * @param changed told the text whenever a key or a paste changed it
     */
    public Element render(String shown, FormKeys keys, Consumer<String> changed) {
        if (!state.text().equals(shown)) {
            state.setText(shown);
            state.moveCursorToEnd();
        }
        return new LineEditInput(
                        textInput(state)
                                .title(Messages.get(label))
                                .placeholder(Messages.get(placeholder))
                                .length(HEIGHT)
                                .onSubmit(keys.submit()::run)
                                .onKeyEvent(keys::handle),
                        state,
                        id)
                .onChange(changed);
    }
}
