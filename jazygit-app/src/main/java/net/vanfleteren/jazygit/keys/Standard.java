package net.vanfleteren.jazygit.keys;

import net.vanfleteren.jazygit.feature.Features;

/**
 * The keys of the features, in the order of {@link Features#ALL}, which is the order in which the keys of a panel are
 * listed, and then those that work wherever the focus is.
 */
final class Standard {

    static final Keymap KEYMAP =
            GlobalKeys.register(Features.ALL.foldLeft(Keymap.empty(), (keymap, feature) -> feature.keys(keymap)));

    private Standard() {}
}
