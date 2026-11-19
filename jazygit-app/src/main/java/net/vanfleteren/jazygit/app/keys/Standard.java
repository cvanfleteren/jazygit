package net.vanfleteren.jazygit.app.keys;

import net.vanfleteren.jazygit.features.chrome.help.HelpMsg;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap;

/**
 * The keys of the features, in the order of {@link Features#ALL}, which is the order in which the keys of a panel are
 * listed, and then those that work wherever the focus is. {@code ?} asks for the help of the panel. It is the
 * {@link Keymap#standard()} keymap, given to the views as a service (ADR 136).
 */
public final class Standard implements Keymap.Source {

    private static final Keymap KEYMAP = GlobalKeys.register(
            Features.ALL.foldLeft(Keymap.withHelp(HelpMsg.Requested::new), (keymap, feature) -> feature.keys(keymap)));

    @Override
    public Keymap keymap() {
        return KEYMAP;
    }
}
