package net.vanfleteren.jazygit.features.changes.cherrypick;

import net.vanfleteren.jazygit.kernel.ui.Feature;
import net.vanfleteren.jazygit.kernel.ui.FeatureAction;

/**
 * Copying commits and cherry-picking them onto the branch checked out.
 */
public final class CherryPickFeature {

    /**
     * What the app knows of it: its keys and its popups.
     */
    public static final Feature FEATURE =
            Feature.of(new FeatureAction(CherryPickKeys::register, CherryPickDialogs::popups));

    private CherryPickFeature() {}
}
