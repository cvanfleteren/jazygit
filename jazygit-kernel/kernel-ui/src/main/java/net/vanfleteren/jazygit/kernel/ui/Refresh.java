package net.vanfleteren.jazygit.kernel.ui;

import java.time.Duration;

/**
 * How often the app looks for changes made outside it: its own timer reads the status, and a feature that looks at
 * what it shows for such a change, as the reflog does, looks as often (ADR 125, 186).
 */
public final class Refresh {

    /**
     * In milliseconds, from {@code jazygit.refreshInterval}: the acceptance tests look faster, as each of their steps
     * waits for the app to see a change. Every 3 s rather than every second: reading the status was most of what an
     * idle app cost (ADR 125).
     */
    public static final Duration INTERVAL = Duration.ofMillis(Long.getLong("jazygit.refreshInterval", 3000));

    private Refresh() {}
}
