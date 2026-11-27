package net.vanfleteren.jazygit.features.changes.fetch;

import java.time.Duration;
import net.vanfleteren.jazygit.kernel.ui.Feature;

/**
 * Fetching from the remotes.
 */
public final class FetchFeature {

    /**
     * How often it fetches, also while offline.
     */
    private static final Duration FETCH_INTERVAL = Duration.ofMinutes(1);

    /**
     * What the app knows of it: its keys, its popups, its timer, and that it fetches when a feature changed a remote
     * (ADR 210).
     */
    public static final Feature FEATURE = Feature.of(FetchAction.ACTION)
            .every(FETCH_INTERVAL, FetchMsg.Tick::new)
            .reacting(FetchUpdate::reacting);

    private FetchFeature() {}
}
