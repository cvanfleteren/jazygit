package net.vanfleteren.jazygit.feature.help;

import net.vanfleteren.jazygit.feature.Feature;
import net.vanfleteren.jazygit.feature.FeatureAction;

/**
 * The help, which lists the keys of the panel that has the focus.
 */
public final class HelpFeature {

    /**
     * What the app knows of it: its keys and its popups.
     */
    public static final Feature FEATURE = Feature.of(FeatureAction.ofPopups(HelpDialog::popups));

    private HelpFeature() {}
}
