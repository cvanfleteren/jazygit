package net.vanfleteren.jazygit.feature.copy;

import net.vanfleteren.jazygit.feature.Feature;

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
