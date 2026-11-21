package net.vanfleteren.jazygit.app.keys;

import net.vanfleteren.jazygit.kernel.ui.Feature;
import net.vanfleteren.jazygit.kernel.ui.keys.Legends;

/**
 * What the features add to the legends of the panels, in the order of {@link Features#ALL} (ADR 168). It is the
 * {@link Legends#standard()} legends, given to the help as a service, as the keymap is (ADR 136).
 */
public final class StandardLegends implements Legends.Provider {

    private static final Legends LEGENDS = new Legends(Features.ALL.flatMap(Feature::legends));

    @Override
    public Legends legends() {
        return LEGENDS;
    }
}
