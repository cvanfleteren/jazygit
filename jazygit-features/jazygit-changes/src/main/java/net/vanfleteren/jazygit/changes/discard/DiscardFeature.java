package net.vanfleteren.jazygit.changes.discard;

import net.vanfleteren.jazygit.ui.keys.Feature;
import net.vanfleteren.jazygit.ui.keys.FeatureAction;

/**
 * Discarding the changes to files.
 */
public final class DiscardFeature {

    /**
     * What the app knows of it: its keys and its popups.
     */
    public static final Feature FEATURE = Feature.of(new FeatureAction(DiscardKeys::register, DiscardDialog::popups));

    private DiscardFeature() {}
}
