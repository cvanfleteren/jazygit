package net.vanfleteren.jazygit.changes.fetch;

import net.vanfleteren.jazygit.ui.Feature;

/**
 * Fetching from the remotes.
 */
public final class FetchFeature {

    /**
     * What the app knows of it: its keys and its popups.
     */
    public static final Feature FEATURE = Feature.of(FetchAction.ACTION);

    private FetchFeature() {}
}
