package net.vanfleteren.jazygit.features.changes.diff;

import net.vanfleteren.jazygit.core.git.model.DiffContext;
import net.vanfleteren.jazygit.kernel.settings.DiffStyle;
import net.vanfleteren.jazygit.kernel.state.popup.Popup;
import net.vanfleteren.jazygit.kernel.state.preferences.DiffFilters;
import net.vanfleteren.jazygit.kernel.state.preferences.Preferences;

/**
 * The popup of the diff options, to change how the diffs are read and drawn, and to get to the diff filters.
 *
 * @param style            how the diffs are drawn
 * @param words            whether the rich view highlights the words that changed in a line
 * @param ignoreWhitespace whether changes to whitespace only are left out, for this session
 * @param context          how many lines show around a change, for this session
 * @param filters          the diff filters, which are enabled, and whether all are switched off
 */
public record DiffOptionsPopup(
        DiffStyle style, boolean words, boolean ignoreWhitespace, DiffContext context, DiffFilters filters)
        implements Popup {

    /**
     * The popup showing the options of {@code preferences}.
     */
    public static DiffOptionsPopup of(Preferences preferences) {
        return new DiffOptionsPopup(
                preferences.settings().diffStyle(),
                preferences.settings().diffWords(),
                preferences.ignoreWhitespace(),
                preferences.context(),
                preferences.diffFilters());
    }
}
