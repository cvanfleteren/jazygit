package net.vanfleteren.jazygit.features.changes.copy;

import net.vanfleteren.jazygit.kernel.ui.Feature;

/**
 * Copying what a diff shows.
 */
public final class CopyFeature {

    /**
     * What the app knows of it: its keys and its popups.
     */
    public static final Feature FEATURE = Feature.of(CopyAction.ACTION);

    private CopyFeature() {}
}
