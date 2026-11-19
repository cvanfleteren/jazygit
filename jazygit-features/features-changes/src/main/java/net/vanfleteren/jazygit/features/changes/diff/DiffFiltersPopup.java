package net.vanfleteren.jazygit.features.changes.diff;

import net.vanfleteren.jazygit.kernel.state.popup.Popup;
import net.vanfleteren.jazygit.kernel.state.preferences.DiffFilters;

/**
 * The popup of the diff filters, to switch them on and off (ADR 144).
 *
 * @param filters the filters to choose from, which are enabled, and whether all are switched off
 */
public record DiffFiltersPopup(DiffFilters filters) implements Popup {}
