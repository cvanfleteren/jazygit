package net.vanfleteren.jazygit.kernel.ui.widgets;

import static dev.tamboui.toolkit.Toolkit.*;

import dev.tamboui.toolkit.element.Element;
import dev.tamboui.widgets.input.TextAreaState;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;

/**
 * The body field of a form whose text lives in the model, as the body of a pull request (ADR 18, 59): ten lines high,
 * Enter adds a line, Ctrl+S submits the form, and every change is sent to the model. The field only keeps where its
 * cursor is.
 */
public final class BodyField {

    // The lines shown at once, past which the body scrolls.
    private static final int ROWS = 10;

    private final TextAreaState state = new TextAreaState();
    private final String id;
    private final String label;
    private final String placeholder;

    /**
     * @param id          the id of the field
     * @param label       the key of the text in its border
     * @param placeholder the key of the text it shows while empty
     */
    public BodyField(String id, String label, String placeholder) {
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
     * The field showing {@code shown}, which the model has: what was typed in it, or what the form opened with.
     *
     * @param changed told the text whenever a key or a paste changed it
     */
    public Element render(String shown, FormKeys keys, Consumer<String> changed) {
        if (!state.text().equals(shown)) {
            state.setText(shown);
        }
        return new SubmittableTextArea(
                        textArea(state).title(Messages.get(label)).placeholder(Messages.get(placeholder)),
                        state,
                        id,
                        keys.submit())
                .onChange(changed)
                .rows(ROWS)
                .onKeyEvent(keys::handle);
    }
}
