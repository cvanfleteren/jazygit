package net.vanfleteren.jazygit.changes.diff;

import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;

/**
 * Choosing the diff filters (ADR 144).
 */
public sealed interface DiffFiltersMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return DiffFiltersUpdate.update(model, this);
    }

    /**
     * The user asked for the popup of the diff filters.
     */
    record Requested() implements DiffFiltersMsg {}

    /**
     * The user closed the popup of the diff filters, going back to the one of the diff options.
     */
    record Closed() implements DiffFiltersMsg {}

    /**
     * The user switched the filter with the id {@code id} on or off.
     */
    record Toggled(String id) implements DiffFiltersMsg {}

    /**
     * The user switched all the filters off, or on again, for this session.
     */
    record AllToggled() implements DiffFiltersMsg {}
}
