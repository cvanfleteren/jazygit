package net.vanfleteren.jazygit.kernel.state.settings;

import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.Update;

/**
 * What the user does with a list of the settings, its entries and the form of one (ADR 164).
 */
public sealed interface ListMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return ListChange.update(model, this);
    }

    /**
     * The user opened {@code list} from the row of the settings panel with the letter {@code row}.
     */
    record Opened(ListSetting<?> list, char row) implements ListMsg {}

    /**
     * The user closed the list, back to the settings panel.
     */
    record Closed() implements ListMsg {}

    /**
     * The user asked for a new entry at the end of the list.
     */
    record AddRequested() implements ListMsg {}

    /**
     * The user asked to edit the entry at {@code index}.
     */
    record EditRequested(int index) implements ListMsg {}

    /**
     * The user deleted the entry at {@code index}.
     */
    record Deleted(int index) implements ListMsg {}

    /**
     * The user moved the entry at {@code index} {@code by} places, up when negative.
     */
    record Moved(int index, int by) implements ListMsg {}

    /**
     * The user picked the field at {@code field} of the form.
     */
    record FieldPicked(int field) implements ListMsg {}

    /**
     * The user confirmed the value typed for a field.
     */
    record FieldEntered() implements ListMsg {}

    /**
     * The user cancelled typing the value of a field, back to the form.
     */
    record FieldCancelled() implements ListMsg {}

    /**
     * The user saved the form.
     */
    record FormSaved() implements ListMsg {}

    /**
     * The user cancelled the form, back to the list.
     */
    record FormCancelled() implements ListMsg {}
}
