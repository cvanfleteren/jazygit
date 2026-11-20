package net.vanfleteren.jazygit.kernel.state.settings;

import net.vanfleteren.jazygit.kernel.state.popup.TextPopup;

/**
 * Typing the value of a field of the form that edits an entry of a list of the settings (ADR 164).
 *
 * @param form  the form, which confirming or cancelling goes back to
 * @param field where the field is among the form's fields
 * @param text  the value as far as it was typed
 */
public record FieldPopup(FormPopup<?> form, int field, String text) implements TextPopup {

    @Override
    public FieldPopup withText(String text) {
        return new FieldPopup(form, field, text);
    }

    /**
     * This field, of {@code form}.
     */
    public FieldPopup withForm(FormPopup<?> form) {
        return new FieldPopup(form, field, text);
    }
}
